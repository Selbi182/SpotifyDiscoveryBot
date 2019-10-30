package spotify.main;

import java.io.File;
import java.util.Calendar;

import spotify.bot.Config;
import spotify.bot.crawler.SpotifyDiscoveryBotCrawler;
import spotify.bot.database.SpotifyBotDatabase;
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
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			runBotOnce();
		} catch (Exception e) {
			Config.logStackTrace(e);
		}
	}

	/**
	 * Run the bot once and exit the program upon completion or error
	 * 
	 * @throws Exception
	 */
	private static void runBotOnce() throws Exception {
		try {
			// Set up heavy instances from the get-go (mostly to make debugging easier)
			Config.getInstance();
			SpotifyBotDatabase.getInstance();
			Calendar.getInstance();

			// Start the bot with the set-by-config album types
			Thread bot = new SpotifyDiscoveryBotCrawler(BotUtils.getSetAlbumTypes()).buildAndStart();
			bot.join(Constants.BOT_TIMEOUT);
			if (bot.isAlive()) {
				bot.interrupt();
				Config.log().severe("Bot instance didn't finish in time and will be forcibly killed!");
			}
		} finally {
			Config.getInstance().closeLogger();
			SpotifyBotDatabase.getInstance().closeConnection();
		}
	}
}
