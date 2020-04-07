package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.ConfigUpdate;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class PlaylistInfoService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

	/**
	 * The old new-songs indicator as Unicode. Roughly looks like [N][E][W]
	 * 
	 * @deprecated While usable, it was platform-inconsistent. Use
	 *             {@link PlaylistInfoService#INDICATOR_NEW} instead
	 */
	@SuppressWarnings("unused")
	@Deprecated
	private final static String NEW_INDICATOR_TEXT = "\uD83C\uDD7D\uD83C\uDD74\uD83C\uDD86";

	/**
	 * New-songs indicator (new songs are found), a white circle
	 */
	private final static String INDICATOR_NEW = "\u26AA";

	/**
	 * New-songs indicator (currently no new songs), a black circle
	 */
	private final static String INDICATOR_OFF = "\u26AB";

	/**
	 * The description timestamp. Example: "January 1, 2000 - 00:00"
	 */
	private final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private ConfigUpdate configUpdate;
	
	@Autowired
	private PlaylistStoreConfig playlistStoreConfig;
	
	@Autowired
	private StaticConfig staticConfig;
	
	@Autowired
	private BotLogger log;

	/**
	 * Display the [NEW] notifiers of the given album groups' playlists titles, if
	 * any songs were added
	 * 
	 * @param albumGroup
	 */
	public void showNotifiers(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws SpotifyWebApiException, SQLException, IOException, InterruptedException {
		List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
		for (PlaylistStore ps : sortedPlaylistStores) {
			List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
			if (!albumTrackPairs.isEmpty()) {
				replaceNotifierSymbol(ps, INDICATOR_OFF, INDICATOR_NEW);
				configUpdate.refreshPlaylistStore(ps.getAlbumGroupExtended());
			}
		}
	}

	/**
	 * Convenience method to try and clear every obsolete New indicator
	 * 
	 * @return true if at least one playlist name was changed
	 */
	public boolean clearObsoleteNotifiers() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		boolean changed = false;
		for (PlaylistStore ps : playlistStoreConfig.getAllPlaylistStores()) {
			if (shouldIndicatorBeMarkedAsRead(ps)) {
				if (replaceNotifierSymbol(ps, INDICATOR_NEW, INDICATOR_OFF)) {
					configUpdate.unsetPlaylistStore(ps.getAlbumGroupExtended());
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Update the playlist name by replacing the target symbol with the replacement
	 * symbol IF it isn't already contained in the playlist's name.
	 * 
	 * @param playlistStore
	 *            the PlaylistStore containing the relevant playlist
	 * @param target
	 *            the target String to be replaced
	 * @param replacement
	 *            the replacement String
	 * @return true if the playlist name was changed
	 */
	private boolean replaceNotifierSymbol(PlaylistStore playlistStore, String target, String replacement) throws SpotifyWebApiException, IOException, InterruptedException {
		String playlistId = playlistStore.getPlaylistId();
		if (playlistId != null) {
			Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
			String playlistName = p.getName();
			if (playlistName != null && playlistName.contains(target)) {
				playlistName = playlistName.replace(target, replacement).trim();
				SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).name(playlistName));
				return true;
			}
		}
		return false;
	}

	////////////////////////////////

	/**
	 * Check if the [NEW] indicator for this playlist store should be removed. This
	 * is either done by timeout or by checking if the currently played song is
	 * within the most recently added songs of the playlist.
	 * 
	 * @param playlistId
	 * @return
	 */
	private boolean shouldIndicatorBeMarkedAsRead(PlaylistStore playlistStore) {
		try {
			// Case 1: Notification timestamp is already unset
			Date lastUpdated = playlistStore.getLastUpdate();
			if (lastUpdated == null) {
				return true;
			}

			// Case 2: Timeout since playlist was last updated expired
			int newNotificationTimeout = staticConfig.getNewNotificationTimeout();
			if (!BotUtils.isWithinTimeoutWindow(lastUpdated, newNotificationTimeout)) {
				return true;
			}

			// Case 3: Currently played song is within the recently added playlist tracks
			String playlistId = playlistStore.getPlaylistId();
			PlaylistTrack[] topmostPlaylistTracks = SpotifyCall.execute(spotifyApi
				.getPlaylistsTracks(playlistId)
				.limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT))
				.getItems();
			List<PlaylistTrack> recentlyAddedPlaylistTracks = Arrays.stream(topmostPlaylistTracks)
				.filter((pt -> BotUtils.isWithinTimeoutWindow(pt.getAddedAt(), newNotificationTimeout)))
				.collect(Collectors.toList());

			// -- Case 3a: Playlist does not have recently added tracks or is still empty
			if (recentlyAddedPlaylistTracks.isEmpty()) {
				return true;
			}

			// -- Case 3b: Playlist does have recently added tracks, check if the currently
			// played song is within that list
			CurrentlyPlaying currentlyPlaying = SpotifyCall.execute(spotifyApi.getUsersCurrentlyPlayingTrack());
			if (currentlyPlaying != null) {
				boolean currentlyPlayingSongIsNew = recentlyAddedPlaylistTracks
					.stream()
					.anyMatch(pt -> pt.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
				return currentlyPlayingSongIsNew;
			}
		} catch (Exception e) {
			// Don't care, indicator clearance has absolutely no priority
			log.stackTrace(e);
		}
		return false;
	}

	////////////////////////////////

	/**
	 * Timestamp all given playlists that DIDN'T have any songs added
	 * 
	 * @param collection
	 */
	public void timestampPlaylists(Collection<PlaylistStore> playlistStores) throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		for (PlaylistStore ps : playlistStores) {
			String playlistId = ps.getPlaylistId();
			if (playlistId != null) {
				String newDescription = String.format("Last Search: %s", DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime()));
				SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).description(newDescription));
			}
		}
	}
}
