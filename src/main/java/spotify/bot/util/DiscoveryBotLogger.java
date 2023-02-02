package spotify.bot.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.BotLogger;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Component
public class DiscoveryBotLogger extends BotLogger {
  private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  /**
   * A comparator for {@link AlbumSimplified} following the order: Album Group >
   * (first) Artist > Release Date > Release Name
   */
  private final static Comparator<AlbumSimplified> ALBUM_SIMPLIFIED_COMPARATOR = Comparator
      .comparing(AlbumSimplified::getAlbumGroup)
      .thenComparing(BotUtils::getFirstArtistName)
      .thenComparing(AlbumSimplified::getReleaseDate)
      .thenComparing(AlbumSimplified::getName);
  
  private final static String LOG_FILE_PATH = "./log.txt";

  private final static int MAX_LINE_LENGTH = 160;
  private final static String ELLIPSIS = "...";
  private final static String DROPPED_PREFIX = "x ";
  private final static String INDENT = " ";

  private boolean hasUnflushedLogs;

  private final Logger log = LoggerFactory.getLogger(DiscoveryBotLogger.class);

  /////////////////////

  /**
   * Log a debug message
   */
  public void debug(String message) {
    logAtLevel(message, Level.DEBUG, true);
  }

  /**
   * Log a debug message
   */
  public void debug(String message, boolean writeToExternalLog) {
    logAtLevel(message, Level.DEBUG, true, writeToExternalLog);
  }

  /**
   * Log an info message
   */
  public void info(String message) {
    logAtLevel(message, Level.INFO, true);
  }

  /**
   * Log an info message
   */
  public void info(String message, boolean writeToExternalLog) {
    logAtLevel(message, Level.INFO, true, writeToExternalLog);
  }

  /**
   * Log a warning message
   */
  public void warning(String message) {
    logAtLevel(message, Level.WARNING, true);
  }

  /**
   * Log a warning message
   */
  public void warning(String message, boolean writeToExternalLog) {
    logAtLevel(message, Level.WARNING, true, writeToExternalLog);
  }

  /**
   * Log an error message
   */
  public void error(String message) {
    logAtLevel(message, Level.ERROR, true);
  }

  /**
   * Log an error message
   */
  public void error(String message, boolean writeToExternalLog) {
    logAtLevel(message, Level.ERROR, true, writeToExternalLog);
  }

  /**
   * Log a message at the given log level (truncate enabled)
   */
  public void logAtLevel(String msg, Level level, boolean writeToExternalLog) {
    logAtLevel(msg, level, true, writeToExternalLog);
  }

  /**
   * Log a message at the given log level (truncation optional). Also writes to an
   * external log.txt file.
   */
  public void logAtLevel(String msg, Level level, boolean truncate, boolean writeToExternalLog) {
    if (truncate) {
      msg = truncateToEllipsis(msg);
    }
    switch (level) {
    case DEBUG:
      log.debug(msg);
      break;
    case INFO:
      log.info(msg);
      break;
    case WARNING:
      log.warn(msg);
      break;
    case ERROR:
      log.error(msg);
      break;
    }

    if (writeToExternalLog) {
      try {
        writeToExternalLog(msg);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    hasUnflushedLogs = true;
  }

  /**
   * Get a String of the given char repeated by its max possible line length
   *
   * @param lineCharacter the character
   * @return the line
   */
  public String line(String lineCharacter) {
    return Strings.repeat(lineCharacter, MAX_LINE_LENGTH - ELLIPSIS.length());
  }

  private void writeToExternalLog(String message) throws IOException {
    File logFile = new File(LOG_FILE_PATH);
    if (!logFile.exists()) {
      boolean fileCreated = logFile.createNewFile();
      if (!fileCreated) {
        throw new IOException("Unable to create log file");
      }
    }
    String logMessage = String.format("[%s] %s", DATE_FORMAT.format(Date.from(Instant.now())), message);
    if (logFile.canWrite()) {
      BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
      bw.write(logMessage);
      bw.write('\n');
      bw.close();
    } else {
      throw new IOException("Log file is currently locked, likely because it is being written to. Try again.");
    }
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
      info(DROPPED_PREFIX + INDENT + BotUtils.formatAlbum(as));
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
