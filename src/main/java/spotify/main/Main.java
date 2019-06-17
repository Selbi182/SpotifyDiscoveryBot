package spotify.main;

import java.io.IOException;
import java.util.logging.Logger;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;

public class Main {

	public static void main(String[] args) {
		
		Config config = null;
		// Init
		try {
			config = new Config();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		if (config.isRunOnlyOnce()) {
			runBotOnce(config);
		} else {
			runBotInfinite(config);
		}
	}
	
	private static void runBotInfinite(Config config) {
		try {
			// Init
			Logger log = config.getLog();
			log.info("=== Spotify Discovery Bot ===");

			// Main loop
			boolean isRunning = true;
			while (isRunning) {
				log.info("-----");

				// Start a fresh bot instance in a separate thread with the given config
				Thread bot = new Thread(new SpotifyDiscoveryBot(config));
				bot.start();

				// Sleep thread for the specified amount of minutes
				Thread.sleep(config.getSleepMillis());

				// Fallback when the previous bot instance crashed
				if (bot.isAlive()) {
					bot.interrupt();
					log.severe("Previous bot instance didn't finish in time and will forcibly killed!");
				} else {
					log.info("Sleeping. Next check in " + config.getSleepMinutes() + " minutes...");
				}
			}
		} catch (IOException | SpotifyWebApiException | InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (config != null && config.getLog() != null) {
				config.closeLogger();
			}
		}
	}
	
	private static void runBotOnce(Config config) {
		try {
			// Init
			Logger log = config.getLog();
			
			// Start a fresh bot instance in a separate thread with the given config
			Thread bot = new Thread(new SpotifyDiscoveryBot(config));
			bot.start();

			// Wait for the bot to do its thing
			bot.join(config.getSleepMillis());
			
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
