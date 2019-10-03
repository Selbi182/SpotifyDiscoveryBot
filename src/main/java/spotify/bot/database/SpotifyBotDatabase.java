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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spotify.bot.util.Constants;
import spotify.main.Main;

public class SpotifyBotDatabase {

	private final static String SINGLE_SELECT_MASK = "SELECT * FROM %s LIMIT 1;";
	private final static String SELECT_QUERY_MASK = "SELECT %s FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";

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
		ResultSet rs = statement.executeQuery(String.format(SINGLE_SELECT_MASK, tableName));
		if (!rs.next()) {
			throw new SQLException("Table " + tableName + " not found or empty!");
		}
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
		Set<String> filteredAlbums = new HashSet<>(allAlbums);
		Statement statement = connection.createStatement();
		ResultSet rs = statement.executeQuery(String.format(SELECT_QUERY_MASK, Constants.COL_ALBUM_IDS, Constants.TABLE_ALBUM_CACHE));
		while (rs.next()) {
			filteredAlbums.remove(rs.getString(Constants.COL_ALBUM_IDS));
		}
		return new ArrayList<>(filteredAlbums);
	}

	/**
	 * Store the list of album IDs in the database to prevent them from getting added again
	 * 
	 * @param albumIDs
	 * @throws SQLException 
	 */
	public synchronized void storeAlbumIDsToDB(List<String> albumIDs) throws IOException, SQLException {
		if (!albumIDs.isEmpty()) {
			Statement statement = connection.createStatement();
			for (String s : albumIDs) {
				statement.executeUpdate(String.format(INSERT_QUERY_MASK, Constants.TABLE_ALBUM_CACHE, Constants.COL_ALBUM_IDS, s));
			}
		}
	}

	public synchronized void updateColumnInTable(String table, String column, String newValue) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(String.format("UPDATE %s SET %s = '%s';", table, column, newValue));
	}
}
