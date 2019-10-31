package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.collect.Lists;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.data.artists.GetArtistsAlbumsRequest;

import spotify.bot.Config;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class AlbumRequests {
	/**
	 * Static calls only
	 */
	private AlbumRequests() {}
	
	/**
	 * Get all album IDs of the given list of artists, mapped into album type
	 * 
	 * @param artists
	 * @return
	 * @throws Exception 
	 */
	public static List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, List<AlbumType> albumTypes) throws Exception {
		String albumTypeString = BotUtils.createAlbumTypeString(albumTypes);
		List<AlbumSimplified> albums = new ArrayList<>();
		artists.parallelStream().forEach(a -> {
			List<AlbumSimplified> albumsOfCurrentArtist = getAlbumIdsOfSingleArtist(a, albumTypeString);
			albums.addAll(albumsOfCurrentArtist);
		});
		return albums;
	}
	
	/**
	 * Return the albums of a single given artist
	 * 
	 * @param artistId
	 * @param albumType
	 * @return
	 * @throws Exception
	 */
	private static List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumTypes) {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyApiRequest.execute(new Callable<List<AlbumSimplified>>() {
			@Override
			public List<AlbumSimplified> call() throws Exception {
				List<AlbumSimplified> albumsOfCurrentArtist = new ArrayList<>();
				Paging<AlbumSimplified> albums = null;
				do {
					GetArtistsAlbumsRequest.Builder request = SpotifyApiSessionManager.api().getArtistsAlbums(artistId)
						.market(Config.getInstance().getMarket())
						.limit(Constants.DEFAULT_LIMIT)
						.album_type(albumTypes);
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
	 * @param albumsSimplifiedByType
	 * @return
	 * @throws Exception 
	 */
	public static Map<AlbumType, List<Album>> convertAlbumIdsToFullAlbums(Map<AlbumType, List<AlbumSimplified>> albumsSimplifiedByType) throws Exception {
		Map<AlbumType, List<Album>> albums = BotUtils.createAlbumTypeToListOfTMap(albumsSimplifiedByType.keySet());
		albumsSimplifiedByType.entrySet().parallelStream().forEach(as -> {
			List<List<AlbumSimplified>> partitions = Lists.partition(new ArrayList<>(as.getValue()), Constants.SEVERAL_ALBUMS_LIMIT);
			partitions.parallelStream().forEach(p -> {
				try {
					String[] ids = p.stream().map(AlbumSimplified::getId).toArray(size -> new String[size]);
					Album[] fullAlbums = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getSeveralAlbums(ids).market(Config.getInstance().getMarket()).build());
					albums.get(as.getKey()).addAll(Arrays.asList(fullAlbums));				
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		});
		
		return albums;
	}

	/**
	 * Read all albums of the given artists and album types and filter them by non-cached albums.
	 * New albums will be automatically cached.
	 * 
	 * @param followedArtists
	 * @param albumTypes
	 * @return
	 * @throws Exception
	 */
	public static List<AlbumSimplified> getNonCachedAlbumsOfArtists(List<String> followedArtists, List<AlbumType> albumTypes) throws Exception {
		List<AlbumSimplified> allAlbums = getAlbumsOfArtists(followedArtists, albumTypes);
		List<AlbumSimplified> filteredAlbums = SpotifyBotDatabase.getInstance().filterNonCachedAlbumsOnly(allAlbums);
		SpotifyBotDatabase.getInstance().cacheAlbumIdsAsync(filteredAlbums);
		return filteredAlbums;
	}
}
