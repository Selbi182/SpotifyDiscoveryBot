package spotify.bot.crawler;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistInfoRequests;
import spotify.bot.api.requests.PlaylistSongsRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.config.BotLogger;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;

@Component
public class SpotifyDiscoveryBotCrawler {

	private boolean isBusy;

	@Autowired
	private CrawlFinalizer crawlFinalizer;

	@Autowired
	private BotLogger log;

	@Autowired
	private AlbumRequests albumRequests;

	@Autowired
	private OfflineRequests offlineRequests;

	@Autowired
	private PlaylistSongsRequests playlistSongsRequests;

	@Autowired
	private PlaylistInfoRequests playlistInfoRequests;

	@Autowired
	private TrackRequests trackRequests;

	@Autowired
	private UserInfoRequests userInfoRequests;

	/**
	 * Run the Spotify New Discovery crawler!
	 * 
	 * @return a result map containing the number of added songs by album type
	 * @throws Exception
	 *             if just about anything at all goes wrong lol
	 */
	public Map<AlbumGroup, Integer> runCrawler() throws Exception {
		isBusy = true;
		try {
			return crawl();
		} catch (Exception e) {
			log.stackTrace(e);
			return null;
		} finally {
			crawlFinalizer.finalizeCrawl();
			isBusy = false;
		}
	}

	/**
	 * This is the main crawler logic.<br/>
	 * <br/>
	 * 
	 * The process for new album searching is always the same chain of tasks:
	 * <ol>
	 * <li>Get all followed artists (will be cached every 24 hours)</li>
	 * <li>Fetch all albums of those artists (AlbumSimplified)</li>
	 * <li>Filter out all albums that were already stored in the DB</li>
	 * <li>Filter out all albums not released in the lookbackDays range (default: 30
	 * days)</li>
	 * <li>Get the songs IDs of the remaining (new) albums using</li>
	 * <li>Sort the releases and add them to the respective playlists</li>
	 * </ol>
	 * 
	 * Finally, store the album IDs to the DB to prevent them from getting added a
	 * second time<br/>
	 * This happens even if no new songs are added, because it will significantly
	 * speed up the future search processes
	 * 
	 * @return a result map containing the number of added songs by album type
	 * @throws Exception
	 *             if just about anything at all goes wrong lol
	 */
	private Map<AlbumGroup, Integer> crawl() throws Exception {
		List<AlbumGroup> albumGroups = BotUtils.getSetAlbumGroups();
		Map<AlbumGroup, Integer> additionResults = BotUtils.createAlbumGroupToIntegerMap(albumGroups);
		List<String> followedArtists = userInfoRequests.getFollowedArtistsIds();
		if (!followedArtists.isEmpty()) {
			List<AlbumSimplified> nonCachedAlbums = albumRequests.getNonCachedAlbumsOfArtists(followedArtists, albumGroups);
			if (!nonCachedAlbums.isEmpty()) {
				Map<AlbumGroup, List<AlbumSimplified>> newAlbums = offlineRequests.categorizeAndFilterAlbums(nonCachedAlbums, albumGroups);
				if (!BotUtils.isAllEmptyAlbumsOfGroups(newAlbums)) {
					Map<AlbumGroup, List<AlbumTrackPair>> newSongs = trackRequests.getSongIdsByAlbums(newAlbums, followedArtists);
					playlistSongsRequests.addAllReleasesToSetPlaylists(newSongs, albumGroups);
					BotUtils.writeSongAdditionResults(newSongs, additionResults);
				}
			}
		}
		playlistInfoRequests.timestampPlaylists(albumGroups);
		return additionResults;
	}

	/**
	 * Indicate whether or not the crawler is currently available
	 * 
	 * @return
	 */
	public boolean isReady() {
		return !isBusy;
	}
}
