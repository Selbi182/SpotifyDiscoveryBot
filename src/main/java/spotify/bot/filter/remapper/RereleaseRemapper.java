package spotify.bot.filter.remapper;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.filter.FilterService;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.services.UserService;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class RereleaseRemapper implements Remapper {

	private final static Pattern ALBUM_TITLE_MATCHER = Pattern
		.compile("(anniversary|re\\W?(issue|master|record)|\\d+\\W+(jahr|year))",
			Pattern.CASE_INSENSITIVE);

	private final FilterService filterService;
	private final UserService userService;
	private final DatabaseService databaseService;

	private Set<String> releaseNamesCache;

	public RereleaseRemapper(FilterService filterService, UserService userService, DatabaseService databaseService) {
		this.filterService = filterService;
		this.userService = userService;
		this.databaseService = databaseService;
		this.releaseNamesCache = Set.of();
	}

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.RE_RELEASE;
	}

	/**
	 * Any non-extended album group qualifies as relevant for Rerelease remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return !albumGroupExtended.isExtendedType();
	}

	/**
	 * Determine the action to apply for the album, whether it qualifies as
	 * rerelease or might even be disposable trash as a result of a weird,
	 * incomplete reupload. Full chart:
	 * 
	 * <pre>
	 * CACHED | NORMAL | COMPLETE | RECENT || NONE | REMAP | ERASE
	 * -------|--------|----------|--------||------|-------|------
	 * no     |  yes   |  yes     |  yes   ||  x   |       |
	 * no     |  yes   |  yes     |  no    ||      |  x    |
	 * no     |  yes   |  no      |  yes   ||  x   |       |
	 * no     |  yes   |  no      |  no    ||      |       |  x
	 * no     |  no    |  yes     |  yes   ||      |  x    |
	 * no     |  no    |  yes     |  no    ||      |  x    |
	 * no     |  no    |  no      |  yes   ||      |  x    |
	 * no     |  no    |  no      |  no    ||      |       |  x
	 * -------|--------|----------|--------||------|-------|------
	 * yes    |  yes   |  yes     |  yes   ||      |  x    |
 	 * yes    |  yes   |  yes     |  no    ||      |  x    |
 	 * yes    |  yes   |  no      |  yes   ||      |       |  x
 	 * yes    |  yes   |  no      |  no    ||      |       |  x
 	 * yes    |  no    |  yes     |  yes   ||      |  x    |
 	 * yes    |  no    |  yes     |  no    ||      |  x    |
 	 * yes    |  no    |  no      |  yes   ||      |       |  x
 	 * yes    |  no    |  no      |  no    ||      |       |  x
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
		boolean cached = hasReleaseNameBeenCachedAlready(album);

		if (cached) {
			if (complete) {
				return Action.REMAP;
			} else {
				return Action.ERASE;
			}
		} else {
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
	}

	private boolean containsRereleaseWord(String albumTitle) {
		Matcher matcher = ALBUM_TITLE_MATCHER.matcher(albumTitle);
		if (matcher.find()) {
			return matcher.start() > 0;
		}
		return false;
	}

	private boolean isTrackAvailable(TrackSimplified ts) {
		CountryCode userMarket = userService.getMarketOfCurrentUser();
		CountryCode[] availableMarkets = ts.getAvailableMarkets();
		if (availableMarkets == null) {
			// Hotfix because for some reason this endpoint only returns null anymore
			return true;
		}
		return Arrays.asList(availableMarkets).contains(userMarket);
	}

	private boolean hasReleaseNameBeenCachedAlready(AlbumSimplified album) {
		return releaseNamesCache.contains(SpotifyUtils.albumIdentifierString(album));
	}

	public void refreshCachedReleaseNames() {
		try {
			this.releaseNamesCache = Set.copyOf(databaseService.getReleaseNamesCache());
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
