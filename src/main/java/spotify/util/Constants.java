package spotify.util;

import java.util.Comparator;

import com.wrapper.spotify.model_objects.specification.Album;

public final class Constants {
	// Generic
	public final static int SECOND_IN_MILLIS = 1000;
	public final static int MINUTE_IN_SECONDS = 60;
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
	public final static String KEY_INTELLIGENT_APPEARS_ON_SEARCH = "intelligentAppearsOnSearch";

	public final static String SECTION_CONFIG = "Config";
	public final static String KEY_LOOKBACK_DAYS = "lookbackDays";
	public final static String KEY_SLEEP_MINUTES = "sleepMinutes";
	public final static String KEY_LOGLEVEL = "logLevel";
	public final static String KEY_LOG_TO_FILE = "logToFile";

	// Comparators
	private final static Comparator<Album> COMPARATOR_ALBUM_TYPE = Comparator.comparing(Album::getAlbumType);
	private final static Comparator<Album> COMPARATOR_RELEASE_DATE = Comparator.comparing(Album::getReleaseDate);
	private final static Comparator<Album> COMPARATOR_FIRST_ARTIST_NAME = (a1, a2) -> a1.getArtists()[0].getName()
			.compareTo(a2.getArtists()[0].getName());
	private final static Comparator<Album> COMPARATOR_ALBUM_NAME = Comparator.comparing(Album::getName);

	public final static Comparator<Album> RELEASE_COMPARATOR = COMPARATOR_ALBUM_TYPE
			.thenComparing(COMPARATOR_RELEASE_DATE).thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
			.thenComparing(COMPARATOR_ALBUM_NAME);

	/**
	 * Private constructor
	 */
	private Constants() {}
}
