package spotify.bot.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.requests.data.playlists.ChangePlaylistsDetailsRequest;
import spotify.api.SpotifyApiException;
import spotify.api.SpotifyCall;
import spotify.bot.config.FeatureControl;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.services.PlaylistService;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Service
public class PlaylistMetaService {
  /**
   * The amount of days after which an unread notification will automatically be set to read
   */
  private final static int NEW_NOTIFICATION_TIMEOUT_DAYS = 31;

  /**
   * New-songs indicator (new songs are found), a white circle
   */
  private final static String INDICATOR_NEW = "\u26AA";

  /**
   * New-songs indicator (currently no new songs), a black circle.
   * This property is public, so it can be directly put into the playlist during creation.
   */
  public final static String INDICATOR_OFF = "\u26AB";

  /**
   * The description timestamp. Example: "January 1, 2000 - 00:00"
   */
  private final static DateTimeFormatter DESCRIPTION_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);

  /**
   * The prefix for the automatically created description
   */
  public static final String DESCRIPTION_PREFIX = "Last Discovery: ";

  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final PlaylistStoreConfig playlistStoreConfig;
  private final SpotifyOptimizedExecutorService spotifyOptimizedExecutorService;
  private final FeatureControl featureControl;

  PlaylistMetaService(SpotifyApi spotifyApi,
    PlaylistService playlistService,
    PlaylistStoreConfig playlistStoreConfig,
    SpotifyOptimizedExecutorService spotifyOptimizedExecutorService,
    FeatureControl featureControl) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.playlistStoreConfig = playlistStoreConfig;
    this.spotifyOptimizedExecutorService = spotifyOptimizedExecutorService;
    this.featureControl = featureControl;
  }

  /**
   * To be run once at startup, before the first crawl. This sets the "last updated" parameter
   * inside the PlaylistStores based on their value from the description.
   */
  public void initLastUpdatedFromPlaylistDescriptions() {
    if (featureControl.isPlaylistMetaEnabled()) {
      List<Callable<Void>> callables = new ArrayList<>();
      for (PlaylistStore ps : playlistStoreConfig.getEnabledPlaylistStores()) {
        callables.add(() -> {
          Playlist playlist = SpotifyCall.execute(spotifyApi.getPlaylist(ps.getPlaylistId()));
          if (containsNewIndicator(playlist.getName())) {
            String description = playlist.getDescription();
            if (description.startsWith(DESCRIPTION_PREFIX)) {
              String rawDate = description.replace(DESCRIPTION_PREFIX, "").trim();
              try {
                LocalDateTime lastUpdateFromDescription = DESCRIPTION_TIMESTAMP_FORMAT.parse(rawDate, LocalDateTime::from);
                ps.setLastUpdate(lastUpdateFromDescription);
              } catch (DateTimeParseException e) {
                e.printStackTrace();
              }
            }
          }
          return null; // must return something for Void class
        });
      }
      spotifyOptimizedExecutorService.executeAndWaitVoid(callables);
    }
  }

  /**
   * Display the [NEW] notifiers of the given album groups' playlists titles, if
   * any songs were added
   */
  public void showNotifiers(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws SpotifyApiException {
    if (featureControl.isPlaylistMetaEnabled()) {
      List<Callable<Void>> callables = new ArrayList<>();
      for (PlaylistStore ps : songsByPlaylist.keySet()) {
        callables.add(() -> {
          updatePlaylistTitleAndDescription(ps, INDICATOR_OFF, INDICATOR_NEW, true);
          playlistStoreConfig.setPlaylistStoreUpdatedJustNow(ps.getAlbumGroupExtended());
          return null; // must return something for Void class
        });
      }
      spotifyOptimizedExecutorService.executeAndWaitVoid(callables);
    }
  }

  /**
   * Convenience method to try and clear every obsolete New indicator
   */
  public void clearObsoleteNotifiers() throws SpotifyApiException {
    Collection<PlaylistStore> enabledPlaylistStores = playlistStoreConfig.getEnabledPlaylistStores();

    // Do a lite pre-check to see if ANY playlists even need a deep check (to reduce API calls).
    // This is accomplished by checking whether the "last update" field is set, because it being null
    // implicitly means the playlist is already marked as read.
    List<PlaylistStore> psRequireDeepCheck = enabledPlaylistStores.parallelStream()
      .filter(playlistStore -> playlistStore.getLastUpdate() != null)
      .collect(Collectors.toList());

    if (!psRequireDeepCheck.isEmpty()) {
      // Once it's been established that at least one playlist needs a deep check for notifier clearance,
      // compare the currently playing song with the recently added songs of the playlists
      CurrentlyPlaying currentlyPlaying = SpotifyCall.execute(spotifyApi.getUsersCurrentlyPlayingTrack());

      if (currentlyPlaying != null && currentlyPlaying.getItem() != null) {
        List<Callable<Void>> callables = new ArrayList<>();
        for (PlaylistStore ps : psRequireDeepCheck) {
          callables.add(() -> {
            if (shouldIndicatorBeMarkedAsRead(ps, currentlyPlaying)) {
              playlistStoreConfig.unsetPlaylistStoreUpdatedRecently(ps.getAlbumGroupExtended());
              updatePlaylistTitleAndDescription(ps, INDICATOR_NEW, INDICATOR_OFF, false);
            }
            return null;
          });
        }
        spotifyOptimizedExecutorService.executeAndWaitVoid(callables);
      }
    }
  }

  /**
   * Check if the [NEW] indicator for this playlist should be removed
   */
  private boolean shouldIndicatorBeMarkedAsRead(PlaylistStore playlistStore, CurrentlyPlaying currentlyPlaying) {
    if (!SpotifyUtils.isWithinTimeoutWindow(playlistStore.getLastUpdate(), NEW_NOTIFICATION_TIMEOUT_DAYS)) {
      return true;
    }

    Playlist playlist = playlistService.getPlaylist(playlistStore.getPlaylistId());

    List<PlaylistTrack> recentlyAddedPlaylistTracks = Arrays.stream(playlist.getTracks().getItems())
      .filter(pt -> SpotifyUtils.isWithinTimeoutWindow(pt.getAddedAt(), NEW_NOTIFICATION_TIMEOUT_DAYS))
      .collect(Collectors.toList());

    if (!recentlyAddedPlaylistTracks.isEmpty()) {
      String currentlyPlayingItemId = currentlyPlaying.getItem().getId();
      return recentlyAddedPlaylistTracks.stream()
        .map(PlaylistTrack::getTrack)
        .map(IPlaylistItem::getId)
        .filter(Objects::nonNull)
        .anyMatch(id -> Objects.equals(id, currentlyPlayingItemId));
    }

    return false;
  }

  /**
   * Return true if the given playlist name contains the new indicator (white circle).
   */
  private boolean containsNewIndicator(String playlistName) {
    return playlistName.contains(INDICATOR_NEW);
  }

  ////////////////////////////////

  /**
   * Update the playlist name by replacing the target symbol with the replacement
   * symbol IF it isn't already contained in the playlist's name. Also timestamp
   * the playlist, if specified.
   *
   * @param playlistStore       the PlaylistStore containing the relevant playlist
   * @param notifierTarget      the target String to be replaced
   * @param notifierReplacement the replacement String
   * @param timestamp           write the "Last Discovery" timestamp in the
   *                            description
   */
  private void updatePlaylistTitleAndDescription(PlaylistStore playlistStore, String notifierTarget, String notifierReplacement, boolean timestamp) throws SpotifyApiException {
    String playlistId = playlistStore.getPlaylistId();
    if (playlistId != null) {
      String newPlaylistName = null;
      String newDescription = null;

      if (timestamp) {
        newDescription = DESCRIPTION_PREFIX + LocalDateTime.now().format(DESCRIPTION_TIMESTAMP_FORMAT);
      }

      Playlist p = playlistService.getPlaylist(playlistId);
      if (p != null) {
        String playlistName = p.getName();
        if (playlistName != null && playlistName.contains(notifierTarget)) {
          newPlaylistName = playlistName.replace(notifierTarget, notifierReplacement).trim();
        }
      }

      if (newPlaylistName != null || newDescription != null) {
        ChangePlaylistsDetailsRequest.Builder playlistDetailsBuilder = spotifyApi.changePlaylistsDetails(playlistId);
        if (newPlaylistName != null) {
          playlistDetailsBuilder = playlistDetailsBuilder.name(newPlaylistName);
        }
        if (newDescription != null) {
          playlistDetailsBuilder = playlistDetailsBuilder.description(newDescription);
        }
        SpotifyCall.execute(playlistDetailsBuilder);
      }
    }
  }
}
