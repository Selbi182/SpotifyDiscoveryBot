package spotify.bot.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.api.SpotifyDependenciesSettings;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class DiscoveryBotLogger extends SpotifyLogger {
  /**
   * A comparator for {@link AlbumSimplified} following the order: Album Group >
   * (first) Artist > Release Date > Release Name
   */
  private final static Comparator<AlbumSimplified> ALBUM_SIMPLIFIED_COMPARATOR = Comparator
      .comparing(AlbumSimplified::getAlbumGroup)
      .thenComparing(SpotifyUtils::getFirstArtistName)
      .thenComparing(AlbumSimplified::getReleaseDate)
      .thenComparing(AlbumSimplified::getName);

  private final static String DROPPED_PREFIX = "x ";
  private final static String INDENT = " ";

  private boolean hasUnflushedLogs;

  DiscoveryBotLogger(SpotifyDependenciesSettings spotifyDependenciesSettings) {
    super(spotifyDependenciesSettings);
  }

  /////////////////////

  /**
   * Log a debug message (NOT written to external log)
   */
  @Override
  public void debug(String message) {
    debug(message, false);
  }

  /**
   * Log a debug message
   */
  public void debug(String message, boolean writeToExternalLog) {
    updateFlushedStatus(writeToExternalLog);
    logAtLevel(message, Level.DEBUG, true, writeToExternalLog);
  }

  /**
   * Log an info message
   */
  @Override
  public void info(String message) {
    info(message, true);
  }

  /**
   * Log an info message
   */
  public void info(String message, boolean writeToExternalLog) {
    updateFlushedStatus(writeToExternalLog);
    logAtLevel(message, Level.INFO, true, writeToExternalLog);
  }

  /**
   * Log a warning message
   */
  @Override
  public void warning(String message) {
    warning(message, true);
  }

  /**
   * Log a warning message
   */
  public void warning(String message, boolean writeToExternalLog) {
    updateFlushedStatus(writeToExternalLog);
    logAtLevel(message, Level.WARNING, true, writeToExternalLog);
  }

  /**
   * Log an error message
   */
  @Override
  public void error(String message) {
    error(message, true);
  }

  /**
   * Log an error message
   */
  public void error(String message, boolean writeToExternalLog) {
    updateFlushedStatus(writeToExternalLog);
    logAtLevel(message, Level.ERROR, true, writeToExternalLog);
  }

  /////////////////////

  /**
   * Reset the log and print a line if anything was flushed
   */
  public void resetAndPrintLine() {
    if (reset()) {
      printLine();
      reset();
    }
  }

  /**
   * Set the unflushed logs flag to true if it isn't already and the bot is writing to the external log
   */
  private void updateFlushedStatus(boolean writeToExternalLog) {
    this.hasUnflushedLogs = hasUnflushedLogs || writeToExternalLog;
  }

  /**
   * Resets the hasUnflushedLogs flag
   *
   * @return true if anything was flushed
   */
  private boolean reset() {
    boolean hasBeenReset = this.hasUnflushedLogs;
    this.hasUnflushedLogs = false;
    return hasBeenReset;
  }

  /////////////////////

  /**
   * Print the given list of album track pairs and override their internal album
   * group with the given extension
   */
  public void printAlbumTrackPairs(List<AlbumTrackPair> albumTrackPairs, AlbumGroupExtended albumGroupExtended) {
    for (AlbumTrackPair atp : albumTrackPairs) {
      info(INDENT + DiscoveryBotUtils.toStringExtended(atp, albumGroupExtended));
    }
  }

  /**
   * Print the given list of AlbumSimplifieds with a prefix indicating that they
   * were dropped
   */
  private void printDroppedAlbumSimplified(List<AlbumSimplified> albumSimplifieds) {
    for (AlbumSimplified as : albumSimplifieds) {
      info(DROPPED_PREFIX + INDENT + SpotifyUtils.formatAlbum(as));
    }
  }

  /**
   * Log all releases in base which aren't in subtrahend
   */
  public void printDroppedAlbumDifference(Collection<AlbumSimplified> base, Collection<AlbumSimplified> subtrahend, String logDescription) {
    Set<AlbumSimplified> differenceView = new HashSet<>(base);
    differenceView.removeAll(subtrahend);
    printDroppedAlbums(differenceView, logDescription);
  }

  /**
   * Log the dropped album track pairs
   */
  public void printDroppedAlbumTrackPairs(Collection<AlbumTrackPair> droppedAlbums, String logDescription) {
    List<AlbumSimplified> collect = droppedAlbums.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList());
    printDroppedAlbums(collect, logDescription);
  }

  /**
   * Log the dropped albums
   */
  public void printDroppedAlbums(Collection<AlbumSimplified> droppedAlbums, String logDescription) {
    if (!droppedAlbums.isEmpty()) {
      if (logDescription != null) {
        info(DROPPED_PREFIX + logDescription);
      }
      List<AlbumSimplified> sortedDroppedAlbums = droppedAlbums.stream().sorted(ALBUM_SIMPLIFIED_COMPARATOR).collect(Collectors.toList());
      printDroppedAlbumSimplified(sortedDroppedAlbums);
    }
  }

  /**
   * Same as printDroppedAlbumDifference but for AlbumTrackPairs
   */
  public void printDroppedAlbumTrackPairDifference(Collection<AlbumTrackPair> unfilteredReleases, Collection<AlbumTrackPair> filteredReleases, String logDescription) {
    printDroppedAlbumDifference(unfilteredReleases.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
        filteredReleases.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
        logDescription);
  }

  public void printDroppedAlbumsCustomGroup(List<Map.Entry<AlbumSimplified, AlbumGroupExtended>> droppedAlbums, String logDescription){
    if (!droppedAlbums.isEmpty()) {
      if (logDescription != null) {
        info(DROPPED_PREFIX + logDescription);
      }
      for (Map.Entry<AlbumSimplified, AlbumGroupExtended> droppedAlbum : droppedAlbums) {
        info(DROPPED_PREFIX + INDENT + DiscoveryBotUtils.formatAlbum(droppedAlbum.getKey(), droppedAlbum.getValue()));
      }
    }
  }
}
