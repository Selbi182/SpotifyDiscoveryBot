package spotify.bot.api.requests;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.BotLogger;
import spotify.bot.config.Config;
import spotify.bot.database.DiscoveryDatabase;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class PlaylistSongsRequests {

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
	private PlaylistInfoRequests playlistInfoRequests;

	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per
	 * release is used to retain order). May remove older songs to make room.
	 * 
	 * @param sortedNewReleases
	 * @param songs
	 * @return
	 * @throws Exception
	 */
	private int addSongsToPlaylist(List<AlbumTrackPair> albumTrackPairs, AlbumGroup albumGroup) {
		try {
			String playlistId = BotUtils.getPlaylistIdByGroup(albumGroup);
			return addSongsToPlaylistId(albumTrackPairs, playlistId);
		} catch (Exception e) {
			log.stackTrace(e);
		}
		return 0;
	}

	private int addSongsToPlaylistId(List<AlbumTrackPair> albumTrackPairs, String playlistId) throws Exception {
		int songsAdded = 0;
		if (!albumTrackPairs.isEmpty()) {
			circularPlaylistFitting(playlistId, albumTrackPairs.stream().mapToInt(AlbumTrackPair::trackCount).sum());
			for (AlbumTrackPair atp : albumTrackPairs) {
				for (List<TrackSimplified> partition : Lists.partition(atp.getTracks(), Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					SpotifyCall.execute(spotify.api().addTracksToPlaylist(playlistId, json).position(0));
					songsAdded += partition.size();
					Thread.sleep(Constants.PLAYLIST_ADDITION_COOLDOWN);
				}
			}
			return songsAdded;
		}
		return 0;
	}

	/**
	 * Check if circular playlist fitting is required (if enabled; otherwise an
	 * exception is thrown)
	 * 
	 * @param playlistId
	 * @param songsToAddCount
	 * @throws Exception
	 */
	private void circularPlaylistFitting(String playlistId, int songsToAddCount) throws Exception {
		Playlist p = SpotifyCall.execute(spotify.api().getPlaylist(playlistId));

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
	 * Delete as many songs from the bottom as necessary to make room for any new
	 * songs to add, as Spotify playlists have a fixed limit of 10000 songs.
	 * 
	 * If circularPlaylistFitting isn't enabled, an exception is thrown on a full
	 * playlist instead.
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

		List<PlaylistTrack> tracksToDelete = SpotifyCall.executePaging(spotify.api().getPlaylistsTracks(playlistId).offset(offset).limit(Constants.PLAYLIST_ADD_LIMIT));

		JsonArray json = new JsonArray();
		for (int i = 0; i < tracksToDelete.size(); i++) {
			JsonObject object = new JsonObject();
			object.addProperty("uri", Constants.TRACK_PREFIX + tracksToDelete.get(i).getTrack().getId());
			JsonArray positions = new JsonArray();
			positions.add(currentPlaylistCount - songsToDeleteCount + i);
			object.add("positions", positions);
			json.add(object);
		}

		SpotifyCall.execute(spotify.api().removeTracksFromPlaylist(playlistId, json));

		// Repeat if more than 100 songs have to be added/deleted (should rarely happen,
		// so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}

	/**
	 * Adds all releases to the set playlists. Playlist Stores containing a parent
	 * playlist will use those instead. Playlists will get timestamped and receive a
	 * [NEW] indicator on new additions
	 * 
	 * @param newSongsByGroup
	 * @param setAlbumGroups
	 * @throws Exception
	 */
	public void addAllReleasesToSetPlaylists(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> setAlbumGroups) throws Exception {
		Map<AlbumGroup, List<AlbumTrackPair>> mergedAlbumGroupsOfSongs = offlineRequests.groupTracksToParentAlbumGroup(newSongsByGroup, setAlbumGroups);
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : mergedAlbumGroupsOfSongs.entrySet()) {
			AlbumGroup albumGroup = entry.getKey();
			List<AlbumTrackPair> albumTrackPairs = entry.getValue();
			if (!albumTrackPairs.isEmpty()) {
				List<AlbumTrackPair> sortedAlbums = offlineRequests.sortReleases(albumTrackPairs);
				int addedSongsCount = addSongsToPlaylist(sortedAlbums, albumGroup);
				database.refreshPlaylistStore(albumGroup.getGroup(), addedSongsCount);
				playlistInfoRequests.showNotifier(albumGroup);
			}
		}
	}
}
