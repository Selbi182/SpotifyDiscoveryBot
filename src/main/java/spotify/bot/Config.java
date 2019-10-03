package spotify.bot;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.neovisionaries.i18n.CountryCode;

import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.Constants;
import spotify.main.Main;

public class Config {

	// SINGLETON
	private static Config instance;

	/////////////////
	
	// Logger
	private Logger log;

	/////////////////
	
	// [BotConfig]
	private final String clientId;
	private final String clientSecret;
	private final String callbackUri;
	private final Level logLevel;
	private final String logToFile;
	private final int newNotificationTimeout;
	private final int artistCacheTimeout;

	// [UserConfig]
	private String accessToken;
	private String refreshToken;
	private final String playlistAlbums;
	private final String playlistSingles;
	private final String playlistCompilations;
	private final String playlistAppearsOn;
	private final boolean intelligentAppearsOnSearch;
	private final CountryCode market;
	private final int lookbackDays;
	private final boolean circularPlaylistFitting;
	
	// [TimestampStore]
	private Date lastUpdatedPlaylistAlbums;
	private Date lastUpdatedPlaylistSingles;
	private Date lastUpdatedPlaylistCompilations;
	private Date lastUpdatedPlaylistAppearsOn;
	private Date lastUpdatedArtistCache;
	
	////////////////
	
	/**
	 * Creates or returns the current (singleton) configuration instance for the Spotify bot
	 * based on the contents of the local INI-file
	 * 
	 * @throws IOException
	 * @throws SQLException 
	 */
	public static Config getInstance() throws IOException, SQLException {
		if (Config.instance == null) {
			Config.instance = new Config();
		}
		return Config.instance;
	}

	/**
	 * Sets up the configuration for the Spotify bot based on the contents of the local INI-file
	 * 
	 * @throws IOException
	 * @throws SQLException 
	 */
	private Config() throws IOException, SQLException {
		// Read and set config
		ResultSet botConfig = SpotifyBotDatabase.getInstance().singleRow(Constants.TABLE_BOT_CONFIG);
		ResultSet userConfig = SpotifyBotDatabase.getInstance().singleRow(Constants.TABLE_USER_CONFIG);
		ResultSet timestampStore = SpotifyBotDatabase.getInstance().singleRow(Constants.TABLE_TIMESTAMP_STORE);
		
		// Set bot config
		clientId = botConfig.getString(Constants.COL_CLIENT_ID);
		clientSecret = botConfig.getString(Constants.COL_CLIENT_SECRET);
		callbackUri = botConfig.getString(Constants.COL_CALLBACK_URI);
		logLevel = Level.parse(botConfig.getString(Constants.COL_LOGLEVEL));
		logToFile = botConfig.getString(Constants.COL_LOG_TO_FILE);
		newNotificationTimeout = botConfig.getInt(Constants.COL_NEW_NOTIFICATION_TIMEOUT);
		artistCacheTimeout = botConfig.getInt(Constants.COL_ARTIST_CACHE_TIMEOUT);
		
		// Create logger
		createLogger();
		
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
		
		// Set timestamps
		lastUpdatedPlaylistAlbums = timestampStore.getDate(Constants.COL_LAST_UPDATED_PLAYLIST_ALBUMS);
		lastUpdatedPlaylistSingles = timestampStore.getDate(Constants.COL_LAST_UPDATED_PLAYLIST_SINGLES);
		lastUpdatedPlaylistCompilations = timestampStore.getDate(Constants.COL_LAST_UPDATED_PLAYLIST_COMPILATIONS);
		lastUpdatedPlaylistAppearsOn = timestampStore.getDate(Constants.COL_LAST_UPDATED_PLAYLIST_APPEARS_ON);
		lastUpdatedArtistCache = timestampStore.getTimestamp(Constants.COL_LAST_UPDATED_ARTIST_CACHE);
	}
	
	private void createLogger() throws IOException {
		this.log = Logger.getGlobal();
		if (logToFile != null && !logToFile.isEmpty()) {
			File logFilePath = new File(Main.OWN_LOCATION, logToFile);
			if (!logFilePath.canRead()) {
				logFilePath.createNewFile();
			}
			Handler h = new FileHandler(logFilePath.getAbsolutePath(), true);
			h.setFormatter(new SimpleFormatter());
			log.addHandler(h);
		}
		log.setLevel(logLevel);
		for (Handler h : log.getHandlers()) {
			h.setLevel(logLevel);
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
		SpotifyBotDatabase.getInstance().updateColumnInTable(Constants.TABLE_USER_CONFIG, Constants.COL_ACCESS_TOKEN, accessToken);
		SpotifyBotDatabase.getInstance().updateColumnInTable(Constants.TABLE_USER_CONFIG, Constants.COL_REFRESH_TOKEN, refreshToken);
	}

	////////////////////
	
	/**
	 * Fetch the current logger instance
	 * @return
	 * @throws IOException
	 * @throws SQLException 
	 */
	public static Logger log() throws IOException, SQLException {
		return Config.getInstance().log;
	}
	
	/**
	 * Kill all logger handlers before closing the app
	 */
	public void closeLogger() {
		if (log != null) {
			for (Handler h : log.getHandlers()) {
				h.close();
			}			
		}
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
	
	public Date getLastUpdatedPlaylistAlbums() {
		return lastUpdatedPlaylistAlbums;
	}

	public void setLastUpdatedPlaylistAlbums(Date lastUpdatedPlaylistAlbums) {
		this.lastUpdatedPlaylistAlbums = lastUpdatedPlaylistAlbums;
	}

	public Date getLastUpdatedPlaylistSingles() {
		return lastUpdatedPlaylistSingles;
	}

	public void setLastUpdatedPlaylistSingles(Date lastUpdatedPlaylistSingles) {
		this.lastUpdatedPlaylistSingles = lastUpdatedPlaylistSingles;
	}

	public Date getLastUpdatedPlaylistCompilations() {
		return lastUpdatedPlaylistCompilations;
	}

	public void setLastUpdatedPlaylistCompilations(Date lastUpdatedPlaylistCompilations) {
		this.lastUpdatedPlaylistCompilations = lastUpdatedPlaylistCompilations;
	}

	public Date getLastUpdatedPlaylistAppearsOn() {
		return lastUpdatedPlaylistAppearsOn;
	}

	public void setLastUpdatedPlaylistAppearsOn(Date lastUpdatedPlaylistAppearsOn) {
		this.lastUpdatedPlaylistAppearsOn = lastUpdatedPlaylistAppearsOn;
	}

	public Date getLastUpdatedArtistCache() {
		return lastUpdatedArtistCache;
	}

	public void setLastUpdatedArtistCache(Date lastUpdatedArtistCache) {
		this.lastUpdatedArtistCache = lastUpdatedArtistCache;
	}
}
