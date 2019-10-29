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
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.util.BotUtils;

public class OfflineRequests {
	/**
	 * Static calls only
	 */
	private OfflineRequests() {}
	
	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param fullAlbums
	 * @return
	 */
	public static Map<AlbumType, List<Album>> filterNewAlbumsOnly(Map<AlbumType, List<Album>> fullAlbums, int lookbackDays) {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");
		Set<String> validDates = new HashSet<>();
		for (int i = 0; i < lookbackDays; i++) {
			validDates.add(date.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
		
		Map<AlbumType, List<Album>> filteredAlbums = BotUtils.createAlbumTypeMap(fullAlbums.keySet());
		fullAlbums.entrySet().parallelStream().forEach(fa -> {
			fa.getValue().stream().forEach(a -> {
				if (a != null && a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
					if (validDates.contains(a.getReleaseDate())) {
						filteredAlbums.get(fa.getKey()).add(a);
					}
				}
			});
		});
		return filteredAlbums;
	}

	/**
	 * Categorizes the given list of albums into a map of their respective album GROUPS
	 * (aka the return context of the simplified album object) 
	 * 
	 * @param albumsSimplified
	 * @return
	 */
	public static Map<AlbumType, List<AlbumSimplified>> categorizeAlbumsByAlbumGroup(List<AlbumSimplified> albumsSimplified) {
		Map<AlbumType, List<AlbumSimplified>> categorized = new ConcurrentHashMap<>();
		albumsSimplified.parallelStream().forEach(as -> {
			// TODO API hat keine album group, die husos
			//AlbumType albumTypeOfAlbum = as.getAlbumGroup();
			AlbumType albumTypeOfAlbum = as.getAlbumType();
			if (!categorized.containsKey(albumTypeOfAlbum)) {
				categorized.put(albumTypeOfAlbum, new ArrayList<>());
			}
			categorized.get(albumTypeOfAlbum).add(as);
		});
		return categorized;
	}
}
