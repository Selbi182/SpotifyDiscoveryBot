package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.data.artists.GetArtistsAlbumsRequest;

import spotify.bot.Config;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.util.Constants;

public class AlbumRequests {
	/**
	 * Static calls only
	 */
	private AlbumRequests() {}
	
	/**
	 * Get all album IDs of the given list of artists
	 * 
	 * @param artists
	 * @return
	 * @throws Exception 
	 */
	public static List<String> getAlbumsIdsByArtists(List<Artist> artists, AlbumType albumType) throws Exception {
		List<String> ids = new ArrayList<>();
		for (Artist a : artists) {
			List<String> albumsIdsOfCurrentArtist = SpotifyApiRequest.execute(new Callable<List<String>>() {
				@Override
				public List<String> call() throws Exception {
					List<AlbumSimplified> albumsOfCurrentArtist = new ArrayList<>();
					Paging<AlbumSimplified> albums = null;
					do {
						GetArtistsAlbumsRequest.Builder request = SpotifyApiSessionManager.api().getArtistsAlbums(a.getId())
							.market(Config.getInstance().getMarket())
							.limit(Constants.DEFAULT_LIMIT)
							.album_type(albumType.getType());
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
			Config.log().warning("No album IDs found!");
		}
		return ids;
	}

	/**
	 * Convert the given list of album IDs into fully equipped Album DTOs
	 * 
	 * @param ids
	 * @return
	 * @throws Exception 
	 */
	public static List<Album> convertAlbumIdsToFullAlbums(List<String> ids) throws Exception {
		List<Album> albums = new ArrayList<>();
		for (List<String> partition : Lists.partition(ids, Constants.SEVERAL_ALBUMS_LIMIT)) {
			String[] idSubListPrimitive = partition.toArray(new String[partition.size()]);
			Album[] fullAlbums = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getSeveralAlbums(idSubListPrimitive).market(Config.getInstance().getMarket()).build());
			albums.addAll(Arrays.asList(fullAlbums));
		}
		return albums;
	}
}
