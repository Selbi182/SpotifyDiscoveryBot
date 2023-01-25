package spotify.bot.api;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import spotify.bot.config.dto.SpotifyApiConfig;

@Configuration
public class SpotifyApiWrapper {

	@Autowired
	private SpotifyApiConfig spotifyApiConfig;

	/**
	 * Creates a SpotifyApi instance with the most common settings. A
	 * preconfiguration from the database is taken first.
	 * 
	 * @return the API instance
	 */
	@Bean
	SpotifyApi spotifyApi() {
		SpotifyApi spotifyApi = new SpotifyApi.Builder()
			.setClientId(spotifyApiConfig.getClientId())
			.setClientSecret(spotifyApiConfig.getClientSecret())
			.setRedirectUri(createRedirectUri())
			.build();
		spotifyApi.setAccessToken(spotifyApiConfig.getAccessToken());
		spotifyApi.setRefreshToken(spotifyApiConfig.getRefreshToken());
		return spotifyApi;
	}

	@Value("${server.port}")
	private String serverPort;

	private URI createRedirectUri() {
		String callbackUri = UriComponentsBuilder.newInstance()
			.scheme("http")
			.host("localhost")
			.port(Integer.parseInt(serverPort))
			.path(SpotifyApiAuthorization.LOGIN_CALLBACK_URI)
			.build()
			.toUriString();
		return SpotifyHttpManager.makeUri(callbackUri);
	}
}
