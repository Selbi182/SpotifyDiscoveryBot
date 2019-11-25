package spotify.bot;

import java.io.IOException;
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

import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import spotify.bot.crawler.SpotifyDiscoveryBotCrawler;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlScheduler {

	/**
	 * Cron job representing "at every 10th second after every 30 minutes".
	 */
	private final static String CRAWL_CRON = "10 */30 * * * *";

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
	 * <li>200 (OK): No new songs found</li>
	 * <li>201 (CREATED): New songs were added!</li>
	 * <li>409 (CONFLICT): The crawler is not currently ready, either because it's
	 * still booting or because a previous crawling instance is still in progress;
	 * request will be ignored</li>
	 * </ul>
	 * 
	 * @return a ResponseEntity defining the result of the crawling process
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 */
	@Scheduled(cron = CRAWL_CRON)
	@RequestMapping("/crawl")
	public ResponseEntity<String> runScheduler() throws SpotifyWebApiException, InterruptedException, IOException, SQLException {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Crawler isn't ready!", HttpStatus.CONFLICT);
		}
		Map<AlbumGroupExtended, Integer> results = crawler.runCrawler();
		String response = BotUtils.compileResultString(results);
		if (response != null) {
			log.info(response);
			return new ResponseEntity<>(response, HttpStatus.CREATED);
		}
		return new ResponseEntity<>("No new releases found.", HttpStatus.OK);
	}

	/**
	 * Periodic task running every 10 seconds to remove the [NEW] indicator where
	 * applicable. Will only run while crawler is idle.
	 * 
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SpotifyWebApiException
	 */
	@Scheduled(cron = CLEAR_NOTIFIER_CRON)
	@RequestMapping("/clear-notifiers")
	public ResponseEntity<String> clearNewIndicatorScheduler() throws SpotifyWebApiException, SQLException, IOException, InterruptedException, Exception {
		if (!crawler.isReady()) {
			return new ResponseEntity<>("Can't clear [NEW] indicators now, as crawler is currently in progress...", HttpStatus.CONFLICT);
		}
		if (crawler.clearObsoleteNotifiers()) {
			return new ResponseEntity<>("New indicator(s) removed!", HttpStatus.CREATED);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
