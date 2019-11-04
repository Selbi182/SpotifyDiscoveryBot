package spotify.bot.crawler;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;

@Component
public class SpotifyDiscoveryBotCrawler {
	
	@Autowired
	private AlbumRequests albumRequests;
	
	@Autowired
	private OfflineRequests offlineRequests;
	
	@Autowired
	private PlaylistRequests playlistRequests;
	
	@Autowired
	private TrackRequests trackRequests;
	
	@Autowired
	private UserInfoRequests userInfoRequests;
	
	/**
	 * <pre>
	 * The main logic of the crawler. The process for new album searching is always the same chain of tasks:
	 * 1. Get all followed artists (will be cached every 24 hours)
	 * 2. Fetch all albums of those artists (AlbumSimplified)
	 * 3. Filter out all albums that were already stored in the DB
	 * 4. Filter out all albums not released in the lookbackDays range (default: 30 days)
	 * 5. Get the songs IDs of the remaining (new) albums using
	 * 6. Sort the releases and add them to the respective playlists
	 * 
	 * Finally, store the album IDs to the DB to prevent them from getting added a second time
	 * This happens even if no new songs are added, because it will significantly speed up the future search processes
	 * </pre>
	 */
	public Map<AlbumGroup, Integer> runCrawler() throws Exception {
		List<AlbumGroup> albumGroups = BotUtils.getSetAlbumGroups();
		List<String> followedArtists = userInfoRequests.getFollowedArtistsIds();
		List<AlbumSimplified> nonCachedAlbums = albumRequests.getNonCachedAlbumsOfArtists(followedArtists, albumGroups);
		Map<AlbumGroup, Integer> songsAddedPerAlbumGroups = BotUtils.createAlbumGroupToIntegerMap(albumGroups);
		if (!nonCachedAlbums.isEmpty()) {
			Map<AlbumGroup, List<AlbumSimplified>> newAlbums = offlineRequests.categorizeAndFilterAlbums(nonCachedAlbums, albumGroups);
			if (!BotUtils.isAllEmptyAlbumsOfGroups(newAlbums)) {
				Map<AlbumGroup, List<AlbumTrackPair>> newSongs = trackRequests.getSongIdsByAlbums(newAlbums, followedArtists);
				songsAddedPerAlbumGroups = playlistRequests.addAllReleasesToSetPlaylists(newSongs, albumGroups);
			}
		}								
		playlistRequests.timestampUnchangedPlaylistsAndCheckForObsoleteNotifiers(songsAddedPerAlbumGroups);
		return songsAddedPerAlbumGroups;
	}
}
