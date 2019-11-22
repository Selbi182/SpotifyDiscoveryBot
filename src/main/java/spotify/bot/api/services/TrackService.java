package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.util.BotLogger;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class TrackService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private BotLogger log;

	/**
	 * Get all songs IDs of the given list of albums, categorized as
	 * {@link AlbumTrackPair}
	 * 
	 * @param followedArtists
	 * 
	 * @param albums
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public List<AlbumTrackPair> getTracksOfAlbums(List<AlbumSimplified> albums) throws IOException, SQLException {
		List<AlbumTrackPair> atps = new ArrayList<>();
		albums.parallelStream().forEach(a -> {
			atps.add(getTracksOfSingleAlbum(a));
		});
		return atps;
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