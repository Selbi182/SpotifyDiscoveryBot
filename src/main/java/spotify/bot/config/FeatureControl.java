package spotify.bot.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Configuration;

import spotify.api.SpotifyDependenciesSettings;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;

/**
 * This class controls which settings are manually disabled. The main purpose for this
 * is making development easier. See the file <code>feature_control.ini</code>.
 */
@Configuration
public class FeatureControl {
  private static final String FEATURE_CONTROL_FILE_NAME = "feature_control.ini";
  private static final String COMMENT_SYMBOL = "#";

  private enum DevMode {
    /**
     * No albums or artists are ever added to the cache, meaning releases will be rediscovered
     * during every crawl and be classified as new. This will also not prevent these albums getting
     * added to the target playlists, unless DISABLE_PLAYLIST_ADDITIONS is also active.
     */
    DISABLE_CACHE,

    /**
     * Newly discovered releases will not be added to any target playlists. They will still be marked
     * with a notifier though and the "Last Discovery" description will still be set, unless
     * DISABLE_PLAYLIST_META is also active.
     */
    DISABLE_PLAYLIST_ADDITIONS,

    /**
     * The black and white circles of the target playlists and the "Last Discovery" description will not
     * be modified under any circumstance.
     */
    DISABLE_PLAYLIST_META,

    /**
     * Scheduled crawls that run once every half hour will be completely disabled. Only manual calls
     * at /crawl and the initial crawl (if enabled) will work.
     */
    DISABLE_SCHEDULED_CRAWLS,

    /**
     * The initial crawl when first starting the bot will be skipped entirely.
     */
    DISABLE_INITIAL_CRAWL,

    /**
     * Relaying releases will be skipped even when a config file exists.
     */
    DISABLE_RELAY
  }

  private final Set<DevMode> devModes;

  FeatureControl(SpotifyDependenciesSettings spotifyDependenciesSettings, DiscoveryBotLogger logger) {
    Set<DevMode> devModes = Set.of();
    File devModeFile = new File(spotifyDependenciesSettings.configFilesBase(), FEATURE_CONTROL_FILE_NAME);
    if (devModeFile.canRead()) {
      try (Stream<String> lines = Files.lines(devModeFile.toPath())) {
        devModes = lines
          .map(FeatureControl::parseDevModeLine)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      } catch (IOException e) {
        logger.error("Found " + FEATURE_CONTROL_FILE_NAME + " file but couldn't read it. Defaulting to NO developer settings!", false);
        e.printStackTrace();
      }
    }

    if (!devModes.isEmpty()) {
      String devModesString = devModes
        .stream()
        .map(DevMode::toString)
        .collect(Collectors.joining(", "));

      String devModeText = String.format(">>> DISABLED FEATURE%s [%s] <<<", devModes.size() != 1 ? "S" : "", devModesString);

      logger.info(DiscoveryBotUtils.repeatChar('=', devModeText.length()), false);
      logger.info(devModeText, false);
      logger.info(DiscoveryBotUtils.repeatChar('=', devModeText.length()), false);
    }
    this.devModes = devModes;
  }

  private static DevMode parseDevModeLine(String line) {
    String trimmed = line.strip();
    if (!trimmed.isBlank() && !trimmed.startsWith(COMMENT_SYMBOL)) {
      return DevMode.valueOf(trimmed);
    }
    return null;
  }

  public boolean isCacheEnabled() {
    return !devModes.contains(DevMode.DISABLE_CACHE);
  }

  public boolean isPlaylistAdditionEnabled() {
    return !devModes.contains(DevMode.DISABLE_PLAYLIST_ADDITIONS);
  }

  public boolean isPlaylistMetaEnabled() {
    return !devModes.contains(DevMode.DISABLE_PLAYLIST_META);
  }

  public boolean isScheduledCrawlsEnabled() {
    return !devModes.contains(DevMode.DISABLE_SCHEDULED_CRAWLS);
  }

  public boolean isInitialCrawlEnabled() {
    return !devModes.contains(DevMode.DISABLE_INITIAL_CRAWL);
  }

  public boolean isRelayEnabled() {
    return !devModes.contains(DevMode.DISABLE_RELAY);
  }
}