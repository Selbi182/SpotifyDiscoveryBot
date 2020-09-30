package spotify.bot.filter.remapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.filter.FilterService;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class RereleaseRemapper implements Remapper {

	@Autowired
	private FilterService filterService;

	@Autowired
	private SpotifyApiConfig spotifyApiConfig;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.RE_RELEASE;
	}

	/**
	 * Only rereleased albums are relevent. While singles and EPs get rereleased
	 * too, they are way less interesting.
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return AlbumGroupExtended.ALBUM.equals(albumGroupExtended);
	}

	/**
	 * A rerelease qualifies as remappable if all of these criteria are met:
	 * <ul>
	 * <li>Only albums are allowed (this is already covered by
	 * {@link RereleaseRemapper#isAllowedAlbumGroup})</li>
	 * <li>Must have a suitably low release date (e.g. if the album would be dropped
	 * in {@link FilterService#filterNewAlbumsOnly} if this remapper were off)</li>
	 * <li>NONE of the tracks of the album are missing in the current market (since
	 * a lot of rereleases for some reason have only some of the tracks
	 * available)</li>
	 * </ul>
	 * If a rerelease is old enough but does not have all songs available in the
	 * current market, it will should be erased (these are usually glitchy or
	 * otherwise garbage rereleases absolutely no one cares about).
	 */
	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		AlbumSimplified album = atp.getAlbum();
		boolean isAnOldRelease = !filterService.isValidDate(album);
		if (isAnOldRelease) {
			boolean allAvailable = atp.getTracks().stream().allMatch(this::isTrackAvailable);
			if (allAvailable) {
				return Action.REMAP;
			}
			return Action.ERASE;
		}
		return Action.NONE;
	}

	private boolean isTrackAvailable(TrackSimplified ts) {
		CountryCode requiredMarket = spotifyApiConfig.getMarket();
		for (CountryCode market : ts.getAvailableMarkets()) {
			if (requiredMarket.equals(market)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @deprecated Unsupported operation, as rerelease remapping needs information
	 *             about the album release date, not just the tracks. Use
	 *             {@link RereleaseRemapper#determineRemapAction(AlbumTrackPair)}
	 */
	@Deprecated
	@Override
	public Action determineRemapAction(String albumTitle, List<TrackSimplified> tracks) {
		throw new UnsupportedOperationException("Use determineRemapAction(AlbumTrackPair) instead");
	}
}
