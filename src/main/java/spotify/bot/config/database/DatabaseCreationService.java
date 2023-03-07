package spotify.bot.config.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DatabaseCreationService {

  private static final String SQL_CACHE_ARTISTS =
      "CREATE TABLE if NOT EXISTS cache_artists (\n"
          + "    artist_id STRING NOT NULL\n"
          + "                   UNIQUE ON CONFLICT IGNORE\n"
          + "                   PRIMARY KEY);";

  private static final String SQL_CACHE_RELEASES =
      "CREATE TABLE if NOT EXISTS cache_releases (\n"
          + "    release_id STRING NOT NULL\n"
          + "                    UNIQUE ON CONFLICT IGNORE\n"
          + "                    PRIMARY KEY);";

  private static final String SQL_CACHE_RELEASES_NAMES =
      "CREATE TABLE if NOT EXISTS cache_releases_names (\n"
          + "    release_name STRING UNIQUE ON CONFLICT IGNORE\n"
          + "                      NOT NULL\n"
          + "                      PRIMARY KEY);";

  /**
   * Create the discovery bot database with all required tables
   * (typically if this is the first time the app is launched)
   */
  public void createTables(Connection connection) throws SQLException {
    for (String tableCreationCommand : List.of(SQL_CACHE_ARTISTS, SQL_CACHE_RELEASES, SQL_CACHE_RELEASES_NAMES)) {
      Statement statement = connection.createStatement();
      statement.execute(tableCreationCommand);
      statement.closeOnCompletion();
    }
  }
}
