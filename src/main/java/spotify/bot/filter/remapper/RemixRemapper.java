package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
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
		return albumGroupExtended.isNotExtendedType();
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(SpotifyUtils.isRemix(atp));
	}
}
