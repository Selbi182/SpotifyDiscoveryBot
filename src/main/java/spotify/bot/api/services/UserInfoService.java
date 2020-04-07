package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Artist;

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
	 * 
	 * @return
	 */
	public List<String> getFollowedArtistsIds() throws IOException, SQLException, SpotifyWebApiException, InterruptedException {
		// Try to fetch from cache first
		boolean cache = userOptions.isCacheFollowedArtists();
		List<String> cachedArtists = null;
		if (cache) {
			cachedArtists = getCachedArtists();
		}

		// If cache is outdated, fetch fresh dataset and update cache
		List<Artist> followedArtists = SpotifyCall.executePaging(spotifyApi
			.getUsersFollowedArtists(ModelObjectType.ARTIST)
			.limit(MAX_FOLLOWED_ARTIST_FETCH_LIMIT));
		List<String> followedArtistIds = followedArtists.stream().map(Artist::getId).collect(Collectors.toList());
		BotUtils.removeNullStrings(followedArtistIds);
		if (followedArtistIds.isEmpty()) {
			log.warning("No followed artists found!");
		}
		if (cache) {
			databaseService.updateFollowedArtistsCacheAsync(followedArtistIds, cachedArtists);
		}
		return followedArtistIds;
	}

	private List<String> getCachedArtists() throws IOException, SQLException {
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
