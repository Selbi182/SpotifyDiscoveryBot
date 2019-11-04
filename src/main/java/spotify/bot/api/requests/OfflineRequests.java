package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
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
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.ReleaseValidator;

@Service
public class OfflineRequests {
	
	@Autowired
	private Config config;

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
	 * Categorizes the given list of albums into a map of their respective album GROUPS
	 * (aka the return context of the simplified album object) 
	 * 
	 * @param albumsSimplified
	 * @param albumGroups 
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumSimplified>> categorizeAlbumsByAlbumGroup(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) {
		Map<AlbumGroup, List<AlbumSimplified>> categorized = BotUtils.createAlbumGroupToListOfTMap(albumGroups);
		albumsSimplified.parallelStream().forEach(as -> {
			AlbumGroup albumGroupOfAlbum = as.getAlbumGroup();
			categorized.get(albumGroupOfAlbum).add(as);
		});
		return categorized;
	}
	
	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param albumsSimplifiedByGroup
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumSimplified>> filterNewAlbumsOnly(Map<AlbumGroup, List<AlbumSimplified>> albumsSimplifiedByGroup, int lookbackDays) {
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = BotUtils.createAlbumGroupToListOfTMap(albumsSimplifiedByGroup.keySet());
		albumsSimplifiedByGroup.entrySet().stream().forEach(fa -> {
			List<AlbumSimplified> filteredAlbumsOfGroup = fa.getValue().stream().filter(as -> ReleaseValidator.getInstance().isValidDate(as)).collect(Collectors.toList());
			filteredAlbums.get(fa.getKey()).addAll(filteredAlbumsOfGroup);
		});
		return filteredAlbums;
	}

	/**
	 * Returns a rearranged view of the given map, depending on whether any album groups point to the same target playlist.
	 * The dominance is ordered as follows: ALBUM > SINGLE > COMPILATION > APPEARS_ON
	 * 
	 * @param newSongsByGroup
	 * @param albumGroups
	 * @return
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> mergeOnIdenticalPlaylists(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup, List<AlbumGroup> albumGroups) {
		Map<String, List<AlbumGroup>> albumGroupsByPlaylistId = new HashMap<>();
		for (AlbumGroup ag : albumGroups) {
			String playlistId = BotUtils.getPlaylistIdByGroup(ag);
			if (!albumGroupsByPlaylistId.containsKey(playlistId)) {
				albumGroupsByPlaylistId.put(playlistId, new ArrayList<>());
			}
			albumGroupsByPlaylistId.get(playlistId).add(ag);
		}
		if (albumGroupsByPlaylistId.size() == albumGroups.size()) {
			return newSongsByGroup;
		}
		
		Map<AlbumGroup, List<AlbumTrackPair>> mergedAlbumGroups = BotUtils.createAlbumGroupToListOfTMap(albumGroups);
		for (List<AlbumGroup> group : albumGroupsByPlaylistId.values()) {
			for (AlbumGroup ag : group) {
				mergedAlbumGroups.get(group.get(0)).addAll(newSongsByGroup.get(ag));
			}
		}
		return mergedAlbumGroups;
	}

	/**
	 * Categorize the list of given albums by album group and filter them by new albums only
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
