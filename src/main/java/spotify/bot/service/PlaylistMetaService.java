package spotify.bot.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
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
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.playlists.ChangePlaylistsDetailsRequest;
import spotify.api.BotException;
import spotify.api.SpotifyCall;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.properties.PlaylistStoreConfig;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.services.PlaylistService;
import spotify.util.BotUtils;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.data.AlbumTrackPair;

@Service
public class PlaylistMetaService {
  private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

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
  private final static SimpleDateFormat DESCRIPTION_TIMESTAMP_FORMAT = new SimpleDateFormat("MMMMM d, yyyy \u2014 HH:mm", Locale.ENGLISH);

  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final PlaylistStoreConfig playlistStoreConfig;
  private final SpotifyOptimizedExecutorService spotifyOptimizedExecutorService;
  private final DiscoveryBotLogger log;

  PlaylistMetaService(SpotifyApi spotifyApi,
      PlaylistService playlistService,
      PlaylistStoreConfig playlistStoreConfig,
      SpotifyOptimizedExecutorService spotifyOptimizedExecutorService,
      DiscoveryBotLogger discoveryBotLogger) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.playlistStoreConfig = playlistStoreConfig;
    this.spotifyOptimizedExecutorService = spotifyOptimizedExecutorService;
    this.log = discoveryBotLogger;
  }

  /**
   * Display the [NEW] notifiers of the given album groups' playlists titles, if
   * any songs were added
   */
  public void showNotifiers(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws BotException {
    if (!DeveloperMode.isPlaylistAdditionDisabled()) {
      List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
      List<Callable<Void>> callables = new ArrayList<>();
      for (PlaylistStore ps : sortedPlaylistStores) {
        List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
        Collections.sort(albumTrackPairs);
        callables.add(() -> {
          updatePlaylistTitleAndDescription(ps, INDICATOR_OFF, INDICATOR_NEW, true);
          playlistStoreConfig.setPlaylistStoreUpdatedJustNow(ps.getAlbumGroupExtended());
          return null; // must return something for Void class
        });
        log.printAlbumTrackPairs(albumTrackPairs, ps.getAlbumGroupExtended());
      }
      spotifyOptimizedExecutorService.executeAndWaitVoid(callables);
    }
  }

  /**
   * Convenience method to try and clear every obsolete New indicator
   *
   * @return true if at least one playlist name was changed
   */
  public boolean clearObsoleteNotifiers() throws BotException {
    boolean changed = false;
    if (!DeveloperMode.isPlaylistAdditionDisabled()) {
      for (PlaylistStore ps : playlistStoreConfig.getAllPlaylistStores()) {
        if (ps.isForceUpdateOnFirstTime() || shouldIndicatorBeMarkedAsRead(ps)) {
          if (updatePlaylistTitleAndDescription(ps, INDICATOR_NEW, INDICATOR_OFF, false)) {
            changed = true;
            playlistStoreConfig.unsetPlaylistStoreUpdatedRecently(ps.getAlbumGroupExtended());
          }
        }
      }
    }
    return changed;
  }

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
   * @return true if the playlist name was changed (a changed playlist description
   *         has no effect on its own)
   */
  private boolean updatePlaylistTitleAndDescription(PlaylistStore playlistStore, String notifierTarget, String notifierReplacement, boolean timestamp) throws BotException {
    boolean changed = false;
    String playlistId = playlistStore.getPlaylistId();
    if (playlistId != null) {
      String newPlaylistName = null;
      String newDescription = null;

      if (timestamp) {
        newDescription = "Last Discovery: " + DESCRIPTION_TIMESTAMP_FORMAT.format(Calendar.getInstance().getTime());
      }

      Playlist p = playlistService.getPlaylist(playlistId);
      if (p != null) {
        String playlistName = p.getName();
        if (playlistName != null && playlistName.contains(notifierTarget)) {
          newPlaylistName = playlistName.replace(notifierTarget, notifierReplacement).trim();
          changed = true;
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
    return changed;
  }

  ////////////////////////////////

  /**
   * Check if the [NEW] indicator for this playlist store should be removed. This
   * is either done by timeout or by checking if the currently played song is
   * within the most recently added songs of the playlist.
   */
  private boolean shouldIndicatorBeMarkedAsRead(PlaylistStore playlistStore) {
    try {
      // Case 1: Notification timestamp is already unset
      Date lastUpdated = playlistStore.getLastUpdate();
      if (lastUpdated == null) {
        return true;
      }

      // Case 2: Timeout since playlist was last updated expired
      if (!BotUtils.isWithinTimeoutWindow(lastUpdated, NEW_NOTIFICATION_TIMEOUT_DAYS)) {
        return true;
      }

      // Case 3: Currently played song is within the recently added playlist tracks
      String playlistId = playlistStore.getPlaylistId();

      PlaylistTrack[] topmostPlaylistTracks = SpotifyCall.execute(spotifyApi
          .getPlaylistsItems(playlistId)
          .limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT))
          .getItems();
      List<PlaylistTrack> recentlyAddedPlaylistTracks = Arrays.stream(topmostPlaylistTracks)
          .filter((pt -> BotUtils.isWithinTimeoutWindow(pt.getAddedAt(), NEW_NOTIFICATION_TIMEOUT_DAYS)))
          .collect(Collectors.toList());

      // -- Case 3a: Playlist does not have recently added tracks or is still empty
      if (recentlyAddedPlaylistTracks.isEmpty()) {
        return true;
      }

      // -- Case 3b: Playlist does have recently added tracks, check if the currently
      // played song is within that list
      CurrentlyPlaying currentlyPlaying = SpotifyCall.execute(spotifyApi.getUsersCurrentlyPlayingTrack());
      if (currentlyPlaying != null) {
        IPlaylistItem item = currentlyPlaying.getItem();
        if (item instanceof Track) {
          String currentlyPlayingSongId = item.getId();
          return recentlyAddedPlaylistTracks.stream()
              .map(PlaylistTrack::getTrack)
              .map(IPlaylistItem::getId)
              .filter(Objects::nonNull)
              .anyMatch(id -> Objects.equals(id, currentlyPlayingSongId));
        }
      }
    } catch (Exception e) {
      // Don't care, indicator clearance has absolutely no priority
      log.stackTrace(e);
    }
    return false;
  }
}
