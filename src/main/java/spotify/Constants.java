package spotify;

public final class Constants {
	// Generic
	public final static int SECOND_IN_MILLIS = 1000;
	
	// Database
	public final static String DB_FILE_NAME = "database.db";
	public final static String DB_TBL_ALBUMS = "albums";
	public final static String DB_ROW_ALBUM_IDS = "album_id";
	
	// INI
	public final static String INI_FILENAME = "settings.ini";
	
	public final static String SECTION_CLIENT = "Client";
	public final static String KEY_CLIENT_ID = "clientId";
	public final static String KEY_CLIENT_SECRET = "clientSecret";
	public final static String KEY_CALLBACK_URI = "callbackUri";
	
	public final static String SECTION_TOKENS = "Tokens";
	public final static String KEY_ACCESS_TOKEN = "accessToken";
	public final static String KEY_REFRESH_TOKEN = "refreshToken";

	public final static String SECTION_USER = "User";
	public final static String KEY_PLAYLIST_ID = "playlistId";
	public final static String KEY_MARKET = "market";
	public final static String KEY_ALBUM_TYPES = "albumTypes";
	
	public final static String SECTION_SPOTIFY = "Spotify";
	public final static String KEY_SCOPES = "scopes";
	public final static String KEY_TRACK_PREFIX = "trackPrefix";
	public final static String KEY_DEFAULT_LIMIT = "defaultLimit";
	public final static String KEY_SEVERAL_ALBUMS_LIMIT = "severalAlbumsLimit";
	public final static String KEY_PLAYLIST_ADD_LIMIT = "playlistAddLimit";
	
	public final static String SECTION_SEARCH = "Search";
	public final static String KEY_LOOKBACK_DAYS = "lookbackDays";
	public final static String KEY_SLEEP_MINUTES = "sleepMinutes";
	
	/**
	 * Private constructor
	 */
	private Constants() {}
}
