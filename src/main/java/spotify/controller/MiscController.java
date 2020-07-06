package spotify.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;

@RestController
@Component
@EnableScheduling
public class MiscController {
	private final static int SHUTDOWN_RETRY_SLEEP = 10 * 1000;

	@Autowired
	private BotLogger log;

	@Autowired
	private DiscoveryBotCrawler crawler;

	@Autowired
	private StaticConfig staticConfig;

	/**
	 * Displays the contents of the of the most recent log entries in a raw JSON
	 * format.
	 * 
	 * @param limit (optional) maximum number of lines to read from the bottom of
	 *              the log (default: 50); Use -1 to read the entire file
	 * @return a ResponseEntity containing the entire log content as a list of
	 *         Strings, or an error (will be the first and only entry)
	 */
	@RequestMapping("/lograw")
	public ResponseEntity<List<String>> showLogRaw(@RequestParam(value = "limit", required = false) Integer limit) {
		try {
			List<String> logFileLines = log.readLog(limit);
			return new ResponseEntity<List<String>>(logFileLines, HttpStatus.OK);
		} catch (IOException e) {
			log.stackTrace(e);
			List<String> message = Arrays.asList(e.getMessage());
			return new ResponseEntity<List<String>>(message, HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Displays the contents of the of the most recent log entries in a humanly
	 * readable form (simply using HTML {@code pre} tags).
	 * 
	 * @param limit (optional) maximum number of lines to read from the bottom of
	 *              the log (default: 100); Use -1 to read the entire file
	 * @return a ResponseEntity containing the entire log content as single String,
	 *         or an error
	 */
	@RequestMapping("/log")
	public ResponseEntity<String> showLog(@RequestParam(value = "limit", required = false) Integer limit) {
		try {
			String logFileLinesImploded = String.format("<pre>%s</pre>",
				log.readLog(limit).stream().collect(Collectors.joining("\n")));
			return new ResponseEntity<String>(logFileLinesImploded, HttpStatus.OK);
		} catch (IOException e) {
			log.stackTrace(e);
			return new ResponseEntity<String>(e.getMessage(), HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping("/clearlog")
	public ResponseEntity<String> clearLog() {
		if (log.clearLog()) {
			return new ResponseEntity<>("Log was successfully cleared!", HttpStatus.OK);
		}
		return new ResponseEntity<>("Couldn't find log! Maybe it's already cleared?", HttpStatus.NOT_FOUND);
	}

	/**
	 * Shut down the bot. If a crawl is still in progress, the shutdown process will
	 * be retried every ten seconds.
	 */
	@RequestMapping("/shutdown")
	public void shutdown(@RequestParam(value = "message", defaultValue = "Shutting down Spotify bot by manual request...") String message) {
		if (message != null && !message.isEmpty()) {
			log.info(message);
		}
		while (!crawler.isReady()) {
			log.warning("Can't stop application during a crawl! Trying again in 10 seconds...");
			BotUtils.sneakySleep(SHUTDOWN_RETRY_SLEEP);
		}
		System.exit(0);
	}

	/**
	 * Shut down the bot automatically once per week on Thursday night (23:50:00) to
	 * have a fresh bot instance ready for New-Music-Fridays.
	 */
	@Scheduled(cron = "0 50 23 * * THU")
	private void scheduledShutdown() {
		if (staticConfig.isRestartBeforeFriday()) {
			shutdown("Shutting down Spotify bot by scheduled cron job (restart_before_friday)...");
		}
	}
}
