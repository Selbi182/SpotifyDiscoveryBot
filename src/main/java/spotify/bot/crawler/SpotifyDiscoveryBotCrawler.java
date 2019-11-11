package spotify.bot.crawler;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistInfoRequests;
import spotify.bot.api.requests.PlaylistSongsRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.config.Config;
import spotify.bot.util.AlbumTrackPair;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;

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
	 * Spring-visible constructor to set the initial ready-state to false
	 */
	SpotifyDiscoveryBotCrawler() {
		setReady(false);
	}

	/**
	 * Run the Spotify New Discovery crawler!
	 * 
	 * @return a result map containing the number of added songs by album type
	 * @throws Exception
	 *             if just about anything at all goes wrong lol
	 */
	public Map<AlbumGroup, Integer> runCrawler() throws Exception {
		if (isReady()) {
			setReady(false);
			try {
				spotifyApiAuthorization.login();
				return crawl();
			} catch (Exception e) {
				log.stackTrace(e);
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
	 * @throws Exception
	 */
	public void clearObsoleteNotifiers() throws Exception {
		if (isReady()) {
			try {
				playlistInfoRequests.clearObsoleteNotifiers();
			} catch (UnauthorizedException e) {
				spotifyApiAuthorization.login();
				clearObsoleteNotifiers();
			} catch (Exception e) {
				log.stackTrace(e);
			}
		}
	}

	/**
	 * Event that will be fired once the Spring application has fully booted. It
	 * will automatically initiate the first crawling iteration.
	 * 
	 * @throws Exception
	 */
	@EventListener(ApplicationReadyEvent.class)
	private void firstCrawlAndEnableReadyState() throws Exception {
		log.info("Executing initial crawl iteration...");
		long time = System.currentTimeMillis();
		{
			setReady(true);
			Map<AlbumGroup, Integer> results = runCrawler();
			String response = BotUtils.compileResultString(results);
			if (response != null) {
				log.info(response);
			}
		}
		log.info("Initial crawl iteration successfully finished in: " + (System.currentTimeMillis() - time) + "ms");
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
	 * @throws Exception
	 *             if just about anything at all goes wrong lol
	 */
	private Map<AlbumGroup, Integer> crawl() throws Exception {
		List<AlbumGroup> albumGroups = config.getSetAlbumGroups();
		Map<AlbumGroup, Integer> additionResults = BotUtils.createAlbumGroupToIntegerMap(albumGroups);
		List<String> followedArtists = userInfoRequests.getFollowedArtistsIds();
		if (!followedArtists.isEmpty()) {
			List<AlbumSimplified> nonCachedAlbums = albumRequests.getNonCachedAlbumsOfArtists(followedArtists, albumGroups);
			if (!nonCachedAlbums.isEmpty()) {
				Map<AlbumGroup, List<AlbumSimplified>> newAlbums = offlineRequests.categorizeAndFilterAlbums(nonCachedAlbums, albumGroups);
				if (!BotUtils.isAllEmptyAlbumsOfGroups(newAlbums)) {
					Map<AlbumGroup, List<AlbumTrackPair>> newSongs = trackRequests.getSongIdsByAlbums(newAlbums, followedArtists);
					if (!BotUtils.isAllEmptyAlbumsOfGroups(newSongs)) {
						playlistSongsRequests.addAllReleasesToSetPlaylists(newSongs, albumGroups);
						BotUtils.writeSongAdditionResults(newSongs, additionResults);
					}
				}
			}
		}
		playlistInfoRequests.timestampPlaylists(albumGroups);
		return additionResults;
	}
}
