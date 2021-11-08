package spotify.bot.config.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@Service
public class DatabaseService {

	private final static String CACHE_ALBUMS_THREAD_NAME = "Caching ALBUM IDs";
	private final static String CACHE_ALBUMS__NAMES_THREAD_NAME = "Caching ALBUM NAMES";
	private final static String CACHE_ARTISTS_THREAD_NAME = "Caching ARTIST IDs";

	@Autowired
	private DiscoveryDatabase database;

	@Autowired
	private BotLogger log;

	///////////////////////

	/**
	 * Update the access and refresh tokens in the database
	 * 
	 * @param accessToken
	 * @param refreshToken
	 */
	public void updateTokens(String accessToken, String refreshToken) throws SQLException {
		database.update(DBConstants.TABLE_SPOTIFY_API, DBConstants.COL_ACCESS_TOKEN, accessToken);
		database.update(DBConstants.TABLE_SPOTIFY_API, DBConstants.COL_REFRESH_TOKEN, refreshToken);
	}

	////////////////////////
	// READ

	/**
	 * Return the entire contents of the "cache_releases" table as Strings
	 * 
	 * @return
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
	 * 
	 * @return
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
	 * 
	 * @return
	 */
	public List<String> getArtistCache() throws SQLException {
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_ARTISTS);
		List<String> cachedArtists = new ArrayList<>();
		while (rs.next()) {
			cachedArtists.add(rs.getString(DBConstants.COL_ARTIST_ID));
		}
		return cachedArtists;
	}

	public SpotifyApiConfig getSpotifyApiConfig() throws SQLException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_SPOTIFY_API);
		SpotifyApiConfig spotifyApiConfig = new SpotifyApiConfig();
		spotifyApiConfig.setClientId(db.getString(DBConstants.COL_CLIENT_ID));
		spotifyApiConfig.setClientSecret(db.getString(DBConstants.COL_CLIENT_SECRET));
		spotifyApiConfig.setAccessToken(db.getString(DBConstants.COL_ACCESS_TOKEN));
		spotifyApiConfig.setRefreshToken(db.getString(DBConstants.COL_REFRESH_TOKEN));
		spotifyApiConfig.setMarket(CountryCode.valueOf(db.getString(DBConstants.COL_MARKET)));
		return spotifyApiConfig;
	}

	public StaticConfig getStaticConfig() throws SQLException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_CONFIG_STATIC);
		StaticConfig staticConfig = new StaticConfig();
		staticConfig.setLookbackDays(db.getInt(DBConstants.COL_LOOKBACK_DAYS));
		staticConfig.setNewNotificationTimeout(db.getInt(DBConstants.COL_NEW_NOTIFICATION_TIMEOUT));
		staticConfig.setArtistCacheTimeout(db.getInt(DBConstants.COL_ARTIST_CACHE_TIMEOUT));
		staticConfig.setArtistCacheLastUpdated(db.getDate(DBConstants.COL_ARTIST_CACHE_LAST_UPDATE));
		staticConfig.setRestartBeforeFriday(db.getBoolean(DBConstants.COL_RESTART_BEFORE_FRIDAY));
		staticConfig.setAutoVacuum(db.getBoolean(DBConstants.COL_AUTO_VACUUM));
		return staticConfig;
	}

	public UserOptions getUserConfig() throws SQLException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_CONFIG_USER_OPTIONS);
		UserOptions userOptions = new UserOptions();
		userOptions.setIntelligentAppearsOnSearch(db.getBoolean(DBConstants.COL_INTELLIGENT_APPEARS_ON_SEARCH));
		userOptions.setCircularPlaylistFitting(db.getBoolean(DBConstants.COL_CIRCULAR_PLAYLIST_FITTING));
		userOptions.setEpSeparation(db.getBoolean(DBConstants.COL_EP_SEPARATION));
		userOptions.setRemixSeparation(db.getBoolean(DBConstants.COL_REMIX_SEPARATION));
		userOptions.setLiveSeparation(db.getBoolean(DBConstants.COL_LIVE_SEPARATION));
		userOptions.setRereleaseSeparation(db.getBoolean(DBConstants.COL_RERELEASE_SEPARATION));
		return userOptions;
	}

	public PlaylistStoreConfig getPlaylistStoreConfig() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap = getAllPlaylistStoresMap();
		PlaylistStoreConfig playlistStoreConfig = new PlaylistStoreConfig(playlistStoreMap);
		return playlistStoreConfig;
	}

	public Map<AlbumGroupExtended, PlaylistStore> getAllPlaylistStoresMap() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap = new HashMap<>();
		ResultSet dbPlaylistStore = database.selectAll(DBConstants.TABLE_PLAYLIST_STORE);
		while (dbPlaylistStore.next()) {
			AlbumGroupExtended albumGroup = AlbumGroupExtended.valueOf(dbPlaylistStore.getString(DBConstants.COL_ALBUM_GROUP));
			PlaylistStore ps = new PlaylistStore(albumGroup);
			ps.setPlaylistId(dbPlaylistStore.getString(DBConstants.COL_PLAYLIST_ID));
			ps.setLastUpdate(dbPlaylistStore.getDate(DBConstants.COL_LAST_UPDATE));
			playlistStoreMap.put(albumGroup, ps);
		}
		return playlistStoreMap;
	}
	
	public Map<String, List<PlaylistStore>> getBlacklistedArtistReleasePairs() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> allPlaylistStoresMap = getAllPlaylistStoresMap();
		
		Map<String, List<PlaylistStore>> blacklistedReleaseTypesForArtists = new HashMap<>();
		ResultSet dbBlacklistedTypes = database.selectAll(DBConstants.TABLE_BLACKLISTED_TYPES);

		while (dbBlacklistedTypes.next()) {
			String artistId = dbBlacklistedTypes.getString(DBConstants.COL_ARTIST_ID);
			String blacklistedTypesRaw = dbBlacklistedTypes.getString(DBConstants.COL_BLACKLISTED_TYPES);
			List<PlaylistStore> blacklistedTypes = new ArrayList<>();
			for (String blacklistedType : blacklistedTypesRaw.split(",")) {
				AlbumGroupExtended blacklistedAlbumGroup = AlbumGroupExtended.valueOf(blacklistedType);
				PlaylistStore playlistStore = allPlaylistStoresMap.get(blacklistedAlbumGroup);
				blacklistedTypes.add(playlistStore);
			}
			blacklistedReleaseTypesForArtists.put(artistId, blacklistedTypes);
		}
		return blacklistedReleaseTypesForArtists;
	}

	////////////////////////
	// WRITE

	/**
	 * Unset the given recent addition info of the given playlist store
	 * 
	 * @param albumGroupString
	 */
	public synchronized void unsetPlaylistStore(AlbumGroupExtended albumGroup) throws SQLException {
		if (albumGroup != null) {
			database.updateNull(
				DBConstants.TABLE_PLAYLIST_STORE,
				DBConstants.COL_LAST_UPDATE,
				DBConstants.COL_ALBUM_GROUP,
				albumGroup.getGroupName().toUpperCase());
		}
	}

	/**
	 * Update the playlist store's given timestamp
	 * 
	 * @param albumGroupString
	 */
	public synchronized void refreshPlaylistStore(AlbumGroupExtended albumGroup) throws SQLException {
		if (albumGroup != null) {
			database.updateWithCondition(
				DBConstants.TABLE_PLAYLIST_STORE,
				DBConstants.COL_LAST_UPDATE,
				String.valueOf(BotUtils.currentTime()),
				DBConstants.COL_ALBUM_GROUP,
				albumGroup.getGroupName().toUpperCase());
		}
	}

	/**
	 * Cache the album IDs of the given list of albums
	 * 
	 * @param albumsSimplified
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
	 * 
	 * @param albumsSimplified
	 */
	public synchronized void cacheAlbumIdsAsync(List<AlbumSimplified> albumsSimplified) {
		Runnable r = () -> cacheAlbumIdsSync(albumsSimplified);
		new Thread(r, CACHE_ALBUMS_THREAD_NAME).start();
	}

	/**
	 * Cache the album names of the given list of albums
	 * 
	 * @param albumsSimplified
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
	 * 
	 * @param albumsSimplified
	 */
	public synchronized void cacheAlbumNamesAsync(List<AlbumSimplified> albumsSimplified) {
		Runnable r = () -> cacheAlbumNamesSync(albumsSimplified);
		new Thread(r, CACHE_ALBUMS__NAMES_THREAD_NAME).start();
	}

	/**
	 * Cache the artist IDs in a separate thread
	 * 
	 * @param followedArtists
	 */
	public synchronized void updateFollowedArtistsCacheAsync(List<String> followedArtists) {
		Runnable r = () -> {
			try {
				List<String> cachedArtists = getArtistCache();
				if (cachedArtists != null && !cachedArtists.isEmpty()) {
					database.clearTable(DBConstants.TABLE_CACHE_ARTISTS);
					database.insertAll(
						followedArtists,
						DBConstants.TABLE_CACHE_ARTISTS,
						DBConstants.COL_ARTIST_ID);
				}
				refreshArtistCacheLastUpdate();
			} catch (SQLException e) {
				log.stackTrace(e);
			}
		};
		new Thread(r, CACHE_ARTISTS_THREAD_NAME).start();
	}

	/**
	 * Update the update store's given timestamp and set the song count
	 */
	private synchronized void refreshArtistCacheLastUpdate() throws SQLException {
		database.update(DBConstants.TABLE_CONFIG_STATIC, DBConstants.COL_ARTIST_CACHE_LAST_UPDATE, String.valueOf(BotUtils.currentTime()));
	}
	
	/**
	 * Execute the VACUUM command in the database for housekeeping
	 */
	public synchronized void vacuum() {
		Runnable r = () -> {
			try {
				database.vacuum();
			} catch (SQLException e) {
				log.stackTrace(e);
			}
		};
		new Thread(r, CACHE_ALBUMS__NAMES_THREAD_NAME).start();
	}
}
