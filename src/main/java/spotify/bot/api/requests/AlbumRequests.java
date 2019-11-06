package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.data.artists.GetArtistsAlbumsRequest;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.Config;
import spotify.bot.database.DBConstants;
import spotify.bot.database.DiscoveryDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class AlbumRequests {
	/**
	 * Config instance
	 */
	@Autowired
	private Config config;
	
	@Autowired
	private SpotifyApiWrapper spotify;
	
	@Autowired
	private DiscoveryDatabase database;

	/**
	 * Read all albums of the given artists and album groups and filter them by non-cached albums.
	 * New albums will be automatically cached.
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
		database.cacheAlbumIdsAsync(filteredAlbums);
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
		artists.parallelStream().forEach(a -> {
			List<AlbumSimplified> albumsOfCurrentArtist = getAlbumIdsOfSingleArtist(a, albumGroupString);
			albums.addAll(albumsOfCurrentArtist);
		});
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
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) {
		List<AlbumSimplified> albumsOfCurrentArtist = spotify.execute(new Callable<List<AlbumSimplified>>() {
			@Override
			public List<AlbumSimplified> call() throws Exception {
				List<AlbumSimplified> albumsOfCurrentArtist = new ArrayList<>();
				Paging<AlbumSimplified> albums = null;
				do {
					GetArtistsAlbumsRequest.Builder request = spotify.api().getArtistsAlbums(artistId)
						.market(config.getMarket())
						.limit(Constants.DEFAULT_LIMIT)
						.album_type(albumGroups);
					if (albums != null && albums.getNext() != null) {
						request = request.offset(albums.getOffset() + Constants.DEFAULT_LIMIT);
					}
					albums = request.build().execute();
					albumsOfCurrentArtist.addAll(Arrays.asList(albums.getItems()));
				} while (albums.getNext() != null);
				return albumsOfCurrentArtist;
			}
		});
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
		// Organize every album by their ID
		Map<String, AlbumSimplified> filteredAlbums = new HashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			if (as != null) {				
				filteredAlbums.put(as.getId(), as);
			}
		}
		
		// Get the cached albums and remove those from the above map
		ResultSet rs = database.fullTable(DBConstants.TABLE_ALBUM_CACHE);
		while (rs.next()) {
			filteredAlbums.remove(rs.getString(DBConstants.COL_ALBUM_IDS));
		}
		
		// Return the leftover albums
		return filteredAlbums.values().stream().collect(Collectors.toList());
	}

}
