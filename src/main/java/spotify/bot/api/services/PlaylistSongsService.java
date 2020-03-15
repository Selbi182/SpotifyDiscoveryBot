package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.util.BotLogger;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class PlaylistSongsService {

	private final static int TOP_OF_PLAYLIST = 0;
	private final static int PLAYLIST_ADDITION_COOLDOWN = 1000;
	private final static int PLAYLIST_ADD_LIMIT = 100;
	private final static int PLAYLIST_SIZE_LIMIT = 10000;
	private final static String TRACK_PREFIX = "spotify:track:";

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	/**
	 * Adds all releases to the given playlist IDs
	 * 
	 * @param songsByPlaylistId
	 * @param enabledAlbumGroups
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SpotifyWebApiException
	 */
	public void addAllReleasesToSetPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylistId) throws SpotifyWebApiException, SQLException, IOException, InterruptedException {
		log.debug("Adding to playlists:");
		for (Map.Entry<PlaylistStore, List<AlbumTrackPair>> entry : songsByPlaylistId.entrySet()) {
			String playlistId = entry.getKey().getPlaylistId();
			List<AlbumTrackPair> albumTrackPairs = entry.getValue();
			if (!albumTrackPairs.isEmpty()) {
				Collections.sort(albumTrackPairs);
				addSongsToPlaylistId(playlistId, albumTrackPairs);
				log.printAlbumTrackPairs(albumTrackPairs);
			}
		}
	}

	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per
	 * release is used to retain order). May remove older songs to make room.
	 * 
	 * @param sortedNewReleases
	 * @param songs
	 * @return
	 */
	private void addSongsToPlaylistId(String playlistId, List<AlbumTrackPair> albumTrackPairs) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		if (!albumTrackPairs.isEmpty()) {
			boolean playlistHasCapacity = circularPlaylistFitting(playlistId, albumTrackPairs.stream().mapToInt(AlbumTrackPair::trackCount).sum());
			if (playlistHasCapacity) {
				for (AlbumTrackPair atp : albumTrackPairs) {
					for (List<TrackSimplified> partition : Lists.partition(atp.getTracks(), PLAYLIST_ADD_LIMIT)) {
						JsonArray json = new JsonArray();
						for (TrackSimplified s : partition) {
							json.add(TRACK_PREFIX + s.getId());
						}
						SpotifyCall.execute(spotifyApi.addTracksToPlaylist(playlistId, json).position(TOP_OF_PLAYLIST));
						Thread.sleep(PLAYLIST_ADDITION_COOLDOWN);
					}
				}
			}
		}
	}

	/**
	 * Check if circular playlist fitting is required (if enabled; otherwise an
	 * exception is thrown)
	 * 
	 * @param playlistId
	 * @param songsToAddCount
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 * @throws SQLException
	 * @return true on success, false if playlist is full and can't be cleared
	 */
	private boolean circularPlaylistFitting(String playlistId, int songsToAddCount) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));

		final int currentPlaylistCount = p.getTracks().getTotal();
		if (currentPlaylistCount + songsToAddCount > PLAYLIST_SIZE_LIMIT) {
			if (!config.getUserOptions().isCircularPlaylistFitting()) {
				log.error(p.getName() + " is full! Maximum capacity is " + PLAYLIST_SIZE_LIMIT + ". Enable circularPlaylistFitting or flush the playlist for new songs.");
				return false;
			}
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount, songsToAddCount);
		}
		return true;
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
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws Exception
	 */
	private void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws SpotifyWebApiException, IOException, InterruptedException {
		int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - PLAYLIST_SIZE_LIMIT;
		boolean repeat = totalSongsToDeleteCount > PLAYLIST_ADD_LIMIT;
		int songsToDeleteCount = repeat ? PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
		final int offset = currentPlaylistCount - songsToDeleteCount;

		List<PlaylistTrack> tracksToDelete = SpotifyCall.executePaging(spotifyApi.getPlaylistsTracks(playlistId).offset(offset).limit(PLAYLIST_ADD_LIMIT));

		JsonArray json = new JsonArray();
		for (int i = 0; i < tracksToDelete.size(); i++) {
			JsonObject object = new JsonObject();
			object.addProperty("uri", TRACK_PREFIX + tracksToDelete.get(i).getTrack().getId());
			JsonArray positions = new JsonArray();
			positions.add(currentPlaylistCount - songsToDeleteCount + i);
			object.add("positions", positions);
			json.add(object);
		}

		SpotifyCall.execute(spotifyApi.removeTracksFromPlaylist(playlistId, json));

		// Repeat if more than 100 songs have to be added/deleted (should rarely happen,
		// so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}
}
