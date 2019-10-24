package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.albums.GetAlbumsTracksRequest;

import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class TrackRequests {
	/**
	 * Static calls only
	 */
	private TrackRequests() {}

	/**
	 * Get all songs IDs of the given list of albums, categorized by album type
	 * 
	 * @param albums
	 * @return
	 * @throws Exception
	 */
	public static Map<AlbumType, List<List<TrackSimplified>>> getSongIdsByAlbums(Map<AlbumType, List<Album>> albumsByAlbumType) {
		Map<AlbumType, List<List<TrackSimplified>>> tracksOfAlbumsByType = new ConcurrentHashMap<>();
		albumsByAlbumType.keySet().parallelStream().forEach(at -> {
			if (!tracksOfAlbumsByType.containsKey(at)) {
				tracksOfAlbumsByType.put(at, new ArrayList<>());
			}
			albumsByAlbumType.get(at).parallelStream().forEach(a -> {
				List<TrackSimplified> currentList = tracksOfAlbum(a);
				tracksOfAlbumsByType.get(at).add(currentList);
			});
		});
		return tracksOfAlbumsByType;
	}
	
	/**
	 * Get all songs IDs of the given list of albums
	 * 
	 * @param albums
	 * @return
	 * @throws Exception
	 */
	public static List<List<TrackSimplified>> getSongIdsByAlbums(List<Album> albumsByAlbumType) {
		List<List<TrackSimplified>> tracksOfAlbums = new ArrayList<>();
		albumsByAlbumType.parallelStream().forEach(a -> {
			List<TrackSimplified> currentList = tracksOfAlbum(a);
			tracksOfAlbums.add(currentList);
		});
		return tracksOfAlbums;
	}
	
	/**
	 * Get the tracks of the given album
	 * 
	 * @param album
	 * @return
	 */
	private static List<TrackSimplified> tracksOfAlbum(Album album) {
		try {
			List<TrackSimplified> tracksOfAlbum = SpotifyApiRequest.execute(new Callable<List<TrackSimplified>>() {
				@Override
				public List<TrackSimplified> call() throws Exception {
					List<TrackSimplified> currentList = new ArrayList<>();
					Paging<TrackSimplified> albumTracks = null;
					do {
						GetAlbumsTracksRequest.Builder request = SpotifyApiSessionManager.api().getAlbumsTracks(album.getId()).limit(Constants.DEFAULT_LIMIT);
						if (albumTracks != null && albumTracks.getNext() != null) {
							request = request.offset(albumTracks.getOffset() + Constants.DEFAULT_LIMIT);
						}
						albumTracks = request.build().execute();
						currentList.addAll(Arrays.asList(albumTracks.getItems()));
					} while (albumTracks.getNext() != null);
					return currentList;
				}
			});
			return tracksOfAlbum;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Find all releases marked as "appears_on" by the given list of artists, but filter the result such that only songs of artists you follow are preserved. Also filter out any compilation appearances.
	 * 
	 * @param extraAlbumIdsFiltered
	 * @param followedArtists
	 * @return
	 * @throws Exception
	 */
	public static Map<AlbumType, List<List<TrackSimplified>>> intelligentAppearsOnSearch(List<Album> newAlbums, Set<String> followedArtists) {
		List<Album> newAlbumCandidates = newAlbums.stream().filter((a) -> !BotUtils.isCollectionOrSampler(a)).collect(Collectors.toList()); 
		newAlbumCandidates = newAlbumCandidates.stream().filter(a -> !BotUtils.containsFeaturedArtist(followedArtists, a.getArtists())).collect(Collectors.toList());
		List<List<TrackSimplified>> songsByAlbums = getSongIdsByAlbums(newAlbumCandidates);
		List<List<TrackSimplified>> filteredTracks = new ArrayList<>();
		songsByAlbums.parallelStream().forEach(songsOfAlbum -> {
			List<TrackSimplified> selectedSongs = songsOfAlbum.stream()
				.filter(song -> BotUtils.containsFeaturedArtist(followedArtists, song.getArtists()))
				.collect(Collectors.toList());
			filteredTracks.add(selectedSongs);
		});
		Map<AlbumType, List<List<TrackSimplified>>> selectedSongsByAlbumAndAlbum = new HashMap<>();
		selectedSongsByAlbumAndAlbum.put(AlbumType.APPEARS_ON, filteredTracks);
		return selectedSongsByAlbumAndAlbum;
	}
}
