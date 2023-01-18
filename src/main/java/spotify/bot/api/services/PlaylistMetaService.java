package spotify.bot.api.services;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.IPlaylistItem;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.data.playlists.ChangePlaylistsDetailsRequest;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.ConfigUpdate;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class PlaylistMetaService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

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
	public void showNotifiers(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws BotException, SQLException {
		if (!DeveloperMode.isPlaylistAdditionDisabled()) {
			List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
			for (PlaylistStore ps : sortedPlaylistStores) {
				List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
				if (!albumTrackPairs.isEmpty()) {
					updatePlaylistTitleAndDescription(ps, INDICATOR_OFF, INDICATOR_NEW, true);
					configUpdate.refreshPlaylistStore(ps.getAlbumGroupExtended());
				}
			}
		}
	}

	/**
	 * Convenience method to try and clear every obsolete New indicator
	 * 
	 * @return true if at least one playlist name was changed
	 */
	public boolean clearObsoleteNotifiers() throws SQLException, BotException {
		boolean changed = false;
		if (!DeveloperMode.isPlaylistAdditionDisabled()) {
			for (PlaylistStore ps : playlistStoreConfig.getAllPlaylistStores()) {
				if (shouldIndicatorBeMarkedAsRead(ps)) {
					if (updatePlaylistTitleAndDescription(ps, INDICATOR_NEW, INDICATOR_OFF, false)) {
						configUpdate.unsetPlaylistStore(ps.getAlbumGroupExtended());
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Update the playlist name by replacing the target symbol with the replacement
	 * symbol IF it isn't already contained in the playlist's name. Also timestamp
	 * the playlist, if specified.
	 * 
	 * @param playlistStore       the PlaylistStore containing the relevant playlist
	 * @param notifierTarget      the target String to be replaced
	 * @param notifierReplacement the replacement String
	 * @param timestamp           write the "Last Discovery" timestamp in the
	 *                            description
	 * @return true if the playlist name was changed (a changed playlist description
	 *         has no effect on its own)
	 */
	private boolean updatePlaylistTitleAndDescription(PlaylistStore playlistStore, String notifierTarget, String notifierReplacement, boolean timestamp) throws BotException {
		boolean changed = false;
		String playlistId = playlistStore.getPlaylistId();
		if (playlistId != null) {
			String newPlaylistName = null;
			String newDescription = null;

			if (timestamp) {
				newDescription = "Last Discovery: " + DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime());
			}

			Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
			if (p != null) {
				String playlistName = p.getName();
				if (playlistName != null && playlistName.contains(notifierTarget)) {
					newPlaylistName = playlistName.replace(notifierTarget, notifierReplacement).trim();
					changed = true;
				}
			}

			if (newPlaylistName != null || newDescription != null) {
				ChangePlaylistsDetailsRequest.Builder playlistDetailsBuilder = spotifyApi.changePlaylistsDetails(playlistId);
				if (newPlaylistName != null) {
					playlistDetailsBuilder = playlistDetailsBuilder.name(newPlaylistName);
				}
				if (newDescription != null) {
					playlistDetailsBuilder = playlistDetailsBuilder.description(newDescription);
				}
				SpotifyCall.execute(playlistDetailsBuilder);
			}
		}
		return changed;
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
				.getPlaylistsItems(playlistId)
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
				IPlaylistItem item = currentlyPlaying.getItem();
				if (item instanceof Track) {
					String currentlyPlayingSongId = item.getId();
					return recentlyAddedPlaylistTracks.stream()
						.map(PlaylistTrack::getTrack)
						.map(IPlaylistItem::getId)
						.filter(Objects::nonNull)
						.anyMatch(id -> Objects.equals(id, currentlyPlayingSongId));
				}
			}
		} catch (Exception e) {
			// Don't care, indicator clearance has absolutely no priority
			log.stackTrace(e);
		}
		return false;
	}
}
