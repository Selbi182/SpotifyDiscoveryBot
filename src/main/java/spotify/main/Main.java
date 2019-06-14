package spotify.main;

import java.io.IOException;
import java.util.logging.Logger;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;

public class Main {

	public static void main(String[] args) {
		Config config = null;
		try {
			// Init
			config = new Config();
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
}
