package spotify;

import java.io.IOException;
import java.util.logging.Handler;

public class Main {

	public static void main(String[] args) throws InterruptedException {
		SpotifyReleases botInstance = null;
		boolean isRunning = true;
		while (isRunning) {
			try {
				botInstance = new SpotifyReleases();
				botInstance.spotifyDiscoveryMainLoop();
			} catch (IOException e) {
				e.printStackTrace();
				isRunning = false;
			} catch (Exception e) {
				e.printStackTrace();
				if (botInstance != null) {
					SpotifyReleases.LOG.warning("Restarting the bot after " + botInstance.sleepMinutes + " minutes...");
					Thread.sleep(botInstance.sleepMillis);
				}
				isRunning = true;
			} finally {
				for (Handler h : SpotifyReleases.LOG.getHandlers()) {
					h.close();
				}
			}
		}
	}
}
