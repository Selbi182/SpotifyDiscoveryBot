package spotify.bot.config;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;

@Configuration
public class BotConfigFactory {
	
	@Autowired
	private DatabaseService databaseService;

	/**
	 * Retuns the bot configuration. May be created if not present.
	 * 
	 * @return
	 */
	@Bean
	public SpotifyApiConfig getSpotifyApiConfig() throws SQLException {
		return databaseService.getSpotifyApiConfig();
	}

	/**
	 * Retuns the bot configuration. May be created if not present.
	 * 
	 * @return
	 */
	@Bean
	public StaticConfig getStaticConfig() throws SQLException {
		return databaseService.getStaticConfig();
	}

	/**
	 * Returns the user configuration. May be created if not present.
	 * 
	 * @return
	 * @throws IOException
	 */
	@Bean
	public UserOptions getUserOptions() throws SQLException {
		return databaseService.getUserConfig();
	}

	/**
	 * Returns the playlist store configuration. May be created if not present.
	 * 
	 * @return
	 * @throws SQLException
	 */
	@Bean
	public PlaylistStoreConfig getPlaylistStoreConfig() throws SQLException {
		return databaseService.getPlaylistStoreConfig();
	}
}
