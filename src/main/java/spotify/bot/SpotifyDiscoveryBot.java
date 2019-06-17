package spotify.bot;

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
import java.util.logging.Logger;

import org.ini4j.InvalidFileFormatException;

import com.google.gson.JsonArray;
import com.wrapper.spotify.SpotifyApi;
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

import spotify.util.Constants;

public class SpotifyDiscoveryBot implements Runnable {

	// Local variables
	private SpotifyApi api;
	private Config conf;
	private Logger log;

	/**
	 * Initialize the app
	 * @throws IOException 
	 * @throws InvalidFileFormatException 
	 * @throws SpotifyWebApiException 
	 * @throws Exception
	 */
	public SpotifyDiscoveryBot(Config conf) throws IOException, SpotifyWebApiException {
		// Set local variables
		this.conf = conf;
		this.log = conf.getLog();
		this.api = conf.getSpotifyApi();
		
		// Set tokens, if preexisting
		api.setAccessToken(conf.getAccessToken());
		api.setRefreshToken(conf.getRefreshToken());
		
		// Try to login with the stored access tokens or re-authenticate
		try {
			refreshAccessToken();
		} catch (UnauthorizedException | BadRequestException e) {
			log.warning("Access token expired or is invalid, please sign in again under this URL:");
			authenticate();
		}
	}
	
