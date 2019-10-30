package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.util.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.ReleaseValidator;

public class OfflineRequests {
	/**
	 * Static calls only
	 */
	private OfflineRequests() {}

	/**
	 * Sort the albums of each album type
	 * 
	 * @param albumsByType
	 * @return 
	 */
	public static List<AlbumTrackPair> sortReleases(List<AlbumTrackPair> albumTrackPairs) {
		Collections.sort(albumTrackPairs);
		return albumTrackPairs;
	}
	
	/**
	 * Categorizes the given list of albums into a map of their respective album GROUPS
	 * (aka the return context of the simplified album object) 
	 * 
	 * @param albumsSimplified
	 * @param albumTypes 
	 * @return
	 */
	public static Map<AlbumType, List<AlbumSimplified>> categorizeAlbumsByAlbumGroup(List<AlbumSimplified> albumsSimplified, List<AlbumType> albumTypes) {
		Map<AlbumType, List<AlbumSimplified>> categorized = BotUtils.createAlbumTypeMap(albumTypes);
		albumsSimplified.parallelStream().forEach(as -> {
			AlbumType albumTypeOfAlbum = as.getAlbumGroup() != null ? AlbumType.keyOf(as.getAlbumGroup()) : as.getAlbumType();
			categorized.get(albumTypeOfAlbum).add(as);
		});
		return categorized;
	}
	
	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param albumsSimplifiedByType
	 * @return
	 */
	public static Map<AlbumType, List<AlbumSimplified>> filterNewAlbumsOnly(Map<AlbumType, List<AlbumSimplified>> albumsSimplifiedByType, int lookbackDays) {
		Map<AlbumType, List<AlbumSimplified>> filteredAlbums = BotUtils.createAlbumTypeMap(albumsSimplifiedByType.keySet());
		albumsSimplifiedByType.entrySet().stream().forEach(fa -> {
			List<AlbumSimplified> filteredAlbumsOfType = fa.getValue().stream().filter(as -> ReleaseValidator.getInstance().isValidDate(as)).collect(Collectors.toList());
			filteredAlbums.get(fa.getKey()).addAll(filteredAlbumsOfType);
		});
		return filteredAlbums;
	}

	/**
	 * Returns a rearranged view of the given map, depending on whether any album types point to the same target playlist.
	 * The dominance is ordered as follows: ALBUM > SINGLE > COMPILATION > APPEARS_ON
	 * 
	 * @param newSongsByType
	 * @param albumTypes
	 * @return
	 */
	public static Map<AlbumType, List<AlbumTrackPair>> mergeOnIdenticalPlaylists(Map<AlbumType, List<AlbumTrackPair>> newSongsByType, List<AlbumType> albumTypes) {
		Map<String, List<AlbumType>> groupedAlbumTypes = new HashMap<>();
		for (AlbumType at : albumTypes) {
			String playlistId = BotUtils.getPlaylistIdByType(at);
			if (!groupedAlbumTypes.containsKey(playlistId)) {
				groupedAlbumTypes.put(playlistId, new ArrayList<>());
			}
			groupedAlbumTypes.get(playlistId).add(at);
		}
		if (groupedAlbumTypes.size() == albumTypes.size()) {
			return newSongsByType;
		}
		
		Map<AlbumType, List<AlbumTrackPair>> mergedAlbumTypes = BotUtils.createAlbumTypeMap(albumTypes);
		for (List<AlbumType> group : groupedAlbumTypes.values()) {
			for (AlbumType at : group) {
				mergedAlbumTypes.get(group.get(0)).addAll(newSongsByType.get(at));
			}
		}
		return mergedAlbumTypes;
	}
}
