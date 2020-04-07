package spotify.bot.api;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

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
	SpotifyApi spotifyApi() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		SpotifyApi spotifyApi = new SpotifyApi.Builder()
			.setClientId(spotifyApiConfig.getClientId())
			.setClientSecret(spotifyApiConfig.getClientSecret())
			.setRedirectUri(SpotifyHttpManager.makeUri(SpotifyApiAuthorization.LOGIN_CALLBACK_URI))
			.build();
		spotifyApi.setAccessToken(spotifyApiConfig.getAccessToken());
		spotifyApi.setRefreshToken(spotifyApiConfig.getRefreshToken());
		return spotifyApi;
	}
}
