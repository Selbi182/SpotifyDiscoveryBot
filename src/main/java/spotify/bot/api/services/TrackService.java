package spotify.bot.api.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.AudioFeatures;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.BotException;
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
	 */
	public List<AlbumTrackPair> getTracksOfAlbums(List<AlbumSimplified> albums) throws BotException {
		List<AlbumTrackPair> atps = new ArrayList<>();
		for (AlbumSimplified as : albums) {
			AlbumTrackPair tracksOfSingleAlbum = getTracksOfSingleAlbum(as);
			atps.add(tracksOfSingleAlbum);
		}
		return atps;
	}

	/**
	 * Get the tracks of the given album
	 * 
	 * @param album
	 * @return
	 */
	private AlbumTrackPair getTracksOfSingleAlbum(AlbumSimplified album) throws BotException {
		List<TrackSimplified> tracksOfAlbum = SpotifyCall.executePaging(spotifyApi
			.getAlbumsTracks(album.getId())
			.limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT));
		return new AlbumTrackPair(album, tracksOfAlbum);
	}

	/**
	 * Get the audio features for every track in the given list
	 * 
	 * @param tracks
	 * @return
	 */
	public List<AudioFeatures> getAudioFeatures(List<TrackSimplified> tracks) {
		try {
			String[] trackIds = tracks.stream().map(TrackSimplified::getId).toArray(String[]::new);
			AudioFeatures[] audioFeatures = SpotifyCall.execute(spotifyApi.getAudioFeaturesForSeveralTracks(trackIds));
			return Arrays.asList(audioFeatures);
		} catch (BotException e) {
			log.stackTrace(e);
		}
		return null;
	}
}
