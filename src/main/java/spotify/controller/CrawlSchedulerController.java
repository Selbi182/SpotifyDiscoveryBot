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
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlSchedulerController {

	/**
	 * Cron job representing "at every 10th second after every 30 minutes".
	 */
	private final static String CRAWL_CRON = "10 */30 * * * *";

	/**
	 * Cron job representing "every 10th second starting at the 5th second of a
	 * minute"
	 */
	private final static String CLEAR_NOTIFIER_CRON = "5/10 * * * * *";

	@Autowired
	private DiscoveryBotCrawler crawler;

	@Autowired
	private BotLogger log;

	/**
	 * Run the scheduler every nth minute, starting at minute :01 to offset
	 * Spotify's timezone deviation.<br/>
	 * Can be manually refreshed at: http://localhost:8080/refresh<br/>
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
	@Scheduled(cron = CRAWL_CRON)
	@RequestMapping("/crawl")
	public ResponseEntity<String> runScheduler() throws BotException, SQLException {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Crawler isn't ready!", HttpStatus.CONFLICT);
		}
		Map<AlbumGroupExtended, Integer> results = crawler.tryCrawl();
		String response = BotUtils.compileResultString(results);
		if (response != null) {
			log.info(response);
			log.printLine();
			return new ResponseEntity<>(response, HttpStatus.CREATED);
		}
		return new ResponseEntity<>("No new releases found.", HttpStatus.OK);
	}

	/**
	 * Periodic task running every 10 seconds to remove the [NEW] indicator where
	 * applicable. Will only run while crawler is idle.
	 * 
	 * @return a ResponseEntity indicating whether any notifies were cleared
	 * @throws BotException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@Scheduled(cron = CLEAR_NOTIFIER_CRON)
	@RequestMapping("/clear-notifiers")
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
