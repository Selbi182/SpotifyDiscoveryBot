package spotify;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

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
	 * @param args
	 *            [0] the location of the SQLite database; if not set, will be
	 *            treated as "database.sql" of the current working directory context
	 *            of the application
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			alternateDatabaseFile = new File(args[0]);
		}
		SpringApplication.run(Main.class, args);
	}
}
