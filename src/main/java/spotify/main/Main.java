package spotify.main;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import com.wrapper.spotify.enums.AlbumType;

import spotify.bot.Config;
import spotify.bot.api.requests.UserInfoRequests;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.factory.CrawlThreadFactory;
import spotify.bot.factory.CrawlThreadFactory.CrawlThreadBuilder;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class Main {

	/**
	 * Own file location to read and write files in the same folder as the JAR
	 */
	public final static File OWN_LOCATION = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath()).getAbsoluteFile();
	
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
			Config.getInstance().closeLogger();
			SpotifyBotDatabase.getInstance().closeConnection();
		}
	}

	/**
	 * Main bot controlling instance for the sub-threads respective to each possible album type
	 */
	static class SpotifyDiscoveryBot implements Runnable {
		@Override
		public void run() {
			try {
				// Set up heavy instances from the get-go (mostly to make debugging easier)
				Config.getInstance();
				SpotifyBotDatabase.getInstance();
				Calendar.getInstance();
				
				// Fetch all followed artists of the user
				final List<String> followedArtists = UserInfoRequests.getFollowedArtistsIds();

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
