package spotify.bot.util;

import java.util.Comparator;
import java.util.List;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

/**
 * Container class to map a simplified album by its simplified tracks
 */
public class AlbumTrackPair implements Comparable<AlbumTrackPair>, Comparator<AlbumTrackPair> {
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

	/////////////
	
	private final static Comparator<AlbumTrackPair> COMPARATOR_ALBUM_TYPE = Comparator.comparing(atp -> atp.getAlbum().getAlbumType());
	private final static Comparator<AlbumTrackPair> COMPARATOR_RELEASE_DATE = Comparator.comparing(atp -> atp.getAlbum().getReleaseDate());
	private final static Comparator<AlbumTrackPair> COMPARATOR_TRACK_COUNT = Comparator.comparing(atp -> atp.getTracks().size(), Comparator.reverseOrder());
	private final static Comparator<AlbumTrackPair> COMPARATOR_FIRST_ARTIST_NAME = Comparator.comparing(atp -> atp.getAlbum().getArtists()[0].getName(), Comparator.reverseOrder());
	private final static Comparator<AlbumTrackPair> COMPARATOR_ALBUM_NAME = Comparator.comparing(atp -> atp.getAlbum().getName(), Comparator.reverseOrder());
		
	private final static Comparator<AlbumTrackPair> ATP_COMPARATOR = 
		COMPARATOR_ALBUM_TYPE
		.thenComparing(COMPARATOR_RELEASE_DATE)
		.thenComparing(COMPARATOR_TRACK_COUNT)
		.thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
		.thenComparing(COMPARATOR_ALBUM_NAME);
	
	@Override
	public int compare(AlbumTrackPair o1, AlbumTrackPair o2) {
		return ATP_COMPARATOR.compare(o1, o2);
	}

	@Override
	public int compareTo(AlbumTrackPair o) {
		return compare(this, o);
	}
}
