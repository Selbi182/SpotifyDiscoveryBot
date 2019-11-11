package spotify.bot.api.requests;

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
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class AlbumRequests {

	@Autowired
	private Config config;
	
	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private SpotifyApi spotifyApi;

	/**
	 * Read all albums of the given artists and album groups and filter them by
	 * non-cached albums. New albums will be automatically cached.
	 * 
	 * @param followedArtists
	 * @param albumGroups
	 * @return
	 * @throws Exception
	 */
	public List<AlbumSimplified> getNonCachedAlbumsOfArtists(List<String> followedArtists, List<AlbumGroup> albumGroups) throws Exception {
		List<AlbumSimplified> allAlbums = getAlbumsOfArtists(followedArtists, albumGroups);
		List<AlbumSimplified> filteredAlbums = filterNonCachedAlbumsOnly(allAlbums);
		BotUtils.removeNulls(filteredAlbums);
		databaseService.cacheAlbumIdsAsync(filteredAlbums);
		return filteredAlbums;
	}

	/**
	 * Get all album IDs of the given list of artists, mapped into album group
	 * 
	 * @param artists
	 * @return
	 * @throws Exception
	 */
	private List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, List<AlbumGroup> albumGroups) throws Exception {
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
	 * @throws Exception
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) throws Exception {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(config.getUserConfig().getMarket())
			.limit(Constants.DEFAULT_LIMIT)
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

}
