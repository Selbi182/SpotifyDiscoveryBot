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
import spotify.bot.config.Config;
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class PlaylistInfoService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

	/**
	 * The new-songs indicator as Unicode. This roughly looks like [N][E][W] when
	 * printed out.
	 */
	private final static String NEW_INDICATOR_TEXT = "\uD83C\uDD7D\uD83C\uDD74\uD83C\uDD86";

	/**
	 * The description timestamp. Example: "January 1, 2000 - 00:00"
	 */
	private final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	/**
	 * Display the [NEW] notifiers of the given album groups' playlists titles, if
	 * any songs were added
	 * 
	 * @param albumGroup
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	public void showNotifiers(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws SpotifyWebApiException, SQLException, IOException, InterruptedException {
		List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
		for (PlaylistStore ps : sortedPlaylistStores) {
			List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
			if (!albumTrackPairs.isEmpty()) {
				showSingleNotifier(ps);
				config.refreshPlaylistStore(ps.getAlbumGroupExtended());
			}
		}
	}

	/**
	 * Display the [NEW] notifier of a playlist (if it isn't already set) in the
	 * playlist's title
	 * 
	 * @param albumGroup
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private void showSingleNotifier(PlaylistStore playlistStore) throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		String playlistId = playlistStore.getPlaylistId();
		if (playlistId != null) {
			Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
			String playlistName = p.getName();
			if (!playlistName.contains(NEW_INDICATOR_TEXT)) {
				playlistName = playlistName + " " + NEW_INDICATOR_TEXT;
				SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).name(playlistName));
			}
		}
	}

	////////////////////////////////

	/**
	 * Timestamp all given playlists that DIDN'T have any songs added
	 * 
	 * @param collection
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
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

	////////////////////////////////

	/**
	 * Convenience method to try and clear every obsolete New indicator
	 * 
	 * @return
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SpotifyWebApiException
	 */
	public boolean clearObsoleteNotifiers() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		boolean changed = false;
		for (PlaylistStore ps : config.getAllPlaylistStores()) {
			String playlistId = ps.getPlaylistId();
			if (playlistId != null) {
				Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
				String playlistName = p.getName();
				if (playlistName != null && playlistName.contains(NEW_INDICATOR_TEXT)) {
					if (isIndicatorMarkedAsRead(ps)) {
						playlistName = playlistName.replace(NEW_INDICATOR_TEXT, "").trim();
						SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).name(playlistName));
						config.unsetPlaylistStore(ps.getAlbumGroupExtended());
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Check if the [NEW] indicator for this playlist store should be removed. This
	 * is either done by timeout or by checking if the currently played song is
	 * within the most recently added songs of the playlist.
	 * 
	 * @param playlistId
	 * @return
	 * @throws IOException
	 */
	private boolean isIndicatorMarkedAsRead(PlaylistStore playlistStore) {
		try {
			// Case 1: Notification timestamp is already unset
			Date lastUpdated = playlistStore.getLastUpdate();
			if (lastUpdated == null) {
				return true;
			}

			// Case 2: Timeout since playlist was last updated expired
			int newNotificationTimeout = config.getStaticConfig().getNewNotificationTimeout();
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
}
