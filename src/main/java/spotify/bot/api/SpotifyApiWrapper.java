package spotify.bot.api;

import java.io.IOException;
import java.sql.SQLException;

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

	private static final int ZERO_SIZE_CACHE = 0;

	@Autowired
	private Config config;

	/**
	 * Creates a SpotifyApi instance with a cache size of 0 entries (default would
	 * be 1000). This makes the API somewhat slower under most circumstances, but
	 * it's required to get real-time updates about new releases at the minute they
	 * come out
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
			.setHttpManager(nonCachingHttpManager())
			.build();
		initializeTokens(spotifyApi);
		return spotifyApi;
	}

	/**
	 * Set the previously set tokens of the config, if any were set
	 * 
	 * @param api
	 * @throws IOException
	 * @throws SQLException
	 */
	private void initializeTokens(SpotifyApi api) throws SQLException, IOException {
		api.setAccessToken(config.getUserConfig().getAccessToken());
		api.setRefreshToken(config.getUserConfig().getRefreshToken());
	}

	/**
	 * Create a SpotifyHttpManager with a cache size of 0
	 * 
	 * @return
	 */
	private IHttpManager nonCachingHttpManager() {
		IHttpManager nonCacheHttpManager = new SpotifyHttpManager.Builder()
			.setCacheMaxEntries(ZERO_SIZE_CACHE)
			.build();
		return nonCacheHttpManager;
	}
}
