package spotify.bot.config.database;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.config.dto.PlaylistStore;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@Service
public class DatabaseService {

	private final static String CACHE_ALBUMS_THREAD_NAME = "Caching ALBUM IDs";
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
	 * @throws SQLException
	 */
	public void updateTokens(String accessToken, String refreshToken) throws SQLException {
		database.update(DBConstants.TABLE_SPOTIFY_API, DBConstants.COL_ACCESS_TOKEN, accessToken);
		database.update(DBConstants.TABLE_SPOTIFY_API, DBConstants.COL_REFRESH_TOKEN, refreshToken);
	}

	////////////////////////
	// READ

	/**
	 * Return the entire contents of the "album_cache" table as Strings
	 * 
	 * @return
	 * @throws SQLException
	 */
	public List<String> getAlbumCache() throws SQLException {
		List<String> albumCacheIds = new ArrayList<>();
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_RELEASES);
		while (rs.next()) {
			albumCacheIds.add(rs.getString(DBConstants.COL_RELEASE_ID));
		}
		return albumCacheIds;
	}

	/**
	 * Return the entire contents of the "artist_cache" table as Strings
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public List<String> getArtistCache() throws IOException, SQLException {
		ResultSet rs = database.selectAll(DBConstants.TABLE_CACHE_ARTISTS);
		List<String> cachedArtists = new ArrayList<>();
		while (rs.next()) {
			cachedArtists.add(rs.getString(DBConstants.COL_ARTIST_ID));
		}
		return cachedArtists;
	}

	public SpotifyApiConfig getSpotifyApiConfig() throws SQLException, IOException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_SPOTIFY_API);
		SpotifyApiConfig spotifyApiConfig = new SpotifyApiConfig();
		spotifyApiConfig.setClientId(db.getString(DBConstants.COL_CLIENT_ID));
		spotifyApiConfig.setClientSecret(db.getString(DBConstants.COL_CLIENT_SECRET));
		spotifyApiConfig.setAccessToken(db.getString(DBConstants.COL_ACCESS_TOKEN));
		spotifyApiConfig.setRefreshToken(db.getString(DBConstants.COL_REFRESH_TOKEN));
		return spotifyApiConfig;
	}

	public StaticConfig getStaticConfig() throws SQLException, IOException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_CONFIG_STATIC);
		StaticConfig staticConfig = new StaticConfig();
		staticConfig.setMarket(CountryCode.valueOf(db.getString(DBConstants.COL_MARKET)));
		staticConfig.setLookbackDays(db.getInt(DBConstants.COL_LOOKBACK_DAYS));
		staticConfig.setNewNotificationTimeout(db.getInt(DBConstants.COL_NEW_NOTIFICATION_TIMEOUT));
		staticConfig.setArtistCacheTimeout(db.getInt(DBConstants.COL_ARTIST_CACHE_TIMEOUT));
		staticConfig.setArtistCacheLastUpdated(db.getDate(DBConstants.COL_ARTIST_CACHE_LAST_UPDATE));
		return staticConfig;
	}

	public UserOptions getUserConfig() throws SQLException, IOException {
		ResultSet db = database.selectSingle(DBConstants.TABLE_CONFIG_USER_OPTIONS);
		UserOptions userOptions = new UserOptions();
		userOptions.setCacheFollowedArtists(db.getBoolean(DBConstants.COL_CACHE_FOLLOWED_ARTISTS));
		userOptions.setIntelligentAppearsOnSearch(db.getBoolean(DBConstants.COL_INTELLIGENT_APPEARS_ON_SEARCH));
		userOptions.setCircularPlaylistFitting(db.getBoolean(DBConstants.COL_CIRCULAR_PLAYLIST_FITTING));
		userOptions.setEpSeparation(db.getBoolean(DBConstants.COL_EP_SEPARATION));
		userOptions.setLiveSeparation(db.getBoolean(DBConstants.COL_LIVE_SEPARATION));
		return userOptions;
	}

	public Map<AlbumGroupExtended, PlaylistStore> getAllPlaylistStores() throws SQLException {
		Map<AlbumGroupExtended, PlaylistStore> playlistStore = new HashMap<>();
		ResultSet dbPlaylistStore = database.selectAll(DBConstants.TABLE_PLAYLIST_STORE);
		while (dbPlaylistStore.next()) {
			AlbumGroupExtended albumGroup = AlbumGroupExtended.valueOf(dbPlaylistStore.getString(DBConstants.COL_ALBUM_GROUP));
			PlaylistStore ps = new PlaylistStore(albumGroup);
			ps.setPlaylistId(dbPlaylistStore.getString(DBConstants.COL_PLAYLIST_ID));
			ps.setLastUpdate(dbPlaylistStore.getDate(DBConstants.COL_LAST_UPDATE));
			playlistStore.put(albumGroup, ps);
		}
		return playlistStore;
	}

	////////////////////////
	// WRITE

	/**
	 * Unset the given recent addition info of the given playlist store
	 * 
	 * @param albumGroupString
	 * @throws SQLException
	 */
	public synchronized void unsetPlaylistStore(AlbumGroupExtended albumGroup) throws SQLException {
		if (albumGroup != null) {
			database.updateNull(DBConstants.TABLE_PLAYLIST_STORE,
				DBConstants.COL_LAST_UPDATE,
				DBConstants.COL_ALBUM_GROUP,
				albumGroup.getGroup().toUpperCase());
		}
	}

	/**
	 * Update the playlist store's given timestamp
	 * 
	 * @param albumGroupString
	 * @throws SQLException
	 */
	public synchronized void refreshPlaylistStore(String albumGroupString) throws SQLException {
		if (albumGroupString != null) {
			database.updateWithCondition(DBConstants.TABLE_PLAYLIST_STORE,
				DBConstants.COL_LAST_UPDATE,
				String.valueOf(BotUtils.currentTime()),
				DBConstants.COL_ALBUM_GROUP,
				albumGroupString.toUpperCase());
		}
	}

	/**
	 * Cache the album IDs of the given list of albums in a separate thread
	 * 
	 * @param albumsSimplified
	 * @throws SQLException
	 */
	public synchronized void cacheAlbumIdsAsync(List<AlbumSimplified> albumsSimplified) throws SQLException {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				List<String> albumIds = albumsSimplified.stream().map(AlbumSimplified::getId).collect(Collectors.toList());
				try {
					database.insertAll(albumIds, DBConstants.TABLE_CACHE_RELEASES, DBConstants.COL_RELEASE_ID);
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
	public synchronized void updateFollowedArtistsCacheAsync(List<String> followedArtists, List<String> cachedArtists) throws SQLException, IOException {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (cachedArtists == null || cachedArtists.isEmpty()) {
						database.insertAll(followedArtists, DBConstants.TABLE_CACHE_ARTISTS, DBConstants.COL_ARTIST_ID);
					} else {
						Set<String> addedArtists = new HashSet<>(followedArtists);
						addedArtists.removeAll(cachedArtists);
						if (!addedArtists.isEmpty()) {
							database.insertAll(addedArtists, DBConstants.TABLE_CACHE_ARTISTS, DBConstants.COL_ARTIST_ID);
							log.info("New followed artists:");
						}
						Set<String> removedArtists = new HashSet<>(cachedArtists);
						removedArtists.removeAll(followedArtists);
						if (!removedArtists.isEmpty()) {
							database.deleteAll(removedArtists, DBConstants.TABLE_CACHE_ARTISTS, DBConstants.COL_ARTIST_ID);
						}
					}
					refreshArtistCacheLastUpdate();
				} catch (SQLException e) {
					log.stackTrace(e);
				}
			}
		}, CACHE_ARTISTS_THREAD_NAME);
		t.start();
	}

	/**
	 * Update the update store's given timestamp and set the song count
	 * 
	 * @param group
	 * @param addedSongs
	 * @throws SQLException
	 */
	private synchronized void refreshArtistCacheLastUpdate() throws SQLException {
		database.update(DBConstants.TABLE_CONFIG_STATIC,
			DBConstants.COL_ARTIST_CACHE_LAST_UPDATE,
			String.valueOf(BotUtils.currentTime()));
	}
}
