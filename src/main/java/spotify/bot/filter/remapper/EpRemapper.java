package spotify.bot.filter.remapper;

import org.springframework.stereotype.Component;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class EpRemapper implements Remapper {
	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.EP;
	}

	/**
	 * For EP remapping only Singles are relevant
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return AlbumGroupExtended.SINGLE.equals(albumGroupExtended);
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(SpotifyUtils.isExtendedPlay(atp));
	}
}
