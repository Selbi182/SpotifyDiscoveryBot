package spotify;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import spotify.api.SpotifyDependenciesSettings;

@SpringBootApplication
public class SpotifyDiscoveryBot {
	public static void main(String[] args) {
		SpringApplication.run(SpotifyDiscoveryBot.class, args);
	}

	@Component
	public static class Scopes implements SpotifyDependenciesSettings {

		@Override
		public List<String> requiredScopes() {
			return List.of(
					"user-read-playback-position",
					"user-read-playback-state",
					"user-read-currently-playing",
					"user-read-private",
					"playlist-modify-private",
					"playlist-read-private",
					"user-follow-read"
			);
		}

		@Override
		public int port() {
			return 8182;
		}
	}
}
