package spotify;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpotifyDiscoveryBot {
	/**
	 * If enabled, releases are neither added to playlist nor cached. New
	 * notifications markers are still set though. (This flag is controlled by the
	 * presence of a file called <code>DEV_MODE</code> in the working directory.)
	 */
	public static final boolean DEVELOPER_MODE;
	static {
		DEVELOPER_MODE = new File("./DEV_MODE").exists();
		if (DEVELOPER_MODE) {
			System.out.println("===========================================================================");
			System.out.println(">>> DEVELOPER MODE -- releases will NOT be cached or added to playlists <<<");
			System.out.println("===========================================================================");
		}
	}

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
}
