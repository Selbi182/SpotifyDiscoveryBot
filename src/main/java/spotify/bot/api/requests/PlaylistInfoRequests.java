package spotify.bot.api.requests;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.Config;
import spotify.bot.config.Config.PlaylistStore;
import spotify.bot.database.DiscoveryDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class PlaylistInfoRequests {

	@Autowired
	private SpotifyApiWrapper spotify;
	
	@Autowired
	private Config config;
	
	@Autowired
	private DiscoveryDatabase database;
	
	/**
	 * Display the [NEW] notifier of a playlist (if it isn't already set) in the playlist's title
	 * 
	 * @param albumGroup
	 * @throws Exception 
	 */
	public void showNotifier(AlbumGroup albumGroup) {
		String playlistId = BotUtils.getPlaylistIdByGroup(albumGroup);
		if (playlistId != null) {
			Playlist p = spotify.execute(spotify.api().getPlaylist(playlistId).build());
			String playlistName = p.getName();
			if (!playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
				playlistName = playlistName + " " + Constants.NEW_INDICATOR_TEXT;
				spotify.execute(spotify.api().changePlaylistsDetails(playlistId).name(playlistName).build());
			}			
		}
	}
	
	/**
	 * Timestamp all given playlists that DIDN'T have any songs added
	 * 
	 * @param albumGroups 
	 */
	public void timestampPlaylists(List<AlbumGroup> albumGroups) {
		for (AlbumGroup ag : albumGroups) {
			String playlistId = BotUtils.getPlaylistIdByGroup(ag);
			if (playlistId != null) {
				String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime()));
				spotify.execute(spotify.api().changePlaylistsDetails(playlistId).description(newDescription).build());							
			}
		}
	}
	
	/**
	 * Convenience method to try and clear every obsolete New indicator
	 * @throws Exception
	 */
	public void clearObsoleteNotifiers() throws Exception {		
		for (AlbumGroup ag : BotUtils.getSetAlbumGroups()) {
			PlaylistStore ps = config.getPlaylistStoreByAlbumGroup(ag);
			if (ps.getParentAlbumGroup() == null && ps.getLastUpdate() != null && ps.getRecentSongsAddedCount() != null) {
				String playlistId = ps.getPlaylistId();
				Playlist p = spotify.execute(spotify.api().getPlaylist(playlistId).build());
				String playlistName = p.getName();
				//if (playlistName.contains(Constants.NEW_INDICATOR_TEXT)) {
					if (shouldIndicatorBeMarkedAsRead(ps)) {
						playlistName = p.getName().replace(Constants.NEW_INDICATOR_TEXT, "").trim();
						spotify.execute(spotify.api().changePlaylistsDetails(playlistId).name(playlistName).build());
						database.unsetPlaylistStore(ps.getAlbumGroup().getGroup());
					}
				//}
			}
		}
	}
	
	/**
	 * Check if the [NEW] indicator for this playlist store should be removed. This is either done by timeout
	 * or by checking if the currently played song is within the top 50 most recently added songs of the playlist.
	 * 
	 * @param playlistId
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private boolean shouldIndicatorBeMarkedAsRead(PlaylistStore playlistStore) throws IOException, Exception {		
		Date lastUpdated = playlistStore.getLastUpdate();
		Integer lastUpdateSongCount = playlistStore.getRecentSongsAddedCount();
		if (lastUpdated == null || lastUpdateSongCount == null || lastUpdateSongCount == 0) {
			return true;
		}
		
		// Timeout after a certain number of hours since the playlist was last updated
		int newNotificationTimeout = config.getNewNotificationTimeout();
		if (!BotUtils.isTimeoutActive(lastUpdated, newNotificationTimeout)) {
			return true;
		}
		
		// Check if the currently played song is part of the n<=50 most recently added songs
		String playlistId = playlistStore.getPlaylistId();
		CurrentlyPlaying currentlyPlaying = spotify.execute(spotify.api().getUsersCurrentlyPlayingTrack().build());
		PlaylistTrack[] recentlyAddedPlaylistTracks = spotify.execute(spotify.api().getPlaylistsTracks(playlistId).limit(Math.min(lastUpdateSongCount, Constants.DEFAULT_LIMIT)).build()).getItems();
		boolean currentlyPlayingSongIsNew = Arrays.asList(recentlyAddedPlaylistTracks).stream().anyMatch(pt -> pt.getTrack().getId().equals(currentlyPlaying.getItem().getId()));
		return currentlyPlayingSongIsNew;
	}
}
