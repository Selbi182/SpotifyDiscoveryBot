package spotify.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.ini4j.InvalidFileFormatException;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;

public class SpotifyDiscoveryBot implements Runnable {

	// Local variables
	private SpotifyApiSessionManager api;

	/**
	 * Initialize the app
	 * 
	 * @throws InvalidFileFormatException
	 * @throws Exception
	 */
	public SpotifyDiscoveryBot() throws Exception {
		this.api = new SpotifyApiSessionManager();
	}

	/**
	 * Crawler entry point
	 */
	@Override
	public void run() {
		try {
			// Fetch all followed artists of the user
			final List<Artist> followedArtists = UserInfoRequests.getFollowedArtists(api);

			// Set up the crawl threads
			Thread tAlbum = crawlThread(followedArtists, AlbumType.ALBUM);
			Thread tSingle = crawlThread(followedArtists, AlbumType.SINGLE);
			Thread tCompilation = crawlThread(followedArtists, AlbumType.COMPILATION);
			Thread tAppearsOn;
			if (Config.getInstance().isIntelligentAppearsOnSearch()) {
				tAppearsOn = crawlThread(followedArtists, AlbumType.APPEARS_ON, (fa, at) -> intelligentAppearsOnSearch(fa));
			} else {
				tAppearsOn = crawlThread(followedArtists, AlbumType.APPEARS_ON);
			}
			
			// Start all crawlers
			tAlbum.start();
			tSingle.start();
			tCompilation.start();
			tAppearsOn.start();
			
			// Wait for them all to finish
			tAlbum.join();
			tSingle.join();
			tCompilation.join();
			tAppearsOn.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	    
	/**
	 * Create a Thread for the most common crawl operations
	 * 
	 * @param followedArtists
	 * @param playlistId
	 * @param albumType
	 * @return
	 */
	private Thread crawlThread(List<Artist> followedArtists, AlbumType albumType) {
		return crawlThread(followedArtists, albumType, (fa, at) -> crawl(fa, at));
	}
	
	/**
	 * Creates a Thread with the specified crawler
	 * 
	 * @param followedArtists
	 * @param albumType
	 * @param crawler
	 * @return
	 */
	private Thread crawlThread(List<Artist> followedArtists, AlbumType albumType, BiFunction<List<Artist>, AlbumType, List<List<TrackSimplified>>> crawler) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				String playlistId = BotUtils.getPlaylistIdByType(albumType);
				if (BotUtils.isPlaylistSet(playlistId)) {
					try {
						List<List<TrackSimplified>> newTracks = crawler.apply(followedArtists, albumType);
						PlaylistRequests.addSongsToPlaylist(api, newTracks, albumType);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}, albumType.toString());
	}
	
	/**
	 * Search for new releases of the given album type
	 * 
	 * @param appearsOn
	 * @param string
	 * @param followedArtists
	 * @throws Exception 
	 */
	private List<List<TrackSimplified>> crawl(List<Artist> followedArtists, AlbumType albumType) {
		try {
			// The process for new album searching is always the same chain of tasks:
			// 1. Fetch all raw album IDs of those artists
			// 2. Filter out all albums that were already stored in the DB
			// 3. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
			//    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
			//    b. Filter out all albums not released in the lookbackDays range (default: 3 days)
			// 4. Get the songs IDs of the remaining (new) albums
			List<String> albumIds = AlbumRequests.getAlbumsIdsByArtists(api, followedArtists, albumType);
			albumIds = SpotifyBotDatabase.filterNonCachedAlbumsOnly(albumIds);
			List<List<TrackSimplified>> newSongs = new ArrayList<>();
			if (!albumIds.isEmpty()) {
				List<Album> albums = AlbumRequests.convertAlbumIdsToFullAlbums(api, albumIds);
				albums = OfflineRequests.filterNewAlbumsOnly(albums, Config.getInstance().getLookbackDays());
				BotUtils.sortAlbums(albums);
				newSongs = TrackRequests.getSongIdsByAlbums(api, albums);
			}
			
			// Store the album IDs to the DB to prevent them from getting added a second time
			// This happens even if no new songs are added, because it will significantly speed up the future search processes
			SpotifyBotDatabase.storeAlbumIDsToDB(albumIds);
			
			// Return the found songs
			return newSongs;			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private List<List<TrackSimplified>> intelligentAppearsOnSearch(List<Artist> followedArtists) {
		try {
			// The process is very similar to the default crawler:
			// 1. Taking the followed artists, fetch all album IDs of releases that have the "appears_on" tag
			// 2. These albums are are filtered as normal (step 2+3 above)
			// 3. Then, filter only the songs of the remaining releases that have at least one followed artist as contributor
			// 4. Add the remaining songs to the adding queue
			List<String> extraAlbumIds = AlbumRequests.getAlbumsIdsByArtists(api, followedArtists, AlbumType.APPEARS_ON);
			extraAlbumIds = SpotifyBotDatabase.filterNonCachedAlbumsOnly(extraAlbumIds);
			List<List<TrackSimplified>> newAppearsOn = new ArrayList<>();
			if (!extraAlbumIds.isEmpty()) {
				List<Album> extraAlbums = AlbumRequests.convertAlbumIdsToFullAlbums(api, extraAlbumIds);
				extraAlbums = OfflineRequests.filterNewAlbumsOnly(extraAlbums, Config.getInstance().getLookbackDays());
				BotUtils.sortAlbums(extraAlbums);
				newAppearsOn = TrackRequests.findFollowedArtistsSongsOnAlbums(api, extraAlbums, followedArtists);
			}
	
			// Store the album IDs to the DB to prevent them from getting added a second time
			// This happens even if no new songs are added, because it will significantly speed up the future search processes
			SpotifyBotDatabase.storeAlbumIDsToDB(extraAlbumIds);
	
			// Return the found songs
			return newAppearsOn;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
