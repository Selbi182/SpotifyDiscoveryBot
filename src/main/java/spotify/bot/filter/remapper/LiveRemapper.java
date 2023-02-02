package spotify.bot.filter.remapper;

import org.springframework.stereotype.Component;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.services.TrackService;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class LiveRemapper implements Remapper {

	private final TrackService trackService;

	public LiveRemapper(TrackService trackService) {
		this.trackService = trackService;
	}

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.LIVE;
	}

	/**
	 * Any non-extended album group except appears_on qualifies as relevant for Live
	 * remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return !albumGroupExtended.isExtendedType() && !AlbumGroupExtended.APPEARS_ON.equals(albumGroupExtended);
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(BotUtils.isLiveRelease(atp, trackService));
	}
}
