package spotify.bot.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

public class DiscoveryBotUtils {
  /**
   * Indicates how many days in the past are to be considered "present".
   * This is required due to rare occasions where a song gets added slightly later
   * on Spotify than, say, on physical media or Bandcamp.
   */
  public final static int LOOKBACK_DAYS = 60;

  /**
   * A common order of the different playlist groups: Album > Single > EP > Remix
   * > Live > Compilation > Re-Release > Appears On
   */
  public final static List<AlbumGroupExtended> DEFAULT_PLAYLIST_GROUP_ORDER = Arrays.asList(
      AlbumGroupExtended.ALBUM,
      AlbumGroupExtended.SINGLE,
      AlbumGroupExtended.EP,
      AlbumGroupExtended.REMIX,
      AlbumGroupExtended.LIVE,
      AlbumGroupExtended.COMPILATION,
      AlbumGroupExtended.RE_RELEASE,
      AlbumGroupExtended.APPEARS_ON);

  /**
   * Utility class
   */
  private DiscoveryBotUtils() {
  }

  /**
   * Alternative string representation for AlbumTrackPairs in the SpotifyDiscoveryBot
   *
   * @param albumTrackPair the atp
   * @param albumGroupExtended the extended AlbumGroup
   * @return the string
   */
  public static String toStringExtended(AlbumTrackPair albumTrackPair, AlbumGroupExtended albumGroupExtended) {
    AlbumSimplified album = albumTrackPair.getAlbum();
    List<TrackSimplified> tracks = albumTrackPair.getTracks();
    if (album == null || tracks == null) {
      return albumTrackPair.toString();
    }
    String baseRepresentation = String.format("[%s] %s - %s (%s)", albumGroupExtended.toString(), SpotifyUtils.joinArtists(album.getArtists()), album.getName(), album.getReleaseDate());
    if (tracks.size() != 1) {
      return String.format("%s <%d>", baseRepresentation, tracks.size());
    }
    return baseRepresentation;
  }

  /**
   * Compiles the final results of the bot if any songs were added
   *
   * @param songsAddedPerAlbumGroups the count of songs per album group
   * @return the result string
   */
  public static String compileResultString(Map<AlbumGroupExtended, Integer> songsAddedPerAlbumGroups) {
    if (songsAddedPerAlbumGroups != null) {
      int totalSongsAdded = songsAddedPerAlbumGroups.values().stream().mapToInt(Integer::intValue).sum();
      if (totalSongsAdded > 0) {
        StringJoiner sj = new StringJoiner(" / ");
        for (AlbumGroupExtended age : DEFAULT_PLAYLIST_GROUP_ORDER) {
          Integer songsAdded = songsAddedPerAlbumGroups.get(age);
          if (songsAdded != null && songsAdded > 0) {
            sj.add(songsAdded + " " + age);
          }
        }
        return (String.format("%d new song%s added! [%s]", totalSongsAdded, totalSongsAdded != 1 ? "s" : "", sj));
      }
    }
    return "";
  }

  /**
   * Build a readable String for dropped AlbumSimplified with a custom album group
   */
  public static String formatAlbum(AlbumSimplified as, AlbumGroupExtended customAlbumGroup) {
    return String.format("[%s] %s - %s (%s)",
        customAlbumGroup.toString(),
        SpotifyUtils.joinArtists(as.getArtists()),
        as.getName(),
        as.getReleaseDate());
  }

  /**
   * Write the song count per album group into the target map
   */
  public static Map<AlbumGroupExtended, Integer> collectSongAdditionResults(Map<PlaylistStore, List<AlbumTrackPair>> songsByPlaylist) {
    Map<AlbumGroupExtended, Integer> targetCountMap = new HashMap<>();
    for (Map.Entry<PlaylistStore, List<AlbumTrackPair>> entry : songsByPlaylist.entrySet()) {
      int totalSongsOfGroup = entry.getValue().stream().mapToInt(atp -> atp.getTracks().size()).sum();
      targetCountMap.put(entry.getKey().getAlbumGroupExtended(), totalSongsOfGroup);
    }
    return targetCountMap;
  }

  /**
   * Return a string which only contains a single character repeated n times
   */
  public static String repeatChar(char character, int length) {
    char[] build = new char[length];
    Arrays.fill(build, character);
    return String.valueOf(build);
  }
}
