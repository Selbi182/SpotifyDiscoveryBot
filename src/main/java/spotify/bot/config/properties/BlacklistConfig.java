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

import spotify.api.SpotifyDependenciesSettings;
import spotify.bot.util.data.AlbumGroupExtended;

@Configuration
public class BlacklistConfig {
  private final static String BLACKLIST_FILENAME = "blacklist.properties";

  private final Map<String, List<AlbumGroupExtended>> blacklistMap;
  private final File blacklistFile;

  BlacklistConfig(SpotifyDependenciesSettings spotifyDependenciesSettings) {
    this.blacklistFile = new File(spotifyDependenciesSettings.configFilesBase(), BLACKLIST_FILENAME);
    this.blacklistMap = getBlacklistFromPropertiesFile();
  }

  private Map<String, List<AlbumGroupExtended>> getBlacklistFromPropertiesFile() {
    Map<String, List<AlbumGroupExtended>> blacklistMap = new HashMap<>();
    try {
      if (blacklistFile.canRead()) {
        FileReader reader = new FileReader(blacklistFile);
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
