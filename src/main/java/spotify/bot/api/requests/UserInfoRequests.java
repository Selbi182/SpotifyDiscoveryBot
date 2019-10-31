package spotify.bot.api.requests;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.requests.data.follow.GetUsersFollowedArtistsRequest;

import spotify.bot.Config;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.database.SpotifyBotDatabase;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class UserInfoRequests {
	/**
	 * Static calls only
	 */
	private UserInfoRequests() {}
	
	/**
	 * Get all the user's followed artists
	 * 
	 * @return
	 * @throws Exception
	 */
	public static List<String> getFollowedArtistsIds() throws Exception {
		// Try to fetch from cache first
		List<String> cachedArtists = getCachedFollowedArtists();
		if (cachedArtists != null && !cachedArtists.isEmpty()) {
			Date lastUpdatedArtistCache = Config.getInstance().getUpdateStoreByType(Constants.US_ARTIST_CACHE).getLastUpdatedTimestamp();
			if (lastUpdatedArtistCache != null) {
				int artistCacheTimeout = Config.getInstance().getArtistCacheTimeout();
				if (BotUtils.isTimeoutActive(lastUpdatedArtistCache, artistCacheTimeout)) {
					return cachedArtists;
				}
			}			
		}
		
		// If cache is outdated, fetch fresh dataset and update cache
		List<String> followedArtists = SpotifyApiRequest.execute(new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				List<String> followedArtists = new ArrayList<>();
				PagingCursorbased<Artist> artists = null;
				do {
					GetUsersFollowedArtistsRequest.Builder request = SpotifyApiSessionManager.api().getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT);
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						request = request.after(after);
					}
					artists = request.build().execute();
					Arrays.asList(artists.getItems()).stream().forEach(a -> followedArtists.add(a.getId()));
				} while (artists.getNext() != null);
				SpotifyBotDatabase.getInstance().updateFollowedArtistsCacheAsync(followedArtists, cachedArtists);
				return followedArtists;
			}
		});
		if (followedArtists.isEmpty()) {
			Config.log().warning("No followed artists found!");
		}
		return followedArtists;
	}
	
	private static List<String> getCachedFollowedArtists() throws IOException, SQLException {
		ResultSet rs = SpotifyBotDatabase.getInstance().fullTable(Constants.TABLE_ARTIST_CACHE);
		List<String> cachedArtists = new ArrayList<>();
		while (rs.next()) {
			cachedArtists.add(rs.getString(Constants.COL_ARTIST_IDS));
		}
		return cachedArtists;
	}
}
