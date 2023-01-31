package spotify.bot.config.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Repository;

import spotify.SpotifyDiscoveryBot;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.util.BotUtils;

@Repository
public class DiscoveryDatabase {

	// Database base constants
	private final static String DB_FILE_NAME = "config/database.db";
	private final static String DB_URL_PREFIX = "jdbc:sqlite:";

	private final static String FULL_SELECT_QUERY_MASK = "SELECT * FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";

	// Instance
	private final static File WORKSPACE_LOCATION = new File(".");

	private final DiscoveryBotLogger log;
	private final DatabaseCreationService databaseCreationService;

	private String dbUrl;
	private Connection connection;

	/**
	 * Initialize the Database connection to the local database
	 */
	DiscoveryDatabase(DiscoveryBotLogger discoveryBotLogger, DatabaseCreationService databaseCreationService) {
		this.log = discoveryBotLogger;
		this.databaseCreationService = databaseCreationService;
		try {
			File dbFilePath = BotUtils.normalizeFile(getDbFilePath());
			this.dbUrl = DB_URL_PREFIX + dbFilePath.getAbsolutePath();
			log.info("Establishing SQLite database connection: " + dbFilePath.getAbsolutePath(), false);
			getConnectionInstance();
		} catch (IOException | SQLException e) {
			log.error("=== FAILED TO ESTABLISH DATABASE CONNECTION! APPLICATION IS HALTING! ===", false);
			System.exit(1);
		}
	}

	private File getDbFilePath() throws IOException {
		File alternateDatabaseFilepath = SpotifyDiscoveryBot.getAlternateDatabaseFilePath();
		if (alternateDatabaseFilepath != null && alternateDatabaseFilepath.exists()) {
			if (alternateDatabaseFilepath.canRead()) {
				return alternateDatabaseFilepath;
			}
			throw new IOException("Could not access ALTERNATE database (file is locked)!");
		}

		File workingDirectoryDatabaseFilepath = new File(WORKSPACE_LOCATION, DB_FILE_NAME);
		if (workingDirectoryDatabaseFilepath.exists()) {
			if (workingDirectoryDatabaseFilepath.canRead()) {
				return workingDirectoryDatabaseFilepath;
			}
			throw new IOException("Could not access WORKDIR database (file is locked)!");
		} else {
			log.warning("Database file does not exist and will be created automatically", false);
		}
		return workingDirectoryDatabaseFilepath;
	}

	//////////////

	/**
	 * Returns the Database connection instance. May create a new one if not already
	 * set
	 */
	private Connection getConnectionInstance() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(dbUrl);
			databaseCreationService.createTables(connection);
		}
		return connection;
	}

	/**
	 * Close the SQL connection if it's still live
	 */
	@PreDestroy
	private void closeConnection() throws SQLException {
		if (connection != null) {
			connection.close();
		}
	}

	/**
	 * Creates a new Database statement. May create a new database instance.
	 */
	private Statement createStatement() throws SQLException {
		return getConnectionInstance().createStatement();
	}

	//////////////

	/**
	 * Fetch an entire table result set
	 */
	ResultSet selectAll(String tableName) throws SQLException {
		Statement statement = createStatement();
		ResultSet resultSet = statement.executeQuery(String.format(FULL_SELECT_QUERY_MASK, tableName));
		statement.closeOnCompletion();
		return resultSet;
	}

	/**
	 * Adds all given strings to the specified table's specified column
	 */
	synchronized void insertAll(Collection<String> strings, String table, String column) throws SQLException {
		Statement statement = createStatement();
		if (table != null && column != null && strings != null && !strings.isEmpty()) {
			for (String s : strings) {
				statement.executeUpdate(String.format(INSERT_QUERY_MASK, table, column, s));
			}
		}
		statement.execute("vacuum;");
		statement.closeOnCompletion();
	}
}
