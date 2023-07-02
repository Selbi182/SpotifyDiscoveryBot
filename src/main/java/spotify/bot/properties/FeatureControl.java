package spotify.bot.properties;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;

/**
 * This class controls which settings are manually disabled. The main purpose for this
 * is making development easier. See the file <code>feature_control.ini</code>.
 */
@Configuration
public class FeatureControl {

  @Value("${spotify.discovery.crawl.feature.cache:#{true}}")
  private boolean enableCache;

  @Value("${spotify.discovery.crawl.feature.playlist_additions:#{true}}")
  private boolean enablePlaylistAdditions;

  @Value("${spotify.discovery.crawl.feature.playlist_meta:#{true}}")
  private boolean enablePlaylistMeta;

  @Value("${spotify.discovery.crawl.feature.scheduled_crawls:#{true}}")
  private boolean enableScheduledCrawls;

  @Value("${spotify.discovery.crawl.feature.initial_crawl:#{true}}")
  private boolean enableInitialCrawl;

  @Value("${spotify.discovery.crawl.feature.forwarder:#{true}}")
  private boolean enableForwarder;

  @Value("${spotify.discovery.crawl.feature.auto_purger:#{true}}")
  private boolean enableAutoPurger;

  private final DiscoveryBotLogger logger;

  FeatureControl(DiscoveryBotLogger logger) {
    this.logger = logger;
  }

  @PostConstruct
  void init() throws IllegalAccessException {
    List<String> disabledFeatures = new ArrayList<>();
    for (Field field : FeatureControl.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(Value.class)) {
        boolean enabled = (Boolean) field.get(this);
        if (!enabled) {
          disabledFeatures.add(field.getName().replace("enable", ""));
        }
      }
    }

    if (!disabledFeatures.isEmpty()) {
      String disabledFeaturesText = String.format(">>> DISABLED FEATURE%s [%s] <<<", disabledFeatures.size() != 1 ? "S" : "", String.join(", ", disabledFeatures));
      logger.warning(DiscoveryBotUtils.repeatChar('=', disabledFeaturesText.length()), false);
      logger.warning(disabledFeaturesText, false);
      logger.warning(DiscoveryBotUtils.repeatChar('=', disabledFeaturesText.length()), false);
    }
  }

  public boolean isCacheEnabled() {
    return enableCache;
  }

  public boolean isPlaylistAdditionEnabled() {
    return enablePlaylistAdditions;
  }

  public boolean isPlaylistMetaEnabled() {
    return enablePlaylistMeta;
  }

  public boolean isScheduledCrawlsEnabled() {
    return enableScheduledCrawls;
  }

  public boolean isInitialCrawlEnabled() {
    return enableInitialCrawl;
  }

  public boolean isForwarderEnabled() {
    return enableForwarder;
  }

  public boolean isAutoPurgeEnabled() {
    return enableAutoPurger;
  }
}