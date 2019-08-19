package spotify.main;

import java.util.logging.Logger;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;

public class Main {

	public static void main(String[] args) {	
		try {
			Config config = new Config();
			runBotOnce(config);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	private static void runBotOnce(Config config) throws Exception {
		try {
			// Init
			Logger log = config.getLog();
			
			// Start a fresh bot instance in a separate thread with the given config
			Thread bot = new Thread(new SpotifyDiscoveryBot(config));
			bot.start();

			// Wait for the bot to do its thing
			bot.join();
			
			// Fallback when the previous bot instance crashed
			if (bot.isAlive()) {
				bot.interrupt();
				log.severe("Bot instance didn't finish in time and will forcibly killed!");
			}	
		} finally {
			if (config != null && config.getLog() != null) {
				config.closeLogger();
			}
		}
	}
}
