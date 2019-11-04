package spotify.bot.config;

import static spotify.bot.util.Constants.DB_FILE_NAME;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.util.Constants;

@Repository
public class DiscoveryDatabase {
	
	@Autowired
	private BotLogger log;

	private final static String SINGLE_SELECT_QUERY_MASK = "SELECT * FROM %s LIMIT 1;";
	private final static String FULL_SELECT_QUERY_MASK = "SELECT * FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";
	private final static String DELETE_QUERY_MASK = "DELETE FROM %s WHERE %s = '%s'";
	private final static String CACHE_ALBUMS_THREAD_NAME = "Caching ALBUM IDs";
	private final static String CACHE_ARTISTS_THREAD_NAME = "Caching ARTIST IDs";
	
	private String dbUrl;
	private Connection connection;
	
	@PostConstruct
	public void init() throws IOException, SQLException {
		File dbFilePath = new File(Constants.OWN_LOCATION, DB_FILE_NAME);
		if (!dbFilePath.canRead()) {
			throw new IOException("Could not read .db file! Expected location: " + dbFilePath.getAbsolutePath());
		}
		this.dbUrl = Constants.DB_URL_PREFIX + dbFilePath.getAbsolutePath();
		
		// Connect
		connection = DriverManager.getConnection(dbUrl);
	}

	/**
	 * Close the SQL connection if it's still live
	 * 
	 * @throws SQLException
	 */
	@PreDestroy
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
	public synchronized void unsetUpdateStore(String group) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = null WHERE %s = '%s';",
			Constants.TABLE_UPDATE_STORE,
			Constants.COL_LAST_UPDATED_TIMESTAMP,
			Constants.COL_TYPE,
			group
		));
		statement.executeUpdate(String.format("UPDATE %s SET %s = null WHERE %s = '%s';",
			Constants.TABLE_UPDATE_STORE,
			Constants.COL_LAST_UPDATED_TIMESTAMP,
			Constants.COL_TYPE,
			group
		));
	}

	/**
	 * Update the update store's given timestamp and unset the song count
	 * 
	 * @param group
	 * @throws SQLException
	 */
	public synchronized void refreshUpdateStore(String group) throws SQLException {
		unsetUpdateStore(group);
		refreshUpdateStore(group, null);
	}
	
	/**
	 * Update the update store's given timestamp and set the song count
	 * 
	 * @param group
	 * @param addedSongs
	 * @throws SQLException
	 */
	public synchronized void refreshUpdateStore(String group, Integer addedSongs) throws SQLException {
		Statement statement = connection.createStatement();
		if (group != null) {
			statement.executeUpdate(String.format("UPDATE %s SET %s = strftime('%%s', 'now') * 1000 WHERE %s = '%s';",
				Constants.TABLE_UPDATE_STORE,
				Constants.COL_LAST_UPDATED_TIMESTAMP,
				Constants.COL_TYPE,
				group
			));
		}
		if (addedSongs != null) {
			statement.executeUpdate(String.format("UPDATE %s SET %s = %d WHERE %s = '%s';",
				Constants.TABLE_UPDATE_STORE,
				Constants.COL_LAST_UPDATE_SONG_COUNT,
				addedSongs,
				Constants.COL_TYPE,
				group
			));
		}
	}

	/**
	 * Cache the album IDs of the given list of albums in a separate thread
	 * 
	 * @param albumsSimplified
	 * @throws SQLException 
	 */
	public void cacheAlbumIdsAsync(List<AlbumSimplified> albumsSimplified) throws SQLException {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				List<String> albumIds = albumsSimplified.stream().map(AlbumSimplified::getId).collect(Collectors.toList());
				try {
					storeStringsToTableColumn(albumIds, Constants.TABLE_ALBUM_CACHE, Constants.COL_ALBUM_IDS);
				} catch (SQLException e) {
					log.stackTrace(e);
				}
			}
		}, CACHE_ALBUMS_THREAD_NAME);
		t.start();
	}
	
	/**
	 * Cache the artist IDs in a separate thread
	 * 
	 * @param followedArtists
	 * @param cachedArtists
	 * @throws SQLException
	 * @throws IOException
	 */
	public void updateFollowedArtistsCacheAsync(List<String> followedArtists, List<String> cachedArtists) throws SQLException, IOException {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (cachedArtists == null || cachedArtists.isEmpty()) {
						storeStringsToTableColumn(followedArtists, Constants.TABLE_ARTIST_CACHE, Constants.COL_ARTIST_IDS);
					} else {
						Set<String> addedArtists = new HashSet<>(followedArtists);
						addedArtists.removeAll(cachedArtists);
						if (!addedArtists.isEmpty()) {
							storeStringsToTableColumn(addedArtists, Constants.TABLE_ARTIST_CACHE, Constants.COL_ARTIST_IDS);
						}
						Set<String> removedArtists = new HashSet<>(cachedArtists);
						removedArtists.removeAll(followedArtists);
						if (!removedArtists.isEmpty()) {
							removeStringsFromTableColumn(removedArtists, Constants.TABLE_ARTIST_CACHE, Constants.COL_ARTIST_IDS);
						}			
					}
					refreshUpdateStore(Constants.US_ARTIST_CACHE);
				} catch (SQLException e) {
					log.stackTrace(e);
				}
			}
		}, CACHE_ARTISTS_THREAD_NAME);
		t.start();
	}
}
