package spotify.bot.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;

import spotify.bot.Config;

public final class BotUtils {
	/**
	 * Static calls only
	 */
	private BotUtils() {}


	// Comparators
	private final static Comparator<Album> COMPARATOR_ALBUM_TYPE = Comparator.comparing(Album::getAlbumType);
	private final static Comparator<Album> COMPARATOR_RELEASE_DATE = Comparator.comparing(Album::getReleaseDate);
	private final static Comparator<Album> COMPARATOR_TRACK_COUNT = Comparator.comparing((a) -> a.getTracks().getTotal(), Comparator.reverseOrder());
	private final static Comparator<Album> COMPARATOR_FIRST_ARTIST_NAME = Comparator.comparing((a) -> a.getArtists()[0].getName(), Comparator.reverseOrder());
	private final static Comparator<Album> COMPARATOR_ALBUM_NAME = Comparator.comparing(Album::getName, Comparator.reverseOrder());

	private final static Comparator<Album> RELEASE_COMPARATOR =
			COMPARATOR_ALBUM_TYPE
			.thenComparing(COMPARATOR_RELEASE_DATE)
			.thenComparing(COMPARATOR_TRACK_COUNT)
			.thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
			.thenComparing(COMPARATOR_ALBUM_NAME);

	/**
	 * Sort the album list with the default comparator
	 * 
	 * @param albums
	 */
	public static void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> RELEASE_COMPARATOR.compare(a1, a2));
	}

	/**
	 * Check if the given playlistId has been set
	 */
	public static boolean isPlaylistSet(String playlistId) {
		return playlistId != null && !playlistId.trim().isEmpty();
	}

	/**
	 * Returns the stored playlist ID by the given album type
	 * 
	 * @param albumType
	 * @return
	 * @throws IOException 
	 */
	public static String getPlaylistIdByType(AlbumType albumType) {
		try {
			switch (albumType) {
				case ALBUM:
					return Config.getInstance().getPlaylistAlbums();
				case SINGLE:
					return Config.getInstance().getPlaylistSingles();
				case COMPILATION:
					return Config.getInstance().getPlaylistCompilations();
				case APPEARS_ON:
					return Config.getInstance().getPlaylistAppearsOn();
			}
		} catch (IOException | SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Returns true if at least one of tha arguments equates to null
	 * 
	 * @param objects
	 * @return
	 */
	public static boolean anyNotNull(Object... objects) {
		for (Object o : objects) {
			if (o == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Wait for every given thread to finish and exit
	 * 
	 * @param threads
	 * @throws InterruptedException
	 */
	public static void joinAll(Thread... threads) throws InterruptedException {
		for (Thread t : threads) {
			t.join();
		}
	}

	/**
	 * Check if the given old date is still within the allowed timeout threshold in hours
	 * 
	 * @param oldDate
	 * @param timeout
	 */
	public static boolean isTimeoutActive(Date oldDate, int timeout) {
		Calendar calCurrent = Calendar.getInstance();
		Calendar calOld = Calendar.getInstance();
		calOld.setTime(oldDate);
		calOld.add(Calendar.HOUR_OF_DAY, timeout);
		return calCurrent.before(calOld);
	}
}
