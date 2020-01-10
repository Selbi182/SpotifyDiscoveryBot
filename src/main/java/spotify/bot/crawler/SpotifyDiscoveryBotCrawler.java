package spotify.bot.crawler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

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
import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class SpotifyDiscoveryBotCrawler {

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
	 * Lock controlling the local single-crawl behavior
	 */
	private ReentrantLock lock;

	/**
	 * Indicate whether or not the crawler is currently available
	 * 
	 * @return true if the lock exists and is not locked
	 */
	public boolean isReady() {
		return lock != null && !lock.isLocked();
	}

	/**
	 * Run the Spotify New Discovery crawler if it's ready. Lock it if so.
	 * 
	 * @return a result map containing the number of added songs by album type, null
	 *         if lock wasn't available
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws SQLException
	 */
	public Map<AlbumGroupExtended, Integer> tryCrawl() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		if (lock.tryLock()) {
			try {
				return crawl();
			} finally {
				lock.unlock();
			}
		}
		return null;
	}

	/**
	 * Event that will be fired once the Spring application has fully booted. It
	 * will automatically initiate the first crawling iteration. After completion,
	 * the bot will be made available for scheduled and external (manual) crawling.
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
			Map<AlbumGroupExtended, Integer> results = crawl();
			String response = BotUtils.compileResultString(results);
			if (response != null) {
				log.info(response);
			}
		}
		log.info("Initial crawl successfully finished in: " + (System.currentTimeMillis() - time) + "ms");
		lock = new ReentrantLock();
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
	public boolean clearObsoleteNotifiers() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		if (isReady()) {
			try {
				return playlistInfoService.clearObsoleteNotifiers();
			} catch (UnauthorizedException e) {
				clearObsoleteNotifiers();
			}
		}
		return false;
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
	 * <li>Filter out all albums not released in the lookback-days range</li>
	 * <li>Get the songs IDs of the remaining (new) albums</li>
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
	private Map<AlbumGroupExtended, Integer> crawl() throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		spotifyApiAuthorization.login();

		List<String> followedArtists = userInfoService.getFollowedArtistsIds();
		if (followedArtists.isEmpty()) {
			log.warning("No followed artists found!");
			return null;
		}

		Collection<PlaylistStore> playlistStores = config.getAllPlaylistStores();
		List<AlbumSimplified> allAlbums = albumService.getAllAlbumsOfArtists(followedArtists);
		List<AlbumSimplified> nonCachedAlbums = filterService.getNonCachedAlbums(allAlbums);
		try {
			List<AlbumSimplified> filteredAlbums = filterService.filterNewAlbumsOnly(nonCachedAlbums);
			if (!filteredAlbums.isEmpty()) {
				List<AlbumTrackPair> tracksByAlbums = trackService.getTracksOfAlbums(filteredAlbums);
				Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums = filterService.categorizeAlbumsByAlbumGroup(tracksByAlbums);
				filterService.intelligentAppearsOnSearch(categorizedFilteredAlbums, followedArtists);
				if (!BotUtils.isAllEmptyLists(categorizedFilteredAlbums)) {
					Map<PlaylistStore, List<AlbumTrackPair>> songsByMainPlaylist = filterService.mapToTargetPlaylist(categorizedFilteredAlbums, playlistStores);
					Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist = filterService.remapIntoExtendedPlaylists(songsByMainPlaylist, playlistStores);
					playlistSongsService.addAllReleasesToSetPlaylists(songsByPlaylist);
					playlistInfoService.showNotifiers(songsByPlaylist);
					return BotUtils.collectSongAdditionResults(songsByPlaylist);
				}
			}
		} catch (SQLException | SpotifyWebApiException | IOException | InterruptedException e) {
			throw e;
		} finally {
			filterService.cacheAlbumIds(nonCachedAlbums);
			playlistInfoService.timestampPlaylists(playlistStores);
		}
		return null;
	}
}
