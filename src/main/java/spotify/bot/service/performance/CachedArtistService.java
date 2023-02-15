package spotify.bot.service.performance;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import spotify.api.SpotifyApiException;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.filter.FilterService;
import spotify.bot.service.DiscoveryAlbumService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.data.CachedArtistsContainer;
import spotify.services.ArtistService;
import spotify.util.BotUtils;

/**
 * Performance service to cache the user's followed artists and only update them once each midnight.
 * This is because it's very unlikely that a user follows an artist and then the artist immediately
 * releases new material (i.e. on the same day of the follow).
 */
@Service
public class CachedArtistService {
  private final ArtistService artistService;
  private final DatabaseService databaseService;
  private final DiscoveryAlbumService discoveryAlbumService;
  private final FilterService filterService;
  private final DiscoveryBotLogger log;

  private LocalDate artistCacheLastUpdated;

  CachedArtistService(ArtistService artistService, DatabaseService databaseService, FilterService filterService, DiscoveryAlbumService discoveryAlbumService, DiscoveryBotLogger discoveryBotLogger) {
    this.artistService = artistService;
    this.databaseService = databaseService;
    this.filterService = filterService;
    this.discoveryAlbumService = discoveryAlbumService;
    this.log = discoveryBotLogger;
  }

  /**
   * Get all the user's followed artists
   */
  public CachedArtistsContainer getFollowedArtistsIds() throws SQLException, SpotifyApiException {
    List<String> cachedArtists = getCachedArtistIds();
    if (isArtistCacheExpired()) {
      List<String> followedArtistIds = getRealArtistIds();
      if (followedArtistIds.isEmpty()) {
        throw new SpotifyApiException(new IllegalArgumentException("No followed artists found!"));
      }
      filterService.cacheArtistIds(followedArtistIds);
      this.artistCacheLastUpdated = ZonedDateTime.now().toLocalDate();
      return repackageIntoContainer(followedArtistIds, cachedArtists);
    } else {
      return new CachedArtistsContainer(cachedArtists, List.of());
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
  private List<String> getRealArtistIds() throws SpotifyApiException {
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

  private boolean isArtistCacheExpired() {
    return artistCacheLastUpdated == null || ZonedDateTime.now().toLocalDate().isAfter(artistCacheLastUpdated);
  }

  /////////////

  public void initializeAlbumCacheForNewArtists(CachedArtistsContainer cachedArtistsContainer) throws SQLException {
    List<String> newArtists = cachedArtistsContainer.getNewArtists();
    if (!newArtists.isEmpty()) {
      log.info("Initializing album cache for " + newArtists.size() + " newly followed artist[s]:");
      log.info(artistService.getArtists(newArtists).stream()
          .map(Artist::getName)
          .sorted()
          .collect(Collectors.joining(", ")));
      List<AlbumSimplified> allAlbumsOfNewFollowees = discoveryAlbumService.getAllAlbumsOfArtists(newArtists);
      List<AlbumSimplified> albumsToInitialize = filterService.getNonCachedAlbums(allAlbumsOfNewFollowees);
      filterService.cacheAlbumIds(albumsToInitialize);
      filterService.cacheAlbumNames(albumsToInitialize);
    }
  }
}
