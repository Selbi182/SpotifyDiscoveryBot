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

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.InternalServerErrorException;
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
	 * 
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
	 * Crawler entry point
	 */
	@Override
	public void run() {
		try {
			// Fetch all followed artists of the user
			List<Artist> followedArtists = getFollowedArtists();

			// Crawl for albums
			if (isPlaylistSet(conf.getPlaylistAlbums())) {
				List<List<TrackSimplified>> newAlbums = crawl(followedArtists, AlbumType.ALBUM);
				addSongsToPlaylist(newAlbums, conf.getPlaylistAlbums(), AlbumType.ALBUM);
			}

			// Crawl for singles
			if (isPlaylistSet(conf.getPlaylistSingles())) {
				List<List<TrackSimplified>> newSingles = crawl(followedArtists, AlbumType.SINGLE);
				addSongsToPlaylist(newSingles, conf.getPlaylistSingles(), AlbumType.SINGLE);
			}

			// Crawl for compilations
			if (isPlaylistSet(conf.getPlaylistCompilations())) {
				List<List<TrackSimplified>> newCompilations = crawl(followedArtists, AlbumType.COMPILATION);
				addSongsToPlaylist(newCompilations, conf.getPlaylistCompilations(), AlbumType.COMPILATION);
			}

			// Crawl for appears_on
			if (isPlaylistSet(conf.getPlaylistAppearsOn())) {
				// If intelligentAppearsOnSearch is enabled, only find those songs in
				// "appears_on" releases that have at least one followed artist as contributor.
				// Else, crawl the appears_on releases normally
				List<List<TrackSimplified>> newAppearsOn = new ArrayList<>();
				if (conf.isIntelligentAppearsOnSearch()) {
					newAppearsOn = intelligentAppearsOnSearch(followedArtists);
				} else {
					newAppearsOn = crawl(followedArtists, AlbumType.APPEARS_ON);
				}
				addSongsToPlaylist(newAppearsOn, conf.getPlaylistAppearsOn(), AlbumType.APPEARS_ON);
			}
		} catch (InterruptedException e) {
			// Error message is handled outside, so this is just a fast way-out
			return;
		} catch (SpotifyWebApiException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Search for new releases of the given album type
	 * 
	 * @param appearsOn
	 * @param string
	 * @param followedArtists
	 * 
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<List<TrackSimplified>> crawl(List<Artist> followedArtists, AlbumType albumType) throws SpotifyWebApiException, IOException, InterruptedException {
		// The process for new album searching is always the same chain of tasks:
		// 2. Fetch all raw album IDs of those artists
		// 3. Filter out all albums that were already stored in the DB
		// 4. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
		// a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
		// b. Filter out all albums not released in the lookbackDays range (default: 3 days)
		// 5. Get the songs IDs of the remaining (new) albums
		List<String> albumIds = getAlbumsIdsByArtists(followedArtists, albumType);
		albumIds = filterNonCachedAlbumsOnly(albumIds);
		List<List<TrackSimplified>> newSongs = new ArrayList<>();
		if (!albumIds.isEmpty()) {
			List<Album> albums = convertAlbumIdsToFullAlbums(albumIds);
			albums = filterNewAlbumsOnly(albums);
			sortAlbums(albums);
			newSongs = getSongIdsByAlbums(albums);
		}

		// Store the album IDs to the DB to prevent them from getting added a second time
		// This happens even if no new songs are added, because it will significantly speed up the future search processes
		storeAlbumIDsToDB(albumIds);

		// Return the found songs
		return newSongs;
	}

	private List<List<TrackSimplified>> intelligentAppearsOnSearch(List<Artist> followedArtists) throws SpotifyWebApiException, IOException, InterruptedException {
		// The process is very similar to the default crawler:
		// 1. Taking the followed artists from above, fetch all album IDs of releases that have the "appears_on" tag
		// 2. These albums are are filtered as normal (step 3+4 above)
		// 3. Then, filter only the songs of the remaining releases that have at least one followed artist as contributor
		// 4. Add the remaining songs to the adding queue
		List<String> extraAlbumIds = getAlbumsIdsByArtists(followedArtists, AlbumType.APPEARS_ON);
		extraAlbumIds = filterNonCachedAlbumsOnly(extraAlbumIds);
		List<List<TrackSimplified>> newAppearsOn = new ArrayList<>();
		if (!extraAlbumIds.isEmpty()) {
			List<Album> extraAlbums = convertAlbumIdsToFullAlbums(extraAlbumIds);
			extraAlbums = filterNewAlbumsOnly(extraAlbums);
			sortAlbums(extraAlbums);
			newAppearsOn = findFollowedArtistsSongsOnAlbums(extraAlbums, followedArtists);
		}

		// Store the album IDs to the DB to prevent them from getting added a second time
		// This happens even if no new songs are added, because it will significantly speed up the future search processes
		storeAlbumIDsToDB(extraAlbumIds);

		// Return the found songs
		return newAppearsOn;
	}

	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * 
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
	 * 
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
	 * 
	 * @throws IOException
	 */
	private void updateTokens() throws IOException {
		conf.updateTokens(api.getAccessToken(), api.getRefreshToken());
	}

	//////////////////////////////////////////////

	/**
	 * Timestamp the playlist's description (and, if set, the title) with the last time the crawling process was initiated.
	 * 
	 * @param playlistId
	 * @param updateTitle
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void timestampPlaylist(String playlistId, boolean updateTitle) throws SpotifyWebApiException, IOException, InterruptedException {
		Calendar cal = Calendar.getInstance();
		if (updateTitle) {
			SimpleDateFormat formatTitle = new SimpleDateFormat("yyyy-dd-MM", Locale.ENGLISH);
			String playlistName = api.getPlaylist(playlistId).build().execute().getName();
			int timestampIndex = playlistName.indexOf('[');
			if (timestampIndex > 0) {
				playlistName = playlistName.substring(0, timestampIndex);
			}
			String playlistNameNew = String.format("%s [%s]", playlistName.trim(), formatTitle.format(cal.getTime()).trim());
			api.changePlaylistsDetails(playlistId).name(playlistNameNew).build().execute();
		}
		
		SimpleDateFormat formatDescription = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);
		String newDescription = String.format("Last Search: %s", formatDescription.format(cal.getTime()));
		boolean retry = false;
		do {
			retry = false;
			try {
				api.changePlaylistsDetails(playlistId).description(newDescription).build().execute();
			} catch (TooManyRequestsException e) {
				retry = true;
				int timeout = e.getRetryAfter() + 1;
				Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
			}
		} while (retry);
	}

	/**
	 * Get all the user's followed artists
	 * 
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
				Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
			}
		} while (retry);
		if (followedArtists.isEmpty()) {
			log.warning("No followed artists found!");
		}
		return followedArtists;
	}

	/**
	 * Get all album IDs of the given list of artists
	 * 
	 * @param artists
	 * @return
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<String> getAlbumsIdsByArtists(List<Artist> artists, AlbumType albumType) throws SpotifyWebApiException, IOException, InterruptedException {
		List<String> ids = new ArrayList<>();
		for (Artist a : artists) {
			boolean retry = false;
			Paging<AlbumSimplified> albums = null;
			List<AlbumSimplified> albumsOfCurrentArtist = new ArrayList<>();
			do {
				retry = false;
				try {
					do {
						if (albums != null && albums.getNext() != null) {
							albums = api.getArtistsAlbums(a.getId()).market(conf.getMarket()).limit(Constants.DEFAULT_LIMIT).album_type(albumType.getType()).offset(albums.getOffset() + Constants.DEFAULT_LIMIT).build().execute();
						} else {
							albums = api.getArtistsAlbums(a.getId()).market(conf.getMarket()).limit(Constants.DEFAULT_LIMIT).album_type(albumType.getType()).build().execute();
						}
						albumsOfCurrentArtist.addAll(Arrays.asList(albums.getItems()));
					} while (albums.getNext() != null);
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
				}
			} while (retry);
			for (AlbumSimplified as : albums.getItems()) {
				ids.add(as.getId());
			}
		}
		if (ids.isEmpty()) {
			log.warning("No album IDs found!");
		}
		return ids;
	}

	/**
	 * Filter out all album IDs not currently present in the database
	 * 
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
	 * 
	 * @param ids
	 * @return
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<Album> convertAlbumIdsToFullAlbums(List<String> ids) throws SpotifyWebApiException, IOException, InterruptedException {
		List<Album> albums = new ArrayList<>();
		for (List<String> partition : Lists.partition(ids, Constants.SEVERAL_ALBUMS_LIMIT)) {
			String[] idSubListPrimitive = partition.toArray(new String[partition.size()]);
			boolean retry = false;
			Album[] fullAlbums = null;
			do {
				retry = false;
				try {
					fullAlbums = api.getSeveralAlbums(idSubListPrimitive).market(conf.getMarket()).build().execute();
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
				}
			} while (retry);
			albums.addAll(Arrays.asList(fullAlbums));
		}
		return albums;
	}

	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
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
	 * 
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
					do {
						if (albumTracks != null && albumTracks.getNext() != null) {
							albumTracks = api.getAlbumsTracks(a.getId()).limit(Constants.DEFAULT_LIMIT).offset(albumTracks.getOffset() + Constants.DEFAULT_LIMIT).build().execute();
						} else {
							albumTracks = api.getAlbumsTracks(a.getId()).limit(Constants.DEFAULT_LIMIT).build().execute();
						}
						currentList.addAll(Arrays.asList(albumTracks.getItems()));
					} while (albumTracks.getNext() != null);
				} catch (TooManyRequestsException e) {
					retry = true;
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
				}
			} while (retry);
			tracksByAlbums.add(currentList);
		}
		return tracksByAlbums;
	}

	/**
	 * Find all releases marked as "appears_on" by the given list of artists, but filter the result such that only songs of artists you follow are preserved. Also filter out any compilation appearances.
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
	 * 
	 * @param playlistId
	 * @param albumType
	 * @param songs
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void addSongsToPlaylist(List<List<TrackSimplified>> newSongs, String playlistId, AlbumType albumType) throws SpotifyWebApiException, IOException, InterruptedException {
		if (!newSongs.isEmpty()) {
			int songsAdded = 0;
			AddToPlaylistLoop: for (List<TrackSimplified> songsInAlbum : newSongs) {
				for (List<TrackSimplified> partition : Lists.partition(songsInAlbum, Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					boolean retry = false;
					do {
						retry = false;
						try {
							api.addTracksToPlaylist(playlistId, json).position(0).build().execute();
							songsAdded += json.size();
						} catch (TooManyRequestsException e) {
							retry = true;
							int timeout = e.getRetryAfter() + 1;
							Thread.sleep(timeout * Constants.RETRY_TIMEOUT);
						} catch (BadRequestException e) {
							e.printStackTrace();
						} catch (InternalServerErrorException e) {
							int playlistSize = api.getPlaylist(playlistId).build().execute().getTracks().getTotal();
							if (playlistSize >= Constants.PLAYLIST_SIZE_LIMIT) {
								log.severe(albumType.toString() + " playlist is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ".");
								break AddToPlaylistLoop;
							}
						}
					} while (retry);
				}
				Thread.sleep(Constants.RETRY_TIMEOUT);
			}
			if (songsAdded > 0) {
				log.info("> " + songsAdded + " new " + albumType.toString() + " song" + (songsAdded == 1 ? "" : "s") + " added!");
				timestampPlaylist(playlistId, true);
				return;
			}
		}

		timestampPlaylist(playlistId, false);
	}

	/**
	 * Store the list of album IDs in the database to prevent them from getting added again
	 * 
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
	 * 
	 * @param albums
	 */
	private void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> Constants.RELEASE_COMPARATOR.compare(a1, a2));
	}

	/**
	 * Check if the given playlistId has been set
	 */
	private boolean isPlaylistSet(String playlistId) {
		return playlistId != null && !playlistId.trim().isEmpty();
	}
}
