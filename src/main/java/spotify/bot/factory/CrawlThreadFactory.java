package spotify.bot.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.Config;
import spotify.bot.api.requests.AlbumRequests;
import spotify.bot.api.requests.OfflineRequests;
import spotify.bot.api.requests.PlaylistRequests;
import spotify.bot.api.requests.TrackRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;

public class CrawlThreadFactory {
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
	
	public final static BiFunction<List<Album>, List<Artist>, List<List<TrackSimplified>>> GENERIC_CRAWLER = (albums, artists) -> TrackRequests.getSongIdsByAlbums(albums);
	public final static BiFunction<List<Album>, List<Artist>, List<List<TrackSimplified>>> INTELLIGENT_SEARCH_CRAWLER = (albums, artists) -> TrackRequests.findFollowedArtistsSongsOnAlbums(albums, artists);
	
	/**
	 * Create a Thread for the most common crawl operations
	 * 
	 * @param followedArtists
	 * @param playlistId
	 * @param albumType
	 * @return
	 */
	public static CrawlThreadBuilder crawlThread(List<Artist> followedArtists, AlbumType albumType) {
		CrawlThreadBuilder builder = new CrawlThreadBuilder();
		builder.setFollowedArtists(followedArtists);
		builder.setAlbumType(albumType);
		builder.setCrawler(GENERIC_CRAWLER);
		return builder;
	}
	
	/**
	 * Creates a CrawlThreadBuilder with the specified crawler
	 * 
	 * @param followedArtists
	 * @param albumType
	 * @param crawler
	 * @return
	 */
	public static CrawlThreadBuilder customCrawlThread(List<Artist> followedArtists, AlbumType albumType, BiFunction<List<Album>, List<Artist>, List<List<TrackSimplified>>> crawler) {
		CrawlThreadBuilder builder = new CrawlThreadBuilder();
		builder.setFollowedArtists(followedArtists);
		builder.setAlbumType(albumType);
		builder.setCrawler(crawler);
		return builder;
	}
	
	/**
	 * Private class to streamline crawl thread building
	 */
	public static class CrawlThreadBuilder {
		private List<Artist> followedArtists;
		private AlbumType albumType;
		private BiFunction<List<Album>, List<Artist>, List<List<TrackSimplified>>> crawler;

		private CrawlThreadBuilder() {}

		public Thread buildAndStart() {
			Thread t = new Thread(createCrawler(), albumType.toString());
			t.start();
			return t;
		}

		private Runnable createCrawler() {
			if (BotUtils.anyNotNull(followedArtists, albumType)) {
				throw new NullPointerException("Not all properties are set to create crawl thread");
			}
			if (crawler == null) {
				crawler = GENERIC_CRAWLER;
			}
			return new Runnable() {
				@Override
				public void run() {
					String playlistId = BotUtils.getPlaylistIdByType(albumType);
					if (BotUtils.isPlaylistSet(playlistId)) {
						try {
							// The process for new album searching is always the same chain of tasks:
							// 1. Fetch all raw album IDs of those artists
							// 2. Filter out all albums that were already stored in the DB
							// 3. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
							//	  a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
							// 	  b. Filter out all albums not released in the lookbackDays range (default: 3 days)
							// 4. Get the songs IDs of the remaining (new) albums using the given crawler
							List<String> albumIds = AlbumRequests.getAlbumsIdsByArtists(followedArtists, albumType);
							albumIds = SpotifyBotDatabase.getInstance().filterNonCachedAlbumsOnly(albumIds);
							List<List<TrackSimplified>> newSongs = new ArrayList<>();
							if (!albumIds.isEmpty()) {
								List<Album> albums = AlbumRequests.convertAlbumIdsToFullAlbums(albumIds);
								albums = OfflineRequests.filterNewAlbumsOnly(albums, Config.getInstance().getLookbackDays());
								if (!albums.isEmpty()) {
									BotUtils.sortAlbums(albums);
									newSongs = crawler.apply(albums, followedArtists);									
								}
							}

							// Store the album IDs to the DB to prevent them from getting added a second time
							// This happens even if no new songs are added, because it will significantly speed up the future search processes
							SpotifyBotDatabase.getInstance().storeAlbumIDsToDB(albumIds);

							// Add songs to playlist as very last step to prevent duplication in case of a crash
							PlaylistRequests.addSongsToPlaylist(newSongs, albumType);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			};
		}

		public void setFollowedArtists(List<Artist> followedArtists) {
			this.followedArtists = followedArtists;
		}

		public void setAlbumType(AlbumType albumType) {
			this.albumType = albumType;
		}

		public void setCrawler(BiFunction<List<Album>, List<Artist>, List<List<TrackSimplified>>> crawler) {
			this.crawler = crawler;
		}
	}
}
