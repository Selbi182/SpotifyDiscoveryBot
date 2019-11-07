package spotify.bot.api;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.config.Config;

@Configuration
public class SpotifyApiWrapper {

	@Autowired
	private Config config;

	/**
	 * Get the current SpotifyApi instance
	 * 
	 * @return
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	@Bean
	SpotifyApi spotifyApi() throws SpotifyWebApiException, InterruptedException, IOException {
		SpotifyApi spotifyApi = new SpotifyApi.Builder()
			.setClientId(config.getClientId())
			.setClientSecret(config.getClientSecret())
			.setRedirectUri(SpotifyHttpManager.makeUri(config.getCallbackUri()))
			.build();

		spotifyApi.setAccessToken(config.getAccessToken());
		spotifyApi.setRefreshToken(config.getRefreshToken());

		return spotifyApi;
	}
}
