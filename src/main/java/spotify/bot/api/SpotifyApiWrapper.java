package spotify.bot.api;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wrapper.spotify.IHttpManager;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.config.Config;

@Configuration
public class SpotifyApiWrapper {

	@Autowired
	private Config config;

	/**
	 * Create a default Spotify API instance
	 * 
	 * @return the API instance
	 * 
	 * @throws SpotifyWebApiException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	@Bean
	SpotifyApi spotifyApi() throws SpotifyWebApiException, InterruptedException, IOException {
		SpotifyApi spotifyApi = defaultSpotifyApiBuilder().build();
		initializeTokens(spotifyApi);
		return spotifyApi;
	}

	/**
	 * Create a SpotifyApi instance with a cache size of 0. This makes the API
	 * significantly slower, but may be useful in some instances where real-time
	 * data is required
	 * 
	 * @return
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 */
	@Bean
	SpotifyApi nonCachingSpotifyApi() throws SpotifyWebApiException, InterruptedException, IOException {
		SpotifyApi nonCachingSpotifyApi = defaultSpotifyApiBuilder()
			.setHttpManager(nonCacheHttpManager())
			.build();
		initializeTokens(nonCachingSpotifyApi);
		return nonCachingSpotifyApi;
	}

	/**
	 * Create a base Spotify API builder with only the mandatory settings
	 * 
	 * @return
	 */
	private SpotifyApi.Builder defaultSpotifyApiBuilder() {
		return new SpotifyApi.Builder()
			.setClientId(config.getClientId())
			.setClientSecret(config.getClientSecret())
			.setRedirectUri(SpotifyHttpManager.makeUri(config.getCallbackUri()));
	}

	/**
	 * Set the previously set tokens of the config, if any were set
	 * 
	 * @param api
	 */
	private void initializeTokens(SpotifyApi api) {
		api.setAccessToken(config.getAccessToken());
		api.setRefreshToken(config.getRefreshToken());
	}

	/**
	 * Create a SpotifyHttpManager with a cache size of 0
	 * 
	 * @return
	 */
	private IHttpManager nonCacheHttpManager() {
		IHttpManager nonCacheHttpManager = new SpotifyHttpManager.Builder()
			.setCacheMaxEntries(0)
			.build();
		return nonCacheHttpManager;
	}
}
