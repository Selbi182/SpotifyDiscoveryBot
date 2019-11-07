package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.config.Config;
import spotify.bot.config.Config.PlaylistStore;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.ReleaseValidator;

@Service
public class OfflineRequests {

	@Autowired
	private Config config;
	
	@Autowired
	private ReleaseValidator releaseValidator;

	/**
	 * Sort the albums of each album group
	 * 
	 * @param albumTrackPairs
	 * @return
	 */
	public List<AlbumTrackPair> sortReleases(List<AlbumTrackPair> albumTrackPairs) {
		Collections.sort(albumTrackPairs);
		return albumTrackPairs;
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
	 */
	private Map<AlbumGroup, List<AlbumSimplified>> filterNewAlbumsOnly(Map<AlbumGroup, List<AlbumSimplified>> albumsSimplifiedByGroup, int lookbackDays) {
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = BotUtils.createAlbumGroupToListOfTMap(albumsSimplifiedByGroup.keySet());
		albumsSimplifiedByGroup.entrySet().stream().forEach(fa -> {
			List<AlbumSimplified> filteredAlbumsOfGroup = fa.getValue().stream().filter(as -> releaseValidator.isValidDate(as)).collect(Collectors.toList());
			filteredAlbums.get(fa.getKey()).addAll(filteredAlbumsOfGroup);
		});
		return filteredAlbums;
	}

	/**
	 * Returns a rearranged view of the given map, depending on whether any album
	 * groups point to the same target playlist.
	 * 
	 * @param newSongsByGroup
	 * @param albumGroups
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> groupTracksToParentAlbumGroup(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> albumGroups) {
		Map<AlbumGroup, List<AlbumGroup>> groupedPlaylistStores = createPlaylistGroupsByParent(albumGroups);
		if (groupedPlaylistStores.size() == albumGroups.size()) {
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
	 * Create a merged view of all playlist stores by their parent album group
	 * 
	 * @param enabledAlbumGroups
	 * @return
	 */
	private Map<AlbumGroup, List<AlbumGroup>> createPlaylistGroupsByParent(Collection<AlbumGroup> enabledAlbumGroups) {
		Map<AlbumGroup, List<AlbumGroup>> playlistStoresByAlbumGroupParent = new HashMap<>();
		for (AlbumGroup ag : enabledAlbumGroups) {
			PlaylistStore ps = config.getPlaylistStoreByAlbumGroup(ag);
			if (ps != null && ps.getParentAlbumGroup() == null) {
				playlistStoresByAlbumGroupParent.put(ag, new ArrayList<>());
				playlistStoresByAlbumGroupParent.get(ag).add(ag);
			}
		}
		for (AlbumGroup ag : enabledAlbumGroups) {
			PlaylistStore ps = config.getPlaylistStoreByAlbumGroup(ag);
			if (ps != null && ps.getParentAlbumGroup() != null) {
				playlistStoresByAlbumGroupParent.get(ps.getParentAlbumGroup()).add(ag);
			}
		}
		return playlistStoresByAlbumGroupParent;
	}

	/**
	 * Categorize the list of given albums by album group and filter them by new
	 * albums only (aka. those which weren't previously cached but still too old,
	 * such as re-releases)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @param lookbackDays
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<AlbumGroup, List<AlbumSimplified>> categorizeAndFilterAlbums(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) throws IOException, SQLException {
		Map<AlbumGroup, List<AlbumSimplified>> categorizedAlbums = categorizeAlbumsByAlbumGroup(albumsSimplified, albumGroups);
		int lookbackDays = config.getLookbackDays();
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = filterNewAlbumsOnly(categorizedAlbums, lookbackDays);
		return filteredAlbums;
	}
}
