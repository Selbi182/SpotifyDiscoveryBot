package spotify.bot.properties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.data.AlbumGroupExtended;

@Service
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "spotify.discovery.crawl")
public class BlacklistService {
  private Map<String, List<AlbumGroupExtended>> blacklistMap = Map.of();

  private final DiscoveryBotLogger log;

  BlacklistService(DiscoveryBotLogger log) {
    this.log = log;
  }

  @SuppressWarnings("unused") // will be called by Spring on boot
  void setBlacklist(List<String> blacklistRaw) {
    this.blacklistMap = parseBlacklist(blacklistRaw);
    if (!this.blacklistMap.isEmpty()) {
      log.warning("Blacklisting has been enabled! " + this.blacklistMap);
    }
  }

  private Map<String, List<AlbumGroupExtended>> parseBlacklist(List<String> blacklistRaw) {
    Map<String, List<AlbumGroupExtended>> blacklistMap = new HashMap<>();
    try {
      for (String blacklistEntry : blacklistRaw) {
        String[] split = blacklistEntry.split(":");
        String artistId = split[0].strip();
        List<AlbumGroupExtended> blacklistedTypes = Arrays.stream(split[1].split(","))
          .map(String::toUpperCase)
          .map(AlbumGroupExtended::valueOf)
          .collect(Collectors.toList());
        blacklistMap.put(artistId, blacklistedTypes);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return Map.of();
    }
    return blacklistMap;
  }

  public Map<String, List<AlbumGroupExtended>> getBlacklistMap() {
    return blacklistMap;
  }
}
