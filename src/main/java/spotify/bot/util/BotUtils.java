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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	 * @param albums
	 */
	public static void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> RELEASE_COMPARATOR.compare(a1, a2));
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
	 * Returns the stored playlist update timestamp by the given album type
	 * 
	 * @param albumType
	 * @return
	 * @throws IOException 
	 */
	public static Date getPlaylistLastUpdatedByType(AlbumType albumType) {
		try {
			switch (albumType) {
				case ALBUM:
					return Config.getInstance().getLastUpdatedPlaylistAlbums();
				case SINGLE:
					return Config.getInstance().getLastUpdatedPlaylistSingles();
				case COMPILATION:
					return Config.getInstance().getLastUpdatedPlaylistCompilations();
				case APPEARS_ON:
					return Config.getInstance().getLastUpdatedPlaylistAppearsOn();
			}
		} catch (IOException | SQLException e) {
			Config.logStackTrace(e);
		}
		return null;
	}
	
	/**
	 * Returns the stored playlist update timestamp by the given album type
	 * 
	 * @param albumType
	 * @return
	 * @throws IOException 
	 */
	public static String getPlaylistLastUpdatedColumnByType(AlbumType albumType) {
		switch (albumType) {
			case ALBUM:
				return Constants.COL_LAST_UPDATED_PLAYLIST_ALBUMS;
			case SINGLE:
				return Constants.COL_LAST_UPDATED_PLAYLIST_SINGLES;
			case COMPILATION:
				return Constants.COL_LAST_UPDATED_PLAYLIST_COMPILATIONS;
			case APPEARS_ON:
				return Constants.COL_LAST_UPDATED_PLAYLIST_APPEARS_ON;
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
	 * Delete all album types of the given list that aren't set in the config
	 * 
	 * @param albumTypes
	 */
	public static void removeUnsetAlbumTypes(List<AlbumType> albumTypes) {
		for (Iterator<AlbumType> itr = albumTypes.iterator(); itr.hasNext();) {
			String playlistId = BotUtils.getPlaylistIdByType(itr.next());
			if (!BotUtils.isPlaylistSet(playlistId)) {
				itr.remove();
			}
		}
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
			return Arrays.asList(a.getArtists()).parallelStream().anyMatch(as -> as.getName().equals(Constants.VARIOUS_ARTISTS));
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
		Set<String> artistSubsetIds = Arrays.asList(artistSubset).parallelStream().map(ArtistSimplified::getId).collect(Collectors.toSet());
		return artistSuperset.parallelStream().anyMatch(a -> artistSubsetIds.contains(a));
	}
}
