package spotify.bot.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.util.data.AlbumTrackPair;

@Service
@RestController
public class BotLogger {
	private final static String LOG_FILE_PATH = "./spring.log";
	private final static int DEFAULT_LOG_READ_LINES = 100;

	private Logger log;

	@PostConstruct
	private void init() throws SecurityException, IOException {
		this.log = LoggerFactory.getLogger(BotLogger.class);
	}

	//////////////////////
	// Base Logging

	/**
	 * Log a debug message
	 */
	public void debug(String message) {
		log.debug(message);
	}

	/**
	 * Log an info message
	 */
	public void info(String message) {
		log.info(message);
	}

	/**
	 * Log a warning message
	 */
	public void warning(String message) {
		log.warn(message);
	}

	/**
	 * Log an error message
	 */
	public void error(String message) {
		log.error(message);
	}

	///////////////////////

	/**
	 *
	 * Displays the contents of the automatically configurated, most recent Spring
	 * logging file (<code>./spring.log</code>).
	 * 
	 * @param limit
	 *            (optional) maximum number of lines to read from the top of the log
	 *            (default: 100); Use -1 to read the entire file
	 * @return a ResponseEntity containing the entire log content as String, or an
	 *         error
	 * @throws IOException
	 */
	@RequestMapping("/log")
	public ResponseEntity<List<String>> showLog(@RequestParam(value = "limit", required = false) Integer limit) throws IOException {
		File logFile = new File(LOG_FILE_PATH);
		if (logFile.exists()) {
			if (logFile.canRead()) {
				if (limit == null) {
					limit = DEFAULT_LOG_READ_LINES;
				} else if (limit < 0) {
					limit = Integer.MAX_VALUE;
				}
				List<String> logFileLines = Files.lines(logFile.toPath()).limit(limit).collect(Collectors.toList());
				return new ResponseEntity<List<String>>(logFileLines, HttpStatus.OK);
			} else {
				return new ResponseEntity<List<String>>(Arrays.asList("Log file is currently locked, likely because it is being written to. Try again."), HttpStatus.LOCKED);
			}
		} else {
			return new ResponseEntity<List<String>>(Arrays.asList("Couldn't find log file under expected location " + logFile.getAbsolutePath()), HttpStatus.NOT_FOUND);
		}
	}

	//////////////////////

	/**
	 * Log and print the given exception's stack trace
	 * 
	 * @param e
	 */
	public void stackTrace(Exception e) {
		StringWriter stringWriter = new StringWriter();
		try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
			e.printStackTrace(printWriter);
		}
		log.error(stringWriter.toString());
	}

	/**
	 * Build a readable String for AlbumSimplified
	 * 
	 * @param as
	 * @return
	 */
	public String prettyAlbumSimplified(AlbumSimplified as) {
		return String.format("[%s] %s - %s (%s)",
			as.getAlbumGroup().toString(),
			as.getArtists()[0].getName(),
			as.getName(),
			as.getReleaseDate());
	}

	/**
	 * Print the given list of AlbumSimplifieds
	 * 
	 * @param albumTrackPairs
	 */
	public void printAlbumSimplifiedMulti(List<AlbumSimplified> albumSimplifieds) {
		for (AlbumSimplified as : albumSimplifieds) {
			debug(prettyAlbumSimplified(as));
		}

	}

	/**
	 * Print the given list of album track pairs
	 * 
	 * @param albumTrackPairs
	 */
	public void printAlbumTrackPairs(List<AlbumTrackPair> albumTrackPairs) {
		for (AlbumTrackPair as : albumTrackPairs) {
			debug(as.toString());
		}
	}

	/**
	 * Log all releases in base which aren't in subtrahend
	 * 
	 * @param base
	 * @param subtrahend
	 * @param logDescription
	 */
	public void printAlbumDifference(Collection<AlbumSimplified> base, Collection<AlbumSimplified> subtrahend, String logDescription) {
		Set<AlbumSimplified> differenceView = new HashSet<>(base);
		differenceView.removeAll(subtrahend);
		if (!differenceView.isEmpty()) {
			if (logDescription != null) {
				debug(logDescription);
			}
			List<AlbumSimplified> sortedDifferenceView = differenceView
				.stream()
				.sorted(BotUtils.ALBUM_SIMPLIFIED_COMPARATOR)
				.collect(Collectors.toList());
			printAlbumSimplifiedMulti(sortedDifferenceView);
		}
	}

	/**
	 * Same as {@link BotLogger#printAlbumDifference} but for AlbumTrackPairs
	 * 
	 * @param unfilteredAppearsOnAlbums
	 * @param filteredAppearsOnAlbums
	 * @param logDescription
	 */
	public void printAlbumTrackPairDifference(Collection<AlbumTrackPair> unfilteredAppearsOnAlbums, Collection<AlbumTrackPair> filteredAppearsOnAlbums, String logDescription) {
		printAlbumDifference(
			unfilteredAppearsOnAlbums.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
			filteredAppearsOnAlbums.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
			logDescription);
	}
}
