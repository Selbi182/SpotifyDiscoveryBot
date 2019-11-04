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

import spotify.bot.util.Constants;

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

	// [UserConfig]
	private String accessToken;
	private String refreshToken;
	private String playlistAlbums;
	private String playlistSingles;
	private String playlistCompilations;
	private String playlistAppearsOn;
	private boolean intelligentAppearsOnSearch;
	private CountryCode market;
	private int lookbackDays;
	private boolean circularPlaylistFitting;
	
	// [TimestampStore]
	private Map<String, UpdateStore> updateStoreMap;
	
	////////////////

	/**
	 * Sets up the configuration for the Spotify bot
	 * 
	 * @throws IOException
	 * @throws SQLException 
	 */
	@PostConstruct
	public void init() throws SQLException, IOException {
		// Read and set config
		ResultSet botConfig = database.singleRow(Constants.TABLE_BOT_CONFIG);
		ResultSet userConfig = database.singleRow(Constants.TABLE_USER_CONFIG);
		ResultSet updateStore = database.fullTable(Constants.TABLE_UPDATE_STORE);
		
		// Set bot config
		clientId = botConfig.getString(Constants.COL_CLIENT_ID);
		clientSecret = botConfig.getString(Constants.COL_CLIENT_SECRET);
		callbackUri = botConfig.getString(Constants.COL_CALLBACK_URI);
		newNotificationTimeout = botConfig.getInt(Constants.COL_NEW_NOTIFICATION_TIMEOUT);
		artistCacheTimeout = botConfig.getInt(Constants.COL_ARTIST_CACHE_TIMEOUT);
		
		// Set user config
		accessToken = userConfig.getString(Constants.COL_ACCESS_TOKEN);
		refreshToken = userConfig.getString(Constants.COL_REFRESH_TOKEN);
		playlistAlbums = userConfig.getString(Constants.COL_PLAYLIST_ALBUMS);
		playlistSingles = userConfig.getString(Constants.COL_PLAYLIST_SINGLES);
		playlistCompilations = userConfig.getString(Constants.COL_PLAYLIST_COMPILATIONS);
		playlistAppearsOn = userConfig.getString(Constants.COL_PLAYLIST_APPEARS_ON);
		intelligentAppearsOnSearch = userConfig.getBoolean(Constants.COL_INTELLIGENT_APPEARS_ON_SEARCH);
		market = CountryCode.valueOf(userConfig.getString(Constants.COL_MARKET));
		lookbackDays = userConfig.getInt(Constants.COL_LOOKBACK_DAYS);
		circularPlaylistFitting = userConfig.getBoolean(Constants.COL_CIRCULAR_PLAYLIST_FITTING);
		
		// Set update store
		updateStoreMap = new HashMap<>();
		while (updateStore.next()) {
			Date lastUpdated = updateStore.getDate(Constants.COL_LAST_UPDATED_TIMESTAMP);
			Integer lastUpdateSongCount = updateStore.getInt(Constants.COL_LAST_UPDATE_SONG_COUNT);
			UpdateStore us = new UpdateStore(lastUpdated, lastUpdateSongCount);
			updateStoreMap.put(updateStore.getString(Constants.COL_TYPE), us);	
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
		database.updateColumnInTable(Constants.TABLE_USER_CONFIG, Constants.COL_ACCESS_TOKEN, accessToken);
		database.updateColumnInTable(Constants.TABLE_USER_CONFIG, Constants.COL_REFRESH_TOKEN, refreshToken);
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

	public String getPlaylistAlbums() {
		return playlistAlbums;
	}

	public String getPlaylistSingles() {
		return playlistSingles;
	}

	public String getPlaylistCompilations() {
		return playlistCompilations;
	}

	public String getPlaylistAppearsOn() {
		return playlistAppearsOn;
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

	public UpdateStore getUpdateStoreByGroup(String group) {
		return updateStoreMap.get(group);
	}

	
	///////////////////
	
	public class UpdateStore {
		private Date lastUpdatedTimestamp;
		private Integer lastUpdateSongCount;

		public UpdateStore(Date lastUpdatedTimestamp, Integer lastUpdateSongCount) {
			this.lastUpdatedTimestamp = lastUpdatedTimestamp;
			this.lastUpdateSongCount = lastUpdateSongCount;
		}

		public Date getLastUpdatedTimestamp() {
			return lastUpdatedTimestamp;
		}

		public void setLastUpdatedTimestamp(Date lastUpdatedTimestamp) {
			this.lastUpdatedTimestamp = lastUpdatedTimestamp;
		}

		public Integer getLastUpdateSongCount() {
			return lastUpdateSongCount;
		}

		public void setLastUpdateSongCount(Integer lastUpdateSongCount) {
			this.lastUpdateSongCount = lastUpdateSongCount;
		}
	}
}
