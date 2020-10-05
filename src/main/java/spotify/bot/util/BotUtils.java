package spotify.bot.util;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Ordering;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

public final class BotUtils {

	/**
	 * A common order of the different playlist groups: Album > Single > EP > Remix
	 * > Live > Compilation > Appears On
	 */
	public final static List<AlbumGroupExtended> DEFAULT_PLAYLIST_GROUP_ORDER = Arrays.asList(
		AlbumGroupExtended.ALBUM,
		AlbumGroupExtended.SINGLE,
		AlbumGroupExtended.EP,
		AlbumGroupExtended.REMIX,
		AlbumGroupExtended.LIVE,
		AlbumGroupExtended.COMPILATION,
		AlbumGroupExtended.RE_RELEASE,
		AlbumGroupExtended.APPEARS_ON);

	/**
	 * {@link BotUtils#DEFAULT_PLAYLIST_GROUP_ORDER} as explicit Comparator
	 */
	public final static Comparator<AlbumGroupExtended> DEFAULT_PLAYLIST_GROUP_ORDER_COMPARATOR = Ordering.explicit(DEFAULT_PLAYLIST_GROUP_ORDER);

	/**
	 * Utility class
	 */
	private BotUtils() {
	}

	///////

	/**
	 * Performs a <code>Thread.sleep(sleepMs);</code> call in a surrounded try-catch
	 * that ignores any interrupts. This method mostly exists to reduce the number
	 * of try-catch and throws clutter throughout the code. Yes, I know it's bad
	 * practice, cry me a river.
	 * 
	 * @param millis the number of milliseconds to sleep
	 */
	public static void sneakySleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Check if the given old date is still within the allowed timeout window
	 * 
	 * @param baseDate       the date to check "now" against
	 * @param timeoutInHours the timeout in hours
	 */
	public static boolean isWithinTimeoutWindow(Date baseDate, int timeoutInHours) {
		Instant baseTime = Instant.ofEpochMilli(baseDate.getTime());
		Instant currentTime = Instant.now();
		boolean isWithinTimeoutWindow = currentTime.minus(timeoutInHours, ChronoUnit.HOURS).isBefore(baseTime);
		return isWithinTimeoutWindow;
	}

	/**
	 * Creates a map with a full AlbumGroup -> List<T> relationship (the lists are
	 * empty)
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
	 */
	public static String compileResultString(Map<AlbumGroupExtended, Integer> songsAddedPerAlbumGroups) {
		if (songsAddedPerAlbumGroups != null) {
			int totalSongsAdded = songsAddedPerAlbumGroups.values().stream().mapToInt(Integer::intValue).sum();
			if (totalSongsAdded > 0) {
				StringJoiner sj = new StringJoiner(" / ");
				for (AlbumGroupExtended age : DEFAULT_PLAYLIST_GROUP_ORDER) {
					Integer songsAdded = songsAddedPerAlbumGroups.get(age);
					if (songsAdded != null && songsAdded > 0) {
						sj.add(songsAdded + " " + age);
					}
				}
				return (String.format("%d new song%s added! [%s]", totalSongsAdded, totalSongsAdded != 1 ? "s" : "", sj.toString()));
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

	/**
	 * Build a readable String for dropped AlbumSimplified
	 * 
	 * @param as
	 * @return
	 */
	public static String formatAlbum(AlbumSimplified as) {
		return String.format("[%s] %s - %s (%s)",
			as.getAlbumGroup().toString(),
			joinArtists(as.getArtists()),
			as.getName(),
			as.getReleaseDate());
	}

	/**
	 * Return a string representation of all artist names, separated by ", "
	 * 
	 * @param artists
	 * @return
	 */
	public static String joinArtists(ArtistSimplified[] artists) {
		return Stream.of(artists)
			.map(ArtistSimplified::getName)
			.collect(Collectors.joining(", "));
	}

	/**
	 * Returns the name of the first artist of this album (usually the only one)
	 * 
	 * @param as
	 * @return
	 */
	public static String getFirstArtistName(AlbumSimplified as) {
		return as.getArtists()[0].getName();
	}

	/**
	 * Returns the name of the last artist of this album
	 * 
	 * @param as
	 * @return
	 */
	public static String getLastArtistName(AlbumSimplified as) {
		return as.getArtists()[as.getArtists().length - 1].getName();
	}

	/**
	 * Normalizes a file by converting it to a Path object, calling .normalize(),
	 * and returning it back as file.
	 * 
	 * @param file
	 * @return
	 */
	public static File normalizeFile(File file) {
		if (file != null) {
			return file.toPath().normalize().toFile();
		}
		return null;
	}

	/**
	 * Adds all the items of the given (primitive) array to the specified List, if
	 * and only if the item array is not null and contains at least one item.
	 * 
	 * @param <T>    the shared class type
	 * @param source the items to add
	 * @param target the target list
	 */
	public static <T> void addToListIfNotBlank(T[] source, List<T> target) {
		if (source != null && source.length > 0) {
			List<T> asList = Arrays.asList(source);
			target.addAll(asList);
		}
	}

	/**
	 * Creates a string that tries to be as normalized and generic as possible,
	 * based on the given track. The track's name will be lowercased, stripped off
	 * any white space and special characters, and anything in brackets such as
	 * "feat.", "bonus track", "remastered" will be removed.
	 * 
	 * @param track a string that is assumed to be the title of a track or an album
	 * @return the stripped title
	 */
	public static String strippedTrackIdentifier(String track) {
		return track
			.toLowerCase()
			.replaceAll(",", " ")
			.replaceAll("bonus track", "")
			.replaceAll("\\(.+\\)", "")
			.replaceAll("\\s+", "")
			.replaceAll("\\W+", "")
			.replaceAll("\\W+.*$", "");

	}

	/**
	 * Convert a full album an album simplified. Any extra variables are thrown
	 * away.
	 * 
	 * @param album the album to convert
	 * @return the converted album
	 */
	public static AlbumSimplified asAlbumSimplified(Album album) {
		AlbumSimplified.Builder as = new AlbumSimplified.Builder();

		as.setAlbumGroup(AlbumGroup.keyOf(album.getAlbumType().getType())); // Not exact but works
		as.setAlbumType(album.getAlbumType());
		as.setArtists(album.getArtists());
		as.setAvailableMarkets(album.getAvailableMarkets());
		as.setExternalUrls(album.getExternalUrls());
		as.setHref(album.getHref());
		as.setId(album.getId());
		as.setImages(album.getImages());
		as.setName(album.getName());
		as.setReleaseDate(album.getReleaseDate());
		as.setReleaseDatePrecision(album.getReleaseDatePrecision());
		as.setRestrictions(null); // No alternative
		// ModelObjectType missing
		as.setUri(album.getUri());

		return as.build();
	}
}
