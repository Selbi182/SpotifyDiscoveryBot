package spotify.bot.api.requests;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.Artist;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class UserInfoRequests {

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private Config config;
	
	@Autowired
	private DatabaseService databaseService;

	@Autowired
	private BotLogger log;
	
	/**
	 * Get all the user's followed artists
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<String> getFollowedArtistsIds() throws Exception {
		// Try to fetch from cache first
		List<String> cachedArtists = databaseService.getArtistCache();
		BotUtils.removeNullStrings(cachedArtists);
		if (cachedArtists != null && !cachedArtists.isEmpty()) {
			Date lastUpdatedArtistCache = config.getBotConfig().getArtistCacheLastUpdated();
			if (lastUpdatedArtistCache != null) {
				int artistCacheTimeout = config.getBotConfig().getArtistCacheTimeout();
				if (BotUtils.isTimeoutActive(lastUpdatedArtistCache, artistCacheTimeout)) {
					return cachedArtists;
				}
			}
		}

		// If cache is outdated, fetch fresh dataset and update cache
		List<Artist> followedArtists = SpotifyCall.executePaging(spotifyApi.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT));
		List<String> followedArtistIds = followedArtists.stream().map(Artist::getId).collect(Collectors.toList());
		BotUtils.removeNullStrings(followedArtistIds);
		if (followedArtistIds.isEmpty()) {
			log.warning("No followed artists found!");
		}
		databaseService.updateFollowedArtistsCacheAsync(followedArtistIds, cachedArtists);
		return followedArtistIds;
	}
}
