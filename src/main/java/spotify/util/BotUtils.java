package spotify.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.model_objects.specification.Album;

import spotify.bot.Config;

public final class BotUtils {

	private BotUtils() {
	}

	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param albums
	 * @return
	 */
	public static List<Album> filterNewAlbumsOnly(List<Album> albums, int lookbackDays) {
		List<Album> filteredAlbums = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");
		Set<String> validDates = new HashSet<>();
		for (int i = 0; i < lookbackDays; i++) {
			validDates.add(date.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
		for (Album a : albums) {
			if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
				if (validDates.contains(a.getReleaseDate())) {
					filteredAlbums.add(a);
				}
			}
		}
		return filteredAlbums;
	}

	/**
	 * Sort the album list with the default comparator
	 * 
	 * @param albums
	 */
	public static void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> Constants.RELEASE_COMPARATOR.compare(a1, a2));
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
	 */
	public static String getPlaylistIdByType(Config config, AlbumType albumType) {
		switch (albumType) {
			case ALBUM:
				return config.getPlaylistAlbums();
			case SINGLE:
				return config.getPlaylistSingles();
			case COMPILATION:
				return config.getPlaylistCompilations();
			case APPEARS_ON:
				return config.getPlaylistAppearsOn();
		}
		return null;
	}
}
