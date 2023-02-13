package spotify;

import java.io.File;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import spotify.api.SpotifyApiScopes;

@SpringBootApplication
public class SpotifyDiscoveryBot {
	/**
	 * Path to the alternate SQLite database, if any
	 */
	private static File alternateDatabaseFile = null;

	/**
	 * Return the alternate file path was given for the SQLite database that was
	 * given as parameter. <code>null</code> if none was given.
	 */
	public static File getAlternateDatabaseFilePath() {
		return alternateDatabaseFile;
	}

	/**
	 * Main entry point of the bot
	 * 
	 * @param args [0] the location of the SQLite database; if not set, will be
	 *             treated as "database.sql" of the current working directory
	 *             context of the application
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			alternateDatabaseFile = new File(args[0]);
		}

		SpringApplication.run(SpotifyDiscoveryBot.class, args);
	}

	@Component
	public static class SpotifyBigPictureScopes implements SpotifyApiScopes {

		@Override
		public List<String> requiredScopes() {
			return List.of(
					"user-read-playback-position",
					"user-read-playback-state",
					"user-read-currently-playing",
					"user-read-private"
			);
		}
	}
}