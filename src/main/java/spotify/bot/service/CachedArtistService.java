package spotify.bot.service;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;

import se.michaelthelin.spotify.model_objects.specification.Artist;
import spotify.api.BotException;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.data.CachedArtistsContainer;
import spotify.services.ArtistService;
import spotify.util.BotUtils;

@Service
public class CachedArtistService {
  private final ArtistService artistService;
  private final StaticConfig staticConfig;
  private final DatabaseService databaseService;

  CachedArtistService(ArtistService artistService, StaticConfig staticConfig, DatabaseService databaseService) {
    this.artistService = artistService;
    this.staticConfig = staticConfig;
    this.databaseService = databaseService;
  }

  /**
   * Get all the user's followed artists
   */
  public CachedArtistsContainer getFollowedArtistsIds() throws SQLException, BotException {
    List<String> cachedArtists = getCachedArtistIds();
    if (isArtistCacheExpired(cachedArtists)) {
      List<String> followedArtistIds = getRealArtistIds();
      if (followedArtistIds.isEmpty()) {
        throw new BotException(new IllegalArgumentException("No followed artists found!"));
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
    List<Artist> followedArtists = artistService.getFollowedArtists();
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
