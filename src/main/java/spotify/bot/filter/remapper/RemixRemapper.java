package spotify.bot.filter.remapper;

import org.springframework.stereotype.Component;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class RemixRemapper implements Remapper {

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.REMIX;
	}

	/**
	 * Any non-extended album group qualifies as relevant for Remix remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return !albumGroupExtended.isExtendedType();
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(BotUtils.isRemix(atp));
	}
}
