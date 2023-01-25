package spotify.bot.config.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Repository;

import spotify.SpotifyDiscoveryBot;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.util.BotUtils;

@Repository
public class DiscoveryDatabase {

	// Database base constants
	private final static String DB_FILE_NAME = "database.db";
	private final static String DB_URL_PREFIX = "jdbc:sqlite:";

	// Database query masks
	private final static String SINGLE_SELECT_QUERY_MASK = "SELECT * FROM %s LIMIT 1";
	private final static String FULL_SELECT_QUERY_MASK = "SELECT * FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";
	private final static String DELETE_BASE_QUERY_MASK = "DELETE FROM %s";
	private final static String UPDATE_QUERY_MASK = "UPDATE %s SET %s = '%s'";
	private final static String UPDATE_WITH_CONDITION_QUERY_MASK = "UPDATE %s SET %s = %s WHERE %s = '%s'";

	// Instance
	private final static File WORKSPACE_LOCATION = new File(".");

	private final DiscoveryBotLogger log;

	private String dbUrl;
	private Connection connection;

	DiscoveryDatabase(DiscoveryBotLogger discoveryBotLogger) {
		this.log = discoveryBotLogger;
	}

	/**
	 * Initialize the Database connection to the local <code>database.db</code>
	 * file.
	 */
	@PostConstruct
	private void init() {
		try {
			File dbFilePath = BotUtils.normalizeFile(getDbFilePath());
			this.dbUrl = DB_URL_PREFIX + dbFilePath.getAbsolutePath();
			log.error("Establishing SQLite database connection: " + dbFilePath.getAbsolutePath(), false);
			getConnectionInstance();
		} catch (IOException | SQLException e) {
			log.error("=== FAILED TO ESTABLISH DATABASE CONNECTION! APPLICATION IS HALTING! ===", false);
			System.exit(1);
		}
	}

	private File getDbFilePath() throws IOException {
		File alternateDatabaseFilepath = SpotifyDiscoveryBot.getAlternateDatabaseFilePath();
		if (alternateDatabaseFilepath != null) {
			if (alternateDatabaseFilepath.exists()) {
				if (alternateDatabaseFilepath.canRead()) {
					return alternateDatabaseFilepath;
				}
				throw new IOException("Could not access ALTERNATE database (file is locked)!");
			} else {
				log.warning("ALTERNATE database file path has been specified but doesn't exist!", false);
			}
		}

		File workingDirectoryDatabaseFilepath = new File(WORKSPACE_LOCATION, DB_FILE_NAME);
		if (workingDirectoryDatabaseFilepath.exists()) {
			if (workingDirectoryDatabaseFilepath.canRead()) {
				return workingDirectoryDatabaseFilepath;
			}
			throw new IOException("Could not access WORKDIR database (file is locked)!");
		}
		throw new IOException("WORKDIR database not found!");
	}

	//////////////

	/**
	 * Returns the Database connection instance. May create a new one if not already
	 * set
	 */
	private Connection getConnectionInstance() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(dbUrl);
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
	 * Fetch the single-row result set of the given table
	 */
	ResultSet selectSingle(String tableName) throws SQLException {
		return createStatement().executeQuery(String.format(SINGLE_SELECT_QUERY_MASK, tableName));
	}

	/**
	 * Fetch an entire table result set
	 */
	ResultSet selectAll(String tableName) throws SQLException {
		return createStatement().executeQuery(String.format(FULL_SELECT_QUERY_MASK, tableName));
	}

	////////////////

	/**
	 * Update every given column's value in the given table by a new value
	 */
	synchronized void update(String table, String targetColumn, String newValue) throws SQLException {
		createStatement().executeUpdate(String.format(UPDATE_QUERY_MASK, table, targetColumn, newValue));
	}

	synchronized void updateWithCondition(String table, String targetColumn, String newValue, String conditionColumn, String conditionValue) throws SQLException {
		createStatement().executeUpdate(String.format(UPDATE_WITH_CONDITION_QUERY_MASK, table, targetColumn, newValue, conditionColumn, conditionValue));
	}

	synchronized void updateNull(String table, String targetColumn, String conditionColumn, String conditionValue) throws SQLException {
		updateWithCondition(table, targetColumn, null, conditionColumn, conditionValue);
	}

	/**
	 * Adds all given strings to the specified table's specified column
	 */
	synchronized void insertAll(Collection<String> strings, String table, String column) throws SQLException {
		if (table != null && column != null && strings != null && !strings.isEmpty()) {
			for (String s : strings) {
				createStatement().executeUpdate(String.format(INSERT_QUERY_MASK, table, column, s));
			}
		}
	}

	/**
	 * Delete every entry in the given table
	 */
	synchronized void clearTable(String table) throws SQLException {
		createStatement().execute(String.format(DELETE_BASE_QUERY_MASK, table));
	}
}
