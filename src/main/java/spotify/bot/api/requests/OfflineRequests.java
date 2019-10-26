package spotify.bot.api.requests;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.model_objects.specification.Album;

public class OfflineRequests {
	/**
	 * Static calls only
	 */
	private OfflineRequests() {}
	
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
			if (a != null && a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
				if (validDates.contains(a.getReleaseDate())) {
					filteredAlbums.add(a);
				}
			}
		}
		return filteredAlbums;
	}

	/**
	 * Categorizes the given list of albums into a map of their respective album types 
	 * 
	 * @param albums
	 * @return
	 */
	public static Map<AlbumType, List<Album>> categorizeAlbumsByAlbumType(List<Album> albums) {
		Map<AlbumType, List<Album>> categorized = new ConcurrentHashMap<>();
		albums.parallelStream().forEach(a -> {
			AlbumType albumTypeOfAlbum = a.getAlbumType();
			if (!categorized.containsKey(albumTypeOfAlbum)) {
				categorized.put(albumTypeOfAlbum, new ArrayList<>());
			}
			categorized.get(albumTypeOfAlbum).add(a);
		});
		return categorized;
	}
}
