package spotify.bot;

import static spotify.util.Constants.DB_FILE_NAME;
import static spotify.util.Constants.INI_FILENAME;
import static spotify.util.Constants.KEY_ACCESS_TOKEN;
import static spotify.util.Constants.KEY_ALBUM_TYPES;
import static spotify.util.Constants.KEY_CALLBACK_URI;
import static spotify.util.Constants.KEY_CLIENT_ID;
import static spotify.util.Constants.KEY_CLIENT_SECRET;
import static spotify.util.Constants.KEY_INTELLIGENT_APPEARS_ON_SEARCH;
import static spotify.util.Constants.KEY_LOGLEVEL;
import static spotify.util.Constants.KEY_LOG_TO_FILE;
import static spotify.util.Constants.KEY_LOOKBACK_DAYS;
import static spotify.util.Constants.KEY_MARKET;
import static spotify.util.Constants.KEY_PLAYLIST_ID;
import static spotify.util.Constants.KEY_REFRESH_TOKEN;
import static spotify.util.Constants.KEY_SLEEP_MINUTES;
import static spotify.util.Constants.MINUTE_IN_SECONDS;
import static spotify.util.Constants.SECOND_IN_MILLIS;
import static spotify.util.Constants.SECTION_CLIENT;
import static spotify.util.Constants.SECTION_CONFIG;
import static spotify.util.Constants.SECTION_TOKENS;
import static spotify.util.Constants.SECTION_USER;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.ini4j.Wini;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;

import spotify.util.Constants;

public class Config {

	// Logger
	private final Logger log;

	// INI
	private final Wini iniFile;

	// DB
	private final String dbUrl;

	// Client
	private final String clientId;
	private final String clientSecret;
	private final String callbackUri;

	// General config
	private final int lookbackDays;
	private final int sleepMinutes;
	private final long sleepMillis;
	private final String playlistId;
	private final CountryCode market;
	private final String albumTypes;
	private final boolean intelligentAppearsOnSearch;

	// Spotify API
	private final SpotifyApi spotifyApi;
	private String accessToken;
	private String refreshToken;


	public Config() throws IOException {
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
		Level l = Level.parse(iniFile.get(SECTION_CONFIG, KEY_LOGLEVEL));
		String logToFile = iniFile.get(SECTION_CONFIG, KEY_LOG_TO_FILE);
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
		
		// Build the API
		this.spotifyApi = new SpotifyApi.Builder()
			.setClientId(getClientId())
			.setClientSecret(getClientSecret())
			.setRedirectUri(SpotifyHttpManager.makeUri(getCallbackUri()))
			.build();

		// Set search settings
		this.playlistId = iniFile.get(SECTION_USER, KEY_PLAYLIST_ID);
		this.market = CountryCode.valueOf(iniFile.get(SECTION_USER, KEY_MARKET));
		this.albumTypes = iniFile.get(SECTION_USER, KEY_ALBUM_TYPES);
		this.intelligentAppearsOnSearch = Boolean.valueOf(iniFile.get(SECTION_USER, KEY_INTELLIGENT_APPEARS_ON_SEARCH));

		// Set search settings
		this.lookbackDays = iniFile.get(SECTION_CONFIG, KEY_LOOKBACK_DAYS, int.class);
		this.sleepMinutes = iniFile.get(SECTION_CONFIG, KEY_SLEEP_MINUTES, int.class);
		this.sleepMillis = sleepMinutes * SECOND_IN_MILLIS * MINUTE_IN_SECONDS;

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
	
	public Logger getLog() {
		return log;
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

	public int getSleepMinutes() {
		return sleepMinutes;
	}

	public long getSleepMillis() {
		return sleepMillis;
	}

	public String getPlaylistId() {
		return playlistId;
	}

	public CountryCode getMarket() {
		return market;
	}

	public String getAlbumTypes() {
		return albumTypes;
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
	
	public SpotifyApi getSpotifyApi() {
		return spotifyApi;
	}
}
