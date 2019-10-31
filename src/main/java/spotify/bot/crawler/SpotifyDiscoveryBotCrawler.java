package spotify.bot.crawler;

import java.util.List;
import java.util.Map;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.Config;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.util.AlbumTrackPair;
import spotify.bot.util.BotUtils;

public class SpotifyDiscoveryBotCrawler implements Runnable {
	private List<AlbumType> albumTypes;

	/**
	 * Creates an idle crawling instance with 0 set songs per album type
	 */
	public SpotifyDiscoveryBotCrawler(List<AlbumType> albumTypes) {
		this.albumTypes = albumTypes;
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
		List<AlbumSimplified> nonCachedAlbums = AlbumRequests.getNonCachedAlbumsOfArtists(followedArtists, albumTypes);
		Map<AlbumType, Integer> songsAddedPerAlbumTypes = BotUtils.createAlbumTypeToIntegerMap(albumTypes);
		if (!nonCachedAlbums.isEmpty()) {
			Map<AlbumType, List<AlbumSimplified>> newAlbums = OfflineRequests.categorizeAndFilterAlbums(nonCachedAlbums, albumTypes);
			if (!newAlbums.isEmpty()) {
				Map<AlbumType, List<AlbumTrackPair>> newSongs = TrackRequests.getSongIdsByAlbums(newAlbums, followedArtists);
				songsAddedPerAlbumTypes = PlaylistRequests.addAllReleasesToSetPlaylists(newSongs, albumTypes);							
				BotUtils.logResults(songsAddedPerAlbumTypes);
			}
		}								
		PlaylistRequests.timestampPlaylistsAndSetNotifiers(songsAddedPerAlbumTypes);
	}
}
