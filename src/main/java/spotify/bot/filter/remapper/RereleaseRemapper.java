package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;

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

	private final static Pattern ALBUM_TITLE_MATCHER = Pattern
		.compile("(anniversary|re\\W?(issue|master|issue|record)|\\d+\\W+(jahr|year))",
			Pattern.CASE_INSENSITIVE);

	private SpotifyApiConfig spotifyApiConfig;
	private FilterService filterService;

	public RereleaseRemapper(SpotifyApiConfig spotifyApiConfig, FilterService filterService) {
		this.spotifyApiConfig = spotifyApiConfig;
		this.filterService = filterService;
	}

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
	 * Determine the action to apply for the album, whether it qualifies as
	 * rerelease or might even be disposable trash as a result of a weird,
	 * incomplete reupload. Full chart:
	 * 
	 * <pre>
	 * NORMAL | COMPLETE | RECENT || NONE | REMAP | ERASE
	 *  yes   |  yes     |  yes   ||  x   |       |      
	 *  yes   |  yes     |  no    ||      |  x    |      
	 *  yes   |  no      |  yes   ||  x   |       |     
	 *  yes   |  no      |  no    ||      |       |  x         
	 *  no    |  yes     |  yes   ||      |  x    |      
	 *  no    |  yes     |  no    ||      |  x    |      
	 *  no    |  no      |  yes   ||      |  x    |      
	 *  no    |  no      |  no    ||      |       |  x
	 * </pre>
	 * 
	 * Legend:
	 * <ul>
	 * <li>NORMAL: Is the album title normal (i.e. does it not contain any giveaway
	 * terms like "Remaster", "Rerelease", "Reissue", "Rerecord", "Anniversary")?
	 * <li>COMPLETE: Are all tracks available in the current market (since a lot of
	 * rereleases for some reason have only some of the tracks available)?
	 * <li>RECENT: Is the release date young enough to be qualified as valid by
	 * {@link FilterService#filterNewAlbumsOnly} (if this remapper were disabled)?
	 * </ul>
	 */
	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		AlbumSimplified album = atp.getAlbum();
		List<TrackSimplified> tracks = atp.getTracks();

		boolean normal = !containsRereleaseWord(album.getName());
		boolean complete = tracks.stream().allMatch(this::isTrackAvailable);
		boolean recent = filterService.isValidDate(album);

		if (normal) {
			if (recent) {
				return Action.NONE;
			} else if (!complete) {
				return Action.ERASE;
			}
		} else {
			if (!complete && !recent) {
				return Action.ERASE;
			}
		}
		return Action.REMAP;
	}

	private boolean containsRereleaseWord(String albumTitle) {
		return ALBUM_TITLE_MATCHER.matcher(albumTitle).find();
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
}
