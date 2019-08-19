package spotify.util;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Locale;

import com.wrapper.spotify.model_objects.specification.Album;

public final class Constants {
	// Generic
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
	
	// Comparators
	private final static Comparator<Album> COMPARATOR_ALBUM_TYPE = Comparator.comparing(Album::getAlbumType);
	private final static Comparator<Album> COMPARATOR_RELEASE_DATE = Comparator.comparing(Album::getReleaseDate);
	private final static Comparator<Album> COMPARATOR_FIRST_ARTIST_NAME = (a1, a2) -> a1.getArtists()[0].getName()
			.compareTo(a2.getArtists()[0].getName());
	private final static Comparator<Album> COMPARATOR_ALBUM_NAME = Comparator.comparing(Album::getName);

	public final static Comparator<Album> RELEASE_COMPARATOR =
			COMPARATOR_ALBUM_TYPE
			.thenComparing(COMPARATOR_RELEASE_DATE)
			.thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
			.thenComparing(COMPARATOR_ALBUM_NAME);

	/**
	 * Private constructor
	 */
	private Constants() {}
}
