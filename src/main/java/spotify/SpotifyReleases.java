package spotify;

import static spotify.Constants.DB_FILE_NAME;
import static spotify.Constants.DB_ROW_ALBUM_IDS;
import static spotify.Constants.DB_TBL_ALBUMS;
import static spotify.Constants.INI_FILENAME;
import static spotify.Constants.KEY_ACCESS_TOKEN;
import static spotify.Constants.KEY_ALBUM_TYPES;
import static spotify.Constants.KEY_CALLBACK_URI;
import static spotify.Constants.KEY_CLIENT_ID;
import static spotify.Constants.KEY_CLIENT_SECRET;
import static spotify.Constants.KEY_DEFAULT_LIMIT;
import static spotify.Constants.KEY_INTELLIGENT_APPEARS_ON_SEARCH;
import static spotify.Constants.KEY_LOGLEVEL;
import static spotify.Constants.KEY_LOOKBACK_DAYS;
import static spotify.Constants.KEY_MARKET;
import static spotify.Constants.KEY_PLAYLIST_ADD_LIMIT;
import static spotify.Constants.KEY_PLAYLIST_ID;
import static spotify.Constants.KEY_REFRESH_TOKEN;
import static spotify.Constants.KEY_SCOPES;
import static spotify.Constants.KEY_SEVERAL_ALBUMS_LIMIT;
import static spotify.Constants.KEY_SLEEP_MINUTES;
import static spotify.Constants.KEY_TRACK_PREFIX;
import static spotify.Constants.MINUTE_IN_SECONDS;
import static spotify.Constants.RELEASE_COMPARATOR;
import static spotify.Constants.SECOND_IN_MILLIS;
import static spotify.Constants.SECTION_CLIENT;
import static spotify.Constants.SECTION_CONFIG;
import static spotify.Constants.SECTION_SPOTIFY;
import static spotify.Constants.SECTION_TOKENS;
import static spotify.Constants.SECTION_USER;
import static spotify.Constants.VARIOUS_ARTISTS;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import com.google.gson.JsonArray;
import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

public class SpotifyReleases {

	// API
	private SpotifyApi api;
	
	// Logger
	public final static Logger LOG = Logger.getGlobal();
	
	// INI
	private static File iniFilePath;
	private Wini iniFile;
	
	// DB
	private static File dbFilePath;
	private static String dbUrl;
	private final static String DB_URL_PREFIX = "jdbc:sqlite:";
	
	// Config
	private int defaultLimit = 50;
	private int severalAlbumsLimit = 20;
	private int playlistAddLimit = 100;
	private String trackPrefix = "spotify:track:";
	private String scopes = "user-follow-read playlist-modify-private";
	private int lookbackDays = 3;
	public int sleepMinutes = 45;
	public long sleepMillis = SECOND_IN_MILLIS * 60 * sleepMinutes;
	private String playlistId;
	private CountryCode market = CountryCode.DE;
	private String albumTypes = "album,single,compilation";
	private boolean intelligentAppearsOnSearch = true;
	
	// App
	private boolean isRunning = false;

	/**
	 * Initialize the app
	 * @throws IOException 
	 * @throws InvalidFileFormatException 
	 * @throws SpotifyWebApiException 
	 * @throws Exception
	 */
	public SpotifyReleases() throws IOException, SpotifyWebApiException {
		// Configure Logger
		LOG.info("=== Spotify Discovery Bot ===");
		
		// Set file paths
		File thisJarLocation = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath()).getAbsoluteFile();
		iniFilePath = new File(thisJarLocation, INI_FILENAME);
		
		// Parse INI File
		if (!iniFilePath.canRead()) {
			throw new IOException("Cannot read .ini file!");
		}
		iniFile = new Wini(iniFilePath);
		
		// Ensure readability of the database file
		dbFilePath = new File(thisJarLocation, DB_FILE_NAME); 
		if (!dbFilePath.canRead()) {
			throw new IOException("Cannot read .db file!");
		}
		dbUrl = DB_URL_PREFIX + dbFilePath.getAbsolutePath();
		
		// Configure Logger
		Level l = Level.parse(iniFile.get(SECTION_CONFIG, KEY_LOGLEVEL));
		LOG.setLevel(l);
		for (Handler h : LOG.getHandlers()) {
			h.setLevel(l);
		}
		LOG.info("Initializing...");
		
