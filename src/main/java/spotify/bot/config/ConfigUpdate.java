package spotify.bot.config;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.stereotype.Component;

import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.data.AlbumGroupExtended;

@Component
public class ConfigUpdate {
	private final PlaylistStoreConfig playlistStoreConfig;
	private final DatabaseService databaseService;

	ConfigUpdate(PlaylistStoreConfig playlistStoreConfig, DatabaseService databaseService) {
		this.playlistStoreConfig = playlistStoreConfig;
		this.databaseService = databaseService;
	}


	/////////////////////////
	// PLAYLIST STORE WRITERS

	/**
	 * Updates the playlist store of the given album group by the current timestamp.
	 */
	public void refreshPlaylistStore(AlbumGroupExtended albumGroupExtended) throws SQLException {
		databaseService.refreshPlaylistStore(albumGroupExtended);
		refreshPlaylistStoreMap();
	}

	/**
	 * Removes the timestamp from the given album group's playlist store.
	 */
	public void unsetPlaylistStore(AlbumGroupExtended albumGroupExtended) throws SQLException {
		databaseService.unsetPlaylistStore(albumGroupExtended);
		refreshPlaylistStoreMap();
	}

	/**
	 * Refresh the local playlist store config map.
	 */
	private void refreshPlaylistStoreMap() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> freshPlaylistStoresMap = databaseService.getAllPlaylistStoresMap();
		playlistStoreConfig.setPlaylistStoreMap(freshPlaylistStoresMap);
	}
}
