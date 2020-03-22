package spotify.bot.filter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;

import spotify.bot.config.Config;
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.filter.remapper.EPRemapper;
import spotify.bot.filter.remapper.LiveRemapper;
import spotify.bot.filter.remapper.Remapper;
import spotify.bot.filter.remapper.RemixRemapper;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class RemappingService {

	@Autowired
	private Config config;

	@Autowired
	private EPRemapper epRemapper;

	@Autowired
	private RemixRemapper remixRemapper;

	@Autowired
	private LiveRemapper liveRemapper;

	/**
	 * Transform the given map of releases by album group to the true destination
	 * playlist IDs.
	 * 
	 * @param newSongsByGroup
	 * @return
	 * @throws SQLException
	 */
	public Map<PlaylistStore, List<AlbumTrackPair>> mapToTargetPlaylist(Map<AlbumGroup, List<AlbumTrackPair>> newSongsByGroup) throws SQLException {
		Map<PlaylistStore, List<AlbumTrackPair>> resultMap = new HashMap<>();
		for (Map.Entry<AlbumGroup, List<AlbumTrackPair>> entry : newSongsByGroup.entrySet()) {
			List<AlbumTrackPair> atp = entry.getValue();
			if (!atp.isEmpty()) {
				AlbumGroup ag = entry.getKey();
				PlaylistStore ps = config.getPlaylistStore(ag);
				if (ps != null) {
					resultMap.put(ps, atp);
				}
			}
		}
		return resultMap;
	}

	////////////////////////////////

	/**
	 * Perform extended remapping for EPs, Live releases, and Remix released (these
	 * options need to be user-configured).
	 * 
	 * @param songsByPS
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<PlaylistStore, List<AlbumTrackPair>> remapIntoExtendedPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPS) throws SQLException, IOException {
		// Copy map first to retain the input map (makes debugging easier)
		Map<PlaylistStore, List<AlbumTrackPair>> regroupedMap = new HashMap<>(songsByPS);

		UserOptions userOptions = config.getUserOptions();
		if (userOptions.isRemixSeparation()) {
			remap(remixRemapper, regroupedMap);
		}
		if (userOptions.isLiveSeparation()) {
			remap(liveRemapper, regroupedMap);
		}
		if (userOptions.isEpSeparation()) {
			remap(epRemapper, regroupedMap);
		}

		// Remove entries with empty lists (also makes debugging easier)
		regroupedMap.entrySet().removeIf(e -> e.getValue().isEmpty());
		return regroupedMap;

	}

	private void remap(Remapper remapper, Map<PlaylistStore, List<AlbumTrackPair>> baseTrackMap) throws SQLException {
		AlbumGroupExtended age = remapper.getAlbumGroup();
		PlaylistStore ps = config.getPlaylistStore(age);
		if (ps != null && ps.getPlaylistId() != null) {
			List<AlbumTrackPair> remappedTracks = new ArrayList<>();
			for (Map.Entry<PlaylistStore, List<AlbumTrackPair>> entry : baseTrackMap.entrySet()) {
				if (remapper.isAllowedAlbumGroup(entry.getKey().getAlbumGroupExtended())) {
					List<AlbumTrackPair> releases = entry.getValue();
					if (releases != null && !releases.isEmpty()) {
						List<AlbumTrackPair> filteredReleases = releases.stream()
							.filter(atp -> remapper.qualifiesAsRemappable(atp))
							.collect(Collectors.toList());
						if (!filteredReleases.isEmpty()) {
							remappedTracks.addAll(filteredReleases);
							releases.removeAll(filteredReleases);
						}
					}
				}
			}
			if (!remappedTracks.isEmpty()) {
				baseTrackMap.put(ps, remappedTracks);
			}
		}
	}
}
