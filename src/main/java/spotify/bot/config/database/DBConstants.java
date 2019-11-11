package spotify.bot.config.database;

class DBConstants {

	private DBConstants() {}

	// Database constants
	public final static String TABLE_ALBUM_CACHE = "album_cache";
	public final static String COL_ALBUM_IDS = "album_id";

	public final static String TABLE_ARTIST_CACHE = "artist_cache";
	public final static String COL_ARTIST_IDS = "artist_id";

	public final static String TABLE_BOT_CONFIG = "bot_config";
	public final static String COL_CLIENT_ID = "client_id";
	public final static String COL_CLIENT_SECRET = "client_secret";
	public final static String COL_CALLBACK_URI = "callback_uri";
	public final static String COL_NEW_NOTIFICATION_TIMEOUT = "new_notification_timeout";
	public final static String COL_ARTIST_CACHE_TIMEOUT = "artist_cache_timeout";
	public final static String COL_ARTIST_CACHE_LAST_UPDATE = "artist_cache_last_update";

	public final static String TABLE_USER_CONFIG = "user_config";
	public final static String COL_ACCESS_TOKEN = "access_token";
	public final static String COL_REFRESH_TOKEN = "refresh_token";
	public final static String COL_INTELLIGENT_APPEARS_ON_SEARCH = "intelligent_appears_on_search";
	public final static String COL_MARKET = "market";
	public final static String COL_LOOKBACK_DAYS = "lookback_days";
	public final static String COL_CIRCULAR_PLAYLIST_FITTING = "circular_playlist_fitting";

	public final static String TABLE_PLAYLIST_STORE = "playlist_store";
	public final static String COL_ALBUM_GROUP = "album_group";
	public final static String COL_PLAYLIST_ID = "playlist_id";
	public final static String CPL_PARENT_ALBUM_GROUP = "parent_album_group";
	public final static String COL_LAST_UPDATE = "last_update";
	public final static String COL_RECENT_SONGS_ADDED_COUNT = "recent_songs_added_count";

}
