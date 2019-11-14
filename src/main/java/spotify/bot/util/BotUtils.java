package spotify.bot.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.wrapper.spotify.enums.AlbumGroup;

import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

public final class BotUtils {

	/**
	 * Utility class
	 */
	private BotUtils() {}

	///////

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
	 * Creates a concurrent generic map with some List T as the values
	 * 
	 * @return
	 */
	public static <T> Map<AlbumGroup, List<T>> createAlbumGroupToListOfTMap() {
		Map<AlbumGroup, List<T>> albumGroupToList = new HashMap<>();
		for (AlbumGroup ag : AlbumGroup.values()) {
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
	public static Map<AlbumGroupExtended, Integer> createAlbumGroupToIntegerMap(Collection<AlbumGroupExtended> albumGroups) {
		Map<AlbumGroupExtended, Integer> albumGroupToInteger = new HashMap<>();
		for (AlbumGroupExtended age : albumGroups) {
			albumGroupToInteger.put(age, 0);
		}
		return albumGroupToInteger;
	}

	/**
	 * Returns true if all mappings just contain an empty list (not null)
	 * 
	 * @param listsByMap
	 * @return
	 */
	public static <T, K> boolean isAllEmptyLists(Map<K, List<T>> listsByMap) {
		return listsByMap.values().stream().allMatch(List::isEmpty);
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
	public static String compileResultString(Map<AlbumGroupExtended, Integer> songsAddedPerAlbumGroups) {
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
	 * @param songsByPlaylist
	 * @param targetCountMap
	 */
	public static Map<AlbumGroupExtended, Integer> collectSongAdditionResults(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) {
		Map<AlbumGroupExtended, Integer> targetCountMap = new HashMap<>();
		for (Map.Entry<PlaylistStore, List<AlbumTrackPair>> entry : songsByPlaylist.entrySet()) {
			int totalSongsOfGroup = entry.getValue().stream().mapToInt(atp -> atp.getTracks().size()).sum();
			targetCountMap.put(entry.getKey().getAlbumGroupExtended(), totalSongsOfGroup);
		}
		return targetCountMap;
	}
}
