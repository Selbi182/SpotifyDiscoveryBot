package spotify.bot.properties;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import spotify.bot.config.FeatureControl;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.services.PlaylistService;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;

@Service
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "spotify.discovery.crawl.auto")
public class AutoPurgerService {
  private final PlaylistService playlistService;
  private final PlaylistStoreConfig playlistStoreConfig;
  private final PlaylistMetaService playlistMetaService;
  private final SpotifyOptimizedExecutorService executorService;
  private final FeatureControl featureControl;
  private final DiscoveryBotLogger log;

  private Map<AlbumGroupExtended, Integer> autoPurgeConfigMap = Map.of();

  AutoPurgerService(PlaylistService playlistService, PlaylistStoreConfig playlistStoreConfig, PlaylistMetaService playlistMetaService, SpotifyOptimizedExecutorService executorService, FeatureControl featureControl, DiscoveryBotLogger log) {
    this.playlistService = playlistService;
    this.playlistStoreConfig = playlistStoreConfig;
    this.playlistMetaService = playlistMetaService;
    this.executorService = executorService;
    this.featureControl = featureControl;
    this.log = log;
  }

  @SuppressWarnings("unused") // will be called by Spring on boot
  void setPurge(List<String> autoPurgeRaw) {
    this.autoPurgeConfigMap = parseAutoPurgeConfig(autoPurgeRaw);
    if (!this.autoPurgeConfigMap.isEmpty()) {
      log.warning("Automatic purging of playlist additions has been enabled! " + this.autoPurgeConfigMap);
    }
  }

  private Map<AlbumGroupExtended, Integer> parseAutoPurgeConfig(List<String> autoPurgeRaw) {
    Map<AlbumGroupExtended, Integer> autoPurgeConfigMap = new HashMap<>();
    try {
      for (String autoPurgeEntry : autoPurgeRaw) {
        String[] split = autoPurgeEntry.split(":");
        AlbumGroupExtended albumGroupExtended = AlbumGroupExtended.valueOf(split[0]);
        int expirationDateForType = Integer.parseInt(split[1]);
        autoPurgeConfigMap.put(albumGroupExtended, expirationDateForType);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return Map.of();
    }
    return autoPurgeConfigMap;
  }

  public void runPurger() {
    if (isEnabled()) {
      AtomicInteger purgedTracksCount = new AtomicInteger();

      List<Callable<Void>> callables = new ArrayList<>();
      for (Map.Entry<AlbumGroupExtended, Integer> entry : autoPurgeConfigMap.entrySet()) {
        AlbumGroupExtended albumGroupExtended = entry.getKey();
        PlaylistStoreConfig.PlaylistStore playlistStore = playlistStoreConfig.getPlaylistStore(albumGroupExtended);
        String playlistId = playlistStore.getPlaylistId();

        int expirationDays = entry.getValue();

        callables.add(() -> {
          List<PlaylistTrack> playlistTracks = playlistService.getPlaylistTracks(playlistId);
          List<IPlaylistItem> expiredTracks = playlistTracks.stream()
            .filter(pt -> isExpiredTrack(pt, expirationDays))
            .map(PlaylistTrack::getTrack)
            .collect(Collectors.toList());
          if (!expiredTracks.isEmpty()) {
            playlistService.removeItemsFromPlaylist(playlistId, expiredTracks);
            purgedTracksCount.addAndGet(expiredTracks.size());
            if (playlistTracks.size() == expiredTracks.size()) {
              playlistMetaService.markPlaylistAsRead(playlistStore);
            }
          }
          return null; // must return something for Void class
        });
      }
      executorService.executeAndWaitVoid(callables);

      int purgedTracksCountFinal = purgedTracksCount.get();
      if (purgedTracksCountFinal > 0) {
        log.info(String.format("AutoPurgerService: %d expired tracks have been removed from the playlists", purgedTracksCountFinal));
      }
    }
  }

  private boolean isExpiredTrack(PlaylistTrack playlistTrack, int expirationDays) {
    Date addedAt = playlistTrack.getAddedAt();
    int expirationHours = expirationDays * 24;
    return !SpotifyUtils.isWithinTimeoutWindow(addedAt, expirationHours);
  }

  private boolean isEnabled() {
    return featureControl.isAutoPurgeEnabled() && !this.autoPurgeConfigMap.isEmpty();
  }
}
