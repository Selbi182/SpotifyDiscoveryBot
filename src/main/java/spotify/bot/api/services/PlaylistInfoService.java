package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.dto.PlaylistStoreDTO;
import spotify.bot.util.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class PlaylistInfoService {

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private Config config;

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
	public void showNotifiers(Map<AlbumGroup, List<AlbumTrackPair>> newSongs) throws SpotifyWebApiException, SQLException, IOException, InterruptedException {
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : newSongs.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				showNotifier(entry.getKey());
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
	private void showNotifier(AlbumGroup albumGroup) throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		String playlistId = config.getPlaylistIdByGroup(albumGroup);
		if (playlistId != null) {
			Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
			String playlistName = p.getName();
			if (!playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
				playlistName = playlistName + " " + Constants.NEW_INDICATOR_TEXT;
				SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).name(playlistName));
			}
		}
	}

	/**
	 * Timestamp all given playlists that DIDN'T have any songs added
	 * 
	 * @param albumGroups
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	public void timestampPlaylists(List<AlbumGroup> albumGroups) throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		for (AlbumGroup ag : albumGroups) {
			String playlistId = config.getPlaylistIdByGroup(ag);
			if (playlistId != null) {
				String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime()));
				SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).description(newDescription));
			}
		}
	}

	/**
	 * Convenience method to try and clear every obsolete New indicator
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SpotifyWebApiException
	 */
	public void clearObsoleteNotifiers() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		for (AlbumGroup ag : config.getSetAlbumGroups()) {
			PlaylistStoreDTO ps = config.getPlaylistStore(ag);
			if (ps.getParentAlbumGroup() == null && ps.getLastUpdate() != null && ps.getRecentSongsAddedCount() != null) {
				String playlistId = ps.getPlaylistId();
				Playlist p = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
				String playlistName = p.getName();
				if (playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
					if (shouldIndicatorBeMarkedAsRead(ps)) {
						playlistName = p.getName().replace(Constants.NEW_INDICATOR_TEXT, "").trim();
						SpotifyCall.execute(spotifyApi.changePlaylistsDetails(playlistId).name(playlistName));
						config.unsetPlaylistStore(ps.getAlbumGroup());
					}
				}
			}
		}
	}

	/**
	 * Check if the [NEW] indicator for this playlist store should be removed. This
	 * is either done by timeout or by checking if the currently played song is
	 * within the top 50 most recently added songs of the playlist.
	 * 
	 * @param playlistId
	 * @return
	 * @throws IOException
	 */
	private boolean shouldIndicatorBeMarkedAsRead(PlaylistStoreDTO playlistStore) throws IOException, Exception {
		Date lastUpdated = playlistStore.getLastUpdate();
		Integer lastUpdateSongCount = playlistStore.getRecentSongsAddedCount();
		if (lastUpdated == null || lastUpdateSongCount == null || lastUpdateSongCount == 0) {
			return true;
		}

		// Timeout after a certain number of hours since the playlist was last updated
		int newNotificationTimeout = config.getBotConfig().getNewNotificationTimeout();
		if (!BotUtils.isTimeoutActive(lastUpdated, newNotificationTimeout)) {
			return true;
		}

		// Check if the currently played song is part of the n<=50 most recently added
		// songs
		String playlistId = playlistStore.getPlaylistId();
		CurrentlyPlaying currentlyPlaying = SpotifyCall.execute(spotifyApi.getUsersCurrentlyPlayingTrack());
		if (currentlyPlaying == null) {
			return false;
		}
		int tracksToFetch = Math.min(lastUpdateSongCount, Constants.DEFAULT_LIMIT);
		PlaylistTrack[] recentlyAddedPlaylistTracks = SpotifyCall.execute(spotifyApi.getPlaylistsTracks(playlistId).limit(tracksToFetch)).getItems();
		boolean currentlyPlayingSongIsNew = Arrays.asList(recentlyAddedPlaylistTracks).stream().anyMatch(pt -> pt.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
		return currentlyPlayingSongIsNew;
	}
}
