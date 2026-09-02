package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class LiveRemapper implements Remapper {
	public LiveRemapper() {
	}

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.LIVE;
	}

	/**
	 * Any non-extended album group qualifies as relevant for Live remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return albumGroupExtended.isNotExtendedType();
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(SpotifyUtils.isLiveRelease(atp));
	}
}
