package spotify.main;

import java.util.logging.Logger;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;
import spotify.util.Constants;

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
			Logger log = config.getLog();
			Thread bot = new Thread(new SpotifyDiscoveryBot(config), SpotifyDiscoveryBot.class.getSimpleName());
			bot.start();
			bot.join(Constants.BOT_TIMEOUT);
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
