package spotify.bot.crawler;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.config.Config;
import spotify.bot.database.DiscoveryDatabase;

@Component
public class CrawlFinalizer {

	@Autowired
	private Config config;

	@Autowired
	private SpotifyApiAuthorization spotifyApiAuthorization;

	@Autowired
	private DiscoveryDatabase discoveryDatabase;

	public void finalizeResources() throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		config.refreshUpdateStore();
		spotifyApiAuthorization.login();
		discoveryDatabase.closeConnection();
	}
}
