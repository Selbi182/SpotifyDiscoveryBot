package spotify.bot;

import static spotify.bot.util.Constants.DB_FILE_NAME;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.ini4j.Wini;

import com.neovisionaries.i18n.CountryCode;

import spotify.bot.util.Constants;

public class Config {

	// SINGLETON
	private static Config instance;

	/////////////////
	
	// Logger
	private final Logger log;

	// INI
	private final Wini iniFile;

	// DB
	private final String dbUrl;

	/////////////////
	
	// [Client]
	private final String clientId;
	private final String clientSecret;
	private final String callbackUri;

	// [Tokens]
	private String accessToken;
	private String refreshToken;
	
	// [Playlists]
	private final String playlistAlbums;
	private final String playlistSingles;
	private final String playlistCompilations;
	private final String playlistAppearsOn;
	
	// [UserConfig]
	private final boolean intelligentAppearsOnSearch;
	private final CountryCode market;
	private final int lookbackDays;
	private final int newNotificationTimeout;
	
	////////////////
	
	// INI
	private final static String INI_FILENAME = "settings.ini";

	private final static String SECTION_CLIENT = "Client";
	private final static String KEY_CLIENT_ID = "clientId";
	private final static String KEY_CLIENT_SECRET = "clientSecret";
	private final static String KEY_CALLBACK_URI = "callbackUri";

	private final static String SECTION_TOKENS = "Tokens";
	private final static String KEY_ACCESS_TOKEN = "accessToken";
	private final static String KEY_REFRESH_TOKEN = "refreshToken";

	private final static String SECTION_PLAYLISTS = "Playlists";
	private final static String KEY_PLAYLIST_ALBUMS = "playlistAlbums";
	private final static String KEY_PLAYLIST_SINGLES = "playlistSingles";
	private final static String KEY_PLAYLIST_COMPILATIONS = "playlistCompilations";
	private final static String KEY_PLAYLIST_APPEARS_ON = "playlistAppearsOn";
	
	private final static String SECTION_USER_CONFIG = "UserConfig";
	private final static String KEY_INTELLIGENT_APPEARS_ON_SEARCH = "intelligentAppearsOnSearch";
	private final static String KEY_MARKET = "market";
	private final static String KEY_LOOKBACK_DAYS = "lookbackDays";

	private final static String SECTION_BOT_CONFIG = "BotConfig";
	private final static String KEY_LOGLEVEL = "logLevel";
	private final static String KEY_LOG_TO_FILE = "logToFile";
	private final static String KEY_NEW_NOTIFICATION_TIMEOUT = "newNotificationTimeout";

	/**
	 * Creates or returns the current (singleton) configuration instance for the Spotify bot
	 * based on the contents of the local INI-file
	 * 
	 * @throws IOException
	 */
	public static Config getInstance() throws IOException {
		if (Config.instance == null) {
			Config.instance = new Config();
		}
		return Config.instance;
	}

	/**
	 * Sets up the configuration for the Spotify bot based on the contents of the local INI-file
	 * 
	 * @throws IOException
	 */
	private Config() throws IOException {
		// Set file paths
		File ownLocation = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath()).getAbsoluteFile();
		File iniFilePath = new File(ownLocation, INI_FILENAME);

		// Parse INI File
		if (!iniFilePath.canRead()) {
			throw new IOException("Cannot read .ini file!");
		}
		this.iniFile = new Wini(iniFilePath);

		// Ensure readability of the database file
		File dbFilePath = new File(ownLocation, DB_FILE_NAME);
		if (!dbFilePath.canRead()) {
			throw new IOException("Cannot read .db file!");
		}
		this.dbUrl = Constants.DB_URL_PREFIX + dbFilePath.getAbsolutePath();

		// Configure Logger
		this.log = Logger.getGlobal();
		Level l = Level.parse(iniFile.get(SECTION_BOT_CONFIG, KEY_LOGLEVEL));
		String logToFile = iniFile.get(SECTION_BOT_CONFIG, KEY_LOG_TO_FILE);
		if (logToFile != null && !logToFile.isEmpty()) {
			File logFilePath = new File(ownLocation, logToFile);
			if (!logFilePath.canRead()) {
				logFilePath.createNewFile();
			}
			Handler h = new FileHandler(logFilePath.getAbsolutePath(), true);
			h.setFormatter(new SimpleFormatter());
			log.addHandler(h);
		}
		log.setLevel(l);
		for (Handler h : log.getHandlers()) {
			h.setLevel(l);
		}

		// Set general client data given via the Spotify dashboard
		this.clientId = iniFile.get(SECTION_CLIENT, KEY_CLIENT_ID);
		this.clientSecret = iniFile.get(SECTION_CLIENT, KEY_CLIENT_SECRET);
		this.callbackUri = iniFile.get(SECTION_CLIENT, KEY_CALLBACK_URI);

		// Set playlist IDs
		this.playlistAlbums = iniFile.get(SECTION_PLAYLISTS, KEY_PLAYLIST_ALBUMS);
		this.playlistSingles = iniFile.get(SECTION_PLAYLISTS, KEY_PLAYLIST_SINGLES);
		this.playlistCompilations = iniFile.get(SECTION_PLAYLISTS, KEY_PLAYLIST_COMPILATIONS);
		this.playlistAppearsOn = iniFile.get(SECTION_PLAYLISTS, KEY_PLAYLIST_APPEARS_ON);
		
		// Sert user config
		this.intelligentAppearsOnSearch = Boolean.valueOf(iniFile.get(SECTION_USER_CONFIG, KEY_INTELLIGENT_APPEARS_ON_SEARCH));
		this.market = CountryCode.valueOf(iniFile.get(SECTION_USER_CONFIG, KEY_MARKET));
		this.lookbackDays = iniFile.get(SECTION_USER_CONFIG, KEY_LOOKBACK_DAYS, int.class);
		this.newNotificationTimeout = iniFile.get(SECTION_USER_CONFIG, KEY_NEW_NOTIFICATION_TIMEOUT, int.class);
		
		// Writable tokens
		updateTokens(iniFile.get(SECTION_TOKENS, KEY_ACCESS_TOKEN), iniFile.get(SECTION_TOKENS, KEY_REFRESH_TOKEN));
	}

	/**
	 * Update the access and refresh tokens, both in the config object as well as
	 * the ini file
	 * 
	 * @param accessToken
	 * @param refreshToken
	 * @throws IOException
	 */
	public void updateTokens(String accessToken, String refreshToken) throws IOException {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;

		iniFile.put(SECTION_TOKENS, KEY_ACCESS_TOKEN, accessToken);
		iniFile.put(SECTION_TOKENS, KEY_REFRESH_TOKEN, refreshToken);
		iniFile.store();
	}

	/**
	 * Kill all logger handlers before closing the app
	 */
	public void closeLogger() {
		for (Handler h : log.getHandlers()) {
			h.close();
		}
	}
	
	public static Logger log() throws IOException {
		return Config.getInstance().log;
	}

	public Wini getIniFile() {
		return iniFile;
	}

	public String getDbUrl() {
		return dbUrl;
	}

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
}
