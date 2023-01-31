package spotify.bot.config.database;

class DBConstants {

	private DBConstants() {
	}

	// Database constants
	public final static String TABLE_CACHE_RELEASES = "cache_releases";
	public final static String COL_RELEASE_ID = "release_id";
	
	public final static String TABLE_CACHE_RELEASES_NAMES = "cache_releases_names";
	public final static String COL_RELEASE_NAME = "release_name";

	public final static String TABLE_CACHE_ARTISTS = "cache_artists";
	public final static String COL_ARTIST_ID = "artist_id";
}
