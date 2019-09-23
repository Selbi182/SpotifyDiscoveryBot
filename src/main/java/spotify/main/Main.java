package spotify.main;

import java.util.List;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Artist;

import spotify.bot.Config;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.factory.CrawlThreadFactory;
import spotify.bot.factory.CrawlThreadFactory.CrawlThreadBuilder;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class Main {

	/**
	 * Main entry point of the bot
	 * 
	 * @param args aren't supported yet
	 */
	public static void main(String[] args) {
		try {
			runBotOnce();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Run the bot once and exit the program upon completion or error
	 * 
	 * @throws Exception
	 */
	private static void runBotOnce() throws Exception {
		try {
			Thread bot = new Thread(new SpotifyDiscoveryBot(), SpotifyDiscoveryBot.class.getSimpleName());
			bot.start();
			bot.join(Constants.BOT_TIMEOUT);
			if (bot.isAlive()) {
				bot.interrupt();
				Config.log().severe("Bot instance didn't finish in time and will forcibly killed!");
			}
		} finally {
			if (Config.getInstance() != null && Config.log() != null) {
				Config.getInstance().closeLogger();
			}
		}
	}

	/**
	 * Main bot controlling instance for the sub-threads respective to each possible album type
	 */
	static class SpotifyDiscoveryBot implements Runnable {
		@Override
		public void run() {
			try {
				// Fetch all followed artists of the user
				final List<Artist> followedArtists = UserInfoRequests.getFollowedArtists();

				// Set up the crawl threads and run them
				Thread tAlbum = CrawlThreadFactory.crawlThread(followedArtists, AlbumType.ALBUM).buildAndStart();
				Thread tSingle = CrawlThreadFactory.crawlThread(followedArtists, AlbumType.SINGLE).buildAndStart();
				Thread tCompilation = CrawlThreadFactory.crawlThread(followedArtists, AlbumType.COMPILATION).buildAndStart();
				CrawlThreadBuilder tAppearsOnBuilder = CrawlThreadFactory.crawlThread(followedArtists, AlbumType.APPEARS_ON);
				if (Config.getInstance().isIntelligentAppearsOnSearch()) {
					tAppearsOnBuilder.setCrawler(CrawlThreadFactory.INTELLIGENT_SEARCH_CRAWLER);
				}
				Thread tAppearsOn = tAppearsOnBuilder.buildAndStart();

				// Wait for them all to finish
				BotUtils.joinAll(tAlbum, tSingle, tCompilation, tAppearsOn);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
