package spotify.bot.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.util.data.AlbumTrackPair;

@Service
public class BotLogger {
	private Logger log;

	@PostConstruct
	private void init() throws SecurityException, IOException {
		this.log = LoggerFactory.getLogger(BotLogger.class);

	}

	/**
	 * Log an info message
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public void info(String message) {
		log.info(message);
	}

	/**
	 * Log a warning
	 * 
	 * @param string
	 */
	public void warning(String message) {
		log.warn(message);
	}

	/**
	 * Log an error
	 * 
	 * @param string
	 */
	public void error(String message) {
		log.error(message);
	}

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

	public void printAlbumDifference(List<AlbumSimplified> base, List<AlbumSimplified> subtrahend, String logDescription) {
		Set<AlbumSimplified> differenceView = new HashSet<>(base);
		differenceView.removeAll(subtrahend);
		if (!differenceView.isEmpty()) {
			if (logDescription != null) {
				info(logDescription);				
			}
			for (AlbumSimplified as : differenceView) {
				info(prettyAlbumSimplified(as));
			}
		}
	}
	
	public String prettyAlbumSimplified(AlbumSimplified as) {
		return String.format("[%s] %s - %s (%s)",
			as.getAlbumGroup().toString(),
			as.getArtists()[0].getName(),
			as.getName(),
			as.getReleaseDate());
	}

	public void printATPDifference(List<AlbumTrackPair> unfilteredAppearsOnAlbums, List<AlbumTrackPair> filteredAppearsOnAlbums, String logDescription) {
		printAlbumDifference(
			unfilteredAppearsOnAlbums.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
			filteredAppearsOnAlbums.stream().map(AlbumTrackPair::getAlbum).collect(Collectors.toList()),
			logDescription);
	}
}
