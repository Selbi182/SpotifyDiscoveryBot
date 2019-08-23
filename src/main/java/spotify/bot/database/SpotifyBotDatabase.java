package spotify.bot.database;

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

import spotify.bot.Config;
import spotify.bot.util.Constants;

public class SpotifyBotDatabase {

	private final static String SELECT_QUERY_MASK = "SELECT %s FROM %s";
	private final static String INSERT_QUERY_MASK = "INSERT INTO %s(%s) VALUES('%s')";

	/**
	 * Filter out all album IDs not currently present in the database
	 * 
	 * @param allAlbums
	 * @return
	 */
	public static List<String> filterNonCachedAlbumsOnly(List<String> allAlbums) throws IOException {
		Set<String> filteredAlbums = new HashSet<>(allAlbums);
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(Config.getInstance().getDbUrl());
			Statement statement = connection.createStatement();
			ResultSet rs = statement.executeQuery(String.format(SELECT_QUERY_MASK, Constants.DB_ROW_ALBUM_IDS, Constants.DB_TBL_ALBUMS));
			while (rs.next()) {
				filteredAlbums.remove(rs.getString(Constants.DB_ROW_ALBUM_IDS));
			}
		} catch (SQLException e) {
			Config.log().severe(e.getMessage());
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				Config.log().severe(e.getMessage());
			}
		}
		return new ArrayList<>(filteredAlbums);
	}

	/**
	 * Store the list of album IDs in the database to prevent them from getting added again
	 * 
	 * @param albumIDs
	 */
	public static synchronized void storeAlbumIDsToDB(List<String> albumIDs) throws IOException {
		if (!albumIDs.isEmpty()) {
			Connection connection = null;
			try {
				connection = DriverManager.getConnection(Config.getInstance().getDbUrl());
				Statement statement = connection.createStatement();
				for (String s : albumIDs) {
					statement.executeUpdate(String.format(INSERT_QUERY_MASK, Constants.DB_TBL_ALBUMS, Constants.DB_ROW_ALBUM_IDS, s));
				}
			} catch (SQLException e) {
				Config.log().severe(e.getMessage());
			} finally {
				try {
					if (connection != null)
						connection.close();
				} catch (SQLException e) {
					Config.log().severe(e.getMessage());
				}
			}
		}
	}
}
