package spotify.bot.util;

import java.text.SimpleDateFormat;
import java.util.Locale;

public final class Constants {

	/**
	 * Utility class
	 */
	private Constants() {}

	// Generic
	public final static int RETRY_TIMEOUT_4XX = 500;
	public final static int RETRY_TIMEOUT_5XX = 60 * 1000;
	public final static int PLAYLIST_ADDITION_COOLDOWN = 1000;
	public final static String VARIOUS_ARTISTS = "Various Artists";

	// Spotify
	public final static String SCOPES = "user-follow-read playlist-modify-private";
	public final static String TRACK_PREFIX = "spotify:track:";
	public final static int DEFAULT_LIMIT = 50;
	public final static int PLAYLIST_ADD_LIMIT = 100;
	public final static int PLAYLIST_SIZE_LIMIT = 10000;
	public final static String RELEASE_DATE_FORMAT_DAY = "yyyy-MM-dd";
	public final static String RELEASE_DATE_FORMAT_MONTH = "yyyy-MM";

	// Playlist Timestamps
	public final static String NEW_INDICATOR_TEXT = "\uD83C\uDD7D\uD83C\uDD74\uD83C\uDD86";
	public final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);
}
