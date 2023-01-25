package spotify.bot.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.config.dto.UserOptions;
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
  private final UserOptions userOptions;
  private final DiscoveryBotLogger log;

  PlaylistSongsService(PlaylistService playlistService, UserOptions userOptions, DiscoveryBotLogger discoveryBotLogger) {
    this.playlistService = playlistService;
    this.userOptions = userOptions;
    this.log = discoveryBotLogger;
  }

  /**
   * Adds all releases to the given playlists
   */
  public void addAllReleasesToSetPlaylists(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) throws BotException {
    log.debug("Adding to playlists:");
    List<PlaylistStore> sortedPlaylistStores = songsByPlaylist.keySet().stream().sorted().collect(Collectors.toList());
    for (PlaylistStore ps : sortedPlaylistStores) {
      List<AlbumTrackPair> albumTrackPairs = songsByPlaylist.get(ps);
      if (!albumTrackPairs.isEmpty()) {
        Collections.sort(albumTrackPairs);
        if (!DeveloperMode.isPlaylistAdditionDisabled()) {
          addSongsToPlaylistId(ps.getPlaylistId(), albumTrackPairs);
        }
        log.printAlbumTrackPairs(albumTrackPairs, ps.getAlbumGroupExtended());
      }
    }
  }

  /**
   * Add the given list of song IDs to the playlist (a delay of a second per
   * release is used to retain order). May remove older songs to make room.
   */
  private void addSongsToPlaylistId(String playlistId, List<AlbumTrackPair> albumTrackPairs) throws BotException {
    if (!albumTrackPairs.isEmpty()) {
      boolean playlistHasCapacity = circularPlaylistFitting(playlistId, albumTrackPairs.stream()
          .mapToInt(AlbumTrackPair::trackCount)
          .sum());
      if (playlistHasCapacity) {
        List<List<TrackSimplified>> bundledReleases = extractTrackLists(albumTrackPairs);
        for (List<TrackSimplified> t : bundledReleases) {
          for (List<TrackSimplified> partition : Lists.partition(t, PLAYLIST_ADD_LIMIT)) {
            List<String> ids = partition.stream().map(TrackSimplified::getId).collect(Collectors.toList());
            Playlist playlist = playlistService.getPlaylist(playlistId);
            playlistService.addSongsToPlaylistById(playlist, ids, TOP_OF_PLAYLIST);
            BotUtils.sneakySleep(PLAYLIST_ADDITION_COOLDOWN);
          }
        }
      } else {
        log.error("Playlist has no capacity! " + playlistId);
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
   * Check if circular playlist fitting is required (if enabled; otherwise an
   * exception is thrown)
   *
   * @return true on success, false if playlist is full and can't be cleared
   */
  private boolean circularPlaylistFitting(String playlistId, int songsToAddCount) throws BotException {
    Playlist p = playlistService.getPlaylist(playlistId);

    final int currentPlaylistCount = p.getTracks().getTotal();
    if (currentPlaylistCount + songsToAddCount > PLAYLIST_SIZE_LIMIT) {
      if (!userOptions.isCircularPlaylistFitting()) {
        log.error(p.getName() + " is full! Maximum capacity is " + PLAYLIST_SIZE_LIMIT + ". "
            + "Enable circularPlaylistFitting or flush the playlist for new songs.");
        return false;
      }
      deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount, songsToAddCount);
    }
    return true;
  }

  /**
   * Delete as many songs from the bottom as necessary to make room for any new
   * songs to add, as Spotify playlists have a fixed limit of 10000 songs.
   *
   * If circularPlaylistFitting isn't enabled, an exception is thrown on a full
   * playlist instead.
   */
  private void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws BotException {
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
      deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
    }
  }

}
