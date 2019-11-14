package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class TrackService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private BotLogger log;

	/**
	 * Get all songs IDs of the given list of albums, categorized by album group and
	 * the source album.
	 * 
	 * @param followedArtists
	 * 
	 * @param albums
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<AlbumGroup, List<AlbumTrackPair>> getSongsOfAlbumGroups(Map<AlbumGroup, List<AlbumSimplified>> albumsByAlbumGroup) throws IOException, SQLException {
		Map<AlbumGroup, List<AlbumTrackPair>> tracksOfAlbumsByGroup = BotUtils.createAlbumGroupToListOfTMap();
		albumsByAlbumGroup.entrySet().parallelStream().forEach(ag -> {
			AlbumGroup albumGroup = ag.getKey();
			List<AlbumSimplified> albums = ag.getValue();
			if (!albums.isEmpty()) {
				final List<AlbumTrackPair> target = tracksOfAlbumsByGroup.get(albumGroup);
				albums.parallelStream().forEach(a -> {
					target.add(getTracksOfSingleAlbum(a));
				});
			}
		});
		return tracksOfAlbumsByGroup;
	}

	/**
	 * Get the tracks of the given album
	 * 
	 * @param album
	 * @return
	 */
	private AlbumTrackPair getTracksOfSingleAlbum(AlbumSimplified album) {
		try {
			List<TrackSimplified> tracksOfAlbum = SpotifyCall.executePaging(spotifyApi.getAlbumsTracks(album.getId()).limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT));
			return new AlbumTrackPair(album, tracksOfAlbum);
		} catch (Exception e) {
			log.stackTrace(e);
		}
		return null;
	}
}
