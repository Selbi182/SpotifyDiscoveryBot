package spotify.bot.util;

import java.text.SimpleDateFormat;
import java.util.Locale;

public final class Constants {
	/**
	 * Static calls only
	 */
	private Constants() {}
	
	// Generic
	public static final long BOT_TIMEOUT = 10 * 60 * 1000;
	public final static int RETRY_TIMEOUT = 500;
	public final static String VARIOUS_ARTISTS = "Various Artists";

	// Database
	public final static String DB_FILE_NAME = "database.db";
	public final static String DB_URL_PREFIX = "jdbc:sqlite:";
	
	public final static String TABLE_ALBUM_CACHE = "album_cache";
	public final static String COL_ALBUM_IDS = "album_id";
	
	public final static String TABLE_ARTIST_CACHE = "artist_cache";
	public final static String COL_ARTIST_IDS = "artist_id";
	
	public final static String TABLE_BOT_CONFIG = "bot_config";
	public final static String COL_CLIENT_ID = "client_id";
	public final static String COL_CLIENT_SECRET = "client_secret";
	public final static String COL_CALLBACK_URI = "callback_uri";
    public final static String COL_LOGLEVEL = "log_level";
    public final static String COL_LOG_TO_FILE = "log_to_file";
    public final static String COL_NEW_NOTIFICATION_TIMEOUT = "new_notification_timeout";
    public static final String COL_ARTIST_CACHE_TIMEOUT = "artist_cache_timeout";
    
    public final static String TABLE_USER_CONFIG = "user_config";
    public final static String COL_ACCESS_TOKEN = "access_token";
    public final static String COL_REFRESH_TOKEN = "refresh_token";
    public final static String COL_PLAYLIST_ALBUMS = "playlist_albums";
    public final static String COL_PLAYLIST_SINGLES = "playlist_singles";
    public final static String COL_PLAYLIST_COMPILATIONS = "playlist_compilations";
    public final static String COL_PLAYLIST_APPEARS_ON = "playlist_appears_on";
    public final static String COL_INTELLIGENT_APPEARS_ON_SEARCH = "intelligent_appears_on_search";
    public final static String COL_MARKET = "market";
    public final static String COL_LOOKBACK_DAYS = "lookback_days";
    public final static String COL_CIRCULAR_PLAYLIST_FITTING = "circular_playlist_fitting";

    public final static String TABLE_UPDATE_STORE = "update_store";
    public final static String COL_TYPE = "type";
    public final static String COL_LAST_UPDATED_TIMESTAMP = "last_updated_timestamp";
    public final static String COL_LAST_UPDATE_SONG_COUNT = "last_update_song_count";
    
    // Spotify
	public final static String SCOPES = "user-follow-read playlist-modify-private";
	public final static String TRACK_PREFIX = "spotify:track:";
	public final static int DEFAULT_LIMIT = 50;
	public final static int SEVERAL_ALBUMS_LIMIT = 20;
	public final static int PLAYLIST_ADD_LIMIT = 100;
	public final static int PLAYLIST_SIZE_LIMIT = 10000;
	
	// Playlist Timestamps
	public final static String NEW_INDICATOR_TEXT = "\uD83C\uDD7D\uD83C\uDD74\uD83C\uDD86";
	public final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);
}
