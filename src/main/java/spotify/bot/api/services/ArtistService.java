package spotify.bot.api.services;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.CachedArtistsContainer;

@Service
public class ArtistService {

	private final static int MAX_ARTIST_FETCH_LIMIT = 50;

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private StaticConfig staticConfig;

	@Autowired
	private DatabaseService databaseService;

	/**
	 * Get several artists
	 * @param ids artist IDs
	 * @return the artists
	 */
	public List<Artist> getArtists(List<String> ids) {
		List<Artist> allArtists = new ArrayList<>();
		for (List<String> artistsPartition : Lists.partition(ids, MAX_ARTIST_FETCH_LIMIT)) {
			allArtists.addAll(Arrays.asList(SpotifyCall.execute(spotifyApi.getSeveralArtists(artistsPartition.toArray(String[]::new)))));
		}
		return allArtists;
	}

	/**
	 * Get all the user's followed artists
	 */
	public CachedArtistsContainer getFollowedArtistsIds() throws SQLException, BotException {
		List<String> cachedArtists = getCachedArtistIds();
		if (isArtistCacheExpired(cachedArtists)) {
			List<String> followedArtistIds = getRealArtistIds();
			if (followedArtistIds.isEmpty()) {
				throw new BotException("No followed artists found!");
			}
			databaseService.updateFollowedArtistsCacheAsync(followedArtistIds);
			return repackageIntoContainer(followedArtistIds, cachedArtists);
		} else {
			return new CachedArtistsContainer(cachedArtists, ImmutableList.of());
		}
	}

	/**
	 * Wrap everything into a container to determine which artists were newly added
	 * (to initialize the album cache for them in a later step)
	 */
	private CachedArtistsContainer repackageIntoContainer(List<String> followedArtist, List<String> oldCachedArtists) {
		Set<String> addedArtists = new HashSet<>(followedArtist);
		oldCachedArtists.forEach(addedArtists::remove); // apparently faster than removeAll()
		return new CachedArtistsContainer(followedArtist, addedArtists);
	}

	/**
	 * Get the real artist IDs directly from the Spotify API
	 */
	private List<String> getRealArtistIds() throws BotException {
		List<Artist> followedArtists = SpotifyCall.executePaging(spotifyApi
			.getUsersFollowedArtists(ModelObjectType.ARTIST)
			.limit(MAX_ARTIST_FETCH_LIMIT));
		List<String> followedArtistIds = followedArtists.stream()
			.map(Artist::getId)
			.collect(Collectors.toList());
		BotUtils.removeNullStrings(followedArtistIds);
		return followedArtistIds;
	}

	/**
	 * Get the list of cached artists from the DB
	 */
	private List<String> getCachedArtistIds() throws SQLException {
		List<String> cachedArtists = databaseService.getArtistCache();
		BotUtils.removeNullStrings(cachedArtists);
		return cachedArtists;
	}

	private boolean isArtistCacheExpired(List<String> cachedArtists) {
		if (cachedArtists != null && !cachedArtists.isEmpty()) {
			Date lastUpdatedArtistCache = staticConfig.getArtistCacheLastUpdated();
			if (lastUpdatedArtistCache != null) {
				int artistCacheTimeout = staticConfig.getArtistCacheTimeout();
				return !BotUtils.isWithinTimeoutWindow(lastUpdatedArtistCache, artistCacheTimeout);
			}
		}
		return false;
	}
}
