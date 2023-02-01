package spotify.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import spotify.api.BotException;
import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.config.DeveloperMode;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlSchedulerController {

	private final DiscoveryBotCrawler crawler;
	private final DiscoveryBotLogger log;

	CrawlSchedulerController(DiscoveryBotCrawler discoveryBotCrawler, DiscoveryBotLogger botLogger) {
		this.crawler = discoveryBotCrawler;
		this.log = botLogger;
	}

	/**
	 * Run the scheduler every hour (with a few seconds extra to offset deviations).
	 * 
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@Scheduled(cron = "5 * * * * *")
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
				String response = DiscoveryBotUtils.compileResultString(results);
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
	 * @throws BotException on an external exception related to the Spotify Web API
	 */
	@Scheduled(fixedDelay = 5 * 1000)
	public void clearNewIndicatorScheduler() throws BotException {
		manuallyClearNotifiers();
	}

	/**
	 * Manually clear the notifiers
	 *
	 * @return a ResponseEntity with a summary of the result
	 * @throws BotException on an external exception related to the Spotify Web API
	 */
	@RequestMapping("/clearnotifiers")
	public ResponseEntity<String> manuallyClearNotifiers() throws BotException {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Can't clear [NEW] indicators now, as crawler is currently in progress...", HttpStatus.CONFLICT);
		}
		if (crawler.clearObsoleteNotifiers()) {
			return new ResponseEntity<>("New indicator[s] removed!", HttpStatus.CREATED);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
