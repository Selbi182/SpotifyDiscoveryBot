package spotify.bot;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.api.events.SpotifyApiException;
import spotify.api.events.SpotifyApiLoggedInEvent;
import spotify.bot.properties.FeatureControl;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.filter.FilterService;
import spotify.bot.properties.AutoPurgerService;
import spotify.bot.properties.ForwarderService;
import spotify.bot.filter.RemappingService;
import spotify.bot.service.CachedArtistService;
import spotify.bot.service.DiscoveryAlbumService;
import spotify.bot.service.DiscoveryTrackService;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.service.PlaylistSongsService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.CachedArtistsContainer;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class DiscoveryBotCrawler {
	private final DiscoveryBotLogger log;
	private final CachedArtistService cachedArtistService;
	private final DiscoveryAlbumService discoveryAlbumService;
	private final DiscoveryTrackService discoveryTrackService;
	private final PlaylistStoreConfig playlistStoreConfig;
	private final PlaylistSongsService playlistSongsService;
	private final PlaylistMetaService playlistMetaService;
	private final FilterService filterService;
	private final RemappingService remappingService;
	private final ForwarderService forwarderService;
	private final FeatureControl featureControl;
	private final AutoPurgerService autoPurgerService;

	private List<AlbumSimplified> albumsToCache;

	DiscoveryBotCrawler(
		DiscoveryBotLogger discoveryBotLogger,
		CachedArtistService cachedArtistService,
		DiscoveryAlbumService discoveryAlbumService,
		DiscoveryTrackService discoveryTrackService,
		PlaylistStoreConfig playlistStoreConfig,
		PlaylistSongsService playlistSongsService,
		PlaylistMetaService playlistMetaService,
		FilterService filterService,
		RemappingService remappingService,
		ForwarderService forwarderService,
		FeatureControl featureControl,
		AutoPurgerService autoPurgerService
	) {
		this.log = discoveryBotLogger;
		this.cachedArtistService = cachedArtistService;
		this.discoveryAlbumService = discoveryAlbumService;
		this.discoveryTrackService = discoveryTrackService;
		this.playlistStoreConfig = playlistStoreConfig;
		this.playlistSongsService = playlistSongsService;
		this.playlistMetaService = playlistMetaService;
		this.filterService = filterService;
		this.remappingService = remappingService;
		this.forwarderService = forwarderService;
		this.featureControl = featureControl;
		this.autoPurgerService = autoPurgerService;
	}

	/**
	 * Lock controlling the local single-crawl behavior
	 */
	private ReentrantLock lock;

	/**
	 * Indicate whether the crawler is currently available
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
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	public Map<AlbumGroupExtended, Integer> tryCrawl() throws SpotifyApiException, SQLException {
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
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@EventListener(SpotifyApiLoggedInEvent.class)
	public void firstCrawlAndEnableReadyState() throws SpotifyApiException, SQLException {
		log.printLine();
		long time = System.currentTimeMillis();
		log.debug("Initializing playlist stores...");
		playlistStoreConfig.setupPlaylistStores();
		playlistMetaService.initLastUpdatedFromPlaylistDescriptions();
		log.info("Executing initial crawl...", false);
		if (featureControl.isInitialCrawlEnabled()) {
			Map<AlbumGroupExtended, Integer> results = crawl();
			String response = DiscoveryBotUtils.compileResultString(results);
			if (!response.isBlank()) {
				log.info(response, false);
			}
		} else {
			log.info(">>> SKIPPED <<<", false);
		}
		log.info("Initial crawl successfully finished in: " + (System.currentTimeMillis() - time) + "ms", false);
		log.resetAndPrintLine();
		lock = new ReentrantLock();
	}

	/**
	 * Clears obsolete [NEW] notifiers from playlists where applicable. This method
	 * cannot require the lock.
	 *
	 * @param force if true, force-clear all notifiers no matter what
	 *
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 */
	public void clearObsoleteNotifiers(boolean force) throws SpotifyApiException {
		playlistMetaService.clearObsoleteNotifiers(force);
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
	private Map<AlbumGroupExtended, Integer> crawl() throws SpotifyApiException, SQLException {
		try {
			return crawlScript();
		} finally {
			updateAlbumCache();
		}
	}

	/////////////////////////

	/**
	 * Main crawl script with fail-fast mechanisms to save bandwidth
	 */
	private Map<AlbumGroupExtended, Integer> crawlScript() throws SpotifyApiException, SQLException {
		autoPurgerService.runPurger();

		List<String> followedArtists = getFollowedArtists();
		if (!followedArtists.isEmpty()) {
			List<AlbumSimplified> filteredAlbums = getNewAlbumsFromArtists(followedArtists);
			if (!filteredAlbums.isEmpty()) {
				Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist = getNewTracksByTargetPlaylist(filteredAlbums, followedArtists);
				if (!SpotifyUtils.isAllEmptyLists(newTracksByTargetPlaylist)) {
					return addReleasesToPlaylistsAndCollectResults(newTracksByTargetPlaylist);
				}
			}
		}
		return null;
	}

	/**
	 * Phase 0: Get all followed artists and initialize cache for any new ones
	 */
	private List<String> getFollowedArtists() throws SQLException, SpotifyApiException {
		log.debug("Fetching followed artists...");
		CachedArtistsContainer cachedArtistsContainer = cachedArtistService.getFollowedArtistsIds();
		return cachedArtistsContainer.getAllArtists();
	}

	/**
	 * Phase 1: Get all new releases from the list of followed artists
	 */
	private List<AlbumSimplified> getNewAlbumsFromArtists(List<String> followedArtists) throws SpotifyApiException, SQLException {
		log.debug("Fetching releases of followed artists...");
		List<AlbumSimplified> allAlbums = discoveryAlbumService.getAllAlbumsOfArtists(followedArtists, false);
		log.debug("Filtering for new releases...");
		List<AlbumSimplified> nonCachedAlbums = filterService.getNonCachedAlbums(allAlbums);
		List<AlbumSimplified> noFutureAlbums = filterService.filterFutureAlbums(nonCachedAlbums);
		albumsToCache = List.copyOf(noFutureAlbums);
		log.debug("Resolving appears-on artists...");
		List<AlbumSimplified> insertedAppearOnArtistsAlbums = discoveryAlbumService.resolveViaAppearsOnArtistNames(noFutureAlbums);
		log.debug("Filtering for new albums only...");
		List<AlbumSimplified> filteredNoDuplicatesAlbums = filterService.filterDuplicatedAlbumsReleasedSimultaneously(insertedAppearOnArtistsAlbums);
		return filterService.filterNewAlbumsOnly(filteredNoDuplicatesAlbums);
	}

	/**
	 * Phase 2: Get the tracks of the new releases and map them to their respective target playlist store
	 */
	private Map<PlaylistStore, List<AlbumTrackPair>> getNewTracksByTargetPlaylist(List<AlbumSimplified> filteredAlbums, List<String> followedArtists) throws SpotifyApiException {
		log.debug("Getting tracks of new albums...");
		List<AlbumTrackPair> tracksByAlbums = discoveryTrackService.getTracksOfAlbums(filteredAlbums);
		Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums = filterService.categorizeAlbumsByAlbumGroup(tracksByAlbums);
		log.debug("Applying intelligent appears-on search...");
		Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnFilteredAlbums = filterService.intelligentAppearsOnSearch(categorizedFilteredAlbums, followedArtists);
		if (!SpotifyUtils.isAllEmptyLists(intelligentAppearsOnFilteredAlbums)) {
			log.debug("Remapping to extended playlist types...");
			Map<PlaylistStore, List<AlbumTrackPair>> songsByMainPlaylist = remappingService.mapToTargetPlaylist(intelligentAppearsOnFilteredAlbums);
			Map<PlaylistStore, List<AlbumTrackPair>> songsByExtendedPlaylist = remappingService.remapIntoExtendedPlaylists(songsByMainPlaylist);
			Map<PlaylistStore, List<AlbumTrackPair>> songsByExtendedPlaylistFiltered = remappingService.removeDisabledPlaylistStores(songsByExtendedPlaylist);
			log.debug("Removing blacklisted release types...");
			return filterService.filterBlacklistedReleaseTypesForArtists(songsByExtendedPlaylistFiltered);
		}
		return Map.of();
	}

	/**
	 * Phase 3: Add all releases to their target playlists and collect the results
	 */
	private Map<AlbumGroupExtended, Integer> addReleasesToPlaylistsAndCollectResults(Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist) throws SpotifyApiException {
		log.debug("Adding new releases to target playlists...");
		playlistSongsService.addAllReleasesToSetPlaylists(newTracksByTargetPlaylist);
		playlistMetaService.showNotifiers(newTracksByTargetPlaylist);
		forwarderService.forwardResults(newTracksByTargetPlaylist);
		return DiscoveryBotUtils.collectSongAdditionResults(newTracksByTargetPlaylist);
	}

	/**
	 * Post: Cache any new album IDs found during this crawl process
	 */
	private void updateAlbumCache() {
		if (albumsToCache != null && !albumsToCache.isEmpty()) {
			log.debug("Updating album cache...");
			filterService.cacheAlbumIds(albumsToCache);
			filterService.cacheAlbumNames(albumsToCache);
			albumsToCache = null;
		}
	}
}
