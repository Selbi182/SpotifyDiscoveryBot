package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.albums.GetAlbumsTracksRequest;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.Config;
import spotify.bot.dto.AlbumTrackPair;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class TrackRequests {

	@Autowired
	private SpotifyApiWrapper spotify;

	@Autowired
	private Config config;
	
	/**
	 * Get all songs IDs of the given list of albums, categorized by album group and the source album.
	 * Appears_on might bbe treated differently
	 * 
	 * @param followedArtists 
	 * 
	 * @param albums
	 * @return
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws Exception
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> getSongIdsByAlbums(Map<AlbumGroup, List<AlbumSimplified>> albumsByAlbumGroup, List<String> followedArtists) throws IOException, SQLException {
		Map<AlbumGroup, List<AlbumTrackPair>> tracksOfAlbumsByGroup = BotUtils.createAlbumGroupToListOfTMap(albumsByAlbumGroup.keySet());
		final boolean isIntelligentAppearsOnSearch = config.isIntelligentAppearsOnSearch();
		albumsByAlbumGroup.entrySet().parallelStream().forEach(ag -> {
			AlbumGroup albumGroup = ag.getKey();
			List<AlbumSimplified> albums = ag.getValue();
			final List<AlbumTrackPair> target = tracksOfAlbumsByGroup.get(albumGroup);
			if (AlbumGroup.APPEARS_ON.equals(albumGroup) && isIntelligentAppearsOnSearch) {
				target.addAll(intelligentAppearsOnSearch(albums, followedArtists));
			} else {
				albums.parallelStream().forEach(a -> {
					target.add(tracksOfAlbum(a));
				});
			}
		});
		return tracksOfAlbumsByGroup;
	}
	
	/**
	 * Get all songs IDs of the given list of albums
	 * 
	 * @param albums
	 * @return
	 * @throws Exception
	 */
	public List<AlbumTrackPair> getSongIdsByAlbums(List<AlbumSimplified> albums) {
		List<AlbumTrackPair> tracksOfAlbums = new ArrayList<>();
		albums.parallelStream().forEach(a -> {
			AlbumTrackPair currentList = tracksOfAlbum(a);
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
	private AlbumTrackPair tracksOfAlbum(AlbumSimplified album) {
		try {
			List<TrackSimplified> tracksOfAlbum = spotify.execute(new Callable<List<TrackSimplified>>() {
				@Override
				public List<TrackSimplified> call() throws Exception {
					List<TrackSimplified> currentList = new ArrayList<>();
					Paging<TrackSimplified> albumTracks = null;
					do {
						GetAlbumsTracksRequest.Builder request = spotify.api().getAlbumsTracks(album.getId()).limit(Constants.DEFAULT_LIMIT);
						if (albumTracks != null && albumTracks.getNext() != null) {
							request = request.offset(albumTracks.getOffset() + Constants.DEFAULT_LIMIT);
						}
						albumTracks = request.build().execute();
						currentList.addAll(Arrays.asList(albumTracks.getItems()));
					} while (albumTracks.getNext() != null);
					return currentList;
				}
			});
			return new AlbumTrackPair(album, tracksOfAlbum);
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
	public List<AlbumTrackPair> intelligentAppearsOnSearch(List<AlbumSimplified> appearsOnAlbums, Collection<String> followedArtistsRaw) {
		Set<String> followedArtistsSet = new HashSet<>(followedArtistsRaw);
		
		List<AlbumSimplified> newAlbumCandidates = appearsOnAlbums.stream().filter((a) -> !BotUtils.isCollectionOrSampler(a)).collect(Collectors.toList()); 
		newAlbumCandidates = newAlbumCandidates.stream().filter(a -> !BotUtils.containsFeaturedArtist(followedArtistsSet, a.getArtists())).collect(Collectors.toList());
		List<AlbumTrackPair> songsByAlbums = getSongIdsByAlbums(newAlbumCandidates);
		List<AlbumTrackPair> filteredTracks = new ArrayList<>();
		songsByAlbums.parallelStream().forEach(albumTrackPair -> {
			List<TrackSimplified> selectedSongs = albumTrackPair.getTracks().stream()
				.filter(song -> BotUtils.containsFeaturedArtist(followedArtistsSet, song.getArtists()))
				.collect(Collectors.toList());
			filteredTracks.add(new AlbumTrackPair(albumTrackPair.getAlbum(), selectedSongs));
		});
		return filteredTracks;
	}
}
