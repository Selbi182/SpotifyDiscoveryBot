package spotify.bot.filter.remapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.filter.FilterService;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class RereleaseRemapper implements Remapper {

	@Autowired
	private FilterService filterService;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.RE_RELEASE;
	}

	/**
	 * Only rereleased albums are relevent. While singles and EPs get rereleased
	 * too, they are well less interesting.
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return AlbumGroupExtended.ALBUM.equals(albumGroupExtended);
	}

	/**
	 * Return true if the album would be dropped in
	 * {@code FilterService#filterNewAlbumsOnly} if this rerelease remapper were
	 * disabled.
	 */
	@Override
	public boolean qualifiesAsRemappable(AlbumTrackPair atp) {
		AlbumSimplified album = atp.getAlbum();
		boolean isAnOldRelease = !filterService.isValidDate(album);
		return isAnOldRelease;
	}

	/**
	 * @deprecated Unsupported operation, as rerelease remapping needs information
	 *             about the album release date, not just the tracks. Use
	 *             {@link RereleaseRemapper#qualifiesAsRemappable(AlbumTrackPair)}
	 */
	@Deprecated
	@Override
	public boolean qualifiesAsRemappable(String albumTitle, List<TrackSimplified> tracks) {
		throw new UnsupportedOperationException("Use qualifiesAsRemappable(AlbumTrackPair) instead");
	}
}
