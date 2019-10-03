package spotify.bot.database;

import static spotify.bot.util.Constants.DB_FILE_NAME;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spotify.bot.util.Constants;
import spotify.main.Main;

public class SpotifyBotDatabase {

	private final static String SINGLE_SELECT_QUERY_MASK = "SELECT * FROM %s LIMIT 1;";
	private final static String FULL_SELECT_QUERY_MASK = "SELECT * FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";
	private final static String DELETE_QUERY_MASK = "DELETE FROM %s WHERE %s = '%s'";

	private static SpotifyBotDatabase instance;
	
	private final String dbUrl;
	private Connection connection;
	
	private SpotifyBotDatabase() throws IOException, SQLException {
		File dbFilePath = new File(Main.OWN_LOCATION, DB_FILE_NAME);
		if (!dbFilePath.canRead()) {
			throw new IOException("Could not read .db file! Expected location: " + dbFilePath.getAbsolutePath());
		}
		this.dbUrl = Constants.DB_URL_PREFIX + dbFilePath.getAbsolutePath();
		
		// Connect
		connection = DriverManager.getConnection(dbUrl);
	}
	
	/**
	 * Fetch the current databse connection or instantiate it
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public static SpotifyBotDatabase getInstance() throws IOException, SQLException {
		if (instance == null) {
			instance = new SpotifyBotDatabase();
		}
		return instance;
	}
	
	/**
	 * Close the SQL connection if it's still live
	 * 
	 * @throws SQLException
	 */
	public void closeConnection() throws SQLException {
		if (connection != null) {
			connection.close();
		}
	}
	
	/**
	 * Fetch the single-row result set of the given table
	 * 
	 * @throws IOException 
	 * @throws SQLException 
	 */
	public ResultSet singleRow(String tableName) throws SQLException, IOException {
		Statement statement = connection.createStatement();
		ResultSet rs = statement.executeQuery(String.format(SINGLE_SELECT_QUERY_MASK, tableName));
		if (!rs.next()) {
			throw new SQLException("Table " + tableName + " not found or empty!");
		}
		return rs;
	}
	
	/**
	 * Fetch an entire table result set
	 * 
	 * @param tableName
	 * @return
	 * @throws SQLException 
	 */
	public ResultSet fullTable(String tableName) throws SQLException {
		Statement statement = connection.createStatement();
		ResultSet rs = statement.executeQuery(String.format(FULL_SELECT_QUERY_MASK, tableName));
		return rs;
	}
	
	/**
	 * Filter out all album IDs not currently present in the database
	 * 
	 * @param allAlbums
	 * @return
	 * @throws SQLException 
	 */
	public List<String> filterNonCachedAlbumsOnly(List<String> allAlbums) throws IOException, SQLException {
		ResultSet rs = fullTable(Constants.TABLE_ALBUM_CACHE);
		Set<String> filteredAlbums = new HashSet<>(allAlbums);
		while (rs.next()) {
			filteredAlbums.remove(rs.getString(Constants.COL_ALBUM_IDS));
		}
		return new ArrayList<>(filteredAlbums);
	}

	/**
	 * Update every given column's value in the given table by a new value
	 * 
	 * @param table
	 * @param column
	 * @param newValue
	 * @throws SQLException
	 */
	public synchronized void updateColumnInTable(String table, String column, String newValue) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = '%s';", table, column, newValue));
	}

	/**
	 * Adds all given strings to the specified table's specified column
	 * 
	 * @param stringsToAdd
	 * @param table
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void storeStringsToTableColumn(Collection<String> stringsToAdd, String table, String column) throws SQLException {
		if (table != null && column != null && stringsToAdd != null && !stringsToAdd.isEmpty()) {
			Statement statement = connection.createStatement();
			for (String s : stringsToAdd) {
				statement.executeUpdate(String.format(INSERT_QUERY_MASK, table, column, s));
			}
		}
	}

	/**
	 * Removes all given strings from the specified table's specified column
	 * 
	 * @param stringsToRemove
	 * @param table
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void removeStringsFromTableColumn(Collection<String> stringsToRemove, String table, String column) throws SQLException {
		if (table != null && column != null && stringsToRemove != null && !stringsToRemove.isEmpty()) {
			Statement statement = connection.createStatement();
			for (String s : stringsToRemove) {
				statement.execute(String.format(DELETE_QUERY_MASK, table, column, s));
			}
		}
	}

	/**
	 * Set the given timestamp column to the current date
	 * 
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void updateTimestamp(String column) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = strftime('%%s', 'now') * 1000;", Constants.TABLE_TIMESTAMP_STORE, column));
	}
	
	/**
	 * Unset the given timestamp column to the current date
	 * 
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void unsetTimestamp(String column) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = null;", Constants.TABLE_TIMESTAMP_STORE, column));
	}
}
