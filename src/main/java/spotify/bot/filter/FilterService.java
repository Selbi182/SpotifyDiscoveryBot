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
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.properties.BlacklistConfig;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Service
public class FilterService {
	/**
	 * Indicates how many days in the past are to be considered "present".
	 * This is required due to rare occasions where a song gets added slightly later
	 * on Spotify than, say, on physical media or Bandcamp.
	 */
	private final static int LOOKBACK_DAYS = 60;

	/**
	 * Any sampler is categorized under the artist "Various Artists".
	 */
	private final static String VARIOUS_ARTISTS = "Various Artists";

	private final DiscoveryBotLogger log;
	private final DatabaseService databaseService;
	private final PlaylistStoreConfig playlistStoreConfig;
	private final BlacklistConfig blacklistConfig;

	FilterService(DiscoveryBotLogger discoveryBotLogger,
			DatabaseService databaseService,
			PlaylistStoreConfig playlistStoreConfig,
			BlacklistConfig blacklistConfig) {
		this.log = discoveryBotLogger;
		this.databaseService = databaseService;
		this.playlistStoreConfig = playlistStoreConfig;
		this.blacklistConfig = blacklistConfig;
	}

	private final static DateTimeFormatter RELEASE_DATE_PARSER = new DateTimeFormatterBuilder()
		.append(DateTimeFormatter.ofPattern("yyyy[-MM[-dd]]"))
		.parseDefaulting(ChronoField.DAY_OF_MONTH, 1).parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
		.toFormatter();

	///////////////////
	// FILTER BY CACHED

	/**
	 * Return non-database-filtered list of albums from the input
	 * 
	 * @param allAlbums the albums to check against
	 * @return the leftover (new) albums
	 */
	public List<AlbumSimplified> getNonCachedAlbums(List<AlbumSimplified> allAlbums) throws SQLException {
		return filterNonCachedAlbumsOnly(allAlbums);
	}

	/**
	 * Filter out all album IDs not currently present in the database
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

		Set<String> albumCache = new HashSet<>(databaseService.getReleasesIdsCache());
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
	 */
	private boolean superiorAlbumGroup(AlbumSimplified newAlbum, AlbumSimplified alreadySetAlbum) {
		int newAlbumIndex = DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(AlbumGroupExtended.fromAlbumGroup(newAlbum.getAlbumGroup()));
		int alreadySetAlbumIndex = DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(AlbumGroupExtended.fromAlbumGroup(alreadySetAlbum.getAlbumGroup()));
		return newAlbumIndex < alreadySetAlbumIndex;
	}

	/**
	 * Cache the given artist IDs in the database
	 */
	public void cacheArtistIds(List<String> artistIds) {
		if (!DeveloperMode.isCacheDisabled()) {
			if (!artistIds.isEmpty()) {
				databaseService.cacheArtistIds(artistIds);
			}
		}
	}

	/**
	 * Cache the given album IDs in the database
	 */
	public void cacheAlbumIds(List<AlbumSimplified> albums) {
		if (!DeveloperMode.isCacheDisabled()) {
			if (!albums.isEmpty()) {
				databaseService.cacheAlbumIds(albums);
			}
		}
	}

	/////////////////////
	// CACHED ALBUM NAMES

