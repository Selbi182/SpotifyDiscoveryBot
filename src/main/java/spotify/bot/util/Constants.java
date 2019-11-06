package spotify.bot.util;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class Constants {
	/**
	 * Static calls only
	 */
	private Constants() {}

	/**
	 * Own file location to read and write files in the same folder as the JAR
	 */
	public final static File WORKSPACE_LOCATION = Paths.get(".").toFile();

	// Generic
	public final static int RETRY_TIMEOUT_4XX = 500;
	public final static int RETRY_TIMEOUT_5XX = 60 * 1000;
	public final static int PLAYLIST_ADDITION_COOLDOWN = 1000;
	public final static String VARIOUS_ARTISTS = "Various Artists";

    // Spotify
	public final static String SCOPES = "user-follow-read playlist-modify-private";
	public final static String TRACK_PREFIX = "spotify:track:";
	public final static int DEFAULT_LIMIT = 50;
	public final static int SEVERAL_ALBUMS_LIMIT = 20;
	public final static int PLAYLIST_ADD_LIMIT = 100;
	public final static int PLAYLIST_SIZE_LIMIT = 10000;
	public final static String RELEASE_DATE_FORMAT_DAY = "yyyy-MM-dd";
	public final static String RELEASE_DATE_FORMAT_MONTH = "yyyy-MM";
	
	// Playlist Timestamps
	public final static String NEW_INDICATOR_TEXT = "\uD83C\uDD7D\uD83C\uDD74\uD83C\uDD86";
	public final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);
	
	// Scheduler Time Settings
	public final static int CRAWL_INTERVAL = 15;
	public final static int CRAWL_OFFSET = 1;
	public final static int CLEAR_NOTIFIER_OFFSET = 5;
	public final static int CLEAR_NOTIFIER_INTERVAL = 10;
}
