package spotify.bot.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

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
	 * @param fullAlbums
	 */
	public static void sortAlbums(Map<AlbumType, List<Album>> fullAlbums) {
		fullAlbums.entrySet().parallelStream().forEach(fa -> {
			Collections.sort(fa.getValue(), (a1, a2) -> RELEASE_COMPARATOR.compare(a1, a2));			
		});
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
			Config.logStackTrace(e);
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

	/**
	 * Fetch all album types that are set in the config
	 * 
	 * @param albumTypes
	 */
	public static List<AlbumType> getSetAlbumTypes() {
		List<AlbumType> setAlbumTypes = new ArrayList<>();
		for (AlbumType at : AlbumType.values()) {
			String playlistId = BotUtils.getPlaylistIdByType(at);			
			if (BotUtils.isPlaylistSet(playlistId)) {
				setAlbumTypes.add(at);
			}
		}
		return setAlbumTypes;
	}

	/**
	 * Takes a map of album types with albums and puts them all into a single key
	 * 
	 * @param albumsByAlbumType
	 * @param appearsOn
	 */
	public static Map<AlbumType, List<Album>> flattenToSingleAlbumType(Map<AlbumType, List<Album>> albumsByAlbumType, AlbumType newSoloAlbumType) {
		Map<AlbumType, List<Album>> flattened = new ConcurrentHashMap<>();
		flattened.put(newSoloAlbumType, new ArrayList<>());
		albumsByAlbumType.values().parallelStream().forEach(abat -> {
			flattened.get(newSoloAlbumType).addAll(abat);
		});
		return flattened;
	}

	public static boolean isCollectionOrSampler(Album a) {
		if (!a.getAlbumType().equals(AlbumType.COMPILATION)) {
			return Arrays.asList(a.getArtists()).stream().anyMatch(as -> as.getName().equals(Constants.VARIOUS_ARTISTS));
		}
		return true;
	}

	/**
	 * Checks if at least a single artist of the subset is part of the given artist superset
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
	 * @param albumTypes
	 * @return
	 */
	public static <T> Map<AlbumType, List<T>> createAlbumTypeMap(Collection<AlbumType> albumTypes) {
		Map<AlbumType, List<T>> idsByAlbumType = new ConcurrentHashMap<>();
		albumTypes.stream().forEach(at -> {
			idsByAlbumType.put(at, new ArrayList<>());
		});
		return idsByAlbumType;
	}
	
	/**
	 * Creates the comma-delimited, lowercase String of album types to search for
	 * 
	 * @param albumTypes
	 * @return
	 */
	public static String createAlbumTypeString(List<AlbumType> albumTypes) {
		StringJoiner albumTypesAsString = new StringJoiner(",");
		albumTypes.stream().forEach(at -> albumTypesAsString.add(at.getType()));
		return albumTypesAsString.toString();
	}

	/**
	 * Logs the final results of the bot if any songs were added
	 * 
	 * @param songsAddedPerAlbumTypes
	 * @return
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public static void logResults(Map<AlbumType, Integer> songsAddedPerAlbumTypes) throws IOException, SQLException {
		StringJoiner sj = new StringJoiner(" / ");
		songsAddedPerAlbumTypes.entrySet().stream().forEach(sapat -> {
			if (sapat.getValue() > 0) {
				sj.add(sapat.getValue() + " " + sapat.getKey());						
			}
		});
		if (sj.length() > 0) {
			Config.log().info("> New Songs: [%s]" + sj.toString());			
		}
	}
}
