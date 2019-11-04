package spotify.bot.config;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.junit.platform.commons.util.ExceptionUtils;
import org.springframework.stereotype.Service;

import spotify.bot.util.Constants;

@Service
public class BotLogger {

	private final static String LOG_FILE_NAME = "log.txt";
	private final static Level LOG_LEVEL = Level.INFO;

	private Logger log;

	@PostConstruct
	public void init() throws SecurityException, IOException {
		this.log = Logger.getGlobal();

		File logFilePath = new File(Constants.OWN_LOCATION, LOG_FILE_NAME);
		if (!logFilePath.canRead()) {
			logFilePath.createNewFile();
		}

		Handler h = new FileHandler(logFilePath.getAbsolutePath(), true);
		h.setFormatter(new SimpleFormatter());
		log.addHandler(h);
		log.setLevel(LOG_LEVEL);

		for (Handler handler : log.getHandlers()) {
			handler.setLevel(LOG_LEVEL);
		}
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
		log.warning(message);		
	}
	
	/**
	 * Log an error
	 * 
	 * @param string
	 */
	public void error(String message) {
		log.severe(message);
	}

	/**
	 * Log and print the given exception's stack trace
	 * 
	 * @param e
	 */
	public void stackTrace(Exception e) {
		log.severe(ExceptionUtils.readStackTrace(e));
	}
	
	/**
	 * Kill all logger handlers before closing the app
	 */
	@PreDestroy
	public void closeLogger() {
		if (log != null) {
			for (Handler h : log.getHandlers()) {
				h.close();
			}
		}
	}

}
