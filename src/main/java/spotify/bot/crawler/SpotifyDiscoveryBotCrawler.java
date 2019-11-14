package spotify.bot.crawler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.api.services.AlbumService;
import spotify.bot.api.services.PlaylistInfoService;
import spotify.bot.api.services.PlaylistSongsService;
import spotify.bot.api.services.TrackService;
import spotify.bot.api.services.UserInfoService;
import spotify.bot.config.Config;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class SpotifyDiscoveryBotCrawler {

	private boolean ready;

	@Autowired
	private SpotifyApiAuthorization spotifyApiAuthorization;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	@Autowired
	private UserInfoService userInfoService;

	@Autowired
	private AlbumService albumService;

	@Autowired
	private TrackService trackService;

	@Autowired
	private PlaylistSongsService playlistSongsService;

	@Autowired
	private PlaylistInfoService playlistInfoService;
	
	@Autowired
	private FilterService filterService;

	/**
	 * Set the initial ready-state to false
	 */
	@PostConstruct
	private void init() {
		setReady(false);
	}

	/**
	 * Run the Spotify New Discovery crawler!
	 * 
	 * @return a result map containing the number of added songs by album type
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws SQLException
	 */
	public Map<AlbumGroup, Integer> runCrawler() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		if (isReady()) {
			setReady(false);
			try {
				spotifyApiAuthorization.login();
				return crawl();
			} finally {
				setReady(true);
			}
		}
		return null;
	}

	/**
	 * Clears obsolete [NEW] notifiers from playlists where applicable. This method
	 * cannot require the lock.
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SpotifyWebApiException
	 */
	public void clearObsoleteNotifiers() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		if (isReady()) {
			try {
				playlistInfoService.clearObsoleteNotifiers();
			} catch (UnauthorizedException e) {
				spotifyApiAuthorization.login();
				clearObsoleteNotifiers();
			}
		}
	}

	/**
	 * Event that will be fired once the Spring application has fully booted. It
	 * will automatically initiate the first crawling iteration.
	 * 
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 */
	@EventListener(ApplicationReadyEvent.class)
	private void firstCrawlAndEnableReadyState() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		log.info("Executing initial crawl...");
		long time = System.currentTimeMillis();
		{
			setReady(true);
			Map<AlbumGroup, Integer> results = runCrawler();
			String response = BotUtils.compileResultString(results);
			if (response != null) {
				log.info(response);
			}
		}
		log.info("Initial crawl successfully finished in: " + (System.currentTimeMillis() - time) + "ms");
	}

	/**
	 * Indicate whether or not the crawler is currently available
	 * 
	 * @return
	 */
	public boolean isReady() {
		return ready;
	}

	/**
	 * Set ready state
	 * 
	 * @param ready
	 *            the new ready state
	 */
	private void setReady(boolean ready) {
		this.ready = ready;
	}

	///////////////////

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
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private Map<AlbumGroup, Integer> crawl() throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		List<String> followedArtists = userInfoService.getFollowedArtistsIds();
		if (followedArtists.isEmpty()) {
			log.warning("No followed artists found!");
			return null;
		}		
		List<AlbumGroup> enabledAlbumGroups = config.getEnabledAlbumGroups();
		List<AlbumSimplified> nonCachedAlbums = albumService.getNonCachedAlbumsOfArtists(followedArtists, enabledAlbumGroups);
		try {
			if (!nonCachedAlbums.isEmpty()) {
				Map<AlbumGroup, List<AlbumSimplified>> newAlbums = filterService.categorizeAndFilterAlbums(nonCachedAlbums, enabledAlbumGroups);
				if (!BotUtils.isAllEmptyAlbumsOfGroups(newAlbums)) {
					Map<AlbumGroup, List<AlbumTrackPair>> newSongs = trackService.getSongsOfAlbumGroups(newAlbums);
					newSongs = filterService.intelligentAppearsOnSearch(newSongs, followedArtists);
					if (!BotUtils.isAllEmptyAlbumsOfGroups(newSongs)) {
						Map<String, List<AlbumTrackPair>> songsByPlaylistId = filterService.mapToTargetPlaylistIds(newSongs, enabledAlbumGroups);
						playlistSongsService.addAllReleasesToSetPlaylists(songsByPlaylistId);
						playlistInfoService.showNotifiers(newSongs);
						return BotUtils.collectSongAdditionResults(newSongs);							
					} else {
						log.warning("Only found irrelevant appears_on releases!");
					}
				} else {
					log.warning("No new releases found, despite finding " + nonCachedAlbums.size() + " new non-cached entries!");
				}
			}
		} finally {
			albumService.cacheAlbumIds(nonCachedAlbums);
			playlistInfoService.timestampPlaylists(enabledAlbumGroups);
		}
		return null;
	}
}
