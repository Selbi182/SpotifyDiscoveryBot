package spotify.bot;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wrapper.spotify.enums.AlbumGroup;

import spotify.bot.config.BotLogger;
import spotify.bot.crawler.SpotifyDiscoveryBotCrawler;
import spotify.bot.util.BotUtils;

@RestController
@Component
@EnableScheduling
public class CrawlScheduler {

	/**
	 * Cron job representing "at every 10th second after every 20 minutes".
	 */
	private final static String CRAWL_CRON = "10 */20 * * * *";

	/**
	 * Cron job representing "every 10 seconds starting at the 5th second of a
	 * minute"
	 */
	private final static String CLEAR_NOTIFIER_CRON = "5/10 * * * * *";

	@Autowired
	private SpotifyDiscoveryBotCrawler crawler;

	@Autowired
	private BotLogger log;

	/**
	 * Run the scheduler every nth minute, starting at minute :01 to offset
	 * Spotify's timezone deviation.<br/>
	 * Can be manually refreshed at: http://localhost:8080/refresh<br/>
	 * <br/>
	 * Possible ResponseEntities:
	 * <ul>
	 * <li>201 (CREATED): New songs were added!</li>
	 * <li>204 (NO CONTENT): No new songs found</li>
	 * <li>409 (CONFLICT): A previous crawling instance is still in progress</li>
	 * </ul>
	 * 
	 * @return a ResponseEntity defining the result of the crawling process
	 * 
	 * @throws Exception
	 *             should anything whatsoever go wrong lol
	 */
	@Scheduled(cron = CRAWL_CRON)
	@RequestMapping("/crawl")
	public ResponseEntity<String> runScheduler() throws Exception {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Crawler isn't ready!", HttpStatus.CONFLICT);
		}
		Map<AlbumGroup, Integer> results = crawler.runCrawler();
		String response = BotUtils.compileResultString(results);
		if (response != null) {
			log.info(response);
			return new ResponseEntity<>(response, HttpStatus.CREATED);
		}
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	/**
	 * Periodic task running every 10 seconds to remove the [NEW] indicator where
	 * applicable. Will only run while crawler is idle.
	 * 
	 * @return
	 */
	@Scheduled(cron = CLEAR_NOTIFIER_CRON)
	@RequestMapping("/clear-notifiers")
	public ResponseEntity<String> clearNewIndicatorScheduler() throws Exception {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Can't clear [NEW] indicators now, as crawler is currently in progress...", HttpStatus.CONFLICT);
		}
		crawler.clearObsoleteNotifiers();
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}
}
