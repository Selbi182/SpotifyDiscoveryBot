package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.util.BotUtils;

@Service
public class AlbumService {

	private final static int MAX_ALBUM_FETCH_LIMIT = 50;

	@Autowired
	private Config config;

	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private SpotifyApi spotifyApi;

	/**
	 * Read all albums of the given artists and album groups and filter them by
	 * non-cached albums.
	 * 
	 * @param followedArtists
	 * @param albumGroups
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 */
	public List<AlbumSimplified> getNonCachedAlbumsOfArtists(List<String> followedArtists, List<AlbumGroup> albumGroups)
		throws IOException, SQLException, SpotifyWebApiException, InterruptedException {
		List<AlbumSimplified> allAlbums = getAlbumsOfArtists(followedArtists, albumGroups);
		List<AlbumSimplified> filteredAlbums = filterNonCachedAlbumsOnly(allAlbums);
		BotUtils.removeNulls(filteredAlbums);
		return filteredAlbums;
	}

	/**
	 * Get all album IDs of the given list of artists, mapped into album group
	 * 
	 * @param artists
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, List<AlbumGroup> albumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		String albumGroupString = BotUtils.createAlbumGroupString(albumGroups);
		List<AlbumSimplified> albums = new ArrayList<>();
		for (String a : artists) {
			List<AlbumSimplified> albumsOfCurrentArtist = getAlbumIdsOfSingleArtist(a, albumGroupString);
			albums.addAll(albumsOfCurrentArtist);
		}
		return albums;
	}

	/**
	 * Return the albums of a single given artist
	 * 
	 * @param artistId
	 * @param albumGroup
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(config.getUserConfig().getMarket())
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
		return albumsOfCurrentArtist;
	}

	/**
	 * Filter out all album IDs not currently present in the database
	 * 
	 * @param albumsSimplified
	 * @return
	 * @throws SQLException
	 */
	private List<AlbumSimplified> filterNonCachedAlbumsOnly(List<AlbumSimplified> albumsSimplified) throws IOException, SQLException {
		Map<String, AlbumSimplified> filteredAlbums = new HashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			if (as != null) {
				filteredAlbums.put(as.getId(), as);
			}
		}

		List<String> albumCache = databaseService.getAlbumCache();
		for (String id : albumCache) {
			filteredAlbums.remove(id);
		}

		return filteredAlbums.values().stream().collect(Collectors.toList());
	}

	////////////
	
	public void cacheAlbumIds(List<AlbumSimplified> album) throws SQLException {
		databaseService.cacheAlbumIdsAsync(album);		
	}
}
