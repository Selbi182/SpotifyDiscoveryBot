package spotify.bot.factory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.Config;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class CrawlThreadFactory {
	private enum CrawlType {
		DEFAULT,
		SIMPLE_APPEARS_ON,
		INTELLIGENT_APPEARS_ON
	}
	
	private static CrawlThreadFactory factory;
	
	/**
	 * Static calls only
	 */
	private CrawlThreadFactory() {
		getInstance();
	}
	
	protected CrawlThreadFactory getInstance() {
		if (factory == null) {
			factory = new CrawlThreadFactory();
		}
		return factory;
	}
	
	/////////////////////
	
	public static CrawlThreadBuilder albumSingleCompilation(List<String> followedArtists) {
		return new CrawlThreadBuilder(followedArtists, Arrays.asList(AlbumType.ALBUM, AlbumType.SINGLE, AlbumType.COMPILATION));
	}

	public static CrawlThreadBuilder appearsOn(List<String> followedArtists, boolean intelligentAppearsOnSearch) {
		CrawlThreadBuilder tAppearsOnBuilder = new CrawlThreadBuilder(followedArtists, Arrays.asList(AlbumType.APPEARS_ON));
		if (intelligentAppearsOnSearch) {
			tAppearsOnBuilder.setCrawlType(CrawlType.INTELLIGENT_APPEARS_ON);
		} else {
			tAppearsOnBuilder.setCrawlType(CrawlType.SIMPLE_APPEARS_ON);
		}
		return tAppearsOnBuilder;
	}
	
	////////////////////
	
	/**
	 * Private class to streamline crawl thread building
	 */
	public static class CrawlThreadBuilder {		
		private List<String> followedArtists;
		private List<AlbumType> albumTypes;
		private CrawlType crawlType;
		
		public CrawlThreadBuilder(List<String> followedArtists, List<AlbumType> albumTypes) {
			this.followedArtists = followedArtists;
			this.albumTypes = albumTypes;
			this.crawlType = CrawlType.DEFAULT;
		}

		public void setCrawlType(CrawlType crawlType) {
			this.crawlType = crawlType;
		}
		
		public Thread buildAndStart() {
			Thread t = new Thread(createCrawler(), albumTypes.toString());
			t.start();
			return t;
		}

		private Runnable createCrawler() {
			if (BotUtils.anyNotNull(followedArtists, albumTypes)) {
				throw new NullPointerException("Not all properties are set to create crawl thread");
			}
			return crawlerRunnable();
		}		


		/**
		 * Creates the main logic of the bot crawler in a runnable Java thread. The process for new album searching is always the same chain of tasks:
	 	 * 1. Fetch all raw album IDs of those artists
	 	 * 2. Filter out all albums that were already stored in the DB
	 	 * 3. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
	     *    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
	 	 * 	  b. Filter out all albums not released in the lookbackDays range (default: 3 days)
	 	 * 4. Get the songs IDs of the remaining (new) albums using the given crawler
	     * 
	 	 * Store the album IDs to the DB to prevent them from getting added a second time
	 	 * This happens even if no new songs are added, because it will significantly speed up the future search processes
	 	 * 
		 * @return
		 */
		private Runnable crawlerRunnable() {
			return new Runnable() {
				@Override
				public void run() {
					try {
						runCrawler();
					} catch (Exception e) {
						Config.logStackTrace(e);
					}
				}
				
				private void runCrawler() throws Exception {
					BotUtils.removeUnsetAlbumTypes(albumTypes);

					List<String> albumIds = AlbumRequests.getAlbumsIdsByArtists(followedArtists, albumTypes);
					albumIds = SpotifyBotDatabase.getInstance().filterNonCachedAlbumsOnly(albumIds);
				
					Map<AlbumType, Integer> songsAddedPerAlbumType = new HashMap<>();
					int newSongsCount = 0;
					if (!albumIds.isEmpty()) {
						List<Album> albums = AlbumRequests.convertAlbumIdsToFullAlbums(albumIds);
						albums = OfflineRequests.filterNewAlbumsOnly(albums, Config.getInstance().getLookbackDays());
						if (!albums.isEmpty()) {
							BotUtils.sortAlbums(albums);
							Map<AlbumType, List<Album>> albumsByAlbumType = OfflineRequests.categorizeAlbumsByAlbumType(albums);
							Map<AlbumType, List<List<TrackSimplified>>> newSongs = getNewSongsByCrawlType(albumsByAlbumType, followedArtists);
							newSongs.entrySet().parallelStream().forEach(entry -> {
								int addedSongsCount = PlaylistRequests.addSongsToPlaylist(entry.getValue(), entry.getKey());
								songsAddedPerAlbumType.put(entry.getKey(), addedSongsCount);								
							});									
						}
					}
					
					PlaylistRequests.timestampPlaylistsAndSetNotifiers(songsAddedPerAlbumType);					
					SpotifyBotDatabase.getInstance().storeStringsToTableColumn(albumIds, Constants.TABLE_ALBUM_CACHE, Constants.COL_ALBUM_IDS);
				}
			};
		}
		
		private Map<AlbumType, List<List<TrackSimplified>>> getNewSongsByCrawlType(Map<AlbumType, List<Album>> albumsByAlbumType, List<String> followedArtists) {
			switch (crawlType) {
				case INTELLIGENT_APPEARS_ON:
					albumsByAlbumType = BotUtils.flattenToSingleAlbumType(albumsByAlbumType, AlbumType.APPEARS_ON);
					return TrackRequests.intelligentAppearsOnSearch(albumsByAlbumType.get(AlbumType.APPEARS_ON), new HashSet<>(followedArtists));
				case SIMPLE_APPEARS_ON:
					albumsByAlbumType = BotUtils.flattenToSingleAlbumType(albumsByAlbumType, AlbumType.APPEARS_ON);
				default:
					return TrackRequests.getSongIdsByAlbums(albumsByAlbumType);					
			}
		}
	}
}
