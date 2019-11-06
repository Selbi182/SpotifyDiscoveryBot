package spotify.bot.config;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.enums.AlbumGroup;

import spotify.bot.database.DBConstants;
import spotify.bot.database.DiscoveryDatabase;

@Configuration
public class Config {

	@Autowired
	private DiscoveryDatabase database;
	
	// [BotConfig]
	private String clientId;
	private String clientSecret;
	private String callbackUri;
	private int newNotificationTimeout;
	private int artistCacheTimeout;
	private Date artistCacheLastUpdated;

	// [UserConfig]
	private String accessToken;
	private String refreshToken;
	private boolean intelligentAppearsOnSearch;
	private CountryCode market;
	private int lookbackDays;
	private boolean circularPlaylistFitting;
	
	// [PlaylistStore]
	private Map<AlbumGroup, PlaylistStore> playlistStore;
	
	////////////////

	public Config() {
		playlistStore = new HashMap<>();
	}
	
	/**
	 * Sets up or refreshes the configuration for the Spotify bot from the database
	 * 
	 * @throws IOException
	 * @throws SQLException 
	 */
	@PostConstruct
	public void init() throws SQLException, IOException {
		// Read and set config
		ResultSet dbBotConfig = database.singleRow(DBConstants.TABLE_BOT_CONFIG);
		ResultSet dbUserConfig = database.singleRow(DBConstants.TABLE_USER_CONFIG);
		
		// Set bot config
		clientId = dbBotConfig.getString(DBConstants.COL_CLIENT_ID);
		clientSecret = dbBotConfig.getString(DBConstants.COL_CLIENT_SECRET);
		callbackUri = dbBotConfig.getString(DBConstants.COL_CALLBACK_URI);
		newNotificationTimeout = dbBotConfig.getInt(DBConstants.COL_NEW_NOTIFICATION_TIMEOUT);
		artistCacheTimeout = dbBotConfig.getInt(DBConstants.COL_ARTIST_CACHE_TIMEOUT);
		artistCacheLastUpdated = dbBotConfig.getDate(DBConstants.COL_ARTIST_CACHE_LAST_UPDATE);
		
		// Set user config
		accessToken = dbUserConfig.getString(DBConstants.COL_ACCESS_TOKEN);
		refreshToken = dbUserConfig.getString(DBConstants.COL_REFRESH_TOKEN);
		intelligentAppearsOnSearch = dbUserConfig.getBoolean(DBConstants.COL_INTELLIGENT_APPEARS_ON_SEARCH);
		market = CountryCode.valueOf(dbUserConfig.getString(DBConstants.COL_MARKET));
		lookbackDays = dbUserConfig.getInt(DBConstants.COL_LOOKBACK_DAYS);
		circularPlaylistFitting = dbUserConfig.getBoolean(DBConstants.COL_CIRCULAR_PLAYLIST_FITTING);
		
		// Set playlist store
		refreshUpdateStore();
	}
	
	/**
	 * Refresh the localized update with the data from the database
	 * @throws SQLException 
	 */
	public void refreshUpdateStore() throws SQLException {
		ResultSet dbPlaylistStore = database.fullTable(DBConstants.TABLE_PLAYLIST_STORE);
		while (dbPlaylistStore.next()) {
			AlbumGroup albumGroup = AlbumGroup.valueOf(dbPlaylistStore.getString(DBConstants.COL_ALBUM_GROUP));
			PlaylistStore ps = new PlaylistStore(albumGroup);
			ps.setPlaylistId(dbPlaylistStore.getString(DBConstants.COL_PLAYLIST_ID));
			String parentAlbumGroupString = dbPlaylistStore.getString(DBConstants.CPL_PARENT_ALBUM_GROUP);
			if (parentAlbumGroupString != null) {
				ps.setParentAlbumGroup(AlbumGroup.valueOf(parentAlbumGroupString));				
			}
			ps.setLastUpdate(dbPlaylistStore.getDate(DBConstants.COL_LAST_UPDATE));
			ps.setRecentSongsAddedCount(dbPlaylistStore.getInt(DBConstants.COL_RECENT_SONGS_ADDED_COUNT));
			playlistStore.put(albumGroup, ps);	
		}
	}

	/**
	 * Update the access and refresh tokens, both in the config object as well as
	 * the ini file
	 * 
	 * @param accessToken
	 * @param refreshToken
	 * @throws IOException
	 * @throws SQLException 
	 */
	public void updateTokens(String accessToken, String refreshToken) throws IOException, SQLException {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		database.updateColumnInTable(DBConstants.TABLE_USER_CONFIG, DBConstants.COL_ACCESS_TOKEN, accessToken);
		database.updateColumnInTable(DBConstants.TABLE_USER_CONFIG, DBConstants.COL_REFRESH_TOKEN, refreshToken);
	}

	////////////////////

	public String getClientId() {
		return clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public String getCallbackUri() {
		return callbackUri;
	}

	public int getLookbackDays() {
		return lookbackDays;
	}

	public CountryCode getMarket() {
		return market;
	}

	public boolean isIntelligentAppearsOnSearch() {
		return intelligentAppearsOnSearch;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public int getNewNotificationTimeout() {
		return newNotificationTimeout;
	}

	public boolean isCircularPlaylistFitting() {
		return circularPlaylistFitting;
	}

	public int getArtistCacheTimeout() {
		return artistCacheTimeout;
	}
	
	public Date getArtistCacheLastUpdated() {
		return artistCacheLastUpdated;
	}
	
	public PlaylistStore getPlaylistStoreByAlbumGroup(AlbumGroup ag) {
		return playlistStore.get(ag);
	}
	
	///////////////////

	public class PlaylistStore {
		private final AlbumGroup albumGroup;
		private String playlistId;
		private AlbumGroup parentAlbumGroup;
		private Date lastUpdate;
		private Integer recentSongsAddedCount;

		private PlaylistStore(AlbumGroup albumGroup) {
			this.albumGroup = albumGroup;
		}

		public AlbumGroup getAlbumGroup() {
			return albumGroup;
		}

		public String getPlaylistId() {
			return playlistId;
		}

		public void setPlaylistId(String playlistId) {
			this.playlistId = playlistId;
		}

		public AlbumGroup getParentAlbumGroup() {
			return parentAlbumGroup;
		}

		public void setParentAlbumGroup(AlbumGroup parentAlbumGroup) {
			this.parentAlbumGroup = parentAlbumGroup;
		}

		public Date getLastUpdate() {
			return lastUpdate;
		}

		public void setLastUpdate(Date lastUpdate) {
			this.lastUpdate = lastUpdate;
		}

		public Integer getRecentSongsAddedCount() {
			return recentSongsAddedCount;
		}

		public void setRecentSongsAddedCount(Integer recentSongsAddedCount) {
			this.recentSongsAddedCount = recentSongsAddedCount;
		}

		@Override
		public String toString() {
			return "PlaylistStore [albumGroup=" + albumGroup + ", playlistId=" + playlistId + ", parentAlbumGroup=" + parentAlbumGroup + ", lastUpdated=" + lastUpdate + ", recentSongsAddedCount=" + recentSongsAddedCount + "]";
		}

	}
}
