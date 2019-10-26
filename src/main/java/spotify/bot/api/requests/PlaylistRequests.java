package spotify.bot.api.requests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlayHistory;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest;

import spotify.bot.Config;
import spotify.bot.Config.UpdateStore;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class PlaylistRequests {
	/**
	 * Static calls only
	 */
	private PlaylistRequests() {}
	
	/**
	 * Timestamp the playlist's description with the last time the crawling process was initiated and include the number of added songs
	 * 
	 * @param songsAddedPerAlbumType
	 * @throws Exception 
	 */
	public static void timestampPlaylistsAndSetNotifiers(Map<AlbumType, Integer> songsAddedPerAlbumType) throws Exception {
		songsAddedPerAlbumType.entrySet().parallelStream().forEach(entry -> {
			try {
				AlbumType at = entry.getKey();
				int addedSongs = entry.getValue();
				
				// Fetch the playlist
				String playlistId = BotUtils.getPlaylistIdByType(at);
				Playlist p = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylist(playlistId).build());
				String playlistName = p.getName();
								
				// Write the new description
				String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime()));
				
				// Set/unset the [NEW] indicator, depending on whether any songs were added
				if (addedSongs > 0) {
					if (!playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
						playlistName = String.format("%s %s", playlistName, Constants.NEW_INDICATOR_TEXT);
					}
					SpotifyBotDatabase.getInstance().refreshUpdateStore(at.getType(), addedSongs);
				} else {
					if (playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
						if (isIndicatorMarkedAsRead(playlistId, at)) {
							playlistName = p.getName().replaceFirst(Constants.NEW_INDICATOR_TEXT, "").trim();
							SpotifyBotDatabase.getInstance().unsetUpdateStore(at.getType());
						}
					}		
				}			
				SpotifyApiRequest.execute(SpotifyApiSessionManager.api().changePlaylistsDetails(playlistId).name(playlistName).description(newDescription).build());
			} catch (Exception e) {
				Config.logStackTrace(e);
			}
		});
	}
	
	/**
	 * Check in increasingly complex ways whether a new song indicator is ready for removal. This starts with a simple timeout
	 * and grows toward checking the actual songs the user has recently listened to to mark them as "read".
	 * 
	 * @param playlistId
	 * @param albumType
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private static boolean isIndicatorMarkedAsRead(String playlistId, AlbumType albumType) throws IOException, Exception {
		UpdateStore updateStore = Config.getInstance().getUpdateStoreByType(albumType.getType());
		Date lastUpdated = updateStore.getLastUpdatedTimestamp();
		Integer lastUpdateSongCount = updateStore.getLastUpdateSongCount();
		
		// Case 1: Indicator is already unset
		if (lastUpdated == null || lastUpdateSongCount == null) {
			return true;
		}
		
		// Case 2: Fallback timeout after a certain number of hours since the playlist was last updated
		int newNotificationTimeout = Config.getInstance().getNewNotificationTimeout();
		if (!BotUtils.isTimeoutActive(lastUpdated, newNotificationTimeout)) {
			return true;
		}
		
		// Case 3: Currently played song is in the context of the discovery playlist
		CurrentlyPlaying currentlyPlaying = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getUsersCurrentlyPlayingTrack().build());
		if (currentlyPlaying != null && currentlyPlaying.getContext().getUri().contains(playlistId)) {
			return true;
		}
		
		// Case 4: Currently played song ISN'T in the context of the discovery playlist, but still part of the N most recently added songs
		// (This is usually because the song was added to the queue, as there is no direct API for it)
		PlaylistTrack[] recentlyAddedPlaylistTracks = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylistsTracks(playlistId).limit(Math.min(lastUpdateSongCount, Constants.DEFAULT_LIMIT)).build()).getItems();
		boolean currentlyPlayingSongIsNew = Arrays.asList(recentlyAddedPlaylistTracks).stream().anyMatch(pt -> pt.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
		if (currentlyPlayingSongIsNew) {
			return true;
		}

		// Case 5: Last resort, check the most recently played songs that were played after the playlist was updated whether any of them is in the playlist's context
		// (This should happen super rarely)
		PlayHistory[] playHistory = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getCurrentUsersRecentlyPlayedTracks().limit(Constants.DEFAULT_LIMIT).after(lastUpdated).build()).getItems();
		boolean wasRecentlyPlayed = Arrays.asList(playHistory).stream().anyMatch(ph -> ph.getPlayedAt().after(lastUpdated) && ph.getContext().getUri().contains(playlistId));
		return wasRecentlyPlayed;
	}
	
	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per release is used to retain order). May remove older songs to make room.
	 * 
	 * @param albumType
	 * @param songs
	 * @return 
	 * @throws Exception 
	 */
	public static int addSongsToPlaylist(List<List<TrackSimplified>> newSongs, AlbumType albumType) {
		try {
			String playlistId = BotUtils.getPlaylistIdByType(albumType);
			circularPlaylistFitting(playlistId, newSongs.stream().mapToInt(List::size).sum());
			int songsAdded = 0;
			if (!newSongs.isEmpty()) {
				for (List<TrackSimplified> songsInAlbum : newSongs) {
					for (List<TrackSimplified> partition : Lists.partition(songsInAlbum, Constants.PLAYLIST_ADD_LIMIT)) {
						JsonArray json = new JsonArray();
						for (TrackSimplified s : partition) {
							json.add(Constants.TRACK_PREFIX + s.getId());
						}
						SpotifyApiRequest.execute(SpotifyApiSessionManager.api().addTracksToPlaylist(playlistId, json).position(0).build());
						songsAdded += partition.size();
					}
				}
				if (songsAdded > 0) {
					Config.log().info("> " + songsAdded + " new " + albumType.toString() + " song" + (songsAdded == 1 ? "" : "s") + " added!");
				}
				return songsAdded;
			}
		} catch (Exception e) {
			Config.logStackTrace(e);
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
	private static void circularPlaylistFitting(String playlistId, int songsToAddCount) throws Exception {
		Playlist p = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylist(playlistId).build());

		final int currentPlaylistCount = p.getTracks().getTotal();
		if (currentPlaylistCount + songsToAddCount > Constants.PLAYLIST_SIZE_LIMIT) {
			if (!Config.getInstance().isCircularPlaylistFitting()) {
				Config.log().severe(p.getName() + " is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ". Enable circularPlaylistFitting or flush the playlist for new songs.");
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
	private static void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws IOException, Exception {
		int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - Constants.PLAYLIST_SIZE_LIMIT;
		boolean repeat = totalSongsToDeleteCount > Constants.PLAYLIST_ADD_LIMIT;
		int songsToDeleteCount = repeat ? Constants.PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
		final int offset = currentPlaylistCount - songsToDeleteCount;
		
		List<PlaylistTrack> tracksToDelete = SpotifyApiRequest.execute(new Callable<List<PlaylistTrack>>() {
			@Override
			public List<PlaylistTrack> call() throws Exception {
				List<PlaylistTrack> currentList = new ArrayList<>();
				Paging<PlaylistTrack> playlistTracks = null;
				do {
					GetPlaylistsTracksRequest.Builder request = SpotifyApiSessionManager.api().getPlaylistsTracks(playlistId).offset(offset).limit(Constants.PLAYLIST_ADD_LIMIT);
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
		
		SpotifyApiRequest.execute(SpotifyApiSessionManager.api().removeTracksFromPlaylist(playlistId, json).build());	
		
		// Repeat if more than 100 songs have to be added/deleted (should rarely happen, so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}
}
