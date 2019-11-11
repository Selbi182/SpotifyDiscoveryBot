package spotify.bot.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
}
