package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.data.artists.GetArtistsAlbumsRequest;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.Config;
import spotify.bot.config.DiscoveryDatabase;
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
	 * Get all album IDs of the given list of artists, mapped into album group
	 * 
	 * @param artists
	 * @return
	 * @throws Exception 
	 */
	public List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, List<AlbumGroup> albumGroups) throws Exception {
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
	 * Convert the given list of album IDs into fully equipped Album DTOs
	 * 
	 * @param albumsSimplifiedByGroup
	 * @return
	 * @throws Exception 
	 */
	public Map<AlbumGroup, List<Album>> convertAlbumIdsToFullAlbums(Map<AlbumGroup, List<AlbumSimplified>> albumsSimplifiedByGroup) throws Exception {
		Map<AlbumGroup, List<Album>> albums = BotUtils.createAlbumGroupToListOfTMap(albumsSimplifiedByGroup.keySet());
		albumsSimplifiedByGroup.entrySet().parallelStream().forEach(as -> {
			List<List<AlbumSimplified>> partitions = Lists.partition(new ArrayList<>(as.getValue()), Constants.SEVERAL_ALBUMS_LIMIT);
			partitions.parallelStream().forEach(p -> {
				try {
					String[] ids = p.stream().map(AlbumSimplified::getId).toArray(size -> new String[size]);
					Album[] fullAlbums = spotify.execute(spotify.api().getSeveralAlbums(ids).market(config.getMarket()).build());
					albums.get(as.getKey()).addAll(Arrays.asList(fullAlbums));				
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		});
		
		return albums;
	}

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
		List<AlbumSimplified> filteredAlbums = database.filterNonCachedAlbumsOnly(allAlbums);
		BotUtils.removeNulls(filteredAlbums);
		database.cacheAlbumIdsAsync(filteredAlbums);
		return filteredAlbums;
	}
}
