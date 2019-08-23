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
	public final static String DB_TBL_ALBUMS = "albums";
	public final static String DB_ROW_ALBUM_IDS = "album_id";
	public final static String DB_URL_PREFIX = "jdbc:sqlite:";

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
