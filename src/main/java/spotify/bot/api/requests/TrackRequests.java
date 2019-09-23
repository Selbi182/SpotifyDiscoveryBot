package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.albums.GetAlbumsTracksRequest;

import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.util.Constants;

public class TrackRequests {
	/**
	 * Static calls only
	 */
	private TrackRequests() {}

	/**
	 * Get all songs IDs of the given list of albums
	 * 
	 * @param albums
	 * @return
	 * @throws Exception
	 */
	public static List<List<TrackSimplified>> getSongIdsByAlbums(List<Album> albums) {
		try {
			List<List<TrackSimplified>> tracksByAlbums = new ArrayList<>();
			for (Album a : albums) {
				List<TrackSimplified> currentList = SpotifyApiRequest.execute(new Callable<List<TrackSimplified>>() {
					@Override
					public List<TrackSimplified> call() throws Exception {
						List<TrackSimplified> currentList = new ArrayList<>();
						Paging<TrackSimplified> albumTracks = null;
						do {
							GetAlbumsTracksRequest.Builder request = SpotifyApiSessionManager.api().getAlbumsTracks(a.getId()).limit(Constants.DEFAULT_LIMIT);
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
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Find all releases marked as "appears_on" by the given list of artists, but filter the result such that only songs of artists you follow are preserved. Also filter out any compilation appearances.
	 * 
	 * @param extraAlbumIdsFiltered
	 * @param followedArtists
	 * @return
	 * @throws Exception
	 */
	public static List<List<TrackSimplified>> findFollowedArtistsSongsOnAlbums(List<Album> newAlbums, List<Artist> followedArtists) {
		try {
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
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
