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
import static spotify.Constants.KEY_LOOKBACK_DAYS;
import static spotify.Constants.KEY_MARKET;
import static spotify.Constants.KEY_PLAYLIST_ADD_LIMIT;
import static spotify.Constants.KEY_PLAYLIST_ID;
import static spotify.Constants.KEY_REFRESH_TOKEN;
import static spotify.Constants.KEY_SCOPES;
import static spotify.Constants.KEY_SEVERAL_ALBUMS_LIMIT;
import static spotify.Constants.KEY_SLEEP_MINUTES;
import static spotify.Constants.KEY_TRACK_PREFIX;
import static spotify.Constants.SECOND_IN_MILLIS;
import static spotify.Constants.SECTION_CLIENT;
import static spotify.Constants.SECTION_SEARCH;
import static spotify.Constants.SECTION_SPOTIFY;
import static spotify.Constants.SECTION_TOKENS;
import static spotify.Constants.SECTION_USER;

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import org.ini4j.Wini;

import com.google.gson.JsonArray;
import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
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
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

public class SpotifyReleases {

	// API
	private SpotifyApi api;
	
	// Logger
	public final static Logger LOG = Logger.getLogger(SpotifyReleases.class.getName());
	
	// INI
	private final static File INI_FILE = new File(INI_FILENAME);
	private Wini iniFile;
	
	private int defaultLimit = 50;
	private int severalAlbumsLimit = 20;
	private int playlistAddLimit = 100;
	private String trackPrefix = "spotify:track:";
	private String scopes = "user-follow-read playlist-modify-private";
	private int lookbackDays = 3;
	private int sleepMinutes = 45;
	private String playlistId;
	private CountryCode market = CountryCode.DE;
	private String albumTypes = "album,single,compilation";
	
	// App
	private boolean isRunning = false;

	/**
	 * Initialize the app
	 * @throws Exception
	 */
	public SpotifyReleases() throws Exception {
		LOG.info("=== Spotify Discovery Bot ===");
		
		// Read INI FILE
		LOG.info("Initializing...");
		if (!INI_FILE.canRead()) {
			LOG.severe("Cannot read ini file!");
			return;
		}
		iniFile = new Wini(INI_FILE);
		
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
		
		// Set static Spotify settings
		scopes = iniFile.get(SECTION_SPOTIFY, KEY_SCOPES);
		trackPrefix = iniFile.get(SECTION_SPOTIFY, KEY_TRACK_PREFIX);
		defaultLimit = iniFile.get(SECTION_SPOTIFY, KEY_DEFAULT_LIMIT, int.class);
		severalAlbumsLimit = iniFile.get(SECTION_SPOTIFY, KEY_SEVERAL_ALBUMS_LIMIT, int.class);
		playlistAddLimit = iniFile.get(SECTION_SPOTIFY, KEY_PLAYLIST_ADD_LIMIT, int.class);
		
		// Set search settings
		lookbackDays = iniFile.get(SECTION_SEARCH, KEY_LOOKBACK_DAYS, int.class);
		sleepMinutes = iniFile.get(SECTION_SEARCH, KEY_SLEEP_MINUTES, int.class);
		
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
			
			// Fetch all followed artists of the user
			List<Artist> followedArtists = getFollowedArtists();
			
			// Fetch all album IDs (raw) of those artists
			List<String> albumIds = getAlbumsIdsByArtists(followedArtists);
			
			// Filter out all albums that were already stored in the DB
			// Abort crawling process if no albums were found
			List<String> filteredAlbums = filterNonCachedAlbumsOnly(albumIds);
			if (filteredAlbums.isEmpty()) {
				LOG.info("> No new releases found!");
			} else {
				// If new albums were found, convert into fully detailed albums (to get access to the release date)
				List<Album> fullAlbums = convertAlbumIdsToFullAlbums(filteredAlbums);
				
				// Filter out all albums not released in the lookbackDays range
				List<Album> newAlbums = filterNewAlbumsOnly(fullAlbums);
				
				// Get the Song IDs of the new albums
				// Abort if there are none 
				List<String> newSongs = getSongIdsByAlbums(newAlbums);
				if (!newSongs.isEmpty()) {
					// Finally, add the new songs to the playlist
					addSongsToPlaylist(newSongs);
					if (newSongs.size() == 1) {
						LOG.info("> " + newSongs.size() + " new song added to discovery playlist!");				
					} else {
						LOG.info("> " + newSongs.size() + " new songs added to discovery playlist!");									
					}
				} else {
					LOG.info("> No new releases found!");
				}
			}
			
			// Store the album IDs to the DB to prevent them from getting added a second time
			storeAlbumIDsToDB(filteredAlbums);					
			
			// Edit the playlist's description to show when the last crawl took place
			timestampPlaylist();
			
			// Sleep thread for the specified amount of minutes
			LOG.info("Sleeping. Next check in " + sleepMinutes + " minutes...");
			Thread.sleep(SECOND_IN_MILLIS * 60 * sleepMinutes);
			
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
		PagingCursorbased<Artist> artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(defaultLimit).build().execute();
		followedArtists.addAll(Arrays.asList(artists.getItems()));
		while (artists.getNext() != null) {
			artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(defaultLimit).after(artists.getCursors()[0].getAfter()).build().execute();
			followedArtists.addAll(Arrays.asList(artists.getItems()));
		}
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
	private List<String> getAlbumsIdsByArtists(List<Artist> artists) throws SpotifyWebApiException, IOException, InterruptedException {
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
			for (AlbumSimplified as : albums.getItems()) {
				ids.add(as.getId());
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
			connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE_NAME);
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
			for (Album a : fullAlbums) {
				albums.add(a);
			}
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
		Collections.sort(filteredAlbums, Comparator.comparing(Album::getReleaseDate));
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
	private List<String> getSongIdsByAlbums(List<Album> albums) throws InterruptedException, SpotifyWebApiException, IOException {
		List<String> tracks = new ArrayList<>();
		for (Album a : albums) {
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
			for (TrackSimplified t : albumTracks.getItems()) {
				tracks.add(t.getId());
			}
		}
		return tracks;
	}

	/**
	 * Add the given list of song IDs to the playlist and store them in the DB
	 * @param songs
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void addSongsToPlaylist(List<String> songs) throws SpotifyWebApiException, IOException, InterruptedException {
		int spliceCount = songs.size() / playlistAddLimit + 1;
		for (int i = 0; i < spliceCount; i++) {
			List<String> idSubList;
			if (i == spliceCount - 1) {
				idSubList = songs.subList(i * playlistAddLimit, i * playlistAddLimit + (songs.size() % playlistAddLimit));
			} else {
				idSubList = songs.subList(i * playlistAddLimit, i * playlistAddLimit + playlistAddLimit);
			}
			JsonArray json = new JsonArray();
			for (String s : idSubList) {
				json.add(trackPrefix + s);
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
	}
	
	private void storeAlbumIDsToDB(List<String> albumIDs) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE_NAME);
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
