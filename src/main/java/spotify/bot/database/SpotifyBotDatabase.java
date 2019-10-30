package spotify.bot.database;

import static spotify.bot.util.Constants.DB_FILE_NAME;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

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
	 * @param albumsSimplified
	 * @return
	 * @throws SQLException 
	 */
	public List<AlbumSimplified> filterNonCachedAlbumsOnly(List<AlbumSimplified> albumsSimplified) throws IOException, SQLException {
		ResultSet rs = fullTable(Constants.TABLE_ALBUM_CACHE);
		Map<String, AlbumSimplified> filteredAlbums = new ConcurrentHashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			filteredAlbums.put(as.getId(), as);
		}
		while (rs.next()) {
			filteredAlbums.remove(rs.getString(Constants.COL_ALBUM_IDS));
		}
		return filteredAlbums.values().stream().collect(Collectors.toList());
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
	 * @param strings
	 * @param table
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void storeStringsToTableColumn(Collection<String> strings, String table, String column) throws SQLException {
		if (table != null && column != null && strings != null && !strings.isEmpty()) {
			Statement statement = connection.createStatement();
			for (String s : strings) {
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
	 * Unset the given timestamp column to the current date
	 * 
	 * @param column
	 * @throws SQLException
	 */
	public synchronized void unsetUpdateStore(String type) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = null WHERE %s = '%s';",
			Constants.TABLE_UPDATE_STORE,
			Constants.COL_LAST_UPDATED_TIMESTAMP,
			Constants.COL_TYPE,
			type
		));
		statement.executeUpdate(String.format("UPDATE %s SET %s = null WHERE %s = '%s';",
			Constants.TABLE_UPDATE_STORE,
			Constants.COL_LAST_UPDATED_TIMESTAMP,
			Constants.COL_TYPE,
			type
		));
	}

	/**
	 * Update the update store's given timestamp and unset the song count
	 * 
	 * @param type
	 * @throws SQLException
	 */
	public synchronized void refreshUpdateStore(String type) throws SQLException {
		unsetUpdateStore(type);
		refreshUpdateStore(type, null);
	}
	
	/**
	 * Update the update store's given timestamp and set the song count
	 * 
	 * @param type
	 * @param addedSongs
	 * @throws SQLException
	 */
	public synchronized void refreshUpdateStore(String type, Integer addedSongs) throws SQLException {
		Statement statement = connection.createStatement();
		if (type != null) {
			statement.executeUpdate(String.format("UPDATE %s SET %s = strftime('%%s', 'now') * 1000 WHERE %s = '%s';",
				Constants.TABLE_UPDATE_STORE,
				Constants.COL_LAST_UPDATED_TIMESTAMP,
				Constants.COL_TYPE,
				type
			));
		}
		if (addedSongs != null) {
			statement.executeUpdate(String.format("UPDATE %s SET %s = %d WHERE %s = '%s';",
				Constants.TABLE_UPDATE_STORE,
				Constants.COL_LAST_UPDATE_SONG_COUNT,
				addedSongs,
				Constants.COL_TYPE,
				type
			));
		}
	}

	/**
	 * Convenience method to read and cache the album IDs of the given list of albums
	 * 
	 * @param albumsSimplified
	 * @throws SQLException 
	 */
	public void cacheAlbumIds(List<AlbumSimplified> albumsSimplified) throws SQLException {
		List<String> albumIds = albumsSimplified.stream().map(AlbumSimplified::getId).collect(Collectors.toList());
		storeStringsToTableColumn(albumIds, Constants.TABLE_ALBUM_CACHE, Constants.COL_ALBUM_IDS);
	}
}
