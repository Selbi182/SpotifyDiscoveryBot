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
	 * Returns true if all album groups have empty lists
	 * 
	 * @param listsByGroup
	 * @return
	 */
	public static <T> boolean isAllEmptyAlbumsOfGroups(Map<AlbumGroup, List<T>> listsByGroup) {
		return listsByGroup.values().stream().allMatch(List::isEmpty);
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
	public static Map<AlbumGroup, Integer> collectSongAdditionResults(Map<AlbumGroup, List<AlbumTrackPair>> newSongsMap) {
		Map<AlbumGroup, Integer> targetCountMap = new HashMap<>();
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : newSongsMap.entrySet()) {
			int totalSongsOfGroup = entry.getValue().stream().mapToInt(atp -> atp.getTracks().size()).sum();
			targetCountMap.put(entry.getKey(), totalSongsOfGroup);
		}
		return targetCountMap;
	}
}
