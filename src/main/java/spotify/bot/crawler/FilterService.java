package spotify.bot.crawler;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.AudioFeatures;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class FilterService {

	private final static String VARIOUS_ARTISTS = "Various Artists";

	private final static Pattern EP_MATCHER = Pattern.compile("\\bE\\W?P\\W?\\b");
	private final static int EP_SONG_COUNT_THRESHOLD = 5;
	private final static int EP_DURATION_THRESHOLD = 20 * 60 * 1000;
	private final static int EP_SONG_COUNT_THRESHOLD_LESSER = 3;
	private final static int EP_DURATION_THRESHOLD_LESSER = 10 * 60 * 1000;

	private final static Pattern LIVE_MATCHER = Pattern.compile("\\bLIVE\\b", Pattern.CASE_INSENSITIVE);
	private final static double LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD = 0.5;
	private final static double LIVENESS_THRESHOLD = 0.5;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private SpotifyApi spotifyApi;

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
	 * @throws IOException
	 * @throws SQLException
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
	 * @throws SQLException
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
	 * @throws SQLException
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
	 * @throws IOException
	 * @throws SQLException
	 */
	public List<AlbumSimplified> filterNewAlbumsOnly(List<AlbumSimplified> unfilteredAlbums) throws SQLException, IOException {
		Collection<AlbumSimplified> noDuplicates = filterDuplicateAlbums(unfilteredAlbums);
		int lookbackDays = config.getStaticConfig().getLookbackDays();
		LocalDate lowerReleaseDateBoundary = LocalDate.now().minusDays(lookbackDays);
		List<AlbumSimplified> filteredAlbums = noDuplicates.stream().filter(as -> isValidDate(as, lowerReleaseDateBoundary)).collect(Collectors.toList());
		log.printAlbumDifference(noDuplicates, filteredAlbums,
			String.format("x Dropped %d non-cached but too-old release[s] (lower boundary was %s):", unfilteredAlbums.size() - filteredAlbums.size(), lowerReleaseDateBoundary.toString()));
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
		log.printAlbumDifference(unfilteredAlbums, leftoverAlbums,
			String.format("x Dropped %d duplicate[s] released at the same time:", unfilteredAlbums.size() - leftoverAlbums.size()));
		return leftoverAlbums;
	}

	private String getAlbumIdentifierString(AlbumSimplified as) {
		StringJoiner sj = new StringJoiner("_");
		sj.add(as.getAlbumGroup().getGroup());
		sj.add(as.getReleaseDate());
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
	 * @throws SQLException
	 * @throws IOException
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
	 * @throws IOException
	 * @throws SQLException
	 */
	public void intelligentAppearsOnSearch(Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums, List<String> followedArtists) throws SQLException, IOException {
		if (config.getUserOptions().isIntelligentAppearsOnSearch()) {
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
				log.printAlbumTrackPairDifference(unfilteredAppearsOnAlbums, filteredAppearsOnAlbums,
					String.format("x Dropped %d APPEARS_ON release[s]:", unfilteredAppearsOnAlbums.size() - filteredAppearsOnAlbums.size()));
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

	////////////////////
	// PLAYLIST GROUPING

	/**
	 * Transform the given map of releases by album group to the true destination
	 * playlist IDs. Also tries to separate EPs from Singles (since Spotify doesn't
	 * differentiate between them).
	 * 
	 * @param newSongsByGroup
	 * @param playlistStores
	 * @return
	 * @throws SQLException
	 */
	public Map<PlaylistStore, List<AlbumTrackPair>> mapToTargetPlaylist(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, Collection<PlaylistStore> playlistStores) throws SQLException {
		Map<PlaylistStore, List<AlbumTrackPair>> resultMap = new HashMap<>();
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : newSongsByGroup.entrySet()) {
			PlaylistStore ps = config.getPlaylistStore(entry.getKey());
			if (ps != null) {
				resultMap.put(ps, entry.getValue());
			}
		}
		return resultMap;
	}

	/**
	 * Re-map all albums that match the criteria of an {@link AlbumGroupExtended}.
	 * So far, this only works for EPs or Live albums.
	 * 
	 * @param songsByMainPlaylist
	 * @param playlistStores
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<PlaylistStore, List<AlbumTrackPair>> remapIntoExtendedPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPS, Collection<PlaylistStore> playlistStores)
		throws SQLException, IOException {
		Map<PlaylistStore, List<AlbumTrackPair>> regroupedMap = new HashMap<>(songsByPS);
		if (config.getUserOptions().isEpSeparation()) {
			PlaylistStore psEp = config.getPlaylistStore(AlbumGroupExtended.EP);
			remapEPs(psEp, regroupedMap);
		}
		if (config.getUserOptions().isLiveSeparation()) {
			PlaylistStore psLive = config.getPlaylistStore(AlbumGroupExtended.LIVE);
			remapLive(psLive, regroupedMap);
		}
		return regroupedMap;
	}

	/**
	 * Re-map any given singles that qualify as EPs into their own category
	 * 
	 * @param psEp
	 * @param songsByPS
	 * @throws SQLException
	 */
	private void remapEPs(PlaylistStore psEp, Map<PlaylistStore, List<AlbumTrackPair>> songsByPS) throws SQLException {
		if (psEp != null && psEp.getPlaylistId() != null) {
			List<AlbumTrackPair> singles = songsByPS.get(config.getPlaylistStore(AlbumGroupExtended.SINGLE));
			if (!singles.isEmpty()) {
				List<AlbumTrackPair> eps = singles.stream()
					.filter(atp -> isEP(atp))
					.collect(Collectors.toList());
				if (!eps.isEmpty()) {
					singles.removeAll(eps);
					songsByPS.put(psEp, eps);
				}
			}
		}
	}

	/**
	 * Returns true if the given single qualifies as EP. The definition of an EP is
	 * a single that fulfills ANY of the following attributes:
	 * <ul>
	 * <li>"EP" appears in the album title (uppercase, single word, may contain a
	 * single symbol in between and after the letters)</li>
	 * <li>min 5 songs</li>
	 * <li>min 20 minutes</li>
	 * <li>min 3 songs AND min 10 minutes AND none of the songs are named after the
	 * release title*</li>
	 * </ul>
	 * *The great majority of EPs are covered by the first three strategies. The
	 * last one for really silly edge cases in which an artist may release an EP
	 * that is is too similar to a slightly fancier single by numbers alone.
	 * 
	 * @param atp
	 * @return
	 */
	private boolean isEP(AlbumTrackPair atp) {
		if (EP_MATCHER.matcher(atp.getAlbum().getName()).find()) {
			return true;
		}

		int trackCount = atp.getTracks().size();
		int totalDurationMs = atp.getTracks().stream().mapToInt(TrackSimplified::getDurationMs).sum();

		if (trackCount >= EP_SONG_COUNT_THRESHOLD || totalDurationMs >= EP_DURATION_THRESHOLD) {
			return true;
		}

		if (trackCount >= EP_SONG_COUNT_THRESHOLD_LESSER && totalDurationMs >= EP_DURATION_THRESHOLD_LESSER) {
			return true;
		}
		return false;
	}

	/**
	 * Re-map any given releases that qualify as live releases into their own
	 * category
	 * 
	 * @param psLive
	 * @param songsByPS
	 * @throws SQLException
	 */
	private void remapLive(PlaylistStore psLive, Map<PlaylistStore, List<AlbumTrackPair>> songsByPS) throws SQLException {
		if (psLive != null && psLive.getPlaylistId() != null) {
			List<AlbumTrackPair> allLive = new ArrayList<>();
			for (Map.Entry<PlaylistStore, List<AlbumTrackPair>> entry : songsByPS.entrySet()) {
				List<AlbumTrackPair> source = songsByPS.get(entry.getKey());
				if (source != null && !source.isEmpty()) {
					List<AlbumTrackPair> live = source.stream()
						.filter(atp -> isLive(atp))
						.collect(Collectors.toList());
					if (!live.isEmpty()) {
						source.removeAll(live);
						allLive.addAll(live);
					}
				}
			}
			if (!allLive.isEmpty()) {
				songsByPS.put(psLive, allLive);
			}
		}
	}

	/**
	 * Returns true if the given release qualifies as a live release. The definition
	 * of a live release is a release that fulfills BOTH of these conditions:
	 * <ol>
	 * <li>"LIVE" contained in the release title (any case, single word)</li>
	 * <li>At least half of the songs of this release have a "liveness" value of 50%
	 * or more*</li>
	 * </ol>
	 * *The liveness value is determined by the Spotify API for each individual song.
	 * It gives a vague idea how probable it is for the song to be live. Hints
	 * like recording quality and audience cheers are used.
	 * 
	 * @param atp
	 * @return
	 */
	private boolean isLive(AlbumTrackPair atp) {
		try {
			if (LIVE_MATCHER.matcher(atp.getAlbum().getName()).find()) {
				String[] trackIds = atp.getTracks().stream().map(TrackSimplified::getId).toArray(String[]::new);
				List<AudioFeatures> audioFeatures = Arrays.asList(SpotifyCall.execute(spotifyApi.getAudioFeaturesForSeveralTracks(trackIds)));
				double trackCountLive = audioFeatures.stream().filter(af -> af.getLiveness() >= LIVENESS_THRESHOLD).count();
				double trackCount = atp.getTracks().size();
				return (trackCountLive / trackCount) >= LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD;
			}
		} catch (SpotifyWebApiException | IOException | InterruptedException e) {
			log.stackTrace(e);
		}
		return false;
	}
}