	/**
	 * Cache the given album names in the database
	 */
	public void cacheAlbumNames(List<AlbumSimplified> albums) {
		if (!DeveloperMode.isCacheDisabled()) {
			if (!albums.isEmpty()) {
				databaseService.cacheAlbumNames(albums);
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
		return albums.stream()
			.filter(this::isNotInTheFuture)
			.collect(Collectors.toList());
	}
	
	/**
	 * Return true if this album's release date is either today or before today.
	 * 
	 * @param album the album
	 * @return true if it isn't in the future
	 */
	private boolean isNotInTheFuture(AlbumSimplified album) {
		String releaseDate = album.getReleaseDate();
		try {
			LocalDate parsedReleaseDate = LocalDate.parse(releaseDate, RELEASE_DATE_PARSER);
			LocalDate now = LocalDate.now();
			return now.isEqual(parsedReleaseDate) || now.isAfter(parsedReleaseDate);
		} catch (DateTimeParseException e) {
			return true;
		}
	}
	
	/////////////////////////
	// FILTER BY RELEASE DATE

	/**
	 * Categorizes the given list of albums into a map of their respective album
	 * GROUPS (aka the return context of the simplified album object)
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> categorizeAlbumsByAlbumGroup(List<AlbumTrackPair> albumTrackPairs) {
		Map<AlbumGroup, List<AlbumTrackPair>> categorized = SpotifyUtils.createAlbumGroupToListOfTMap();
		for (AlbumTrackPair atp : albumTrackPairs) {
			AlbumGroup albumGroupOfAlbum = atp.getAlbum().getAlbumGroup();
			if (albumGroupOfAlbum != null) {
				categorized.get(albumGroupOfAlbum).add(atp);
			}
		}
		return categorized;
	}

	/**
	 * Filter duplicate albums with an identical or very similar name released during the current crawl session
	 */
	public List<AlbumSimplified> filterDuplicatedAlbumsReleasedSimultaneously(List<AlbumSimplified> unfilteredAlbums) {
		Map<String, AlbumSimplified> uniqueMap = new HashMap<>();
		for (AlbumSimplified as : unfilteredAlbums) {
			String identifier = SpotifyUtils.albumIdentifierString(as);
			if (!uniqueMap.containsKey(identifier)) {
				uniqueMap.put(identifier, as);
			}
		}
		Collection<AlbumSimplified> leftoverAlbums = uniqueMap.values();
		log.printDroppedAlbumDifference(unfilteredAlbums, leftoverAlbums,
			String.format("Dropped %d duplicate[s] released at the same time:", unfilteredAlbums.size() - leftoverAlbums.size()));
		return new ArrayList<>(leftoverAlbums);
	}
	
	/**
	 * Filter out all releases not released within the lookbackDays range. If
	 * rerelease remapping is enabled, this will only be applied to non-albums
	 */
	public List<AlbumSimplified> filterNewAlbumsOnly(List<AlbumSimplified> unfilteredReleases) {
		List<AlbumSimplified> filteredReleases = unfilteredReleases.stream()
			.filter(release -> (AlbumGroup.ALBUM.equals(release.getAlbumGroup()))
				|| isValidDate(release))
			.collect(Collectors.toList());
		log.printDroppedAlbumDifference(unfilteredReleases, filteredReleases,
			String.format("Dropped %d non-cached but too-old release[s]:", unfilteredReleases.size() - filteredReleases.size()));
		return filteredReleases;
	}

	/**
	 * Evaluate whether a release is new enough to consider it valid for addition to
	 * the playlist
	 */
	public boolean isValidDate(AlbumSimplified album) {
		try {
			LocalDate lowerReleaseDateBoundary = LocalDate.now().minusDays(LOOKBACK_DAYS);
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
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnSearch(Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums, List<String> followedArtists) {
		List<AlbumTrackPair> unfilteredAppearsOnAlbums = categorizedFilteredAlbums.get(AlbumGroup.APPEARS_ON);
		if (!unfilteredAppearsOnAlbums.isEmpty()) {
			// Preprocess into HashSet to speed up contains() operations
			Set<String> followedArtistsSet = new HashSet<>(followedArtists);

			// Filter out any collection, samplers, or albums whose primary artist is
			// already a followee
			List<AlbumTrackPair> albumsWithoutCollectionsOrSamplers = unfilteredAppearsOnAlbums.stream()
				.filter(atp -> !isCollectionOrSampler(atp.getAlbum()))
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
			log.printDroppedAlbumTrackPairDifference(unfilteredAppearsOnAlbums, filteredAppearsOnAlbums, String.format("Dropped %d APPEARS_ON release[s]:", droppedAppearsOnCount));

			// Finalize
			Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnFilteredMap = new HashMap<>(categorizedFilteredAlbums);
			intelligentAppearsOnFilteredMap.put(AlbumGroup.APPEARS_ON, filteredAppearsOnAlbums);
			return intelligentAppearsOnFilteredMap;
		}
		return categorizedFilteredAlbums;
	}

	/**
	 * Returns true if the album group is set to Compilation or the artist is
	 * "Various Artists"
	 */
	private boolean isCollectionOrSampler(AlbumSimplified a) {
		if (!a.getAlbumGroup().equals(AlbumGroupExtended.COMPILATION.asAlbumGroup())) {
			return Arrays.stream(a.getArtists()).anyMatch(as -> as.getName().equals(VARIOUS_ARTISTS));
		}
		return true;
	}

	/**
	 * Checks if at least a single artist of the subset is part of the given artist
	 * superset
	 */
	private static boolean containsFeaturedArtist(Collection<String> artistSuperset, ArtistSimplified[] artistSubset) {
		Set<String> artistSubsetIds = Arrays.stream(artistSubset).map(ArtistSimplified::getId).collect(Collectors.toSet());
		return artistSuperset.stream().anyMatch(artistSubsetIds::contains);
	}

	////////////////////////////////
	// BLACKLISTED RELEASE TYPES

	public Map<PlaylistStore, List<AlbumTrackPair>> filterBlacklistedReleaseTypesForArtists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPS) {
		List<Entry<AlbumSimplified, AlbumGroupExtended>> allDroppedReleases = new ArrayList<>();
		Map<String, List<AlbumGroupExtended>> blacklistMap = blacklistConfig.getBlacklistMap();
		for (Entry<String, List<AlbumGroupExtended>> blacklistedPair : blacklistMap.entrySet()) {
			for (AlbumGroupExtended albumGroupExtended : blacklistedPair.getValue()) {
				PlaylistStore playlistStore = playlistStoreConfig.getPlaylistStore(albumGroupExtended);
				if (songsByPS.containsKey(playlistStore) ) {
					List<AlbumTrackPair> albumTrackPairsToRemove = new ArrayList<>();
					List<AlbumTrackPair> list = songsByPS.get(playlistStore);
					for (AlbumTrackPair atp : list) {
						if (SpotifyUtils.anyArtistMatches(atp.getAlbum(), blacklistedPair.getKey())) {
							albumTrackPairsToRemove.add(atp);
							allDroppedReleases.add(Map.entry(atp.getAlbum(), albumGroupExtended));
						}
					}
					list.removeAll(albumTrackPairsToRemove);
				}
			}
		}
		if (!allDroppedReleases.isEmpty()) {
			log.printDroppedAlbumsCustomGroup(allDroppedReleases, "Dropped " + allDroppedReleases.size() + " blacklisted release[s]:");
		}
		return songsByPS;
	}
}
