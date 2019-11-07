package spotify.bot.crawler;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.config.Config;
import spotify.bot.database.DiscoveryDatabase;

@Component
public class CrawlFinalizer {

	@Autowired
	private Config config;

	
	@Autowired
	private DiscoveryDatabase discoveryDatabase;
	
	public void finalizeCrawl() throws SQLException, SpotifyWebApiException, IOException, InterruptedException {
		config.refreshUpdateStore();
		discoveryDatabase.closeConnection();
	}
}
