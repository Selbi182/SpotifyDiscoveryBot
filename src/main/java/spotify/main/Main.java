package spotify.main;

import spotify.bot.Config;
import spotify.bot.SpotifyDiscoveryBot;
import spotify.bot.util.Constants;

public class Main {

	public static void main(String[] args) {	
		try {
			runBotOnce();
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
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
}
