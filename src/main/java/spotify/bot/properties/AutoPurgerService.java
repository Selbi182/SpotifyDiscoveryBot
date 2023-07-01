package spotify.bot.properties;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import spotify.bot.config.FeatureControl;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.services.PlaylistService;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;

@Service
public class AutoPurgerService {

  @Value("${spotify.discovery.crawl.auto_purge_days:#{null}}")
  private String autoPurgerSpringVar;
  private Integer autoPurgerDays;

  private List<String> enabledPlaylistIds;

  private final PlaylistService playlistService;
  private final PlaylistStoreConfig playlistStoreConfig;
  private final PlaylistMetaService playlistMetaService;
  private final SpotifyOptimizedExecutorService executorService;
  private final FeatureControl featureControl;
  private final DiscoveryBotLogger log;

  AutoPurgerService(PlaylistService playlistService, PlaylistStoreConfig playlistStoreConfig, PlaylistMetaService playlistMetaService, SpotifyOptimizedExecutorService executorService, FeatureControl featureControl, DiscoveryBotLogger log) {
    this.playlistService = playlistService;
    this.playlistStoreConfig = playlistStoreConfig;
    this.playlistMetaService = playlistMetaService;
    this.executorService = executorService;
    this.featureControl = featureControl;
    this.log = log;
  }

  @PostConstruct
  void validateSpringVar() {
    if (autoPurgerSpringVar != null && featureControl.isAutoPurgeEnabled()) {
      try {
        autoPurgerDays = Integer.parseInt(autoPurgerSpringVar);
        if (autoPurgerDays <= 0) {
          throw new IllegalArgumentException();
        }
        log.warning(String.format("Automatic purging of playlist additions is enabled! Set expiration time: %d day(s)", autoPurgerDays));
      } catch (RuntimeException e) {
        log.error("auto_purge_days was set but couldn't be initialized due to an invalid value (must be positive integer). Feature will remain disabled!");
      }
    }
  }

  public void runPurger() {
    if (isEnabled()) {
      AtomicInteger purgedTracksCount = new AtomicInteger();

      List<Callable<Void>> callables = new ArrayList<>();
      for (String playlistId : getEnabledPlaylistIds()) {
        callables.add(() -> {
          List<PlaylistTrack> playlistTracks = playlistService.getPlaylistTracks(playlistId);
          List<IPlaylistItem> expiredTracks = playlistTracks.stream()
            .filter(this::isExpiredTrack)
            .map(PlaylistTrack::getTrack)
            .collect(Collectors.toList());
          if (!expiredTracks.isEmpty()) {
            playlistService.removeItemsFromPlaylist(playlistId, expiredTracks);
            purgedTracksCount.addAndGet(expiredTracks.size());
            if (playlistTracks.size() == expiredTracks.size()) {
              PlaylistStoreConfig.PlaylistStore playlistStore = playlistStoreConfig.getPlaylistStore(playlistId);
              if (playlistStore != null) {
                playlistMetaService.markPlaylistAsRead(playlistStore);
              }
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

  private boolean isExpiredTrack(PlaylistTrack playlistTrack) {
    Date addedAt = playlistTrack.getAddedAt();
    return !SpotifyUtils.isWithinTimeoutWindow(addedAt, autoPurgerDays * 24);
  }

  private boolean isEnabled() {
    return autoPurgerDays != null;
  }

  private List<String> getEnabledPlaylistIds() {
    if (this.enabledPlaylistIds == null) {
      this.enabledPlaylistIds = playlistStoreConfig.getEnabledPlaylistStores().stream()
        .map(PlaylistStoreConfig.PlaylistStore::getPlaylistId)
        .distinct()
        .collect(Collectors.toList());
    }
    return this.enabledPlaylistIds;
  }
}
