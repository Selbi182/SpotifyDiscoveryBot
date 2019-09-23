package spotify.bot.api.requests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.PagingCursorbased;
import com.wrapper.spotify.requests.data.follow.GetUsersFollowedArtistsRequest;

import spotify.bot.Config;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
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
	public static List<Artist> getFollowedArtists() throws Exception {
		List<Artist> followedArtists = SpotifyApiRequest.execute(new Callable<List<Artist>>() {
			@Override
			public List<Artist> call() throws Exception {
				List<Artist> followedArtists = new ArrayList<>();
				PagingCursorbased<Artist> artists = null;
				do {
					GetUsersFollowedArtistsRequest.Builder request = SpotifyApiSessionManager.api().getUsersFollowedArtists(ModelObjectType.ARTIST).limit(Constants.DEFAULT_LIMIT);
					if (artists != null && artists.getNext() != null) {
						String after = artists.getCursors()[0].getAfter();
						request = request.after(after);
					}
					artists = request.build().execute();
					followedArtists.addAll(Arrays.asList(artists.getItems()));
				} while (artists.getNext() != null);
				return followedArtists;
			}
		});
		if (followedArtists.isEmpty()) {
			Config.log().warning("No followed artists found!");
		}
		return followedArtists;
	}
	
}
