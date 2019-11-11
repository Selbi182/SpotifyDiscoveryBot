package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class AlbumService {

	@Autowired
	private Config config;

	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private SpotifyApi spotifyApi;

	private ReleaseValidator releaseValidator;

	@PostConstruct
	private void init() throws SQLException, IOException {
		releaseValidator = new ReleaseValidator(config.getUserConfig().getLookbackDays());
	}

	/**
	 * Read all albums of the given artists and album groups and filter them by
	 * non-cached albums. New albums will be automatically cached.
	 * 
	 * @param followedArtists
	 * @param albumGroups
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 */
	public List<AlbumSimplified> getNonCachedAlbumsOfArtists(List<String> followedArtists, List<AlbumGroup> albumGroups)
		throws IOException, SQLException, SpotifyWebApiException, InterruptedException {
		List<AlbumSimplified> allAlbums = getAlbumsOfArtists(followedArtists, albumGroups);
		List<AlbumSimplified> filteredAlbums = filterNonCachedAlbumsOnly(allAlbums);
		BotUtils.removeNulls(filteredAlbums);
		databaseService.cacheAlbumIdsAsync(filteredAlbums);
		return filteredAlbums;
	}

	/**
	 * Get all album IDs of the given list of artists, mapped into album group
	 * 
	 * @param artists
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, List<AlbumGroup> albumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		String albumGroupString = BotUtils.createAlbumGroupString(albumGroups);
		List<AlbumSimplified> albums = new ArrayList<>();
		for (String a : artists) {
			List<AlbumSimplified> albumsOfCurrentArtist = getAlbumIdsOfSingleArtist(a, albumGroupString);
			albums.addAll(albumsOfCurrentArtist);
		}
		return albums;
	}

	/**
	 * Return the albums of a single given artist
	 * 
	 * @param artistId
	 * @param albumGroup
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(config.getUserConfig().getMarket())
			.limit(Constants.DEFAULT_LIMIT)
			.album_type(albumGroups));
		return albumsOfCurrentArtist;
	}

	/**
	 * Filter out all album IDs not currently present in the database
	 * 
	 * @param albumsSimplified
	 * @return
	 * @throws SQLException
	 */
	private List<AlbumSimplified> filterNonCachedAlbumsOnly(List<AlbumSimplified> albumsSimplified) throws IOException, SQLException {
		Map<String, AlbumSimplified> filteredAlbums = new HashMap<>();
		for (AlbumSimplified as : albumsSimplified) {
			if (as != null) {
				filteredAlbums.put(as.getId(), as);
			}
		}

		List<String> albumCache = databaseService.getAlbumCache();
		for (String id : albumCache) {
			filteredAlbums.remove(id);
		}

		return filteredAlbums.values().stream().collect(Collectors.toList());
	}

	///////////////////

	/**
	 * Categorize the list of given albums by album group and filter them by new
	 * albums only (aka. those which weren't previously cached but still too old,
	 * such as re-releases)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public Map<AlbumGroup, List<AlbumSimplified>> categorizeAndFilterAlbums(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) throws IOException, SQLException {
		Map<AlbumGroup, List<AlbumSimplified>> categorizedAlbums = categorizeAlbumsByAlbumGroup(albumsSimplified, albumGroups);
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = filterNewAlbumsOnly(categorizedAlbums);
		return filteredAlbums;
	}

	/**
	 * Categorizes the given list of albums into a map of their respective album
	 * GROUPS (aka the return context of the simplified album object)
	 * 
	 * @param albumsSimplified
	 * @param albumGroups
	 * @return
	 */
	private Map<AlbumGroup, List<AlbumSimplified>> categorizeAlbumsByAlbumGroup(List<AlbumSimplified> albumsSimplified, List<AlbumGroup> albumGroups) {
		Map<AlbumGroup, List<AlbumSimplified>> categorized = BotUtils.createAlbumGroupToListOfTMap(albumGroups);
		albumsSimplified.parallelStream().forEach(as -> {
			AlbumGroup albumGroupOfAlbum = as.getAlbumGroup();
			if (albumGroupOfAlbum != null) {
				categorized.get(albumGroupOfAlbum).add(as);
			}
		});
		return categorized;
	}

	/**
	 * Filter out all albums not released within the lookbackDays range
	 * 
	 * @param albumsSimplifiedByGroup
	 * @return
	 */
	private Map<AlbumGroup, List<AlbumSimplified>> filterNewAlbumsOnly(Map<AlbumGroup, List<AlbumSimplified>> albumsSimplifiedByGroup) {
		Map<AlbumGroup, List<AlbumSimplified>> filteredAlbums = BotUtils.createAlbumGroupToListOfTMap(albumsSimplifiedByGroup.keySet());
		albumsSimplifiedByGroup.entrySet().stream().forEach(fa -> {
			List<AlbumSimplified> filteredAlbumsOfGroup = fa.getValue().stream().filter(as -> releaseValidator.isValidDate(as)).collect(Collectors.toList());
			filteredAlbums.get(fa.getKey()).addAll(filteredAlbumsOfGroup);
		});
		return filteredAlbums;
	}

	/////////////

	/**
	 * This class contains the date-based logic to decide whether or not a release
	 * is eliglible to be marked as "new" or not
	 */
	private class ReleaseValidator {
		private Set<String> validDates;
		private String validMonthDate;

		private ReleaseValidator(int lookbackDays) {
			Calendar cal = Calendar.getInstance();

			SimpleDateFormat monthPrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_MONTH);
			this.validMonthDate = monthPrecision.format(cal.getTime());

			SimpleDateFormat datePrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_DAY);
			this.validDates = new HashSet<>();
			for (int i = 0; i < lookbackDays; i++) {
				validDates.add(datePrecision.format(cal.getTime()));
				cal.add(Calendar.DAY_OF_MONTH, -1);
			}
		}

		/**
		 * Returns true if the album's given release date is within the previously
		 * specified lookbackDays range
		 * 
		 * @param a
		 * @return
		 */
		private boolean isValidDate(AlbumSimplified a) {
			if (a != null) {
				if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
					return validDates.contains(a.getReleaseDate());
				} else if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.MONTH)) {
					return validMonthDate.equals(a.getReleaseDate());
				}
			}
			return false;
		}
	}

}
