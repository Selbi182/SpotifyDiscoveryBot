package spotify.bot.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Configuration;

import com.google.common.base.Strings;

/**
 * This class controls the developer mode state of the application. Cache and
 * playlist additions can be individually or completely turned off depening on
 * the contents of a file called <code>DEV_MODE.txt</code> in the working
 * directory.
 */
@Configuration
public class DeveloperMode {
	private static final String COMMENT_SYMBOL = "#";

	private enum DevMode {
		/**
		 * Releases won't be cached (meaning they will get reclassified as "new" with
		 * each crawl)
		 */
		DISABLE_CACHE,

		/**
		 * Releases will not be added to the target playlists and the metadata will not
		 * be updated
		 */
		DISABLE_PLAYLIST_ADDITIONS,

		/**
		 * Scheduled crawls that run once every 30 minutes will be completely disabled
		 * only manual calls at {@code /crawl} will be executed
		 */
		DISABLE_SCHEDULED_CRAWLS,

		/**
		 * The initial crawl when first starting the bot will be skipped entirely
		 */
		SKIP_INITIAL_CRAWL
	}

	private static Set<DevMode> devModes = Collections.emptySet();
	static {
		File devModeFile = new File("./DEV_MODE.txt");
		if (devModeFile.canRead()) {
			try (Stream<String> lines = Files.lines(devModeFile.toPath())) {
				devModes = lines
					.map(DeveloperMode::parseDevModeLine)
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			} catch (IOException e) {
				System.out.println("Found DEV_MODE.txt file but couldn't read it. Defaulting to NO developer settings!");
				e.printStackTrace();
			}
		}

		if (!devModes.isEmpty()) {
			String devModesString = devModes
				.stream()
				.map(DevMode::toString)
				.collect(Collectors.joining(", "));

			String devModeText = String.format(">>> DEVELOPER MODE [%s] <<<", devModesString);

			System.out.println(Strings.repeat("=", devModeText.length()));
			System.out.println(devModeText);
			System.out.println(Strings.repeat("=", devModeText.length()));
		}
	}

	private static DevMode parseDevModeLine(String line) {
		String trimmed = line.strip();
		if (!trimmed.isBlank() && !trimmed.startsWith(COMMENT_SYMBOL)) {
			DevMode parsedDevMode = DevMode.valueOf(trimmed);
			return parsedDevMode;
		}
		return null;
	}

	/**
	 * Return true if caching is disabled
	 */
	public static boolean isCacheDisabled() {
		return devModes.contains(DevMode.DISABLE_CACHE);
	}

	/**
	 * Return true if playlist additions are disabled
	 */
	public static boolean isPlaylistAdditionDisabled() {
		return devModes.contains(DevMode.DISABLE_PLAYLIST_ADDITIONS);
	}

	/**
	 * Return true if scheduled crawls are disabled
	 */
	public static boolean isScheduledCrawlDisabled() {
		return devModes.contains(DevMode.DISABLE_SCHEDULED_CRAWLS);
	}

	/**
	 * Return true if the initial crawl should be skipped
	 */
	public static boolean isInitialCrawlSkipped() {
		return devModes.contains(DevMode.SKIP_INITIAL_CRAWL);
	}
}