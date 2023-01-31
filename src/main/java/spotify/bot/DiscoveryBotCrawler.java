package spotify.bot;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import spotify.api.BotException;
import spotify.api.SpotifyApiAuthorization;
import spotify.api.events.SpotifyApiLoggedInEvent;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.filter.FilterService;
import spotify.bot.filter.RelayService;
import spotify.bot.filter.RemappingService;
import spotify.bot.service.CachedArtistService;
import spotify.bot.service.DiscoveryAlbumService;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.service.PlaylistSongsService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.CachedArtistsContainer;
import spotify.services.ArtistService;
import spotify.services.TrackService;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class DiscoveryBotCrawler {
	private final SpotifyApiAuthorization spotifyApiAuthorization;
	private final DiscoveryBotLogger log;
	private final ArtistService artistService;
	private final CachedArtistService cachedArtistService;
	private final DiscoveryAlbumService discoveryAlbumService;
	private final TrackService trackService;
	private final PlaylistSongsService playlistSongsService;
	private final PlaylistMetaService playlistMetaService;
	private final FilterService filterService;
	private final RemappingService remappingService;
	private final RelayService relayService;

	DiscoveryBotCrawler(
			SpotifyApiAuthorization spotifyApiAuthorization,
			DiscoveryBotLogger discoveryBotLogger,
			ArtistService artistService,
			CachedArtistService cachedArtistService,
			DiscoveryAlbumService discoveryAlbumService,
			TrackService trackService,
			PlaylistSongsService playlistSongsService,
			PlaylistMetaService playlistMetaService,
			FilterService filterService,
			RemappingService remappingService,
			RelayService relayService
	) {
		this.spotifyApiAuthorization = spotifyApiAuthorization;
		this.log = discoveryBotLogger;
		this.artistService = artistService;
		this.cachedArtistService = cachedArtistService;
		this.discoveryAlbumService = discoveryAlbumService;
		this.trackService = trackService;
		this.playlistSongsService = playlistSongsService;
		this.playlistMetaService = playlistMetaService;
		this.filterService = filterService;
		this.remappingService = remappingService;
		this.relayService = relayService;
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
	@EventListener(SpotifyApiLoggedInEvent.class)
	public void firstCrawlAndEnableReadyState() throws BotException, SQLException {
		log.printLine();
		log.info("Executing initial crawl...", false);
		long time = System.currentTimeMillis();
		if (!DeveloperMode.isInitialCrawlDisabled()) {
			Map<AlbumGroupExtended, Integer> results = crawl();
			String response = DiscoveryBotUtils.compileResultString(results);
			if (response != null) {
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
	 * @throws BotException on an external exception related to the Spotify Web API
	 */
	public boolean clearObsoleteNotifiers() throws BotException {
		return playlistMetaService.clearObsoleteNotifiers();
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
		spotifyApiAuthorization.refresh();
		return crawlScript();
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
				if (!BotUtils.isAllEmptyLists(newTracksByTargetPlaylist)) {
					return addReleasesToPlaylistsAndCollectResults(newTracksByTargetPlaylist);
				}
			}
		}
		return null;
	}

	/**
	 * Phase 0: Get all followed artists and initialize cache for any new ones
	 */
	private List<String> getFollowedArtists() throws SQLException, BotException {
		CachedArtistsContainer cachedArtistsContainer = cachedArtistService.getFollowedArtistsIds();
		List<String> newArtists = cachedArtistsContainer.getNewArtists();
		if (!newArtists.isEmpty()) {
			log.info("Initializing album cache for " + newArtists.size() + " newly followed artist[s]:");
			artistService.getArtists(newArtists).stream()
					.map(Artist::getName)
					.forEach(name -> log.info("- " + name));
			List<AlbumSimplified> allAlbumsOfNewFollowees = discoveryAlbumService.getAllAlbumsOfArtists(newArtists);
			List<AlbumSimplified> albumsToInitialize = filterService.getNonCachedAlbums(allAlbumsOfNewFollowees);
			filterService.cacheAlbumIds(albumsToInitialize, false);
			filterService.cacheAlbumNames(albumsToInitialize, false);
		}
		return cachedArtistsContainer.getAllArtists();
	}

	/**
	 * Phase 1: Get all new releases from the list of followed artists
	 */
	private List<AlbumSimplified> getNewAlbumsFromArtists(List<String> followedArtists) throws BotException, SQLException {
		List<AlbumSimplified> allAlbums = discoveryAlbumService.getAllAlbumsOfArtists(followedArtists);
		List<AlbumSimplified> nonCachedAlbums = filterService.getNonCachedAlbums(allAlbums);
		List<AlbumSimplified> noFutureAlbums = filterService.filterFutureAlbums(nonCachedAlbums);
		filterService.cacheAlbumIds(noFutureAlbums, true);
		List<AlbumSimplified> insertedAppearOnArtistsAlbums = discoveryAlbumService.resolveViaAppearsOnArtistNames(noFutureAlbums);
		List<AlbumSimplified> filteredNoDuplicatesAlbums = filterService.filterDuplicateAlbums(insertedAppearOnArtistsAlbums);
		List<AlbumSimplified> filteredAlbums = filterService.filterNewAlbumsOnly(filteredNoDuplicatesAlbums);
		return filteredAlbums;
	}

	/**
	 * Phase 2: Get the tracks of the new releases and map them to their respective target playlist store
	 */
	private Map<PlaylistStore, List<AlbumTrackPair>> getNewTracksByTargetPlaylist(List<AlbumSimplified> filteredAlbums, List<String> followedArtists) throws BotException {
		List<AlbumTrackPair> tracksByAlbums = trackService.getTracksOfAlbums(filteredAlbums);
		Map<AlbumGroup, List<AlbumTrackPair>> categorizedFilteredAlbums = filterService.categorizeAlbumsByAlbumGroup(tracksByAlbums);
		Map<AlbumGroup, List<AlbumTrackPair>> intelligentAppearsOnFilteredAlbums = filterService.intelligentAppearsOnSearch(categorizedFilteredAlbums, followedArtists);
		if (!BotUtils.isAllEmptyLists(intelligentAppearsOnFilteredAlbums)) {
			Map<PlaylistStore, List<AlbumTrackPair>> songsByMainPlaylist = remappingService.mapToTargetPlaylist(intelligentAppearsOnFilteredAlbums);
			Map<PlaylistStore, List<AlbumTrackPair>> songsByExtendedPlaylist = remappingService.remapIntoExtendedPlaylists(songsByMainPlaylist);
			Map<PlaylistStore, List<AlbumTrackPair>> songsFilteredWithBlacklistedArtistReleasePairs = filterService.filterBlacklistedReleaseTypesForArtists(songsByExtendedPlaylist);
			return songsFilteredWithBlacklistedArtistReleasePairs;
		}
		return Collections.emptyMap();
	}

	/**
	 * Phase 3: Add all releases to their target playlists and collect the results
	 */
	private Map<AlbumGroupExtended, Integer> addReleasesToPlaylistsAndCollectResults(Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist) throws BotException {
		playlistSongsService.addAllReleasesToSetPlaylists(newTracksByTargetPlaylist);
		playlistMetaService.showNotifiers(newTracksByTargetPlaylist);
		relayService.relayResults(newTracksByTargetPlaylist);
		return DiscoveryBotUtils.collectSongAdditionResults(newTracksByTargetPlaylist);
	}
}
