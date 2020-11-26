package spotify.bot;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.api.services.AlbumService;
import spotify.bot.api.services.ArtistService;
import spotify.bot.api.services.PlaylistMetaService;
import spotify.bot.api.services.PlaylistSongsService;
import spotify.bot.api.services.TrackService;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.filter.FilterService;
import spotify.bot.filter.RemappingService;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;
import spotify.bot.util.data.CachedArtistsContainer;

@Component
public class DiscoveryBotCrawler {
	@Autowired
	private SpotifyApiAuthorization spotifyApiAuthorization;

	@Autowired
	private BotLogger log;

	@Autowired
	private ArtistService artistService;

	@Autowired
	private AlbumService albumService;

	@Autowired
	private TrackService trackService;

	@Autowired
	private PlaylistSongsService playlistSongsService;

	@Autowired
	private PlaylistMetaService playlistInfoService;

	@Autowired
	private FilterService filterService;

	@Autowired
	private RemappingService remappingService;

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
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	public Map<AlbumGroupExtended, Integer> tryCrawl() throws BotException, SQLException {
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
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@EventListener(ApplicationReadyEvent.class)
	private void firstCrawlAndEnableReadyState() throws BotException, SQLException {
		log.printLine();
		log.info("Executing initial crawl...");
		long time = System.currentTimeMillis();
		if (!DeveloperMode.isInitialCrawlSkipped()) {
			Map<AlbumGroupExtended, Integer> results = crawl();
			String response = BotUtils.compileResultString(results);
			if (response != null) {
				log.info(response);
			}
		} else {
			log.info(">>> SKIPPED <<<");
		}
		log.info("Initial crawl successfully finished in: " + (System.currentTimeMillis() - time) + "ms");
		log.resetAndPrintLine();
		lock = new ReentrantLock();
	}

	/**
	 * Clears obsolete [NEW] notifiers from playlists where applicable. This method
	 * cannot require the lock.
	 * 
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	public boolean clearObsoleteNotifiers() throws BotException, SQLException {
		return playlistInfoService.clearObsoleteNotifiers();
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
	 */
	private Map<AlbumGroupExtended, Integer> crawl() throws BotException, SQLException {
		spotifyApiAuthorization.login();
		Map<AlbumGroupExtended, Integer> crawlResults = crawlScript();
		return crawlResults;
	}

	/////////////////////////

	/**
	 * Main crawl script with fail-fast mechanisms to save bandwidth
	 */
	private Map<AlbumGroupExtended, Integer> crawlScript() throws BotException, SQLException {
		List<String> followedArtists = getFollowedArtists();
		if (!followedArtists.isEmpty()) {
			List<AlbumSimplified> filteredAlbums = getNewAlbumsFromArtists(followedArtists);
			if (!filteredAlbums.isEmpty()) {
				Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist = getNewTracksByTargetPlaylist(filteredAlbums, followedArtists);
				if (!newTracksByTargetPlaylist.isEmpty()) {
					Map<AlbumGroupExtended, Integer> crawlResults = addReleasesToPlaylistsAndCollectResults(newTracksByTargetPlaylist);
					return crawlResults;
				}
			}
		}
		return null;
	}

	/**
	 * Phase 0: Get all followed artists and initialize cache for any new ones
	 */
	private List<String> getFollowedArtists() throws SQLException, BotException {
		CachedArtistsContainer cachedArtistsContainer = artistService.getFollowedArtistsIds();
		List<String> newArtists = cachedArtistsContainer.getNewArtists();
		if (!newArtists.isEmpty()) {
			log.info("Initializing album cache for " + newArtists.size() + " newly followed artist[s]...");
			List<AlbumSimplified> allAlbumsOfNewFollowees = albumService.getAllAlbumsOfArtists(newArtists);
			filterService.getNonCachedAlbumsAndCache(allAlbumsOfNewFollowees, false);			
		}
		return cachedArtistsContainer.getAllArtists();
	}

	/**
	 * Phase 1: Get all new releases from the list of followed artists
	 */
	private List<AlbumSimplified> getNewAlbumsFromArtists(List<String> followedArtists) throws BotException, SQLException {
		List<AlbumSimplified> allAlbums = albumService.getAllAlbumsOfArtists(followedArtists);
		List<AlbumSimplified> nonCachedAlbums = filterService.getNonCachedAlbumsAndCache(allAlbums, true);
		List<AlbumSimplified> insertedAppearOnArtistsAlbums = albumService.resolveViaAppearsOnArtistNames(nonCachedAlbums);
		List<AlbumSimplified> filteredNoDuplicatesAlbums = filterService.filterDuplicateAlbums(insertedAppearOnArtistsAlbums);
		List<AlbumSimplified> filteredAlbums = filterService.filterNewAlbumsOnly(filteredNoDuplicatesAlbums);
		return filteredAlbums;
	}

	/**
	 * Phase 2: Get the tracks of the new releases and map them to their respective
	 * target playlist store
	 */
	private Map<PlaylistStore, List<AlbumTrackPair>> getNewTracksByTargetPlaylist(List<AlbumSimplified> filteredAlbums, List<String> followedArtists) throws BotException {
		List<AlbumTrackPair> tracksByAlbums = trackService.getTracksOfAlbums(filteredAlbums);
		Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums = filterService.categorizeAlbumsByAlbumGroup(tracksByAlbums);
		Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnFilteredAlbums = filterService.intelligentAppearsOnSearch(categorizedFilteredAlbums, followedArtists);
		if (!BotUtils.isAllEmptyLists(intelligentAppearsOnFilteredAlbums)) {
			Map<PlaylistStore, List<AlbumTrackPair>> songsByMainPlaylist = remappingService.mapToTargetPlaylist(intelligentAppearsOnFilteredAlbums);
			Map<PlaylistStore, List<AlbumTrackPair>> songsByExtendedPlaylist = remappingService.remapIntoExtendedPlaylists(songsByMainPlaylist);
			return songsByExtendedPlaylist;
		}
		return Collections.emptyMap();
	}

	/**
	 * Phase 3: Add all releases to their target playlists and collect the results
	 */
	private Map<AlbumGroupExtended, Integer> addReleasesToPlaylistsAndCollectResults(Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist) throws BotException, SQLException {
		playlistSongsService.addAllReleasesToSetPlaylists(newTracksByTargetPlaylist);
		playlistInfoService.showNotifiers(newTracksByTargetPlaylist);
		return BotUtils.collectSongAdditionResults(newTracksByTargetPlaylist);
	}
}
