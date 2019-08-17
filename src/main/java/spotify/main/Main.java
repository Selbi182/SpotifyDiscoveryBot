package spotify.main;

import java.io.IOException;
import java.util.logging.Logger;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;

public class Main {

	public static void main(String[] args) {		
		// Init
		Config config = null;
		try {
			config = new Config();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		runBotOnce(config);
	}
	
	private static void runBotOnce(Config config) {
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
		} catch (IOException | SpotifyWebApiException | InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (config != null && config.getLog() != null) {
				config.closeLogger();
			}
		}
	}
}
