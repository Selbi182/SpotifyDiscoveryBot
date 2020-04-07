package spotify.bot.filter;

import java.io.IOException;
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
		.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
		.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
		.toFormatter();

	///////////////////
	// FILTER BY CACHED

	/**
	 * Return non-database-filterd list of albums from the input
	 * 
	 * @param allAlbums
	 * @return
	 */
	public List<AlbumSimplified> getNonCachedAlbums(List<AlbumSimplified> allAlbums) throws IOException, SQLException {
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
	private List<AlbumSimplified> filterNonCachedAlbumsOnly(List<AlbumSimplified> albumsSimplified) throws IOException, SQLException {
		Map<String, AlbumSimplified> filteredAlbums = new HashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			if (as != null) {
				filteredAlbums.put(as.getId(), as);
			}
		}

		List<String> albumCache = databaseService.getAlbumCache();
		for (String id : albumCache) {
			filteredAlbums.remove(id);
		}

		return filteredAlbums.values().stream().collect(Collectors.toList());
	}

	////////////

	/**
	 * Cache the given album IDs in the database
	 * 
	 * @param albums
	 */
	public void cacheAlbumIds(List<AlbumSimplified> albums) throws SQLException {
		if (!albums.isEmpty()) {
			databaseService.cacheAlbumIdsAsync(albums);
		}
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
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param unfilteredAlbums
	 * @return
	 */
	public List<AlbumSimplified> filterNewAlbumsOnly(List<AlbumSimplified> unfilteredAlbums) throws SQLException, IOException {
		Collection<AlbumSimplified> noDuplicates = filterDuplicateAlbums(unfilteredAlbums);
		int lookbackDays = staticConfig.getLookbackDays();
		LocalDate lowerReleaseDateBoundary = LocalDate.now().minusDays(lookbackDays);
		List<AlbumSimplified> filteredAlbums = noDuplicates.stream().filter(as -> isValidDate(as, lowerReleaseDateBoundary)).collect(Collectors.toList());
		log.printDroppedAlbumDifference(noDuplicates, filteredAlbums,
			String.format("Dropped %d non-cached but too-old release[s] (lower boundary was %s):", unfilteredAlbums.size() - filteredAlbums.size(), lowerReleaseDateBoundary.toString()));
		return filteredAlbums;
	}

	/**
	 * Filter duplicate albums. This is done by converting the most important meta
	 * data into a String and making sure those are unique.
	 * 
	 * @param unfilteredAlbums
	 * @return
	 */
	private Collection<AlbumSimplified> filterDuplicateAlbums(List<AlbumSimplified> unfilteredAlbums) {
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
		return leftoverAlbums;
	}

	private String getAlbumIdentifierString(AlbumSimplified as) {
		StringJoiner sj = new StringJoiner("_");
		sj.add(as.getAlbumGroup().getGroup());
		sj.add(as.getArtists()[0].getName());
		sj.add(as.getName());
		return sj.toString();
	}

	/**
	 * Evaluate whether a release is new enough to consider it valid for addition to
	 * the playlist
	 * 
	 * @param album
	 * @param lowerReleaseDateBoundary
	 * @return
	 */
	private boolean isValidDate(AlbumSimplified album, LocalDate lowerReleaseDateBoundary) {
		try {
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
	public void intelligentAppearsOnSearch(Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums, List<String> followedArtists) throws SQLException, IOException {
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
					filteredAppearsOnAlbums.add(new AlbumTrackPair(atp.getAlbum(), selectedSongsOfAlbum));
				}

				// Finalize
				log.printDroppedAlbumTrackPairDifference(unfilteredAppearsOnAlbums, filteredAppearsOnAlbums,
					String.format("Dropped %d APPEARS_ON release[s]:", unfilteredAppearsOnAlbums.size() - filteredAppearsOnAlbums.size()));
				categorizedFilteredAlbums.put(AlbumGroup.APPEARS_ON, filteredAppearsOnAlbums);
			}
		}
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
