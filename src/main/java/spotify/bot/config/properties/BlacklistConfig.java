package spotify.bot.config.properties;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.context.annotation.Configuration;

import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@Configuration
public class BlacklistConfig {
  private final static String BLACKLIST_FILENAME = DiscoveryBotUtils.BASE_CONFIG_PATH + "blacklist.properties";

  private final Map<String, List<AlbumGroupExtended>> blacklistMap;

  BlacklistConfig() {
    this.blacklistMap = getBlacklistFromPropertiesFile();
  }

  private Map<String, List<AlbumGroupExtended>> getBlacklistFromPropertiesFile() {
    Map<String, List<AlbumGroupExtended>> blacklistMap = new HashMap<>();
    try {
      File propertiesFile = new File(BLACKLIST_FILENAME);
      if (propertiesFile.canRead()) {
        FileReader reader = new FileReader(propertiesFile);
        Properties properties = new Properties();
        properties.load(reader);

        for (Object artistIdRaw : properties.keySet()) {
          String artistId = (String) artistIdRaw;
          String[] blacklistForArtist = properties.getProperty(artistId).split(",");
          List<AlbumGroupExtended> blacklistedAlbumGroupsForArtist = new ArrayList<>();
          for (String blacklistedType : blacklistForArtist) {
            blacklistedAlbumGroupsForArtist.add(AlbumGroupExtended.valueOf(blacklistedType));
          }
          blacklistMap.put(artistId, blacklistedAlbumGroupsForArtist);
        }
      }
    } catch (IOException e) {
      System.out.println("Failed to read " + BLACKLIST_FILENAME);
    }
    return blacklistMap;
  }

  public Map<String, List<AlbumGroupExtended>> getBlacklistMap() {
    return blacklistMap;
  }
}
