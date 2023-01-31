package spotify.bot.config.properties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.springframework.context.annotation.Configuration;

import spotify.bot.util.DiscoveryBotLogger;

@Configuration
public class UserOptionsConfig {
	public final static String PROP_INTELLIGENT_APPEARS_ON_SEARCH = "intelligent_appears_on_search";
	public final static String PROP_CIRCULAR_PLAYLIST_FITTING = "circular_playlist_fitting";
	public final static String PROP_EP_SEPARATION = "ep_separation";
	public final static String PROP_REMIX_SEPARATION = "remix_separation";
	public final static String PROP_LIVE_SEPARATION = "live_separation";
	public final static String PROP_RERELEASE_SEPARATION = "rerelease_separation";

	private final static String USER_OPTIONS_FILENAME = "./config/useroptions.properties";

	private final Properties userOptions;

	private final DiscoveryBotLogger discoveryBotLogger;

	UserOptionsConfig(DiscoveryBotLogger discoveryBotLogger) {
		this.discoveryBotLogger = discoveryBotLogger;
		this.userOptions = getUserOptionsFromPropertiesFile();
	}

	public boolean isIntelligentAppearsOnSearch() {
		return getBooleanProperty(PROP_INTELLIGENT_APPEARS_ON_SEARCH);
	}

	public boolean isCircularPlaylistFitting() {
		return getBooleanProperty(PROP_CIRCULAR_PLAYLIST_FITTING);
	}

	public boolean isEpSeparation() {
		return getBooleanProperty(PROP_EP_SEPARATION);
	}

	public boolean isRemixSeparation() {
		return getBooleanProperty(PROP_REMIX_SEPARATION);
	}

	public boolean isLiveSeparation() {
		return getBooleanProperty(PROP_LIVE_SEPARATION);
	}

	public boolean isRereleaseSeparation() {
		return getBooleanProperty(PROP_RERELEASE_SEPARATION);
	}

	///////////////

	private Properties getUserOptionsFromPropertiesFile() {
		try {
			File propertiesFile = new File(USER_OPTIONS_FILENAME);
			if (!propertiesFile.exists()) {
				if (propertiesFile.getParentFile().mkdirs() & propertiesFile.createNewFile()) {
					discoveryBotLogger.info("UserOptionsConfig properties file not found. Creating new file with all values set to true");
				}
			}
			FileReader reader = new FileReader(propertiesFile);
			Properties properties = new Properties();
			properties.load(reader);
			createMissingProperties(properties);
			return properties;
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to read " + USER_OPTIONS_FILENAME + ". Terminating!");
			System.exit(1);
			return null;
		}
	}

	private void createMissingProperties(Properties properties) throws IOException {
		properties.putIfAbsent(PROP_INTELLIGENT_APPEARS_ON_SEARCH, "true");
		properties.putIfAbsent(PROP_CIRCULAR_PLAYLIST_FITTING, "true");
		properties.putIfAbsent(PROP_EP_SEPARATION, "true");
		properties.putIfAbsent(PROP_REMIX_SEPARATION, "true");
		properties.putIfAbsent(PROP_LIVE_SEPARATION, "true");
		properties.putIfAbsent(PROP_RERELEASE_SEPARATION, "true");
		properties.store(new FileOutputStream(USER_OPTIONS_FILENAME), null);
	}

	private boolean getBooleanProperty(String propertyName) {
		return Boolean.parseBoolean(userOptions.getProperty(propertyName));
	}
}
