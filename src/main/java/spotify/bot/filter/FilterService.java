package spotify.bot.filter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.config.DeveloperMode;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class FilterService {

	private final static String VARIOUS_ARTISTS = "Various Artists";

	private final static boolean SHOW_DROPPED_APPEARS_ON_RELEASES = true;

	@Autowired
	private StaticConfig staticConfig;

	@Autowired
	private UserOptions userOptions;

	@Autowired
	private BotLogger log;

	@Autowired
	private DatabaseService databaseService;

	private final static DateTimeFormatter RELEASE_DATE_PARSER = new DateTimeFormatterBuilder()
		.append(DateTimeFormatter.ofPattern("yyyy[-MM[-dd]]"))
		.parseDefaulting(ChronoField.DAY_OF_MONTH, 1).parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
		.toFormatter();

	///////////////////
	// FILTER BY CACHED

	/**
	 * Return non-database-filterd list of albums from the input
	 * 
	 * @param allAlbums the albums to check against
	 * @return the leftover (new) albums
	 */
	public List<AlbumSimplified> getNonCachedAlbums(List<AlbumSimplified> allAlbums) throws SQLException {
		List<AlbumSimplified> filteredAlbums = filterNonCachedAlbumsOnly(allAlbums);
		BotUtils.removeNulls(filteredAlbums);
		return filteredAlbums;
	}

	/**
	 * Filter out all album IDs not currently present in the database
	 * 
	 * @param albumsSimplified
	 * @return
	 */
	private List<AlbumSimplified> filterNonCachedAlbumsOnly(List<AlbumSimplified> albumsSimplified) throws SQLException {
		Map<String, AlbumSimplified> filteredAlbums = new HashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			if (as != null) {
				AlbumSimplified alreadySetAlbum = filteredAlbums.get(as.getId());
				if (alreadySetAlbum == null || superiorAlbumGroup(as, alreadySetAlbum)) {
					filteredAlbums.put(as.getId(), as);
				}
			}
		}

		Set<String> albumCache = new HashSet<>(databaseService.getAlbumCache());
		return filteredAlbums.values().stream()
			.filter(a -> !albumCache.contains(a.getId()))
			.collect(Collectors.toList());
	}

	////////////

	/**
	 * Check if the given album is sporting a "superior" album group. This is needed
	 * when two followed artists are on the same new release (e.g. one as main
	 * artist and the other as appears_on artist) to make sure the album gets added,
	 * not the lesser album group type.
	 * 
	 * @param newAlbum
	 * @param alreadySetAlbum
	 * @return
	 */
	private boolean superiorAlbumGroup(AlbumSimplified newAlbum, AlbumSimplified alreadySetAlbum) {
		int newAlbumIndex = BotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(AlbumGroupExtended.fromAlbumGroup(newAlbum.getAlbumGroup()));
		int alreadySetAlbumIndex = BotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(AlbumGroupExtended.fromAlbumGroup(alreadySetAlbum.getAlbumGroup()));
		return newAlbumIndex < alreadySetAlbumIndex;
	}

	/**
	 * Cache the given album IDs in the database
	 * 
	 * @param albums
	 * @param async
	 */
	public void cacheAlbumIds(List<AlbumSimplified> albums, boolean async) {
		if (!DeveloperMode.isCacheDisabled()) {
			if (!albums.isEmpty()) {
				if (async) {
					databaseService.cacheAlbumIdsAsync(albums);
				} else {
					databaseService.cacheAlbumIdsSync(albums);
				}
			}
		}
	}

	/////////////////////////
	// FILTER FUTURE RELEASES

	/**
	 * Remove all albums that are going to be released in the future. This is
	 * required because the Spotify sometimes returns (unavailable) releases that
	 * aren't going to be unlocked for at least another day. This filter is mostly
	 * for convenience, as a result.
	 * 
	 * @param albums the albums to filter
	 * @return the albums without releases that had a release date after today
	 */
	public List<AlbumSimplified> filterFutureAlbums(List<AlbumSimplified> albums) {
		List<AlbumSimplified> filtered = albums.stream()
			.filter(this::isNotInTheFuture)
			.collect(Collectors.toList());
		return filtered;
	}
	
	/**
	 * Return true if this album's release date is either today or before today.
	 * 
	 * @param album the album
	 * @return true if it isn't in the future
	 */
	private boolean isNotInTheFuture(AlbumSimplified album) {
		String releaseDate = album.getReleaseDate();
		LocalDate parsedReleaseDate = LocalDate.parse(releaseDate, RELEASE_DATE_PARSER);
		LocalDate now = LocalDate.now();
		return now.isEqual(parsedReleaseDate) || now.isAfter(parsedReleaseDate);
	}
	
	/////////////////////////
	// FILTER BY RELEASE DATE

	/**
	 * Categorizes the given list of albums into a map of their respective album
	 * GROUPS (aka the return context of the simplified album object)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> categorizeAlbumsByAlbumGroup(List<AlbumTrackPair> albumTrackPairs) {
		Map<AlbumGroup, List<AlbumTrackPair>> categorized = BotUtils.createAlbumGroupToListOfTMap();
		for (AlbumTrackPair atp : albumTrackPairs) {
			AlbumGroup albumGroupOfAlbum = atp.getAlbum().getAlbumGroup();
			if (albumGroupOfAlbum != null) {
				categorized.get(albumGroupOfAlbum).add(atp);
			}
		}
		return categorized;
	}

	/**
	 * Filter duplicate albums. This is done by converting the most important meta
	 * data into a String and making sure those are unique.
	 * 
	 * @param unfilteredAlbums
	 * @return
	 */
	public List<AlbumSimplified> filterDuplicateAlbums(List<AlbumSimplified> unfilteredAlbums) {
		Map<String, AlbumSimplified> uniqueMap = new HashMap<>();
		for (AlbumSimplified as : unfilteredAlbums) {
			String identifier = getAlbumIdentifierString(as);
			if (!uniqueMap.containsKey(identifier)) {
				uniqueMap.put(identifier, as);
			}
		}
		Collection<AlbumSimplified> leftoverAlbums = uniqueMap.values();
		log.printDroppedAlbumDifference(unfilteredAlbums, leftoverAlbums,
			String.format("Dropped %d duplicate[s] released at the same time:", unfilteredAlbums.size() - leftoverAlbums.size()));
		return new ArrayList<>(leftoverAlbums);
	}

	private String getAlbumIdentifierString(AlbumSimplified as) {
		StringJoiner sj = new StringJoiner("_");
		if (as == null || as.getAlbumGroup() == null || as.getAlbumGroup().getGroup() == null) {
			System.out.println();
		}
		sj.add(as.getAlbumGroup().getGroup());
		sj.add(BotUtils.getFirstArtistName(as));
		sj.add(as.getName());
		return sj.toString();
	}

	/**
	 * Filter out all releases not released within the lookbackDays range. If
	 * rerelease remapping is enabled, this will only be applied to non-albums
	 * 
	 * @param unfilteredReleases
	 * @return
	 */
	public List<AlbumSimplified> filterNewAlbumsOnly(List<AlbumSimplified> unfilteredReleases) {
		List<AlbumSimplified> filteredReleases = unfilteredReleases.stream()
			.filter(release -> (userOptions.isRereleaseSeparation() && AlbumGroup.ALBUM.equals(release.getAlbumGroup()))
				|| isValidDate(release))
			.collect(Collectors.toList());
		log.printDroppedAlbumDifference(unfilteredReleases, filteredReleases,
			String.format("Dropped %d non-cached but too-old release[s]:", unfilteredReleases.size() - filteredReleases.size()));
		return filteredReleases;
	}

	/**
	 * Evaluate whether a release is new enough to consider it valid for addition to
	 * the playlist
	 * 
	 * @param album
	 * @return
	 */
	public boolean isValidDate(AlbumSimplified album) {
		try {
			int lookbackDays = staticConfig.getLookbackDays();
			LocalDate lowerReleaseDateBoundary = LocalDate.now().minusDays(lookbackDays);
			LocalDate releaseDate = LocalDate.parse(album.getReleaseDate(), RELEASE_DATE_PARSER);
			return releaseDate.isAfter(lowerReleaseDateBoundary);
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	////////////////////////////////
	// INTELLIGENT APPEARS_ON_SEARCH

	/**
	 * Find all releases marked as "appears_on" by the given list of artists, but
	 * filter the result such that only songs of artists you follow are preserved.
	 * Also filter out any compilation appearances.
	 * 
	 * @param extraAlbumIdsFiltered
	 * @param followedArtists
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnSearch(Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums, List<String> followedArtists) {
		if (userOptions.isIntelligentAppearsOnSearch()) {
			List<AlbumTrackPair> unfilteredAppearsOnAlbums = categorizedFilteredAlbums.get(AlbumGroup.APPEARS_ON);
			if (!unfilteredAppearsOnAlbums.isEmpty()) {
				// Preprocess into HashSet to speed up contains() operations
				Set<String> followedArtistsSet = new HashSet<>(followedArtists);

				// Filter out any collection, samplers, or albums whose primary artist is
				// already a followee
				List<AlbumTrackPair> albumsWithoutCollectionsOrSamplers = unfilteredAppearsOnAlbums.stream()
					.filter(atp -> !isCollectionOrSampler(atp.getAlbum()))
					.filter(atp -> !containsFeaturedArtist(followedArtistsSet, atp.getAlbum().getArtists()))
					.collect(Collectors.toList());

				// Of those, filter out the actual songs where a featured artist is a followee
				List<AlbumTrackPair> filteredAppearsOnAlbums = new ArrayList<>();
				for (AlbumTrackPair atp : albumsWithoutCollectionsOrSamplers) {
					List<TrackSimplified> selectedSongsOfAlbum = atp.getTracks().stream()
						.filter(song -> containsFeaturedArtist(followedArtistsSet, song.getArtists()))
						.collect(Collectors.toList());
					filteredAppearsOnAlbums.add(AlbumTrackPair.of(atp.getAlbum(), selectedSongsOfAlbum));
				}

				// Show log message
				int droppedAppearsOnCount = unfilteredAppearsOnAlbums.size() - filteredAppearsOnAlbums.size();
				if (SHOW_DROPPED_APPEARS_ON_RELEASES) {
					log.printDroppedAlbumTrackPairDifference(unfilteredAppearsOnAlbums, filteredAppearsOnAlbums, String.format("Dropped %d APPEARS_ON release[s]:", droppedAppearsOnCount));
				} else {
					log.info(String.format("Dropped %d APPEARS_ON release[s]", droppedAppearsOnCount));
					log.info("x (...omitted)");
				}

				// Finalize
				Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnFilteredMap = new HashMap<>(categorizedFilteredAlbums);
				intelligentAppearsOnFilteredMap.put(AlbumGroup.APPEARS_ON, filteredAppearsOnAlbums);
				return intelligentAppearsOnFilteredMap;
			}
		}
		return categorizedFilteredAlbums;
	}

	/**
	 * Returns true if the album group is set to Compilation or the artist is
	 * "Various Artists"
	 * 
	 * @param a
	 * @return
	 */
	private boolean isCollectionOrSampler(AlbumSimplified a) {
		if (!a.getAlbumGroup().equals(AlbumGroupExtended.COMPILATION.asAlbumGroup())) {
			return Arrays.asList(a.getArtists()).stream().anyMatch(as -> as.getName().equals(VARIOUS_ARTISTS));
		}
		return true;
	}

	/**
	 * Checks if age least a single artist of the subset is part of the given artist
	 * superset
	 * 
	 * @param followedArtists
	 * @param artists
	 * @return
	 */
	private static boolean containsFeaturedArtist(Collection<String> artistSuperset, ArtistSimplified[] artistSubset) {
		Set<String> artistSubsetIds = Arrays.asList(artistSubset).stream().map(ArtistSimplified::getId).collect(Collectors.toSet());
		return artistSuperset.stream().anyMatch(a -> artistSubsetIds.contains(a));
	}
}