		// Set general client data given via the Spotify dashboard
		String clientId = iniFile.get(SECTION_CLIENT, KEY_CLIENT_ID);
		String clientSecret = iniFile.get(SECTION_CLIENT, KEY_CLIENT_SECRET);
		String callbackUri = iniFile.get(SECTION_CLIENT, KEY_CALLBACK_URI);
		
		// Build the API
		api = new SpotifyApi.Builder()
			.setClientId(clientId)
			.setClientSecret(clientSecret)
			.setRedirectUri(SpotifyHttpManager.makeUri(callbackUri))
			.build();
		
		// Set tokens, if preexisting
		String accessToken = iniFile.get(SECTION_TOKENS, KEY_ACCESS_TOKEN);
		String refreshToken = iniFile.get(SECTION_TOKENS, KEY_REFRESH_TOKEN);
		api.setAccessToken(accessToken);
		api.setRefreshToken(refreshToken);

		// Set search settings
		playlistId = iniFile.get(SECTION_USER, KEY_PLAYLIST_ID);
		market = CountryCode.valueOf(iniFile.get(SECTION_USER, KEY_MARKET));
		albumTypes = iniFile.get(SECTION_USER, KEY_ALBUM_TYPES);
		intelligentAppearsOnSearch = Boolean.valueOf(iniFile.get(SECTION_USER, KEY_INTELLIGENT_APPEARS_ON_SEARCH));
		
		// Set static Spotify settings
		scopes = iniFile.get(SECTION_SPOTIFY, KEY_SCOPES);
		trackPrefix = iniFile.get(SECTION_SPOTIFY, KEY_TRACK_PREFIX);
		defaultLimit = iniFile.get(SECTION_SPOTIFY, KEY_DEFAULT_LIMIT, int.class);
		severalAlbumsLimit = iniFile.get(SECTION_SPOTIFY, KEY_SEVERAL_ALBUMS_LIMIT, int.class);
		playlistAddLimit = iniFile.get(SECTION_SPOTIFY, KEY_PLAYLIST_ADD_LIMIT, int.class);
		
		// Set search settings
		lookbackDays = iniFile.get(SECTION_CONFIG, KEY_LOOKBACK_DAYS, int.class);
		sleepMinutes = iniFile.get(SECTION_CONFIG, KEY_SLEEP_MINUTES, int.class);
		sleepMillis = sleepMillis * SECOND_IN_MILLIS * MINUTE_IN_SECONDS;
		
		// Try to login with the stored access tokens or re-authenticate
		try {
			refreshAccessToken();
		} catch (UnauthorizedException | BadRequestException e) {
			LOG.warning("Access token expired or is invalid, please sign in again under this URL:");
			authenticate();
		}
		
