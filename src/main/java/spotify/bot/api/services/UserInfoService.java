package spotify.bot.api.services;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.Artist;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;

@Service
public class UserInfoService {

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
}
