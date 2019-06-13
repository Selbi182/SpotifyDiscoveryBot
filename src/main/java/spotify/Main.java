package spotify;

import java.io.IOException;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

public class Main {

	public static void main(String[] args) throws InterruptedException {
		SpotifyReleases botInstance = null;
		boolean isRunning = true;
		while (isRunning) {
			try {
				botInstance = new SpotifyReleases();
				botInstance.spotifyDiscoveryMainLoop();
			} catch (SpotifyWebApiException e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("SpotifyWebApiException occured!");
			} catch (IOException e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("IO-exception, probably some file not found. Terminating the bot, cause you gotta deal with that...");
				isRunning = false;
			} catch (Exception e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("Unknown exception occured!");
			}
			if (isRunning && botInstance != null) {
				SpotifyReleases.LOG.warning("Restarting the bot after " + botInstance.sleepMinutes + " minutes...");
				Thread.sleep(botInstance.sleepMillis);
			}
		}
	}
}
