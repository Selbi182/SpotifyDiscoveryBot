package spotify.bot.config;

import java.sql.SQLException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.config.dto.UserOptions;

@Configuration
public class BotConfigFactory {

	private final DatabaseService databaseService;

	BotConfigFactory(DatabaseService databaseService) {
		this.databaseService = databaseService;
	}

	/**
	 * Returns the bot configuration. May be created if not present.
	 */
	@Bean
	public StaticConfig getStaticConfig() throws SQLException {
		return databaseService.getStaticConfig();
	}

	/**
	 * Returns the user configuration. May be created if not present.
	 */
	@Bean
	public UserOptions getUserOptions() throws SQLException {
		return databaseService.getUserConfig();
	}

	/**
	 * Returns the playlist store configuration. May be created if not present.
	 */
	@Bean
	public PlaylistStoreConfig getPlaylistStoreConfig() throws SQLException {
		return databaseService.getPlaylistStoreConfig();
	}
}
