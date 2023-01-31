package spotify.bot.config.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.util.BotUtils;

@Service
public class DatabaseService {
	private final DiscoveryDatabase database;
	private final DiscoveryBotLogger log;
	private final ExecutorService executorService;

	DatabaseService(DiscoveryDatabase discoveryDatabase, DiscoveryBotLogger botLogger) {
		this.database = discoveryDatabase;
		this.log = botLogger;
		this.executorService = Executors.newFixedThreadPool(3);
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
			cachedArtists.add(rs.getString(DBConstants.COL_ARTIST_ID));
		}
		return cachedArtists;
	}

	////////////////////////
	// WRITE

	/**
	 * Cache the album IDs of the given list of albums
	 */
	public void cacheAlbumIdsSync(List<AlbumSimplified> albumsSimplified) {
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
	 * Cache the album IDs of the given list of albums in a separate thread
	 */
	public synchronized void cacheAlbumIdsAsync(List<AlbumSimplified> albumsSimplified) {
		executorService.execute(() -> cacheAlbumIdsSync(albumsSimplified));
	}

	/**
	 * Cache the album names of the given list of albums
	 */
	public void cacheAlbumNamesSync(List<AlbumSimplified> albumsSimplified) {
		List<String> albumIds = albumsSimplified.stream()
			.map(BotUtils::albumIdentifierString)
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
	 * Cache the album name identifiers of the given list of albums in a separate thread
	 */
	public synchronized void cacheAlbumNamesAsync(List<AlbumSimplified> albumsSimplified) {
		executorService.execute(() -> cacheAlbumNamesSync(albumsSimplified));
	}

	/**
	 * Cache the artist IDs in a separate thread
	 */
	public synchronized void updateFollowedArtistsCacheAsync(List<String> followedArtists) {
		executorService.execute(() -> {
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
		});
	}
}
