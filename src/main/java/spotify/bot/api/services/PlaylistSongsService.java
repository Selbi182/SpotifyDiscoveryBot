package spotify.bot.api.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.IPlaylistItem;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
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
	private UserOptions userOptions;

	@Autowired
	private BotLogger log;

	/**
	 * Adds all releases to the given playlists
	 * 
	 * @param songsByPlaylist
	 * @param enabledAlbumGroups
	 */
	public void addAllReleasesToSetPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws BotException {
		log.debug("Adding to playlists:");
		List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
		for (PlaylistStore ps : sortedPlaylistStores) {
			List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
			if (!albumTrackPairs.isEmpty()) {
				Collections.sort(albumTrackPairs);
				if (!DeveloperMode.isPlaylistAdditionDisabled()) {
					addSongsToPlaylistId(ps.getPlaylistId(), albumTrackPairs);
				}
				log.printAlbumTrackPairs(albumTrackPairs, ps.getAlbumGroupExtended());
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
	private void addSongsToPlaylistId(String playlistId, List<AlbumTrackPair> albumTrackPairs) throws BotException {
		if (!albumTrackPairs.isEmpty()) {
			boolean playlistHasCapacity = circularPlaylistFitting(playlistId, albumTrackPairs.stream()
				.mapToInt(AlbumTrackPair::trackCount)
				.sum());
			if (playlistHasCapacity) {
				List<List<TrackSimplified>> bundledReleases = batchReleases(albumTrackPairs);
				for (List<TrackSimplified> t : bundledReleases) {
					for (List<TrackSimplified> partition : Lists.partition(t, PLAYLIST_ADD_LIMIT)) {
						JsonArray json = new JsonArray();
						for (TrackSimplified s : partition) {
							json.add(TRACK_PREFIX + s.getId());
						}
						SpotifyCall.execute(spotifyApi
							.addItemsToPlaylist(playlistId, json)
							.position(TOP_OF_PLAYLIST));
						BotUtils.sneakySleep(PLAYLIST_ADDITION_COOLDOWN);
					}
				}
			}
		}
	}

	/**
	 * If batch playlist addition is enabled, return a view of all release tracks in
	 * lists of 100 tracks each. This is to improve performance when adding a lot of
	 * tracks at once (especially for singles), at the cost of preserving the exact
	 * order in which releases were published.<br/>
	 * <br/>
	 * 
	 * Otherwise, simply returns a rearranged view of all the ATP tracks.
	 * 
	 * @param allReleases
	 * @return
	 */
	private List<List<TrackSimplified>> batchReleases(List<AlbumTrackPair> allReleases) {
		if (userOptions.isBatchPlaylistAddition()) {
			List<List<TrackSimplified>> bundled = new ArrayList<>();
			List<TrackSimplified> subBatch = new ArrayList<>();
			for (AlbumTrackPair atp : allReleases) {
				List<TrackSimplified> tracksOfRelease = atp.getTracks();
				if ((subBatch.size() + tracksOfRelease.size()) > PLAYLIST_ADD_LIMIT) {
					bundled.add(subBatch);
					subBatch = new ArrayList<>();
				}
				subBatch.addAll(atp.getTracks());
			}
			if (!subBatch.isEmpty()) {
				bundled.add(subBatch);
			}
			return bundled;
		}
		List<List<TrackSimplified>> bundled = new ArrayList<>();
		for (AlbumTrackPair atp : allReleases) {
			bundled.add(atp.getTracks());
		}
		return bundled;
	}

	/**
	 * Check if circular playlist fitting is required (if enabled; otherwise an
	 * exception is thrown)
	 * 
	 * @param playlistId
	 * @param songsToAddCount
	 * @return true on success, false if playlist is full and can't be cleared
	 */
	private boolean circularPlaylistFitting(String playlistId, int songsToAddCount) throws BotException {
		Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));

		final int currentPlaylistCount = p.getTracks().getTotal();
		if (currentPlaylistCount + songsToAddCount > PLAYLIST_SIZE_LIMIT) {
			if (!userOptions.isCircularPlaylistFitting()) {
				log.error(p.getName() + " is full! Maximum capacity is " + PLAYLIST_SIZE_LIMIT + ". "
					+ "Enable circularPlaylistFitting or flush the playlist for new songs.");
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
	 */
	private void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws BotException {
		int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - PLAYLIST_SIZE_LIMIT;
		boolean repeat = totalSongsToDeleteCount > PLAYLIST_ADD_LIMIT;
		int songsToDeleteCount = repeat ? PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
		final int offset = currentPlaylistCount - songsToDeleteCount;

		List<PlaylistTrack> tracksToDelete = SpotifyCall.executePaging(spotifyApi
			.getPlaylistsItems(playlistId)
			.offset(offset)
			.limit(PLAYLIST_ADD_LIMIT));

		JsonArray json = new JsonArray();
		for (int i = 0; i < tracksToDelete.size(); i++) {
			IPlaylistItem track = tracksToDelete.get(i).getTrack();
			if (track instanceof Track) {
				String id = track.getId();
				JsonObject object = new JsonObject();
				object.addProperty("uri", TRACK_PREFIX + id);
				JsonArray positions = new JsonArray();
				positions.add(currentPlaylistCount - songsToDeleteCount + i);
				object.add("positions", positions);
				json.add(object);
			}
		}

		SpotifyCall.execute(spotifyApi.removeItemsFromPlaylist(playlistId, json));

		// Repeat if more than 100 songs have to be added/deleted (should rarely happen,
		// so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}
}
