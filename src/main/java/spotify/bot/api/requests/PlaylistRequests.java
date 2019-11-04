package spotify.bot.api.requests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlayHistory;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.BotLogger;
import spotify.bot.config.Config;
import spotify.bot.config.DiscoveryDatabase;
import spotify.bot.config.Config.UpdateStore;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class PlaylistRequests {

	@Autowired
	private SpotifyApiWrapper spotify;
	
	@Autowired
	private Config config;
	
	@Autowired
	private DiscoveryDatabase database;

	@Autowired
	private BotLogger log;
	
	@Autowired
	private OfflineRequests offlineRequests;
	
	@Autowired
	private PlaylistRequests playlistRequests;
	
	/**
	 * Timestamp all given playlists that DON'T have any songs added and potentially remove the notifier
	 * 
	 * @param songsAddedPerAlbumGroup
	 */
	public void timestampUnchangedPlaylistsAndCheckForObsoleteNotifiers(Map<AlbumGroup, Integer> songsAddedPerAlbumGroup) {
		songsAddedPerAlbumGroup.entrySet().parallelStream().filter(e -> e.getValue() == 0).forEach(sapat -> {
			timestampSinglePlaylistAndSetNotifier(sapat.getKey(), sapat.getValue());
		});
	}
	
	/**
	 * Timestamp the playlist's description with the last time the crawling process was initiated and include the number of added songs
	 * 
	 * @param albumGroup
	 * @param addedSongsCount
	 * @throws Exception 
	 */
	public void timestampSinglePlaylistAndSetNotifier(AlbumGroup albumGroup, int addedSongsCount) {
		try {
			// Fetch the playlist
			String playlistId = BotUtils.getPlaylistIdByGroup(albumGroup);
			Playlist p = spotify.execute(spotify.api().getPlaylist(playlistId).build());
			String playlistName = p.getName();
			
			// Write the new description
			String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime()));
			
			// Set/unset the [NEW] indicator, depending on whether any songs were added
			if (addedSongsCount > 0) {
				if (!playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
					playlistName = String.format("%s %s", playlistName, Constants.NEW_INDICATOR_TEXT);
				}
				database.refreshUpdateStore(albumGroup.getGroup(), addedSongsCount);
			} else {
				if (playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
					if (shouldIndicatorBeMarkedAsRead(playlistId, albumGroup)) {
						playlistName = p.getName().replaceFirst(Constants.NEW_INDICATOR_TEXT, "").trim();
						database.unsetUpdateStore(albumGroup.getGroup());
					}
				}		
			}			
			spotify.execute(spotify.api().changePlaylistsDetails(playlistId).name(playlistName).description(newDescription).build());
		} catch (Exception e) {
			log.stackTrace(e);
		}
	}
	
	/**
	 * Check in increasingly complex ways whether a new song indicator is ready for removal. This starts with a simple timeout
	 * and grows toward checking the actual songs the user has recently listened to to mark them as "read".
	 * 
	 * @param playlistId
	 * @param albumGroup
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private boolean shouldIndicatorBeMarkedAsRead(String playlistId, AlbumGroup albumGroup) throws IOException, Exception {
		UpdateStore updateStore = config.getUpdateStoreByGroup(albumGroup.getGroup());
		Date lastUpdated = updateStore.getLastUpdatedTimestamp();
		Integer lastUpdateSongCount = updateStore.getLastUpdateSongCount();
		
		// Case 1: Indicator is already unset
		if (lastUpdated == null || lastUpdateSongCount == null) {
			return true;
		}
		
		// Case 2: Fallback timeout after a certain number of hours since the playlist was last updated
		int newNotificationTimeout = config.getNewNotificationTimeout();
		if (!BotUtils.isTimeoutActive(lastUpdated, newNotificationTimeout)) {
			return true;
		}
		
		// Case 3: Currently played song is in the context of the discovery playlist
		CurrentlyPlaying currentlyPlaying = spotify.execute(spotify.api().getUsersCurrentlyPlayingTrack().build());
		if (currentlyPlaying != null && currentlyPlaying.getContext().getUri().contains(playlistId)) {
			return true;
		}
		
		// Case 4: Currently played song is NOT in the context of the discovery playlist, but still part of the N most recently added songs
		// (Commonly because it was added to the queue or is played from the artist's page itself)
		PlaylistTrack[] recentlyAddedPlaylistTracks = spotify.execute(spotify.api().getPlaylistsTracks(playlistId).limit(Math.min(lastUpdateSongCount, Constants.DEFAULT_LIMIT)).build()).getItems();
		boolean currentlyPlayingSongIsNew = Arrays.asList(recentlyAddedPlaylistTracks).stream().anyMatch(pt -> pt.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
		if (currentlyPlayingSongIsNew) {
			return true;
		}
		
		// Case 5: Last resort, check if the playlist history contains a recently added playlist track or was played from the context of the playlist
		// (This should happen super rarely)		
		List<PlayHistory> playHistory = Arrays.asList(spotify.execute(spotify.api().getCurrentUsersRecentlyPlayedTracks().limit(Constants.DEFAULT_LIMIT).after(lastUpdated).build()).getItems());
		boolean newTrackWasRecentlyPlayedExternally = playHistory.stream().anyMatch(ph -> ph.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
		boolean newTrackWasRecentlyPlayedInContext = playHistory.stream().anyMatch(ph -> ph.getPlayedAt().after(lastUpdated) && ph.getContext().getUri().contains(playlistId));
		return (newTrackWasRecentlyPlayedExternally || newTrackWasRecentlyPlayedInContext);
	}
	
	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per release is used to retain order). May remove older songs to make room.
	 * 
	 * @param sortedNewReleases
	 * @param songs
	 * @return 
	 * @throws Exception 
	 */
	public int addSongsToPlaylist(List<AlbumTrackPair> albumTrackPairs, AlbumGroup albumGroup) {
		try {
			String playlistId = BotUtils.getPlaylistIdByGroup(albumGroup);
			return addSongsToPlaylistId(albumTrackPairs, playlistId);
		} catch (Exception e) {
			log.stackTrace(e);
		}
		return 0;
	}
	
	private int addSongsToPlaylistId(List<AlbumTrackPair> albumTrackPairs, String playlistId) throws Exception {
		circularPlaylistFitting(playlistId, albumTrackPairs.stream().mapToInt(AlbumTrackPair::trackCount).sum());
		int songsAdded = 0;
		if (!albumTrackPairs.isEmpty()) {
			for (AlbumTrackPair atp : albumTrackPairs) {
				for (List<TrackSimplified> partition : Lists.partition(atp.getTracks(), Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					spotify.execute(spotify.api().addTracksToPlaylist(playlistId, json).position(0).build());
					songsAdded += partition.size();
					Thread.sleep(Constants.PLAYLIST_ADDITION_COOLDOWN);
				}
			}
			return songsAdded;
		}
		return 0;
	}
	
	/**
	 * Check if circular playlist fitting is required (if enabled; otherwise an exception is thrown)
	 * 
	 * @param playlistId
	 * @param songsToAddCount
	 * @throws Exception
	 */
	private void circularPlaylistFitting(String playlistId, int songsToAddCount) throws Exception {
		Playlist p = spotify.execute(spotify.api().getPlaylist(playlistId).build());

		final int currentPlaylistCount = p.getTracks().getTotal();
		if (currentPlaylistCount + songsToAddCount > Constants.PLAYLIST_SIZE_LIMIT) {
			if (!config.isCircularPlaylistFitting()) {
				log.error(p.getName() + " is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ". Enable circularPlaylistFitting or flush the playlist for new songs.");
				return;
			}
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount, songsToAddCount);
		}
	}
	
	/**
	 * Delete as many songs from the bottom as necessary to make room for any new songs to add, as Spotify playlists have a fixed limit of 10000 songs.
	 * 
	 * If circularPlaylistFitting isn't enabled, an exception is thrown on a full playlist instead.
	 * 
	 * @param playlistId
	 * @param currentPlaylistCount
	 * @param songsToAddCount
	 * @throws Exception 
	 * @throws IOException 
	 */
	private void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws IOException, Exception {
		int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - Constants.PLAYLIST_SIZE_LIMIT;
		boolean repeat = totalSongsToDeleteCount > Constants.PLAYLIST_ADD_LIMIT;
		int songsToDeleteCount = repeat ? Constants.PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
		final int offset = currentPlaylistCount - songsToDeleteCount;
		
		List<PlaylistTrack> tracksToDelete = spotify.execute(new Callable<List<PlaylistTrack>>() {
			@Override
			public List<PlaylistTrack> call() throws Exception {
				List<PlaylistTrack> currentList = new ArrayList<>();
				Paging<PlaylistTrack> playlistTracks = null;
				do {
					GetPlaylistsTracksRequest.Builder request = spotify.api().getPlaylistsTracks(playlistId).offset(offset).limit(Constants.PLAYLIST_ADD_LIMIT);
					if (playlistTracks != null && playlistTracks.getNext() != null) {
						request = request.offset(playlistTracks.getOffset() + Constants.PLAYLIST_ADD_LIMIT);
					}
					playlistTracks = request.build().execute();
					currentList.addAll(Arrays.asList(playlistTracks.getItems()));
				} while (playlistTracks.getNext() != null);
				return currentList;
			}
		});
		
		JsonArray json = new JsonArray();				
		for (int i = 0; i < tracksToDelete.size(); i++) {
			JsonObject object = new JsonObject();
			object.addProperty("uri", Constants.TRACK_PREFIX + tracksToDelete.get(i).getTrack().getId());
			JsonArray positions = new JsonArray();
			positions.add(currentPlaylistCount - songsToDeleteCount + i);
			object.add("positions", positions);
			json.add(object);
		}
		
		spotify.execute(spotify.api().removeTracksFromPlaylist(playlistId, json).build());	
		
		// Repeat if more than 100 songs have to be added/deleted (should rarely happen, so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}

	/**
	 * Adds all releases to the set playlists. Reuse of album groups will result in those releases to be added to the same playlist.
	 * Playlists will get timestamped and receive a [NEW] indicator on new additions
	 * 
	 * @param newSongsByGroup
	 * @param setAlbumGroups
	 * @return
	 */
	public Map<AlbumGroup, Integer> addAllReleasesToSetPlaylists(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> setAlbumGroups) {
		Map<AlbumGroup, Integer> songsAddedPerAlbumGroups = new ConcurrentHashMap<>();
		Map<AlbumGroup, List<AlbumTrackPair>> mergedAlbumGroupsOfSongs = offlineRequests.mergeOnIdenticalPlaylists(newSongsByGroup, setAlbumGroups);
		mergedAlbumGroupsOfSongs.entrySet().parallelStream().forEach(entry -> {
			AlbumGroup albumGroup = entry.getKey();
			List<AlbumTrackPair> albumTrackPairs = entry.getValue();
			if (!albumTrackPairs.isEmpty()) {
				List<AlbumTrackPair> sortedAlbums = offlineRequests.sortReleases(albumTrackPairs);
				int addedSongsCount = playlistRequests.addSongsToPlaylist(sortedAlbums, albumGroup);
				songsAddedPerAlbumGroups.put(albumGroup, addedSongsCount);
				timestampSinglePlaylistAndSetNotifier(albumGroup, addedSongsCount);
			}
		});
		return songsAddedPerAlbumGroups;
	}
}
