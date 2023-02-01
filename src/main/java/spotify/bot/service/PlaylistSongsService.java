package spotify.bot.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.api.BotException;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.service.performance.SpotifyOptimizedExecutorService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.services.PlaylistService;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Service
public class PlaylistSongsService {
  private final static int TOP_OF_PLAYLIST = 0;
  private final static int PLAYLIST_ADDITION_COOLDOWN = 1000;
  private final static int PLAYLIST_ADD_LIMIT = 100;
  private final static int PLAYLIST_SIZE_LIMIT = 10000;
  private final static String TRACK_PREFIX = "spotify:track:";

  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService spotifyOptimizedExecutorService;
  private final DiscoveryBotLogger log;

  PlaylistSongsService(PlaylistService playlistService, SpotifyOptimizedExecutorService spotifyOptimizedExecutorService, DiscoveryBotLogger discoveryBotLogger) {
    this.playlistService = playlistService;
    this.spotifyOptimizedExecutorService = spotifyOptimizedExecutorService;
    this.log = discoveryBotLogger;
  }

  /**
   * Adds all releases to the given playlists
   */
  public void addAllReleasesToSetPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws BotException {
    log.info("Adding to playlists:");
    List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
    List<Callable<Void>> callables = new ArrayList<>();
    for (PlaylistStore ps : sortedPlaylistStores) {
      List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
      Collections.sort(albumTrackPairs);
      callables.add(() -> {
        addSongsForPlaylistStore(ps, albumTrackPairs);
        return null; // must return something for Void class
      });
      log.printAlbumTrackPairs(albumTrackPairs, ps.getAlbumGroupExtended());
    }
    spotifyOptimizedExecutorService.executeAndWaitVoid(callables);
  }

  private void addSongsForPlaylistStore(PlaylistStore ps, List<AlbumTrackPair> albumTrackPairs) {
    if (!albumTrackPairs.isEmpty() && !DeveloperMode.isPlaylistAdditionDisabled()) {
      addSongsToPlaylistId(ps.getPlaylistId(), albumTrackPairs);
    }
  }

  /**
   * Add the given list of song IDs to the playlist (a delay of a second per
   * release is used to retain order). May remove older songs to make room.
   */
  public void addSongsToPlaylistId(String playlistId, List<AlbumTrackPair> albumTrackPairs) throws BotException {
    if (!albumTrackPairs.isEmpty()) {
      Playlist playlist = playlistService.getPlaylist(playlistId);
      circularPlaylistFitting(playlist, albumTrackPairs);
      List<List<TrackSimplified>> bundledReleases = extractTrackLists(albumTrackPairs);
      for (List<TrackSimplified> t : bundledReleases) {
        for (List<TrackSimplified> partition : Lists.partition(t, PLAYLIST_ADD_LIMIT)) {
          List<String> ids = partition.stream().map(TrackSimplified::getId).collect(Collectors.toList());
          playlistService.addSongsToPlaylistById(playlist, ids, TOP_OF_PLAYLIST);
          BotUtils.sneakySleep(PLAYLIST_ADDITION_COOLDOWN);
        }
      }
    }
  }

  /**
   * Extract all the ATP tracks into a new list
   */
  private List<List<TrackSimplified>> extractTrackLists(List<AlbumTrackPair> allReleases) {
    List<List<TrackSimplified>> bundled = new ArrayList<>();
    for (AlbumTrackPair atp : allReleases) {
      bundled.add(atp.getTracks());
    }
    return bundled;
  }

  /**
   * Check if circular playlist fitting is required
   */
  private void circularPlaylistFitting(Playlist playlist, List<AlbumTrackPair> albumTrackPairs) throws BotException {
    int songsToAddCount = albumTrackPairs.stream().mapToInt(AlbumTrackPair::trackCount).sum();
    final int currentPlaylistCount = playlist.getTracks().getTotal();
    if (currentPlaylistCount + songsToAddCount > PLAYLIST_SIZE_LIMIT) {
      deleteSongsFromBottomOnLimit(playlist, currentPlaylistCount, songsToAddCount);
    }
  }

  /**
   * Delete as many songs from the bottom as necessary to make room for any new
   * songs to add, as Spotify playlists have a fixed limit of 10000 songs.
   *
   * If circularPlaylistFitting isn't enabled, an exception is thrown on a full
   * playlist instead.
   */
  private void deleteSongsFromBottomOnLimit(Playlist playlist, int currentPlaylistCount, int songsToAddCount) throws BotException {
    String playlistId = playlist.getId();

    int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - PLAYLIST_SIZE_LIMIT;
    boolean repeat = totalSongsToDeleteCount > PLAYLIST_ADD_LIMIT;
    int songsToDeleteCount = repeat ? PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
    final int offset = currentPlaylistCount - songsToDeleteCount;

    List<PlaylistTrack> tracksToDelete = playlistService.getPlaylistTracks(playlistId, offset);

    JsonArray json = new JsonArray();
    for (int i = 0; i < tracksToDelete.size(); i++) {
      IPlaylistItem track = tracksToDelete.get(i).getTrack();
      if (track instanceof Track) {
        String id = track.getId();
        JsonObject object = new JsonObject();
        object.addProperty("uri", TRACK_PREFIX + id);
        JsonArray positions = new JsonArray();
        positions.add(currentPlaylistCount - songsToDeleteCount + i);
        object.add("positions", positions);
        json.add(object);
      }
    }

    playlistService.deleteTracksFromPlaylist(playlistId, json);

    // Repeat if more than 100 songs have to be added/deleted (should rarely happen,
    // so a recursion will be slow, but it'll do the job)
    if (repeat) {
      deleteSongsFromBottomOnLimit(playlist, currentPlaylistCount - 100, songsToAddCount);
    }
  }

}
