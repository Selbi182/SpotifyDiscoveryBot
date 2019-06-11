package spotify;

import java.io.IOException;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

public class Main {

	public static void main(String[] args) {
		SpotifyReleases botInstance;
		boolean isRunning = true;
		while (isRunning) {
			try {
				botInstance = new SpotifyReleases();
				botInstance.spotifyDiscoveryMainLoop();
			} catch (SpotifyWebApiException e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("SpotifyWebApiException occured. Restarting the bot...");
			} catch (IOException e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("IO-exception, probably some file not found. Terminating, cause you gotta deal with that...");
				isRunning = false;
			} catch (Exception e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("XXXXX Something went terribly wrong. Restarting the bot... XXXXX");
			} finally {
				botInstance = null;
			}
		}
	}
}
