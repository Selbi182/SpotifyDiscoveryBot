package spotify.bot.config;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.util.data.AlbumGroupExtended;

@Component
public class ConfigUpdate {
	@Autowired
	private SpotifyApiConfig spotifyApiConfig;
	
	@Autowired
	private PlaylistStoreConfig playlistStoreConfig;
	
	@Autowired
	private DatabaseService databaseService;

	/**
	 * Update the access and refresh tokens, both in the config object as well as
	 * the database
	 * 
	 * @param accessToken
	 * @param refreshToken
	 */
	public void updateTokens(String accessToken, String refreshToken) throws IOException, SQLException {
		spotifyApiConfig.setAccessToken(accessToken);
		spotifyApiConfig.setRefreshToken(refreshToken);
		databaseService.updateTokens(accessToken, refreshToken);
	}

	/////////////////////////
	// PLAYLIST STORE WRITERS

	/**
	 * Updates the playlist store of the given album group by the current timestamp.
	 * 
	 * @param albumGroupExtended
	 */
	public void refreshPlaylistStore(AlbumGroupExtended albumGroupExtended) throws SQLException {
		databaseService.refreshPlaylistStore(albumGroupExtended);
		refreshPlaylistStoreMap();
	}

	/**
	 * Removes the timestamp from the given album group's playlist store.
	 * 
	 * @param albumGroupExtended
	 * @param addedSongsCount
	 */
	public void unsetPlaylistStore(AlbumGroupExtended albumGroupExtended) throws SQLException {
		databaseService.unsetPlaylistStore(albumGroupExtended);
		refreshPlaylistStoreMap();
	}
	
	/**
	 * Refresh the local playlist store config map.
	 * 
	 * @throws SQLException
	 */
	private void refreshPlaylistStoreMap() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> freshPlaylistStoresMap = databaseService.getAllPlaylistStoresMap();
		playlistStoreConfig.setPlaylistStoreMap(freshPlaylistStoresMap);		
	}
}
