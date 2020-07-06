package spotify.bot.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

import org.springframework.context.annotation.Configuration;

import com.google.common.base.Strings;
import com.google.common.io.Files;

/**
 * This class controls the developer mode state of the application. Cache and
 * playlist additions can be individually or completely turned off depening on
 * the contents of a file called <code>DEV_MODE.txt</code> in the working
 * directory.
 */
@Configuration
public class DeveloperMode {
	private enum DevMode {
		/**
		 * No dev mode, used for Live/Production, also the default case
		 */
		OFF,

		/**
		 * Releases won't be cached (meaning they will get reclassified as "new" with
		 * each crawl), but they will still be added to any playlists as usual
		 */
		DISABLE_CACHE,

		/**
		 * Releases will be cached as usual, but they will not be added to any playlists
		 */
		DISABLE_PLAYLIST_ADDITIONS,

		/**
		 * Releases are neither cached nor added to playlist (default dev mode)
		 */
		DISABLE_ALL
	}

	private static DevMode DEVELOPER_MODE;
	static {
		File devModeFile = new File("./DEV_MODE.txt");
		DevMode devMode = DevMode.OFF;
		if (devModeFile.canRead()) {
			try {
				String firstLine = Files.asCharSource(devModeFile, StandardCharsets.UTF_8).readFirstLine();
				devMode = DevMode.valueOf(firstLine.trim());
			} catch (IOException | NullPointerException e) {
				System.out.println("Found DEV_MODE.txt file but couldn't read it. Defaulting to DISABLE_ALL!");
				devMode = DevMode.DISABLE_ALL;
				e.printStackTrace();
			}
		}
		DEVELOPER_MODE = devMode;

		if (isAnyDevMode()) {
			StringJoiner sj = new StringJoiner(" or ");
			if (isCacheDisabled()) {
				sj.add("cached");
			}
			if (isPlaylistAdditionDisabled()) {
				sj.add("added to playlists");
			}
			String devModeText = String.format(">>> DEVELOPER MODE -- releases will NOT be %s <<<", sj.toString());

			System.out.println(Strings.repeat("=", devModeText.length()));
			System.out.println(devModeText);
			System.out.println(Strings.repeat("=", devModeText.length()));
		}
	}

	/**
	 * Return true if ANY dev mode except is set (anything except OFF)
	 */
	public static boolean isAnyDevMode() {
		return !DEVELOPER_MODE.equals(DevMode.OFF);
	}

	/**
	 * Return true if caching is disabled in the current dev mode
	 */
	public static boolean isCacheDisabled() {
		return isFullDevMode() || DEVELOPER_MODE.equals(DevMode.DISABLE_CACHE);
	}

	/**
	 * Return true if playlist additions are disabled in the current dev mode
	 */
	public static boolean isPlaylistAdditionDisabled() {
		return isFullDevMode() || DEVELOPER_MODE.equals(DevMode.DISABLE_PLAYLIST_ADDITIONS);
	}

	/**
	 * Return true if the full dev mode is enabled (BOTH caching and playlist
	 * additions are disabled)
	 */
	public static boolean isFullDevMode() {
		return DEVELOPER_MODE.equals(DevMode.DISABLE_ALL);
	}
}