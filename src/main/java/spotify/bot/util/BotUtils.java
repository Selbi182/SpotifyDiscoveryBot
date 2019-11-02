package spotify.bot.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.Config;

public final class BotUtils {
	/**
	 * Static calls only
	 */
	private BotUtils() {}

	/**
	 * Check if the given playlistId has been set
	 */
	public static boolean isPlaylistSet(String playlistId) {
		return playlistId != null && !playlistId.trim().isEmpty();
	}

	/**
	 * Returns the stored playlist ID by the given album group. Should the same ID be set for multiple playlists,
	 * the album group is returned hierarchically: ALBUM > SINGLE > COMPILATION > APPEARS_ON
	 * 
	 * @param albumGroup
	 * @return
	 * @throws IOException 
	 */
	public static String getPlaylistIdByGroup(AlbumGroup albumGroup) {
		try {
			switch (albumGroup) {
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
			Config.logStackTrace(e);
		}
		return null;
	}
	
	/**
	 * Returns true if ag least one of tha arguments equates to null
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

	/**
	 * Fetch all album groups that are set in the config
	 * 
	 * @param albumGroups
	 */
	public static List<AlbumGroup> getSetAlbumGroups() {
		List<AlbumGroup> setAlbumGroups = new ArrayList<>();
		for (AlbumGroup ag : AlbumGroup.values()) {
			String playlistId = BotUtils.getPlaylistIdByGroup(ag);			
			if (BotUtils.isPlaylistSet(playlistId)) {
				setAlbumGroups.add(ag);
			}
		}
		return setAlbumGroups;
	}

	/**
	 * Takes a map of album groups with albums and puts them all into a single key
	 * 
	 * @param albumsByAlbumGroup
	 * @param appearsOn
	 */
	public static Map<AlbumGroup, List<Album>> flattenToSingleAlbumGroup(Map<AlbumGroup, List<Album>> albumsByAlbumGroup, AlbumGroup newSoloAlbumGroup) {
		Map<AlbumGroup, List<Album>> flattened = new ConcurrentHashMap<>();
		flattened.put(newSoloAlbumGroup, new ArrayList<>());
		albumsByAlbumGroup.values().parallelStream().forEach(abat -> {
			flattened.get(newSoloAlbumGroup).addAll(abat);
		});
		return flattened;
	}

	/**
	 * Returns true if the album group is set to Compilation or the artist is "Various Artists"
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
	 * Checks if ag least a single artist of the subset is part of the given artist superset
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
		Map<AlbumGroup, List<T>> albumGroupToList = new ConcurrentHashMap<>();
		albumGroups.stream().forEach(ag -> {
			albumGroupToList.put(ag, new ArrayList<>());
		});
		return albumGroupToList;
	}
	
	/**
	 * Creates a concurrent generic map with 0-set integers as the values
	 * 
	 * @param albumGroups
	 * @return
	 */
	public static Map<AlbumGroup, Integer> createAlbumGroupToIntegerMap(Collection<AlbumGroup> albumGroups) {
		Map<AlbumGroup, Integer> albumGroupToInteger = new ConcurrentHashMap<>();
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
	 * Logs the final results of the bot if any songs were added
	 * 
	 * @param songsAddedPerAlbumGroups
	 * @return
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public static void logResults(Map<AlbumGroup, Integer> songsAddedPerAlbumGroups) {
		int totalSongsAdded = songsAddedPerAlbumGroups.values().stream().mapToInt(Integer::intValue).sum();
		if (totalSongsAdded > 0) {
			StringJoiner sj = new StringJoiner(" / ");
			songsAddedPerAlbumGroups.entrySet().stream().forEach(sapat -> {
				if (sapat.getValue() > 0) {
					sj.add(sapat.getValue() + " " + sapat.getKey());						
				}
			});
			try {
				Config.log().info(String.format("%d new song%s added! [%s]", totalSongsAdded, totalSongsAdded > 1 ? "s" : "", sj.toString()));
			} catch (IOException | SQLException e) {
				// Oh well, no info I guess
			}			
		}
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
	 * Remove all items from this list that are either <i>null</i> or "null" (a literal String)
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
}
