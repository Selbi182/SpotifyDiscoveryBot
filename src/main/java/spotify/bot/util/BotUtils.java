package spotify.bot.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.config.Config;
import spotify.bot.config.Config.PlaylistStore;
import spotify.bot.dto.AlbumTrackPair;

public final class BotUtils {

	/**
	 * Utility class
	 */
	private BotUtils() {}
	
	///////

	private static Config config;

	/**
	 * Initialize the utility class's configuration
	 * 
	 * @param config
	 */
	static void initializeUtilConfig(Config config) {
		BotUtils.config = config;
	}
	
	///////

	/**
	 * Returns the stored playlist ID by the given album group. Should the same ID
	 * be set for multiple playlists, the album group is returned hierarchically:
	 * ALBUM > SINGLE > COMPILATION > APPEARS_ON
	 * 
	 * @param albumGroup
	 * @return
	 * @throws IOException
	 */
	public static String getPlaylistIdByGroup(AlbumGroup albumGroup) {
		return config.getPlaylistStoreByAlbumGroup(albumGroup).getPlaylistId();
	}

	/**
	 * Check if the given old date is still within the allowed timeout threshold in
	 * hours
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

	/**
	 * Fetch all album groups that are set in the config
	 * 
	 * @param albumGroups
	 */
	public static List<AlbumGroup> getSetAlbumGroups() {
		List<AlbumGroup> setAlbumGroups = new ArrayList<>();
		for (AlbumGroup ag : AlbumGroup.values()) {
			PlaylistStore ps = config.getPlaylistStoreByAlbumGroup(ag);
			if (ps != null) {
				if ((ps.getPlaylistId() != null && !ps.getPlaylistId().trim().isEmpty()) || ps.getParentAlbumGroup() != null) {
					setAlbumGroups.add(ag);
				}
			}
		}
		return setAlbumGroups;
	}

	/**
	 * Returns true if the album group is set to Compilation or the artist is
	 * "Various Artists"
	 * 
	 * @param a
	 * @return
	 */
	public static boolean isCollectionOrSampler(AlbumSimplified a) {
		if (!a.getAlbumGroup().equals(AlbumGroup.COMPILATION)) {
			return Arrays.asList(a.getArtists()).stream().anyMatch(as -> as.getName().equals(Constants.VARIOUS_ARTISTS));
		}
		return true;
	}

	/**
	 * Checks if ag least a single artist of the subset is part of the given artist
	 * superset
	 * 
	 * @param followedArtists
	 * @param artists
	 * @return
	 */
	public static boolean containsFeaturedArtist(Collection<String> artistSuperset, ArtistSimplified[] artistSubset) {
		Set<String> artistSubsetIds = Arrays.asList(artistSubset).stream().map(ArtistSimplified::getId).collect(Collectors.toSet());
		return artistSuperset.stream().anyMatch(a -> artistSubsetIds.contains(a));
	}

	/**
	 * Creates a concurrent generic map with some List T as the values
	 * 
	 * @param albumGroups
	 * @return
	 */
	public static <T> Map<AlbumGroup, List<T>> createAlbumGroupToListOfTMap(Collection<AlbumGroup> albumGroups) {
		Map<AlbumGroup, List<T>> albumGroupToList = new HashMap<>();
		for (AlbumGroup ag : albumGroups) {
			albumGroupToList.put(ag, new ArrayList<>());
		}
		return albumGroupToList;
	}

	/**
	 * Creates a concurrent generic map with 0-set integers as the values
	 * 
	 * @param albumGroups
	 * @return
	 */
	public static Map<AlbumGroup, Integer> createAlbumGroupToIntegerMap(Collection<AlbumGroup> albumGroups) {
		Map<AlbumGroup, Integer> albumGroupToInteger = new HashMap<>();
		albumGroups.stream().forEach(ag -> {
			albumGroupToInteger.put(ag, 0);
		});
		return albumGroupToInteger;
	}

	/**
	 * Creates the comma-delimited, lowercase String of album groups to search for
	 * 
	 * @param albumGroups
	 * @return
	 */
	public static String createAlbumGroupString(List<AlbumGroup> albumGroups) {
		StringJoiner albumGroupsAsString = new StringJoiner(",");
		albumGroups.stream().forEach(ag -> albumGroupsAsString.add(ag.getGroup()));
		return albumGroupsAsString.toString();
	}

	/**
	 * Returns true if all album groups have empty album lists
	 * 
	 * @param albumsByGroup
	 * @return
	 */
	public static boolean isAllEmptyAlbumsOfGroups(Map<AlbumGroup, List<AlbumSimplified>> albumsByGroup) {
		return albumsByGroup.values().stream().allMatch(l -> l.isEmpty());
	}

	/**
	 * Remove all items from this list that are either <i>null</i> or "null" (a
	 * literal String)
	 * 
	 * @param followedArtists
	 */
	public static void removeNullStrings(Collection<String> collection) {
		collection.removeIf(e -> e == null || e.toLowerCase().equals("null"));
	}

	/**
	 * Remove all items from this collection that are null
	 * 
	 * @param collection
	 */
	public static void removeNulls(Collection<?> collection) {
		collection.removeIf(e -> e == null);
	}

	/**
	 * Compiles the final results of the bot if any songs were added
	 * 
	 * @param songsAddedPerAlbumGroups
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String compileResultString(Map<AlbumGroup, Integer> songsAddedPerAlbumGroups) {
		if (songsAddedPerAlbumGroups != null) {
			int totalSongsAdded = songsAddedPerAlbumGroups.values().stream().mapToInt(Integer::intValue).sum();
			if (totalSongsAdded > 0) {
				StringJoiner sj = new StringJoiner(" / ");
				songsAddedPerAlbumGroups.entrySet().stream().forEach(sapat -> {
					if (sapat.getValue() > 0) {
						sj.add(sapat.getValue() + " " + sapat.getKey());
					}
				});
				return (String.format("%d new song%s added! [%s]", totalSongsAdded, totalSongsAdded > 1 ? "s" : "", sj.toString()));
			}
		}
		return null;
	}

	/**
	 * Return the current time as unix timestamp
	 * 
	 * @return
	 */
	public static long currentTime() {
		Calendar cal = Calendar.getInstance();
		return cal.getTimeInMillis();
	}

	/**
	 * Write the song count per album group into the target map
	 * 
	 * @param newSongsMap
	 * @param targetCountMap
	 */
	public static void writeSongAdditionResults(Map<AlbumGroup, List<AlbumTrackPair>> newSongsMap, Map<AlbumGroup, Integer> targetCountMap) {
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : newSongsMap.entrySet()) {
			int totalSongsOfGroup = entry.getValue().stream().mapToInt(atp -> atp.getTracks().size()).sum();
			targetCountMap.put(entry.getKey(), totalSongsOfGroup);
		}
	}
}
