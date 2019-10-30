package spotify.bot.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

/**
 * Container class to map a simplified album by its simplified tracks
 */
public class AlbumTrackPair implements Comparable<AlbumTrackPair> {
	private AlbumSimplified album;
	private List<TrackSimplified> tracks;

	public AlbumTrackPair(AlbumSimplified album, List<TrackSimplified> tracks) {
		this.album = album;
		this.tracks = tracks;
	}

	public AlbumSimplified getAlbum() {
		return album;
	}

	public List<TrackSimplified> getTracks() {
		return tracks;
	}
	
	public int trackCount() {
		return tracks.size();
	}

	@Override
	public int compareTo(AlbumTrackPair o) {
		// TODO comparator lol
		return 0;
		
//
//		// Comparators
//		private final static Comparator<AlbumSimplified> COMPARATOR_ALBUM_TYPE = Comparator.comparing(AlbumSimplified::getAlbumType);
//		private final static Comparator<AlbumSimplified> COMPARATOR_RELEASE_DATE = Comparator.comparing(AlbumSimplified::getReleaseDate);
//		//private final static Comparator<AlbumSimplified> COMPARATOR_TRACK_COUNT = Comparator.comparing(AlbumSimplified::getTotalTracks, Comparator.reverseOrder());
//		private final static Comparator<AlbumSimplified> COMPARATOR_FIRST_ARTIST_NAME = Comparator.comparing((a) -> a.getArtists()[0].getName(), Comparator.reverseOrder());
//		private final static Comparator<AlbumSimplified> COMPARATOR_ALBUM_NAME = Comparator.comparing(AlbumSimplified::getName, Comparator.reverseOrder());
//
//		private final static Comparator<AlbumSimplified> RELEASE_COMPARATOR =
//				COMPARATOR_ALBUM_TYPE
//				.thenComparing(COMPARATOR_RELEASE_DATE)
//				//.thenComparing(COMPARATOR_TRACK_COUNT)
//				.thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
//				.thenComparing(COMPARATOR_ALBUM_NAME);
//
//		
//		for (List<AlbumSimplified> albums : albumTrackPairs) {
//			Collections.sort(albums, (a1, a2) -> RELEASE_COMPARATOR.compare(a1, a2));	
//		}
	}
}
