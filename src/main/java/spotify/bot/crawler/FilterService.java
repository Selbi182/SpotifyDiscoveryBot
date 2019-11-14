package spotify.bot.crawler;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.config.Config;
import spotify.bot.config.dto.PlaylistStoreDTO;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class FilterService {

	private final static String VARIOUS_ARTISTS = "Various Artists";

	private final static Pattern EP_MATCHER = Pattern.compile("\\bE\\W?P\\W?\\b");
	private final static int EP_SONG_COUNT_THRESHOLD = 5;
	private final static int EP_DURATION_THRESHOLD = 20 * 60 * 1000;

	@Autowired
	private Config config;

	private final static DateTimeFormatter RELEASE_DATE_PARSER = new DateTimeFormatterBuilder()
		.append(DateTimeFormatter.ofPattern("yyyy[-MM[-dd]]"))
		.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
		.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
		.toFormatter();

	/////////////////////////
	// FILTER BY RELEASE DATE

	/**
	 * Categorize the list of given albums by album group and filter them by new
	 * albums only (aka. those which weren't previously cached but still too old,
	 * such as re-releases)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public Map<AlbumGroup, List<AlbumSimplified>> categorizeAndFilterAlbums(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) throws SQLException, IOException {
		Map<AlbumGroup, List<AlbumSimplified>> categorizedAlbums = categorizeAlbumsByAlbumGroup(albumsSimplified, albumGroups);
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = filterNewAlbumsOnly(categorizedAlbums);
		return filteredAlbums;
	}

	/**
	 * Categorizes the given list of albums into a map of their respective album
	 * GROUPS (aka the return context of the simplified album object)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @return
	 */
	private Map<AlbumGroup, List<AlbumSimplified>> categorizeAlbumsByAlbumGroup(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) {
		Map<AlbumGroup, List<AlbumSimplified>> categorized = BotUtils.createAlbumGroupToListOfTMap(albumGroups);
		albumsSimplified.parallelStream().forEach(as -> {
			AlbumGroup albumGroupOfAlbum = as.getAlbumGroup();
			if (albumGroupOfAlbum != null) {
				categorized.get(albumGroupOfAlbum).add(as);
			}
		});
		return categorized;
	}

	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param albumsSimplifiedByGroup
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	private Map<AlbumGroup, List<AlbumSimplified>> filterNewAlbumsOnly(Map<AlbumGroup, List<AlbumSimplified>> albumsSimplifiedByGroup) throws SQLException, IOException {
		int lookbackDays = config.getUserConfig().getLookbackDays();
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = BotUtils.createAlbumGroupToListOfTMap(albumsSimplifiedByGroup.keySet());
		for (Map.Entry<AlbumGroup, List<AlbumSimplified>> entry : albumsSimplifiedByGroup.entrySet()) {
			List<AlbumSimplified> filteredAlbumsOfGroup = entry.getValue().stream().filter(as -> isValidDate(as, lookbackDays)).collect(Collectors.toList());
			filteredAlbums.get(entry.getKey()).addAll(filteredAlbumsOfGroup);
		}
		return filteredAlbums;
	}

	/**
	 * Evaluate whether a release is new enough to consider it valid for addition to
	 * the playlist
	 * 
	 * @param as
	 * @param lookbackDays
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	private boolean isValidDate(AlbumSimplified as, int lookbackDays) {
		LocalDate releaseDate = LocalDate.parse(as.getReleaseDate(), RELEASE_DATE_PARSER);
		LocalDate lowerReleaseDateBoundary = LocalDate.now().minusDays(lookbackDays);
		return releaseDate.isAfter(lowerReleaseDateBoundary);
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
	public Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnSearch(Map<AlbumGroup, List<AlbumTrackPair>> newSongs, List<String> followedArtists) throws SQLException, IOException {
		boolean isIntelligentAppearsOnSearchEnabled = config.getUserConfig().isIntelligentAppearsOnSearch();
		List<AlbumTrackPair> appearsOnAlbums = newSongs.get(AlbumGroup.APPEARS_ON);
		if (!isIntelligentAppearsOnSearchEnabled || appearsOnAlbums.isEmpty()) {
			return newSongs;
		}

		Set<String> followedArtistsSet = new HashSet<>(followedArtists);
		List<AlbumTrackPair> intelligentAppearsOnSearchResults = filterNonCompilationsByFeaturedArtist(appearsOnAlbums, followedArtistsSet);
		newSongs.put(AlbumGroup.APPEARS_ON, intelligentAppearsOnSearchResults);

		return newSongs;
	}

	private List<AlbumTrackPair> filterNonCompilationsByFeaturedArtist(List<AlbumTrackPair> albumTrackPairs, Set<String> followedArtists) {
		// Filter out any collection, samplers, or albums whose primary artist is
		// already a followee
		List<AlbumTrackPair> albumsWithoutCollectionsOrSamplers = albumTrackPairs.stream()
			.filter(atp -> !isCollectionOrSampler(atp.getAlbum()))
			.filter(atp -> !containsFeaturedArtist(followedArtists, atp.getAlbum().getArtists()))
			.collect(Collectors.toList());

		// Of those, filter out the actual songs where a featured artist is a followee
		List<AlbumTrackPair> filteredTracks = new ArrayList<>();
		for (AlbumTrackPair atp : albumsWithoutCollectionsOrSamplers) {
			List<TrackSimplified> selectedSongsOfAlbum = atp.getTracks().stream()
				.filter(song -> containsFeaturedArtist(followedArtists, song.getArtists()))
				.collect(Collectors.toList());
			filteredTracks.add(new AlbumTrackPair(atp.getAlbum(), selectedSongsOfAlbum));
		}
		return filteredTracks;
	}

	/**
	 * Returns true if the album group is set to Compilation or the artist is
	 * "Various Artists"
	 * 
	 * @param a
	 * @return
	 */
	private boolean isCollectionOrSampler(AlbumSimplified a) {
		if (!a.getAlbumGroup().equals(AlbumGroup.COMPILATION)) {
			return Arrays.asList(a.getArtists()).stream().anyMatch(as -> as.getName().equals(VARIOUS_ARTISTS));
		}
		return true;
	}

	/**
	 * Checks if ag least a single artist of the subset is part of the given artist
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
	 * @param enabledAlbumGroups
	 * @return
	 * @throws SQLException
	 */
	public Map<String, List<AlbumTrackPair>> mapToTargetPlaylistIds(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> enabledAlbumGroups) throws SQLException {
		List<AlbumTrackPair> singles = newSongsByGroup.get(AlbumGroup.SINGLE);
		List<AlbumTrackPair> eps = singles.stream()
			.filter(atp -> isEP(atp))
			.collect(Collectors.toList());
		singles.removeAll(eps);

		// TODO

		Map<AlbumGroup, List<AlbumTrackPair>> groupedTracks = groupTracksToParentAlbumGroup(newSongsByGroup, enabledAlbumGroups);
		Map<String, List<AlbumTrackPair>> tracksByPlaylistId = new HashMap<>();
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : groupedTracks.entrySet()) {
			String playlistId = config.getPlaylistIdByGroup(entry.getKey());
			tracksByPlaylistId.put(playlistId, entry.getValue());
		}
		return tracksByPlaylistId;
	}

	private boolean isEP(AlbumTrackPair atp) {
		return (atp.getTracks().size() >= EP_SONG_COUNT_THRESHOLD
			|| atp.getTracks().stream().mapToInt(TrackSimplified::getDurationMs).sum() >= EP_DURATION_THRESHOLD
			|| EP_MATCHER.matcher(atp.getAlbum().getName()).find());
	}

	/**
	 * Returns a rearranged view of the given map, depending on whether any album
	 * groups point to the same target playlist.
	 * 
	 * @param newSongsByGroup
	 * @param enabledAlbumGroups
	 * @return
	 * @throws SQLException
	 */
	private Map<AlbumGroup, List<AlbumTrackPair>> groupTracksToParentAlbumGroup(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> enabledAlbumGroups) throws SQLException {
		Map<AlbumGroup, List<AlbumGroup>> groupedPlaylistStores = getPlaylistStoresByParent(enabledAlbumGroups);
		if (groupedPlaylistStores.size() == enabledAlbumGroups.size()) {
			// Each album group has its own set playlist, no merging required
			return newSongsByGroup;
		}

		Map<AlbumGroup, List<AlbumTrackPair>> mergedAlbumGroups = new HashMap<>();
		for (Map.Entry<AlbumGroup, List<AlbumGroup>> playlistGroup : groupedPlaylistStores.entrySet()) {
			AlbumGroup parentAlbumGroup = playlistGroup.getKey();
			List<AlbumTrackPair> mergedAlbumTrackPairs = new ArrayList<>();
			for (AlbumGroup childAlbumGroup : playlistGroup.getValue()) {
				mergedAlbumTrackPairs.addAll(newSongsByGroup.get(childAlbumGroup));
			}
			mergedAlbumGroups.put(parentAlbumGroup, mergedAlbumTrackPairs);
		}
		return mergedAlbumGroups;
	}

	/**
	 * Gets a merged view of all playlist stores by their parent album group
	 * 
	 * @param enabledAlbumGroups
	 * @return
	 * @throws SQLException
	 */
	private Map<AlbumGroup, List<AlbumGroup>> getPlaylistStoresByParent(Collection<AlbumGroup> enabledAlbumGroups) throws SQLException {
		Map<AlbumGroup, List<AlbumGroup>> playlistStoresByAlbumGroupParent = new HashMap<>();
		for (AlbumGroup ag : enabledAlbumGroups) {
			PlaylistStoreDTO ps = config.getPlaylistStore(ag);
			if (ps != null && ps.getParentAlbumGroup() == null) {
				playlistStoresByAlbumGroupParent.put(ag, new ArrayList<>());
				playlistStoresByAlbumGroupParent.get(ag).add(ag);
			}
		}
		for (AlbumGroup ag : enabledAlbumGroups) {
			PlaylistStoreDTO ps = config.getPlaylistStore(ag);
			if (ps != null && ps.getParentAlbumGroup() != null) {
				playlistStoresByAlbumGroupParent.get(ps.getParentAlbumGroup()).add(ag);
			}
		}
		return playlistStoresByAlbumGroupParent;
	}
}
