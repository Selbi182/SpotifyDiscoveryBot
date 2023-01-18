package spotify.bot.util.data;

import java.util.Comparator;
import java.util.List;

import org.springframework.lang.NonNull;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.util.BotUtils;

/**
 * Container class to map a simplified album by its simplified tracks
 */
public class AlbumTrackPair implements Comparable<AlbumTrackPair>, Comparator<AlbumTrackPair> {
	private final AlbumSimplified album;
	private final List<TrackSimplified> tracks;

	private AlbumTrackPair(AlbumSimplified album, List<TrackSimplified> tracks) {
		this.album = album;
		this.tracks = tracks;
	}
	
	public static AlbumTrackPair of(AlbumSimplified album, List<TrackSimplified> tracks) {
		return new AlbumTrackPair(album, tracks);
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

	private final static Comparator<AlbumTrackPair> COMPARATOR_ALBUM_GROUP = Comparator.comparing(atp -> atp.getAlbum().getAlbumGroup());
	private final static Comparator<AlbumTrackPair> COMPARATOR_RELEASE_DATE = Comparator.comparing(atp -> atp.getAlbum().getReleaseDate());
	private final static Comparator<AlbumTrackPair> COMPARATOR_TRACK_COUNT = Comparator.comparing(atp -> atp.getTracks().size(), Comparator.reverseOrder());
	private final static Comparator<AlbumTrackPair> COMPARATOR_FIRST_ARTIST_NAME = Comparator.comparing(atp -> BotUtils.getFirstArtistName(atp.getAlbum()), Comparator.reverseOrder());
	private final static Comparator<AlbumTrackPair> COMPARATOR_ALBUM_NAME = Comparator.comparing(atp -> atp.getAlbum().getName(), Comparator.reverseOrder());

	private final static Comparator<AlbumTrackPair> ATP_COMPARATOR = COMPARATOR_ALBUM_GROUP.thenComparing(COMPARATOR_RELEASE_DATE).thenComparing(COMPARATOR_TRACK_COUNT).thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
		.thenComparing(COMPARATOR_ALBUM_NAME);

	@Override
	public int compare(AlbumTrackPair o1, AlbumTrackPair o2) {
		return ATP_COMPARATOR.compare(o1, o2);
	}

	@Override
	public int compareTo(@NonNull AlbumTrackPair o) {
		return compare(this, o);
	}

	@Override
	public String toString() {
		return toStringExtended(AlbumGroupExtended.fromAlbumGroup(album.getAlbumGroup()));
	}

	public String toStringExtended(AlbumGroupExtended albumGroupExtended) {
		if (album == null || tracks == null) {
			return super.toString();
		}
		String baseRepresentation = String.format("[%s] %s - %s (%s)", albumGroupExtended.toString(), BotUtils.joinArtists(album.getArtists()), album.getName(), album.getReleaseDate());
		if (tracks.size() != 1) {
			return String.format("%s <%d>", baseRepresentation, tracks.size());
		}
		return baseRepresentation;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((album == null) ? 0 : album.hashCode());
		result = prime * result + ((tracks == null) ? 0 : tracks.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AlbumTrackPair other = (AlbumTrackPair) obj;
		if (album == null) {
			if (other.album != null)
				return false;
		} else if (!album.equals(other.album))
			return false;
		if (tracks == null) {
			return other.tracks == null;
		} else
			return tracks.equals(other.tracks);
	}
}
