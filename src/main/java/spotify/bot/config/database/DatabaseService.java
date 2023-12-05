package spotify.bot.config.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.util.SpotifyUtils;

@Service
public class DatabaseService {
	private final DiscoveryDatabase database;
	private final DiscoveryBotLogger log;

	DatabaseService(DiscoveryDatabase discoveryDatabase, DiscoveryBotLogger botLogger) {
		this.database = discoveryDatabase;
		this.log = botLogger;
	}

	////////////////////////
	// READ

	/**
	 * Return the entire contents of the "cache_releases" table as Strings
	 */
	public List<String> getReleasesIdsCache() throws SQLException {
		List<String> albumCacheIds = new ArrayList<>();
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_RELEASES);
		while (rs.next()) {
			albumCacheIds.add(rs.getString(DBConstants.COL_RELEASE_ID));
		}
		return albumCacheIds;
	}
	
	/**
	 * Return the entire contents of the "cache_releases_names" table as Strings
	 */
	public List<String> getReleaseNamesCache() throws SQLException {
		List<String> albumCacheNames = new ArrayList<>();
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_RELEASES_NAMES);
		while (rs.next()) {
			albumCacheNames.add(rs.getString(DBConstants.COL_RELEASE_NAME));
		}
		return albumCacheNames;
	}

	/**
	 * Return the entire contents of the "cache_artists" table as Strings
	 */
	public List<String> getArtistCache() throws SQLException {
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_ARTISTS);
		List<String> cachedArtists = new ArrayList<>();
		while (rs.next()) {
			String string = rs.getString(DBConstants.COL_ARTIST_ID);
			if (!SpotifyUtils.isNullString(string)) {
				cachedArtists.add(string);
			}
		}
		return cachedArtists;
	}

	////////////////////////
	// WRITE

	/**
	 * Cache the album IDs of the given list of albums
	 */
	public void cacheAlbumIds(List<AlbumSimplified> albumsSimplified) {
		List<String> albumIds = albumsSimplified.stream()
			.map(AlbumSimplified::getId)
			.collect(Collectors.toList());
		try {
			database.insertAll(
				albumIds,
				DBConstants.TABLE_CACHE_RELEASES,
				DBConstants.COL_RELEASE_ID);
		} catch (SQLException e) {
			log.stackTrace(e);
		}
	}

	/**
	 * Cache the album names of the given list of albums
	 */
	public void cacheAlbumNames(List<AlbumSimplified> albumsSimplified) {
		List<String> albumIds = albumsSimplified.stream()
			.map(SpotifyUtils::albumIdentifierString)
			.collect(Collectors.toList());
		try {
			database.insertAll(
				albumIds,
				DBConstants.TABLE_CACHE_RELEASES_NAMES,
				DBConstants.COL_RELEASE_NAME);
		} catch (SQLException e) {
			log.stackTrace(e);
		}
	}

	/**
	 * Cache the artist IDs in a separate thread
	 */
	public synchronized void cacheArtistIds(List<String> followedArtists) {
		try {
			List<String> cachedArtists = getArtistCache();
			if (cachedArtists != null) {
				database.insertAll(
					followedArtists,
					DBConstants.TABLE_CACHE_ARTISTS,
					DBConstants.COL_ARTIST_ID);
			}
		} catch (SQLException e) {
			log.stackTrace(e);
		}
	}

	/**
	 * Uncache the artist IDs in a separate thread
	 */
	public synchronized void uncacheArtistIds(List<String> unfollowedArtists) {
		try {
			List<String> cachedArtists = getArtistCache();
			if (cachedArtists != null) {
				database.removeAll(
					unfollowedArtists,
					DBConstants.TABLE_CACHE_ARTISTS,
					DBConstants.COL_ARTIST_ID);
			}
		} catch (SQLException e) {
			log.stackTrace(e);
		}
	}
}
