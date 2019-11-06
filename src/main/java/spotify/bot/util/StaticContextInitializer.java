package spotify.bot.util;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import spotify.bot.config.Config;

@Configuration
public class StaticContextInitializer {

	@Autowired
	private Config config;

	@PostConstruct
	public void injectStaticConfigContext() {
		BotUtils.initializeUtilConfig(config);
		ReleaseValidator.initializeUtilConfig(config);
	}
}
