package spotify.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import spotify.bot.SpotifyDiscoveryBot;
import spotify.bot.config.dto.StaticConfig;
import spotify.bot.util.BotLogger;

@RestController
@Component
@EnableScheduling
public class MiscController {
	private final static int SHUTDOWN_RETRY_SLEEP = 10 * 1000;

	@Autowired
	private BotLogger log;

	@Autowired
	private SpotifyDiscoveryBot crawler;
	
	@Autowired
	private StaticConfig staticConfig;

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
	 *             on a read error
	 */
	@RequestMapping("/log")
	public ResponseEntity<List<String>> showLog(@RequestParam(value = "limit", required = false) Integer limit) throws IOException {
		List<String> logFileLines = log.readLog(limit);
		return new ResponseEntity<List<String>>(logFileLines, HttpStatus.OK);
	}

	/**
	 * Shut down the bot. If a crawl is still in progress, the shutdown process will
	 * be retried every ten seconds.
	 * 
	 * @throws InterruptedException
	 *             if interrupted during retry cooldown
	 */
	@RequestMapping("/shutdown")
	public void shutdown(@RequestParam(value = "message", defaultValue = "Shutting down Spotify bot by manual request...") String message) throws InterruptedException {
		if (message != null && !message.isEmpty()) {
			log.info(message);
		}
		while (!crawler.isReady()) {
			log.warning("Can't stop application during a crawl! Trying again in 10 seconds...");
			Thread.sleep(SHUTDOWN_RETRY_SLEEP);
		}
		System.exit(0);
	}

	/**
	 * Shut down the bot automatically once per week on Thursday night (23:50:00) to
	 * have a fresh bot instance ready for New-Music-Fridays.
	 * 
	 * @throws InterruptedException
	 *             if interrupted during retry cooldown
	 */
	@Scheduled(cron = "0 50 23 * * THU")
	private void scheduledShutdown() throws InterruptedException {
		if (staticConfig.isRestartBeforeFriday()) {
			shutdown("Shutting down Spotify bot by scheduled cron job (restart_before_friday)...");			
		}
	}
}