	/**
	 * Main loop that should never quit
	 */
	@Override
	public void run() {
		try {
			log.info("Searching...");
			
			// The process for new album searching is always the same chain of tasks:
			// 1. Fetch all followed artists of the user
			// 2. Fetch all raw album IDs of those artists
			// 3. Filter out all albums that were already stored in the DB
			// 4. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
			//    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
			//    b. Filter out all albums not released in the lookbackDays range (default: 3 days)
			// 5. Get the songs IDs of the remaining (new) albums
			List<Artist> followedArtists = getFollowedArtists();
			List<String> albumIds = getAlbumsIdsByArtists(followedArtists, conf.getAlbumTypes());
			albumIds = filterNonCachedAlbumsOnly(albumIds);
			List<List<TrackSimplified>> newSongs = new ArrayList<>();
			if (!albumIds.isEmpty()) {
				List<Album> albums = convertAlbumIdsToFullAlbums(albumIds);
				albums = filterNewAlbumsOnly(albums);
				sortAlbums(albums);
				newSongs = getSongIdsByAlbums(albums);
			}
			
			// If intelligentAppearsOnSearch is enabled, also add any songs found in releases via the
			// "appears_on" album type that have at least one followed artist as contributor.
			// The process is very similar to the one above:
			// 1. Taking the followed artists from above, fetch all album IDs of releases that have the "appears_on" tag
			// 2. These albums are are filtered as normal (step 3+4 above)
			// 3. Then, filter only the songs of the remaining releases that have at least one followed artist as contributor
			// 4. Add the remaining songs to the adding queue
			if (conf.isIntelligentAppearsOnSearch()) {
				List<String> extraAlbumIds = getAlbumsIdsByArtists(followedArtists, AlbumType.APPEARS_ON.toString());
				extraAlbumIds = filterNonCachedAlbumsOnly(extraAlbumIds);
				if (!extraAlbumIds.isEmpty()) {
					List<Album> extraAlbums = convertAlbumIdsToFullAlbums(extraAlbumIds);
					extraAlbums = filterNewAlbumsOnly(extraAlbums);
					sortAlbums(extraAlbums);
					List<List<TrackSimplified>> extraSongs = findFollowedArtistsSongsOnAlbums(extraAlbums, followedArtists);
					newSongs.addAll(extraSongs);
				}
				albumIds.addAll(extraAlbumIds);
			}
			
			// Add any new songs to the playlist!
			if (!newSongs.isEmpty()) {
				int songsAdded = addSongsToPlaylist(newSongs);
				log.info("> " + songsAdded + " new song" + (songsAdded > 1 ? "s" : "") + " added to discovery playlist!");
			} else {
				log.info("> No new releases found!");				
			}
			
			// Store the album IDs to the DB to prevent them from getting added a second time
			// This happens even if no new songs are added, because it will significantly speed up the future search processes
			storeAlbumIDsToDB(albumIds);						
			
			// Edit the playlist's description to show when the last crawl took place
			timestampPlaylist();
		} catch (InterruptedException e) {
			// Error message is handled outside, so this is just a fast way-out
			return;
		} catch (SpotifyWebApiException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	private void authenticate() throws SpotifyWebApiException, IOException {
		AuthorizationCodeUriRequest authorizationCodeRequest = api.authorizationCodeUri().scope(Constants.SCOPES).build();
		URI uri = authorizationCodeRequest.execute();
		log.info(uri.toString());

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
		conf.updateTokens(api.getAccessToken(), api.getRefreshToken());
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
		api.changePlaylistsDetails(conf.getPlaylistId()).description(newDescription).build().execute();
		// TODO retry
	}

	/**
	 * Get all the user's followed artists
	 * @return
	 * @throws IOException 
	 * @throws SpotifyWebApiException 
	 * @throws InterruptedException 
	 * @throws Exception
	 */
	private List<Artist> getFollowedArtists() throws SpotifyWebApiException, IOException, InterruptedException {
		List<Artist> followedArtists = new ArrayList<>();
		boolean retry = false;
		PagingCursorbased<Artist> artists = null;
		do {
			retry = false;
			try {
				do {
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT).after(after).build().execute();
					} else {
						artists = api.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT).build().execute();
					}
					followedArtists.addAll(Arrays.asList(artists.getItems()));
				} while (artists.getNext() != null);
			} catch (TooManyRequestsException e) {
				retry = true;
				int timeout = e.getRetryAfter() + 1;
				Thread.sleep(timeout * Constants.SECOND_IN_MILLIS);
			}
		} while (retry);
		if (followedArtists.isEmpty()) {
			log.warning("No followed artists found!");
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
	private List<String> getAlbumsIdsByArtists(List<Artist> artists, String albumTypes) throws SpotifyWebApiException, IOException, InterruptedException {
		List<String> ids = new ArrayList<>();
		for (Artist a : artists) {
			boolean retry = false;
			Paging<AlbumSimplified> albums = null;
			do {
				retry = false;
				try {
					albums = api.getArtistsAlbums(a.getId()).market(conf.getMarket()).album_type(albumTypes).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.SECOND_IN_MILLIS);
				}
			} while (retry);
			if (albums != null) {
				for (AlbumSimplified as : albums.getItems()) {
					ids.add(as.getId());
				}
			}
		}
		if (ids.isEmpty()) {
			log.warning("No album IDs found!");
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
			connection = DriverManager.getConnection(conf.getDbUrl());
			Statement statement = connection.createStatement();
			ResultSet rs = statement.executeQuery(String.format("SELECT %s FROM %s", Constants.DB_ROW_ALBUM_IDS, Constants.DB_TBL_ALBUMS));
			while (rs.next()) {
				filteredAlbums.remove(rs.getString(Constants.DB_ROW_ALBUM_IDS));
			}
		} catch (SQLException e) {
			log.severe(e.getMessage());
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				log.severe(e.getMessage());
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
		int spliceCount = ids.size() / Constants.SEVERAL_ALBUMS_LIMIT + 1;
		for (int i = 0; i < spliceCount; i++) {
			List<String> idSubList;
			if (i == spliceCount - 1) {
				idSubList = ids.subList(i * Constants.SEVERAL_ALBUMS_LIMIT, i * Constants.SEVERAL_ALBUMS_LIMIT + (ids.size() % Constants.SEVERAL_ALBUMS_LIMIT));
			} else {
				idSubList = ids.subList(i * Constants.SEVERAL_ALBUMS_LIMIT, i * Constants.SEVERAL_ALBUMS_LIMIT + Constants.SEVERAL_ALBUMS_LIMIT);
			}
			String[] idSubListPrimitive = idSubList.toArray(new String[idSubList.size()]);
			boolean retry = false;
			Album[] fullAlbums = null;
			do {
				retry = false;
				try {
					fullAlbums = api.getSeveralAlbums(idSubListPrimitive).market(conf.getMarket()).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.SECOND_IN_MILLIS);
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
		for (int i = 0; i < conf.getLookbackDays(); i++) {
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
					albumTracks = api.getAlbumsTracks(a.getId()).limit(Constants.DEFAULT_LIMIT).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.SECOND_IN_MILLIS);
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
					if (as.getName().equals(Constants.VARIOUS_ARTISTS)) {
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
	private int addSongsToPlaylist(List<List<TrackSimplified>> newSongs) throws SpotifyWebApiException, IOException, InterruptedException {
		int songsAdded = 0;
		for (List<TrackSimplified> songsInAlbum : newSongs) {
			int spliceCount = songsInAlbum.size() / Constants.PLAYLIST_ADD_LIMIT + 1;
			for (int i = 0; i < spliceCount; i++) {
				List<TrackSimplified> idSubList;
				if (i == spliceCount - 1) {
					idSubList = songsInAlbum.subList(i * Constants.PLAYLIST_ADD_LIMIT, i * Constants.PLAYLIST_ADD_LIMIT + (songsInAlbum.size() % Constants.PLAYLIST_ADD_LIMIT));
				} else {
					idSubList = songsInAlbum.subList(i * Constants.PLAYLIST_ADD_LIMIT, i * Constants.PLAYLIST_ADD_LIMIT + Constants.PLAYLIST_ADD_LIMIT);
				}
				JsonArray json = new JsonArray();
				for (TrackSimplified s : idSubList) {
					json.add(Constants.TRACK_PREFIX + s.getId());
					songsAdded++;
				}
				boolean retry = false;
				do {
					retry = false;
					try {
						api.addTracksToPlaylist(conf.getPlaylistId(), json).build().execute();
					} catch (TooManyRequestsException e) {
						retry = true;
						int timeout = e.getRetryAfter() + 1;
						Thread.sleep(timeout * Constants.SECOND_IN_MILLIS);
					}
				} while (retry);
			}
			Thread.sleep(Constants.SECOND_IN_MILLIS);
		}
		return songsAdded;
	}
	
	/**
	 * Store the list of album IDs in the database to prevent them from getting added again
	 * @param albumIDs
	 */
	private void storeAlbumIDsToDB(List<String> albumIDs) {
		if (!albumIDs.isEmpty()) {
			Connection connection = null;
			try {
				connection = DriverManager.getConnection(conf.getDbUrl());
				Statement statement = connection.createStatement();
				for (String s : albumIDs) {
					statement.executeUpdate(String.format("INSERT INTO %s(%s) VALUES('%s')", Constants.DB_TBL_ALBUMS, Constants.DB_ROW_ALBUM_IDS, s));
				}
			} catch (SQLException e) {
				log.severe(e.getMessage());
			} finally {
				try {
					if (connection != null)
						connection.close();
				} catch (SQLException e) {
					log.severe(e.getMessage());
				}
			}			
		}
	}
	
	/**
	 * Sort the album list with the default comparator
	 * @param albums
	 */
	private void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> Constants.RELEASE_COMPARATOR.reversed().compare(a1, a2));
	}
}
