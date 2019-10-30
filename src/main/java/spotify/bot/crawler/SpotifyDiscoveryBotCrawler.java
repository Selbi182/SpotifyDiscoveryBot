package spotify.bot.crawler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.Config;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.AlbumTrackPair;
import spotify.bot.util.BotUtils;

public class SpotifyDiscoveryBotCrawler implements Runnable {
	private List<AlbumType> albumTypes;
	private Map<AlbumType, Integer> songsAddedPerAlbumTypes;

	/**
	 * Creates an idle crawling instance with 0 set songs per album type
	 */
	public SpotifyDiscoveryBotCrawler(List<AlbumType> albumTypes) {
		this.albumTypes = albumTypes;
		this.songsAddedPerAlbumTypes = new ConcurrentHashMap<>();
		albumTypes.stream().forEach(at -> {
			songsAddedPerAlbumTypes.put(at, 0);
		});
	}

	/**
	 * Instantiate a new thread with the set album types as thread name and start it
	 * 
	 * @return
	 */
	public Thread buildAndStart() {
		Thread t = new Thread(this, albumTypes.toString());
		t.start();
		return t;
	}

	@Override
	public void run() {
		try {
			runCrawler();
		} catch (Exception e) {
			Config.logStackTrace(e);
		}
	}
	
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
	private void runCrawler() throws Exception {
		List<String> followedArtists = UserInfoRequests.getFollowedArtistsIds();
		List<AlbumSimplified> albumsSimplified = AlbumRequests.getAlbumsOfArtists(followedArtists, albumTypes);
		albumsSimplified = SpotifyBotDatabase.getInstance().filterNonCachedAlbumsOnly(albumsSimplified);
		if (!albumsSimplified.isEmpty()) {
			Map<AlbumType, List<AlbumSimplified>> albumsByType = OfflineRequests.categorizeAlbumsByAlbumGroup(albumsSimplified, albumTypes);
			albumsByType = OfflineRequests.filterNewAlbumsOnly(albumsByType, Config.getInstance().getLookbackDays());
			if (!albumsByType.isEmpty()) {
				Map<AlbumType, List<AlbumTrackPair>> newSongsByType = TrackRequests.getSongIdsByAlbums(albumsByType, followedArtists);
				newSongsByType = OfflineRequests.mergeOnIdenticalPlaylists(newSongsByType, albumTypes);
				newSongsByType.entrySet().stream().forEach(entry -> {
					AlbumType albumType = entry.getKey();
					List<AlbumTrackPair> albumTrackPairs = entry.getValue();
					if (!albumTrackPairs.isEmpty()) {
						List<AlbumTrackPair> sortedAlbums = OfflineRequests.sortReleases(albumTrackPairs);
						int addedSongsCount = PlaylistRequests.addSongsToPlaylist(sortedAlbums, albumType);
						songsAddedPerAlbumTypes.put(albumType, addedSongsCount);								
						PlaylistRequests.timestampPlaylistsAndSetNotifiers(albumType, addedSongsCount);						
					}
				});									
				BotUtils.logResults(songsAddedPerAlbumTypes);
			}
		}
		SpotifyBotDatabase.getInstance().cacheAlbumIds(albumsSimplified);
	}
}
