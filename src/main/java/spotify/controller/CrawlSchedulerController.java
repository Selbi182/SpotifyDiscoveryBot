package spotify.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.api.BotException;
import spotify.bot.config.DeveloperMode;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlSchedulerController {

	@Autowired
	private DiscoveryBotCrawler crawler;

	@Autowired
	private BotLogger log;

	/**
	 * Run the scheduler every 30 minutes (with a few seconds extra to offset
	 * timezone deviations).
	 * 
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@Scheduled(cron = "5 */30 * * * *")
	private void scheduledCrawl() throws BotException, SQLException {
		if (!DeveloperMode.isScheduledCrawlDisabled()) {
			runCrawler();
		}
	}

	/**
	 * Entry point for the bot crawler. May be called by the scheduler, but may also
	 * be manually called from: http://localhost:8080/refresh<br/>
	 * <br/>
	 * Possible ResponseEntities:
	 * <ul>
	 * <li>200 (OK): No new songs found</li>
	 * <li>201 (CREATED): New songs were added!</li>
	 * <li>409 (CONFLICT): The crawler is not currently ready, either because it's
	 * still booting or because a previous crawling instance is still in progress;
	 * request will be ignored</li>
	 * </ul>
	 * 
	 * @return a ResponseEntity defining the result of the crawling process
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@RequestMapping("/crawl")
	public ResponseEntity<String> runCrawler() throws BotException, SQLException {
		if (crawler.isReady()) {
			try {
				Map<AlbumGroupExtended, Integer> results = crawler.tryCrawl();
				String response = BotUtils.compileResultString(results);
				if (response != null) {
					log.info(response);
					return new ResponseEntity<>(response, HttpStatus.CREATED);
				}
			} finally {
				log.resetAndPrintLine();
			}
			return new ResponseEntity<>("No new releases found.", HttpStatus.OK);
		}
		return new ResponseEntity<>("Crawler isn't ready!", HttpStatus.CONFLICT);
	}

	/**
	 * Periodic task running every 5 seconds to remove the [NEW] indicator where
	 * applicable. Will only run while crawler is idle.
	 * 
	 * @return a ResponseEntity indicating whether any notifies were cleared
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@Scheduled(fixedDelay = 5 * 1000)
	@RequestMapping("/clearnotifiers")
	public ResponseEntity<String> clearNewIndicatorScheduler() throws BotException, SQLException {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Can't clear [NEW] indicators now, as crawler is currently in progress...", HttpStatus.CONFLICT);
		}
		if (crawler.clearObsoleteNotifiers()) {
			return new ResponseEntity<>("New indicator(s) removed!", HttpStatus.CREATED);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
