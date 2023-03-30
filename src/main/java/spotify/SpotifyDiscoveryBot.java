package spotify;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Component;

import spotify.api.SpotifyDependenciesSettings;

@SpringBootApplication
public class SpotifyDiscoveryBot {
  public static void main(String[] args) {
    new SpringApplicationBuilder(SpotifyDiscoveryBot.class).headless(GraphicsEnvironment.isHeadless()).run(args);
  }

  @Component
  public static class SpotifyDiscoveryBotSettings implements SpotifyDependenciesSettings {

    @Override
    public List<String> requiredScopes() {
      return List.of(
          "user-read-playback-position",
          "user-read-playback-state",
          "user-read-currently-playing",
          "user-read-private",
          "user-read-email",
          "playlist-modify-private",
          "playlist-modify-public",
          "playlist-read-collaborative",
          "playlist-read-private",
          "user-follow-read"
      );
    }

    @Override
    public int port() {
      return 8182;
    }

    @Override
    public boolean enableExternalLogging() {
      return true;
    }

    @Override
    public File configFilesBase() {
      return new File("./config/");
    }
  }
}
