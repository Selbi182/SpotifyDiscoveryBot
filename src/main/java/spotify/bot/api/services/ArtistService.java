package spotify.bot.api.services;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;

@Service
public class ArtistService {

	private final static int MAX_FOLLOWED_ARTIST_FETCH_LIMIT = 50;

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private UserOptions userOptions;

	@Autowired
	private StaticConfig staticConfig;

	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private BotLogger log;

	/**
	 * Get all the user's followed artists
	 */
	public List<String> getFollowedArtistsIds() throws SQLException, BotException {
		// Try to fetch from cache first (if enabled)
		boolean isCache = userOptions.isCacheFollowedArtists();
		if (isCache) {
			List<String> cachedArtists = getCachedArtists();
			if (cachedArtists != null) {
				return cachedArtists;
			}
		}

		// If cache is outdated (or disabled), fetch fresh dataset
		List<Artist> followedArtists = SpotifyCall.executePaging(spotifyApi
			.getUsersFollowedArtists(ModelObjectType.ARTIST)
			.limit(MAX_FOLLOWED_ARTIST_FETCH_LIMIT));
		List<String> followedArtistIds = followedArtists.stream()
			.map(Artist::getId)
			.collect(Collectors.toList());
		BotUtils.removeNullStrings(followedArtistIds);
		if (followedArtistIds.isEmpty()) {
			log.warning("No followed artists found!");
		}
		if (isCache) {
			databaseService.updateFollowedArtistsCacheAsync(followedArtistIds);
		}
		return followedArtistIds;
	}

	/**
	 * Get the list of cached artists from the DB. Returns null if none were found
	 * or the cache is outdated
	 */
	private List<String> getCachedArtists() throws SQLException {
		List<String> cachedArtists = databaseService.getArtistCache();
		BotUtils.removeNullStrings(cachedArtists);
		if (cachedArtists != null && !cachedArtists.isEmpty()) {
			Date lastUpdatedArtistCache = staticConfig.getArtistCacheLastUpdated();
			if (lastUpdatedArtistCache != null) {
				int artistCacheTimeout = staticConfig.getArtistCacheTimeout();
				if (BotUtils.isWithinTimeoutWindow(lastUpdatedArtistCache, artistCacheTimeout)) {
					return cachedArtists;
				}
			}
		}
		return null;
	}

	/**
	 * Replace any appears_on releases' artists were preserved in
	 * {@link AlbumService#attachOriginArtistIdForAppearsOnReleases}.
	 * 
	 * @param filteredAlbums
	 * @return
	 * @throws BotException
	 */
	public List<AlbumSimplified> insertViaAppearsOnArtists(List<AlbumSimplified> filteredAlbums) throws BotException {
		List<String> relevantAppearsOnArtistsIds = filteredAlbums.stream()
			.filter(album -> AlbumGroup.APPEARS_ON.equals(album.getAlbumGroup()))
			.map(BotUtils::getFirstArtistName)
			.collect(Collectors.toList());

		Map<String, String> artistIdToName = new HashMap<>();
		List<List<String>> partition = Lists.partition(relevantAppearsOnArtistsIds, 50);
		for (List<String> p : partition) {
			Artist[] execute = SpotifyCall.execute(spotifyApi.getSeveralArtists(p.toArray(String[]::new)));
			for (Artist a : execute) {
				artistIdToName.put(a.getId(), a.getName());
			}
		}

		for (AlbumSimplified as : filteredAlbums) {
			if (AlbumGroup.APPEARS_ON.equals(as.getAlbumGroup())) {
				String viaArtistId = BotUtils.getFirstArtistName(as);
				String viaArtistName = artistIdToName.get(viaArtistId);
				if (viaArtistName != null) {
					ArtistSimplified viaArtistWithName = new ArtistSimplified.Builder()
						.setName(String.format("(%s)", viaArtistName))
						.build();
					as.getArtists()[0] = viaArtistWithName;
				}
			}
		}
		return filteredAlbums;
	}
}
