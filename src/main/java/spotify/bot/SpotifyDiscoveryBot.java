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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.ini4j.InvalidFileFormatException;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.InternalServerErrorException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.albums.GetAlbumsTracksRequest;
import com.wrapper.spotify.requests.data.artists.GetArtistsAlbumsRequest;
import com.wrapper.spotify.requests.data.follow.GetUsersFollowedArtistsRequest;

import spotify.util.ApiRequest;
import spotify.util.BotUtils;
import spotify.util.Constants;

public class SpotifyDiscoveryBot implements Runnable {

	// Local variables
	private SpotifyApi spotify;
	private Config config;
	private Logger log;

	/**
	 * Initialize the app
	 * 
	 * @throws InvalidFileFormatException
	 * @throws Exception
	 */
	public SpotifyDiscoveryBot(Config config) throws Exception {
		// Set local variables
		this.config = config;
		this.log = config.getLog();
		this.spotify = config.getSpotifyApi();

		// Set tokens, if preexisting
		spotify.setAccessToken(config.getAccessToken());
		spotify.setRefreshToken(config.getRefreshToken());

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
			final List<Artist> followedArtists = getFollowedArtists();

			// Set up the crawl threads
			Thread tAlbum = new Thread(crawlThread(followedArtists, config.getPlaylistAlbums(), AlbumType.ALBUM), AlbumType.ALBUM.getType());
			Thread tSingle = new Thread(crawlThread(followedArtists, config.getPlaylistSingles(), AlbumType.SINGLE), AlbumType.SINGLE.getType());
			Thread tCompilation = new Thread(crawlThread(followedArtists, config.getPlaylistCompilations(), AlbumType.COMPILATION), AlbumType.COMPILATION.getType());
			Thread tAppearsOn = new Thread(new Runnable() {
				@Override
				public void run() {
					// Crawl for appears_on
					if (BotUtils.isPlaylistSet(config.getPlaylistAppearsOn())) {
						try {
							// If intelligentAppearsOnSearch is enabled, only find those songs in
							// "appears_on" releases that have at least one followed artist as contributor.
							// Else, crawl the appears_on releases normally
							List<List<TrackSimplified>> newAppearsOn = new ArrayList<>();
							if (config.isIntelligentAppearsOnSearch()) {
								newAppearsOn = intelligentAppearsOnSearch(followedArtists);
							} else {
								newAppearsOn = crawl(followedArtists, AlbumType.APPEARS_ON);
							}
							addSongsToPlaylist(newAppearsOn, config.getPlaylistAppearsOn(), AlbumType.APPEARS_ON);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}, AlbumType.APPEARS_ON.getType());
			
			// Start all crawlers
			tAlbum.start();
			tSingle.start();
			tCompilation.start();
			tAppearsOn.start();
			
			// Wait for them all to finish
			tAlbum.join();
			tSingle.join();
			tCompilation.join();
			tAppearsOn.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create a Runnable for the most common crawl operations
	 * @param followedArtists
	 * @param playlistId
	 * @param albumType
	 * @return
	 */
	private Runnable crawlThread(List<Artist> followedArtists, String playlistId, AlbumType albumType) {
		return new Runnable() {
			@Override
			public void run() {
				if (BotUtils.isPlaylistSet(playlistId)) {
					try {
						List<List<TrackSimplified>> newTracks = crawl(followedArtists, albumType);
						addSongsToPlaylist(newTracks, playlistId, albumType);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
	}
	
	/**
	 * Search for new releases of the given album type
	 * 
	 * @param appearsOn
	 * @param string
	 * @param followedArtists
	 * @throws Exception 
	 */
	private List<List<TrackSimplified>> crawl(List<Artist> followedArtists, AlbumType albumType) throws Exception {
		// The process for new album searching is always the same chain of tasks:
		// 1. Fetch all raw album IDs of those artists
		// 2. Filter out all albums that were already stored in the DB
		// 3. Any remaining songs are potential adding candidates that now need to be filtered by new-songs-only
		//    a. Convert the album IDs into fully detailed album DTOs (to gain access to the release date)
		//    b. Filter out all albums not released in the lookbackDays range (default: 3 days)
		// 4. Get the songs IDs of the remaining (new) albums
		List<String> albumIds = getAlbumsIdsByArtists(followedArtists, albumType);
		albumIds = filterNonCachedAlbumsOnly(albumIds);
		List<List<TrackSimplified>> newSongs = new ArrayList<>();
		if (!albumIds.isEmpty()) {
			List<Album> albums = convertAlbumIdsToFullAlbums(albumIds);
			albums = filterNewAlbumsOnly(albums);
			BotUtils.sortAlbums(albums);
			newSongs = getSongIdsByAlbums(albums);
		}

		// Store the album IDs to the DB to prevent them from getting added a second time
		// This happens even if no new songs are added, because it will significantly speed up the future search processes
		storeAlbumIDsToDB(albumIds);

		// Return the found songs
		return newSongs;
	}

	private List<List<TrackSimplified>> intelligentAppearsOnSearch(List<Artist> followedArtists) throws Exception {
		// The process is very similar to the default crawler:
		// 1. Taking the followed artists, fetch all album IDs of releases that have the "appears_on" tag
		// 2. These albums are are filtered as normal (step 2+3 above)
		// 3. Then, filter only the songs of the remaining releases that have at least one followed artist as contributor
		// 4. Add the remaining songs to the adding queue
		List<String> extraAlbumIds = getAlbumsIdsByArtists(followedArtists, AlbumType.APPEARS_ON);
		extraAlbumIds = filterNonCachedAlbumsOnly(extraAlbumIds);
		List<List<TrackSimplified>> newAppearsOn = new ArrayList<>();
		if (!extraAlbumIds.isEmpty()) {
			List<Album> extraAlbums = convertAlbumIdsToFullAlbums(extraAlbumIds);
			extraAlbums = filterNewAlbumsOnly(extraAlbums);
			BotUtils.sortAlbums(extraAlbums);
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
	 * @throws Exception 
	 */
	private void authenticate() throws Exception {
		URI uri = ApiRequest.execute(spotify.authorizationCodeUri().scope(Constants.SCOPES).build());
		log.info(uri.toString());

		Scanner scanner = new Scanner(System.in);
		String code = scanner.nextLine().replace(spotify.getRedirectURI().toString() + "?code=", "").trim();
		scanner.close();

		AuthorizationCodeCredentials acc = ApiRequest.execute(spotify.authorizationCode(code).build());
		spotify.setAccessToken(acc.getAccessToken());
		spotify.setRefreshToken(acc.getRefreshToken());

		updateTokens();
	}

	/**
	 * Refresh the access token
	 * @throws Exception 
	 */
	private void refreshAccessToken() throws Exception {
		AuthorizationCodeCredentials acc = ApiRequest.execute(spotify.authorizationCodeRefresh().build());
		spotify.setAccessToken(acc.getAccessToken());
		updateTokens();
	}

	/**
	 * Store the access and refresh tokens in the INI file
	 * 
	 * @throws IOException
	 */
	private void updateTokens() throws IOException {
		config.updateTokens(spotify.getAccessToken(), spotify.getRefreshToken());
	}

	//////////////////////////////////////////////

	/**
	 * Timestamp the playlist's description (and, if set, the title) with the last time the crawling process was initiated.
	 * 
	 * @param playlistId
	 * @throws Exception 
	 */
	private void timestampPlaylist(String playlistId) throws Exception {
		Calendar cal = Calendar.getInstance();
		
		Playlist p = ApiRequest.execute(spotify.getPlaylist(playlistId).build());
		String playlistName = p.getName().replace(Constants.NEW_INDICATOR_TEXT, "").trim();
		PlaylistTrack[] playlistTracks = p.getTracks().getItems();
		if (playlistTracks.length > 0) {
			Date mostRecentAdditionDate = playlistTracks[0].getAddedAt();
			Calendar calOld = Calendar.getInstance();
			calOld.setTime(mostRecentAdditionDate);
			calOld.add(Calendar.HOUR_OF_DAY, config.getNewNotificationTimeout());
			if (cal.before(calOld)) {
				playlistName = String.format("%s %s", playlistName, Constants.NEW_INDICATOR_TEXT);
			}
		}
		
		String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(cal.getTime()));
		
		ApiRequest.execute(spotify.changePlaylistsDetails(playlistId).name(playlistName).description(newDescription).build());			
	}

	/**
	 * Get all the user's followed artists
	 * 
	 * @return
	 * @throws Exception
	 */
	private List<Artist> getFollowedArtists() throws Exception {
		List<Artist> followedArtists = ApiRequest.execute(new Callable<List<Artist>>() {
			@Override
			public List<Artist> call() throws Exception {
				List<Artist> followedArtists = new ArrayList<>();
				PagingCursorbased<Artist> artists = null;
				do {
					GetUsersFollowedArtistsRequest.Builder request = spotify.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT);
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						request = request.after(after);
					}
					artists = request.build().execute();
					followedArtists.addAll(Arrays.asList(artists.getItems()));
				} while (artists.getNext() != null);
				return followedArtists;
			}
		});
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
	 * @throws Exception 
	 */
	private List<String> getAlbumsIdsByArtists(List<Artist> artists, AlbumType albumType) throws Exception {
		List<String> ids = new ArrayList<>();
		for (Artist a : artists) {
			List<String> albumsIdsOfCurrentArtist = ApiRequest.execute(new Callable<List<String>>() {
				@Override
				public List<String> call() throws Exception {
					List<AlbumSimplified> albumsOfCurrentArtist = new ArrayList<>();
					Paging<AlbumSimplified> albums = null;
					do {
						GetArtistsAlbumsRequest.Builder request = spotify.getArtistsAlbums(a.getId()).market(config.getMarket()).limit(Constants.DEFAULT_LIMIT).album_type(albumType.getType());
						if (albums != null && albums.getNext() != null) {
							request = request.offset(albums.getOffset() + Constants.DEFAULT_LIMIT);
						}
						albums = request.build().execute();
						albumsOfCurrentArtist.addAll(Arrays.asList(albums.getItems()));
					} while (albums.getNext() != null);
					return albumsOfCurrentArtist.stream().map(AlbumSimplified::getId).collect(Collectors.toList());
				}
			});
			ids.addAll(albumsIdsOfCurrentArtist);
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
			connection = DriverManager.getConnection(config.getDbUrl());
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
	 * @throws Exception 
	 */
	private List<Album> convertAlbumIdsToFullAlbums(List<String> ids) throws Exception {
		List<Album> albums = new ArrayList<>();
		for (List<String> partition : Lists.partition(ids, Constants.SEVERAL_ALBUMS_LIMIT)) {
			String[] idSubListPrimitive = partition.toArray(new String[partition.size()]);
			Album[] fullAlbums = ApiRequest.execute(spotify.getSeveralAlbums(idSubListPrimitive).market(config.getMarket()).build());
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
		for (int i = 0; i < config.getLookbackDays(); i++) {
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
	 * @throws Exception 
	 */
	private List<List<TrackSimplified>> getSongIdsByAlbums(List<Album> albums) throws Exception {
		List<List<TrackSimplified>> tracksByAlbums = new ArrayList<>();
		for (Album a : albums) {
			List<TrackSimplified> currentList = ApiRequest.execute(new Callable<List<TrackSimplified>>() {
				@Override
				public List<TrackSimplified> call() throws Exception {
					List<TrackSimplified> currentList = new ArrayList<>();
					Paging<TrackSimplified> albumTracks = null;
					do {
						GetAlbumsTracksRequest.Builder request = spotify.getAlbumsTracks(a.getId()).limit(Constants.DEFAULT_LIMIT);
						if (albumTracks != null && albumTracks.getNext() != null) {
							request = request.offset(albumTracks.getOffset() + Constants.DEFAULT_LIMIT);
						}
						albumTracks = request.build().execute();
						currentList.addAll(Arrays.asList(albumTracks.getItems()));
					} while (albumTracks.getNext() != null);
					return currentList;
				}
			});
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
	 * @throws Exception 
	 */
	private List<List<TrackSimplified>> findFollowedArtistsSongsOnAlbums(List<Album> newAlbums, List<Artist> followedArtists) throws Exception {
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
	 * @throws Exception 
	 */
	private void addSongsToPlaylist(List<List<TrackSimplified>> newSongs, String playlistId, AlbumType albumType) throws Exception {
		if (!newSongs.isEmpty()) {
			int songsAdded = 0;
			AddToPlaylistLoop:
			for (List<TrackSimplified> songsInAlbum : newSongs) {
				for (List<TrackSimplified> partition : Lists.partition(songsInAlbum, Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					try {
						ApiRequest.execute(spotify.addTracksToPlaylist(playlistId, json).position(0).build());
					} catch (InternalServerErrorException e) {
						Playlist p = ApiRequest.execute(spotify.getPlaylist(playlistId).build());
						int playlistSize = p.getTracks().getTotal();
						if (playlistSize >= Constants.PLAYLIST_SIZE_LIMIT) {
							log.severe(albumType.toString() + " playlist is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ".");
							break AddToPlaylistLoop;
						}
					}
				}
			}
			if (songsAdded > 0) {
				log.info("> " + songsAdded + " new " + albumType.toString() + " song" + (songsAdded == 1 ? "" : "s") + " added!");
			}
		}

		timestampPlaylist(playlistId);
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
				connection = DriverManager.getConnection(config.getDbUrl());
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
}
