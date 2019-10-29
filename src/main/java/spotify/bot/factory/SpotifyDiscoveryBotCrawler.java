package spotify.bot.factory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.Config;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

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
	 * Creates the main logic of the bot crawler in a runnable Java thread. The process for new album searching is always the same chain of tasks:
	 * 0. Fetch all followed artists
	 * 1. Fetch all raw album IDs of those artists
	 * 2. Filter out all albums that were already stored in the DB
	 * 3. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
	 *    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
	 * 	  b. Filter out all albums not released in the lookbackDays range (default: 3 days)
	 * 4. Get the songs IDs of the remaining (new) albums using the given crawler
	 * 
	 * Store the album IDs to the DB to prevent them from getting added a second time
	 * This happens even if no new songs are added, because it will significantly speed up the future search processes
	 * </pre>
	 */
	private void runCrawler() throws Exception {
		final List<String> followedArtists = UserInfoRequests.getFollowedArtistsIds();
		
		List<AlbumSimplified> albumsSimplified = AlbumRequests.getAlbumsOfArtists(followedArtists, albumTypes);
		albumsSimplified = SpotifyBotDatabase.getInstance().filterNonCachedAlbumsOnly(albumsSimplified);
		if (!albumsSimplified.isEmpty()) {
			Map<AlbumType, List<AlbumSimplified>> albumsSimplifiedByType = OfflineRequests.categorizeAlbumsByAlbumGroup(albumsSimplified);
			Map<AlbumType, List<Album>> fullAlbums = AlbumRequests.convertAlbumIdsToFullAlbums(albumsSimplifiedByType);
			fullAlbums = OfflineRequests.filterNewAlbumsOnly(fullAlbums, Config.getInstance().getLookbackDays());
			if (!fullAlbums.isEmpty()) {
				BotUtils.sortAlbums(fullAlbums);
				Map<AlbumType, List<List<TrackSimplified>>> newSongs = TrackRequests.getSongIdsByAlbums(fullAlbums, followedArtists);
				newSongs.entrySet().parallelStream().forEach(entry -> {
					int addedSongsCount = PlaylistRequests.addSongsToPlaylist(entry.getValue(), entry.getKey());
					songsAddedPerAlbumTypes.put(entry.getKey(), addedSongsCount);								
				});									
			}
		}
		
		PlaylistRequests.timestampPlaylistsAndSetNotifiers(songsAddedPerAlbumTypes);
		
		List<String> albumIds = albumsSimplified.stream().map(AlbumSimplified::getId).collect(Collectors.toList());
		SpotifyBotDatabase.getInstance().storeStringsToTableColumn(albumIds, Constants.TABLE_ALBUM_CACHE, Constants.COL_ALBUM_IDS);
		
		BotUtils.logResults(songsAddedPerAlbumTypes);
	}
}
