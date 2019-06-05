package spotify;

public class Main {

	public static void main(String[] args) {
		SpotifyReleases botInstance;
		while (true) {
			try {
				botInstance = new SpotifyReleases();
				botInstance.spotifyDiscoveryMainLoop();
			} catch (Exception e) {
				e.printStackTrace();
				SpotifyReleases.LOG.severe("XXXXX Something went terribly wrong. Restarting the bot... XXXXX");
			} finally {
				botInstance = null;
			}
		}
	}
}
