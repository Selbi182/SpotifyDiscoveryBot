package spotify.bot.api;

import java.io.IOException;
import java.sql.SQLException;

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
	 * Creates a SpotifyApi instance with the most common settings. A
	 * preconfiguration from the database is taken first.
	 * 
	 * @return the API instance
	 * 
	 * @throws SpotifyWebApiException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 */
	@Bean
	SpotifyApi spotifyApi() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		SpotifyApi spotifyApi = new SpotifyApi.Builder()
			.setClientId(config.getBotConfig().getClientId())
			.setClientSecret(config.getBotConfig().getClientSecret())
			.setRedirectUri(SpotifyHttpManager.makeUri(config.getBotConfig().getCallbackUri()))
			.build();
		spotifyApi.setAccessToken(config.getUserConfig().getAccessToken());
		spotifyApi.setRefreshToken(config.getUserConfig().getRefreshToken());
		return spotifyApi;
	}
}