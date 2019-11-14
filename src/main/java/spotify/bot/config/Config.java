package spotify.bot.config;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.wrapper.spotify.enums.AlbumGroup;

import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.BotConfigDTO;
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.config.dto.UserConfigDTO;
import spotify.bot.util.data.AlbumGroupExtended;

@Configuration
public class Config {

	@Autowired
	private DatabaseService databaseService;

	private BotConfigDTO botConfig;
	private UserConfigDTO userConfig;
	private Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap;

	/**
	 * Sets up or refreshes the configuration for the Spotify bot from the database
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	@PostConstruct
	private void init() throws SQLException, IOException {
		this.botConfig = getBotConfig();
		this.userConfig = getUserConfig();
		this.playlistStoreMap = getPlaylistStoreMap();
	}

	/**
	 * Update the access and refresh tokens, both in the config object as well as
	 * the database
	 * 
	 * @param accessToken
	 * @param refreshToken
	 * @throws IOException
	 * @throws SQLException
	 */
	public void updateTokens(String accessToken, String refreshToken) throws IOException, SQLException {
		userConfig.setAccessToken(accessToken);
		userConfig.setRefreshToken(refreshToken);
		databaseService.updateTokens(accessToken, refreshToken);
	}

	////////////////////
	// CONFIG DTOS

	/**
	 * Retuns the bot configuration. May be created if not present.
	 * 
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public BotConfigDTO getBotConfig() throws SQLException, IOException {
		if (botConfig == null) {
			botConfig = databaseService.getBotConfig();
		}
		return botConfig;
	}

	/**
	 * Returns the user configuration. May be created if not present.
	 * 
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public UserConfigDTO getUserConfig() throws SQLException, IOException {
		if (userConfig == null) {
			userConfig = databaseService.getUserConfig();
		}
		return userConfig;
	}

	/**
	 * Returns the playlist stores as a map. May be created if not present.
	 * 
	 * @return
	 * @throws SQLException
	 */
	private Map<AlbumGroupExtended, PlaylistStore> getPlaylistStoreMap() throws SQLException {
		if (playlistStoreMap == null) {
			playlistStoreMap = databaseService.getAllPlaylistStores();
		}
		return playlistStoreMap;
	}

	/////////////////////////
	// PLAYLIST STORE READERS

	/**
	 * Returns all set playlist stores.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public Collection<PlaylistStore> getAllPlaylistStores() throws SQLException {
		return getPlaylistStoreMap().values();
	}

	/**
	 * 
	 * Returns the stored playlist store by the given album group.
	 * 
	 * @param albumGroup
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public PlaylistStore getPlaylistStore(AlbumGroup albumGroup) throws SQLException {
		return getPlaylistStore(AlbumGroupExtended.fromAlbumGroup(albumGroup));
	}

	/**
	 * 
	 * Returns the stored playlist store by the given album group.
	 * 
	 * @param albumGroup
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public PlaylistStore getPlaylistStore(AlbumGroupExtended albumGroupExtended) throws SQLException {
		PlaylistStore ps = getPlaylistStoreMap().get(albumGroupExtended);
		return ps;
	}

	/**
	 * Returns the stored playlist ID by the given album group. Should the same ID
	 * be set for multiple playlists, the album group is returned hierarchically:
	 * ALBUM > SINGLE > COMPILATION > APPEARS_ON
	 * 
	 * @param albumGroup
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public String getPlaylistIdByGroup(AlbumGroupExtended albumGroup) throws SQLException {
		PlaylistStore ps = getPlaylistStore(albumGroup);
		if (ps != null) {
			return ps.getPlaylistId();
		}
		return null;
	}

	/**
	 * Fetch all album groups that are set in the config
	 * 
	 * @param albumGroups
	 * @throws SQLException
	 */
	public List<AlbumGroup> getEnabledAlbumGroups() throws SQLException {
		List<AlbumGroup> setAlbumGroups = new ArrayList<>();
		for (AlbumGroup age : AlbumGroup.values()) {
			PlaylistStore ps = getPlaylistStore(age);
			if (ps != null) {
				if ((ps.getPlaylistId() != null && !ps.getPlaylistId().trim().isEmpty())) {
					setAlbumGroups.add(age);
				}
			}
		}
		return setAlbumGroups;
	}

	/**
	 * Fetch all album groups that are set in the config
	 * 
	 * @param albumGroups
	 * @throws SQLException
	 */
	public List<AlbumGroupExtended> getEnabledSpecialAlbumGroups() throws SQLException {
		List<AlbumGroupExtended> setAlbumGroups = new ArrayList<>();
		for (AlbumGroupExtended age : AlbumGroupExtended.values()) {
			PlaylistStore ps = getPlaylistStore(age);
			if (ps != null) {
				if ((ps.getPlaylistId() != null && !ps.getPlaylistId().trim().isEmpty())) {
					setAlbumGroups.add(age);
				}
			}
		}
		return setAlbumGroups;
	}

	/////////////////////////
	// PLAYLIST STORE WRITERS

	/**
	 * Updates the playlist store of the given album group by the current timestamp.
	 * 
	 * @param albumGroup
	 * @throws SQLException
	 */
	public void refreshPlaylistStore(AlbumGroupExtended albumGroup) throws SQLException {
		databaseService.refreshPlaylistStore(albumGroup.getGroup());
		invalidatePlaylistStore();
	}

	/**
	 * Removes the timestamp from the given album group's playlist store.
	 * 
	 * @param albumGroup
	 * @param addedSongsCount
	 * @throws SQLException
	 */
	public void unsetPlaylistStore(AlbumGroupExtended albumGroup) throws SQLException {
		databaseService.unsetPlaylistStore(albumGroup);
		invalidatePlaylistStore();
	}

	/**
	 * Sets the current playlist store map to null so it gets reloaded on any new
	 * database call in {@link Config#getPlaylistStore}.
	 */
	private void invalidatePlaylistStore() {
		playlistStoreMap = null;
	}
}