		LOG.info("Successfully authenticated!");
	}
	
	/**
	 * Main loop that should never quit
	 * @throws Exception
	 */
	public void spotifyDiscoveryMainLoop() throws Exception {
		LOG.info("=== Entering Main Loop ===");
		isRunning = true;
		
		while (isRunning) {
			LOG.info("Searching for any new releases within the past " + lookbackDays + " days...");
			
			// The process for new album searching is always the same chain of tasks:
			// 1. Fetch all followed artists of the user
			// 2. Fetch all raw album IDs of those artists
			// 3. Filter out all albums that were already stored in the DB
			// 4. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
			//    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
			//    b. Filter out all albums not released in the lookbackDays range (default: 3 days)
			// 5. Get the songs IDs of the remaining (new) albums
			List<Artist> followedArtists = getFollowedArtists();
			List<String> albumIds = getAlbumsIdsByArtists(followedArtists, albumTypes);
			albumIds = filterNonCachedAlbumsOnly(albumIds);
			List<List<TrackSimplified>> newSongs = new ArrayList<>();
			if (!albumIds.isEmpty()) {
				List<Album> fullAlbums = convertAlbumIdsToFullAlbums(albumIds);
				List<Album> newAlbums = filterNewAlbumsOnly(fullAlbums);
				sortAlbums(newAlbums);
				newSongs = getSongIdsByAlbums(newAlbums);
			}

			// If intelligentAppearsOnSearch is enabled, also add any songs found in releases via the
			// "appears_on" album type that have at least one followed artist as contributor.
			// The process is very similar to the one above:
			// 1. Taking the followed artists from above, fetch all album IDs of releases that have the "appears_on" tag
			// 2. These albums are are filtered as normal (step 3+4 above)
			// 3. Then, filter only the songs of the remaining releases that have at least one followed artist as contributor
			// 4. Add the remaining songs to the adding queue
			if (intelligentAppearsOnSearch) {
				List<String> extraAlbumIds = getAlbumsIdsByArtists(followedArtists, AlbumType.APPEARS_ON.toString());
				extraAlbumIds = filterNonCachedAlbumsOnly(extraAlbumIds);
				if (!extraAlbumIds.isEmpty()) {
					List<Album> fullAlbumsExtra = convertAlbumIdsToFullAlbums(extraAlbumIds);
					List<Album> newAlbumsExtra = filterNewAlbumsOnly(fullAlbumsExtra);
					sortAlbums(newAlbumsExtra);
					List<List<TrackSimplified>> extraSongs = findFollowedArtistsSongsOnAlbums(newAlbumsExtra, followedArtists);
					newSongs.addAll(extraSongs);
				}
				albumIds.addAll(extraAlbumIds);
			}
			
			// Store the album IDs to the DB to prevent them from getting added a second time
			// This happens even if no new songs are added, because it will significantly speed up the future search processes
			storeAlbumIDsToDB(albumIds);						
			
			// Add any new songs to the playlist!
			if (!newSongs.isEmpty()) {
				addSongsToPlaylist(newSongs);
				LOG.info("> " + newSongs.size() + " new song" + (newSongs.size() == 1 ? "s" : "") + " added to discovery playlist!");
			} else {
				LOG.info("> No new releases found!");				
			}
			
			// Edit the playlist's description to show when the last crawl took place
			timestampPlaylist();
			
			// Sleep thread for the specified amount of minutes
			LOG.info("Sleeping. Next check in " + sleepMinutes + " minutes...");
			Thread.sleep(sleepMillis);
			
			// Refresh the access token before it expires
			refreshAccessToken();
			LOG.info("-------");
		}
	}

	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	private void authenticate() throws SpotifyWebApiException, IOException {
		AuthorizationCodeUriRequest authorizationCodeRequest = api.authorizationCodeUri().scope(scopes).build();
		URI uri = authorizationCodeRequest.execute();
		LOG.info(uri.toString());

		Scanner scanner = new Scanner(System.in);
		String code = scanner.nextLine().replace(api.getRedirectURI().toString() + "?code=", "").trim();
		scanner.close();

		AuthorizationCodeRequest acr = api.authorizationCode(code).build();
		AuthorizationCodeCredentials acc = acr.execute();
		api.setAccessToken(acc.getAccessToken());
		api.setRefreshToken(acc.getRefreshToken());

		updateTokens();
	}
	
	/**
	 * Refresh the access token
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	private void refreshAccessToken() throws SpotifyWebApiException, IOException {
		AuthorizationCodeCredentials acc = api.authorizationCodeRefresh().build().execute();
		api.setAccessToken(acc.getAccessToken());
		updateTokens();
	}

	/**
	 * Store the access and refresh tokens in the INI file
	 * @throws IOException 
	 */
	private void updateTokens() throws IOException {
		iniFile.put(SECTION_TOKENS, KEY_ACCESS_TOKEN, api.getAccessToken());
		iniFile.put(SECTION_TOKENS, KEY_REFRESH_TOKEN, api.getRefreshToken());
		iniFile.store();
	}
	
	//////////////////////////////////////////////
	
	/**
	 * Timestamp the playlist's description with the last time the crawling process was initiated
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	private void timestampPlaylist() throws SpotifyWebApiException, IOException {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat format = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);
		String newDescription = String.format("Last Search: %s", format.format(cal.getTime()));
		api.changePlaylistsDetails(playlistId).description(newDescription).build().execute();
	}

	/**
	 * Get all the user's followed artists
	 * @return
	 * @throws Exception
	 */
	private List<Artist> getFollowedArtists() throws Exception {
		List<Artist> followedArtists = new ArrayList<>();
		boolean retry = false;
		PagingCursorbased<Artist> artists = null;
		do {
			retry = false;
			try {
				do {
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(defaultLimit).after(after).build().execute();
					} else {
						artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(defaultLimit).build().execute();
					}
					followedArtists.addAll(Arrays.asList(artists.getItems()));
				} while (artists.getNext() != null);
			} catch (TooManyRequestsException e) {
				retry = true;
				int timeout = e.getRetryAfter() + 1;
				Thread.sleep(timeout * SECOND_IN_MILLIS);
			}
		} while (retry);
		return followedArtists;
	}

	/**
	 * Get all album IDs of the given list of artists
	 * @param artists
	 * @return
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<String> getAlbumsIdsByArtists(List<Artist> artists, String albumTypes) throws SpotifyWebApiException, IOException, InterruptedException {
		List<String> ids = new ArrayList<>();
		for (Artist a : artists) {
			boolean retry = false;
			Paging<AlbumSimplified> albums = null;
			do {
				retry = false;
				try {
					albums = api.getArtistsAlbums(a.getId()).market(market).album_type(albumTypes).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * SECOND_IN_MILLIS);
				}
			} while (retry);
			if (albums != null) {
				for (AlbumSimplified as : albums.getItems()) {
					ids.add(as.getId());
				}
			}
		}
		return ids;
	}

	/**
	 * Filter out all album IDs not currently present in the database
	 * @param allAlbums
	 * @return
	 */
	private List<String> filterNonCachedAlbumsOnly(List<String> allAlbums) {
		Set<String> filteredAlbums = new HashSet<>(allAlbums);
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(dbUrl);
			Statement statement = connection.createStatement();
			ResultSet rs = statement.executeQuery(String.format("SELECT %s FROM %s", DB_ROW_ALBUM_IDS, DB_TBL_ALBUMS));
			while (rs.next()) {
				filteredAlbums.remove(rs.getString(DB_ROW_ALBUM_IDS));
			}
		} catch (SQLException e) {
			LOG.severe(e.getMessage());
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				LOG.severe(e.getMessage());
			}
		}
		return new ArrayList<>(filteredAlbums);
	}

	/**
	 * Convert the given list of album IDs into fully equipped Album DTOs
	 * @param ids
	 * @return
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<Album> convertAlbumIdsToFullAlbums(List<String> ids) throws SpotifyWebApiException, IOException, InterruptedException {
		List<Album> albums = new ArrayList<>();
		int spliceCount = ids.size() / severalAlbumsLimit + 1;
		for (int i = 0; i < spliceCount; i++) {
			List<String> idSubList;
			if (i == spliceCount - 1) {
				idSubList = ids.subList(i * severalAlbumsLimit, i * severalAlbumsLimit + (ids.size() % severalAlbumsLimit));
			} else {
				idSubList = ids.subList(i * severalAlbumsLimit, i * severalAlbumsLimit + severalAlbumsLimit);
			}
			String[] idSubListPrimitive = idSubList.toArray(new String[idSubList.size()]);
			boolean retry = false;
			Album[] fullAlbums = null;
			do {
				retry = false;
				try {
					fullAlbums = api.getSeveralAlbums(idSubListPrimitive).market(market).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * SECOND_IN_MILLIS);
				}
			} while (retry);
			albums.addAll(Arrays.asList(fullAlbums));
		}
		return albums;
	}

	/**
	 * Filter out all albums not released within the lookbackDays range
	 * @param albums
	 * @return
	 */
	private List<Album> filterNewAlbumsOnly(List<Album> albums) {
		List<Album> filteredAlbums = new ArrayList<>();
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");
		Set<String> validDates = new HashSet<>();
		for (int i = 0; i < lookbackDays; i++) {
			validDates.add(date.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
		for (Album a : albums) {
			if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
				if (validDates.contains(a.getReleaseDate())) {
					filteredAlbums.add(a);
				}
			}
		}
		return filteredAlbums;
	}

	/**
	 * Get all songs IDs of the given list of albums
	 * @param albums
	 * @return
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	private List<List<TrackSimplified>> getSongIdsByAlbums(List<Album> albums) throws InterruptedException, SpotifyWebApiException, IOException {
		List<List<TrackSimplified>> tracksByAlbums = new ArrayList<>();
		for (Album a : albums) {
			List<TrackSimplified> currentList = new ArrayList<>();
			boolean retry = false;
			Paging<TrackSimplified> albumTracks = null;
			do {
				retry = false;
				try {
					albumTracks = api.getAlbumsTracks(a.getId()).limit(defaultLimit).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * SECOND_IN_MILLIS);
				}
			} while (retry);
			currentList.addAll(Arrays.asList(albumTracks.getItems()));
			tracksByAlbums.add(currentList);
		}
		return tracksByAlbums;
	}
	
	/**
	 * Find all releases marked as "appears_on" by the given list of artists, but
	 * filter the result such that only songs of artists you follow are preserved.
	 * Also filter out any compilation appearances.
	 * 
	 * @param extraAlbumIdsFiltered
	 * @param followedArtists 
	 * @return
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<List<TrackSimplified>> findFollowedArtistsSongsOnAlbums(List<Album> newAlbums, List<Artist> followedArtists) throws SpotifyWebApiException, IOException, InterruptedException {
		List<List<TrackSimplified>> selectedSongsByAlbum = new ArrayList<>();
		List<Album> newAlbumsWithoutCompilations = new ArrayList<>();
		for (Album a : newAlbums) {
			if (!a.getAlbumType().equals(AlbumType.COMPILATION)) {
				boolean okayToAdd = true;
				for (ArtistSimplified as : a.getArtists()) {
					if (as.getName().equals(VARIOUS_ARTISTS)) {
						okayToAdd = false;
						break;
					}
				}
				if (okayToAdd) {
					newAlbumsWithoutCompilations.add(a);						
				}
			}
		}
		Set<String> followedArtistsIds = new HashSet<>();
		for (Artist a : followedArtists) {
			followedArtistsIds.add(a.getId());
		}
		List<List<TrackSimplified>> newSongs = getSongIdsByAlbums(newAlbumsWithoutCompilations);
		for (List<TrackSimplified> songsInAlbum : newSongs) {
			List<TrackSimplified> selectedSongs = new ArrayList<>();
			for (TrackSimplified ts : songsInAlbum) {
				for (ArtistSimplified as : ts.getArtists()) {
					if (followedArtistsIds.contains(as.getId())) {
						selectedSongs.add(ts);
						break;
					}
				}
			}
			selectedSongsByAlbum.add(selectedSongs);
		}
		return selectedSongsByAlbum;
	}

	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per release is used to retain order).
	 * @param songs
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void addSongsToPlaylist(List<List<TrackSimplified>> newSongs) throws SpotifyWebApiException, IOException, InterruptedException {
		for (List<TrackSimplified> songsInAlbum : newSongs) {
			int spliceCount = songsInAlbum.size() / playlistAddLimit + 1;
			for (int i = 0; i < spliceCount; i++) {
				List<TrackSimplified> idSubList;
				if (i == spliceCount - 1) {
					idSubList = songsInAlbum.subList(i * playlistAddLimit, i * playlistAddLimit + (songsInAlbum.size() % playlistAddLimit));
				} else {
					idSubList = songsInAlbum.subList(i * playlistAddLimit, i * playlistAddLimit + playlistAddLimit);
				}
				JsonArray json = new JsonArray();
				for (TrackSimplified s : idSubList) {
					json.add(trackPrefix + s.getId());
				}
				boolean retry = false;
				do {
					retry = false;
					try {
						api.addTracksToPlaylist(playlistId, json).build().execute();
					} catch (TooManyRequestsException e) {
						retry = true;
						int timeout = e.getRetryAfter() + 1;
						Thread.sleep(timeout * SECOND_IN_MILLIS);
					}
				} while (retry);
			}
			Thread.sleep(SECOND_IN_MILLIS);
		}
	}
	
	/**
	 * Store the list of album IDs in the database to prevent them from getting added again
	 * @param albumIDs
	 */
	private void storeAlbumIDsToDB(List<String> albumIDs) {
		if (!albumIDs.isEmpty()) {
			Connection connection = null;
			try {
				connection = DriverManager.getConnection(dbUrl);
				Statement statement = connection.createStatement();
				for (String s : albumIDs) {
					statement.executeUpdate(String.format("INSERT INTO %s(%s) VALUES('%s')", DB_TBL_ALBUMS, DB_ROW_ALBUM_IDS, s));
				}
			} catch (SQLException e) {
				LOG.severe(e.getMessage());
			} finally {
				try {
					if (connection != null)
						connection.close();
				} catch (SQLException e) {
					LOG.severe(e.getMessage());
				}
			}			
		}
	}
	
	/**
	 * Sort the album list with the default comparator
	 * @param albums
	 */
	private void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> RELEASE_COMPARATOR.reversed().compare(a1, a2));
	}
}
