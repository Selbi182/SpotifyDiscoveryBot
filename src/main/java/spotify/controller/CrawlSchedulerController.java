package spotify.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import spotify.api.SpotifyApiException;
import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.config.DeveloperMode;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlSchedulerController implements SchedulingConfigurer {
	private final DiscoveryBotCrawler crawler;
	private final DiscoveryBotLogger log;

	@Value("${spotify.discovery.crawl.cron:5 */30 * * * *}")
	private String crawlCron;

	CrawlSchedulerController(DiscoveryBotCrawler discoveryBotCrawler, DiscoveryBotLogger botLogger) {
		this.crawler = discoveryBotCrawler;
		this.log = botLogger;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		if (!DeveloperMode.isScheduledCrawlDisabled()) {
			taskRegistrar.addCronTask(new CronTask(this::scheduledCrawl, crawlCron));
			log.info("Scheduled crawl are enabled with the following cronjob: " + crawlCron, false);
		}
		if (!DeveloperMode.isNotificationMarkersDisabled()) {
			taskRegistrar.addFixedDelayTask(this::clearNewIndicatorScheduler, 10 * 1000);
		}
	}

	/**
	 * Run the scheduler every half hour (with a few seconds extra to offset deviations).
	 */
	private void scheduledCrawl() {
		try {
			runCrawler();
		} catch (SQLException | SpotifyApiException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Entry point for the bot crawler. May be called by the scheduler, but may also
	 * be manually called from: http://localhost:8080/refresh<br/>
	 * <br/>
	 * Possible ResponseEntities:
	 * <ul>
	 * <li>200 (OK): New songs were added to the playlist or no new songs found</li>
	 * <li>409 (CONFLICT): The crawler is not currently ready, either because it's
	 * still booting or because a previous crawling instance is still in progress;
	 * request will be ignored</li>
	 * </ul>
	 * 
	 * @return a ResponseEntity defining the result of the crawling process
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 * @throws SQLException on an internal exception related to the SQLite database
	 */
	@RequestMapping("/crawl")
	public ResponseEntity<String> runCrawler() throws SpotifyApiException, SQLException {
		if (crawler.isReady()) {
			try {
				Map<AlbumGroupExtended, Integer> results = crawler.tryCrawl();
				String response = DiscoveryBotUtils.compileResultString(results);
				if (!response.isBlank()) {
					log.info(response);
					return ResponseEntity.ok(response);
				}
			} finally {
				log.resetAndPrintLine();
			}
			return ResponseEntity.ok("No new releases found.");
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body("Crawler isn't ready!");
	}

	/**
	 * Periodic task running every 10 seconds to remove the [NEW] indicator where
	 * applicable. Will only run while crawler is idle.
	 * 
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 */
	public void clearNewIndicatorScheduler() throws SpotifyApiException {
		manuallyClearNotifiers();
	}

	/**
	 * Manually clear the notifiers
	 *
	 * @return a ResponseEntity with a summary of the result
	 * @throws SpotifyApiException on an external exception related to the Spotify Web API
	 */
	@RequestMapping("/clearnotifiers")
	public ResponseEntity<String> manuallyClearNotifiers() throws SpotifyApiException {
		if (!crawler.isReady()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Can't clear [NEW] indicators now, as crawler is currently in progress...");
		}
		crawler.clearObsoleteNotifiers();
		return ResponseEntity.ok("All notifiers were cleared!");
	}
}
