package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.requests.data.follow.GetUsersFollowedArtistsRequest;

import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.config.BotLogger;
import spotify.bot.config.Config;
import spotify.bot.database.DBConstants;
import spotify.bot.database.DiscoveryDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

@Service
public class UserInfoRequests {

	@Autowired
	private SpotifyApiWrapper spotify;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;
	
	@Autowired
	private DiscoveryDatabase database;
	
	/**
	 * Get all the user's followed artists
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<String> getFollowedArtistsIds() throws Exception {
		// Try to fetch from cache first
		List<String> cachedArtists = getCachedFollowedArtists();
		BotUtils.removeNullStrings(cachedArtists);
		if (cachedArtists != null && !cachedArtists.isEmpty()) {
			Date lastUpdatedArtistCache = config.getArtistCacheLastUpdated();
			if (lastUpdatedArtistCache != null) {
				int artistCacheTimeout = config.getArtistCacheTimeout();
				if (BotUtils.isTimeoutActive(lastUpdatedArtistCache, artistCacheTimeout)) {
					return cachedArtists;
				}
			}			
		}
		
		// If cache is outdated, fetch fresh dataset and update cache
		List<String> followedArtists = spotify.execute(new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				List<String> followedArtists = new ArrayList<>();
				PagingCursorbased<Artist> artists = null;
				do {
					GetUsersFollowedArtistsRequest.Builder request = spotify.api().getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT);
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						request = request.after(after);
					}
					artists = request.build().execute();
					Arrays.asList(artists.getItems()).stream().forEach(a -> followedArtists.add(a.getId()));
				} while (artists.getNext() != null);
				database.updateFollowedArtistsCacheAsync(followedArtists, cachedArtists);
				return followedArtists;
			}
		});
		if (followedArtists.isEmpty()) {
			log.warning("No followed artists found!");
		}
		BotUtils.removeNullStrings(followedArtists);
		return followedArtists;
	}
	
	private List<String> getCachedFollowedArtists() throws IOException, SQLException {
		ResultSet rs = database.fullTable(DBConstants.TABLE_ARTIST_CACHE);
		List<String> cachedArtists = new ArrayList<>();
		while (rs.next()) {
			cachedArtists.add(rs.getString(DBConstants.COL_ARTIST_IDS));
		}
		return cachedArtists;
	}
}
