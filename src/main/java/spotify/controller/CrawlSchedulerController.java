package spotify.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import spotify.api.events.SpotifyApiException;
import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.properties.FeatureControl;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@RestController
@Component
@EnableScheduling
public class CrawlSchedulerController implements SchedulingConfigurer {
	private final DiscoveryBotCrawler crawler;
	private final DiscoveryBotLogger log;
	private final FeatureControl featureControl;

	@Value("${spotify.discovery.crawl.cron:5 */30 * * * *}")
	private String crawlCron;

	CrawlSchedulerController(DiscoveryBotCrawler discoveryBotCrawler, DiscoveryBotLogger botLogger, FeatureControl featureControl) {
		this.crawler = discoveryBotCrawler;
		this.log = botLogger;
		this.featureControl = featureControl;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		if (featureControl.isScheduledCrawlsEnabled()) {
			taskRegistrar.addCronTask(this::scheduledCrawl, crawlCron);
			log.info("Scheduled crawls are enabled with the following cronjob: " + crawlCron, false);
		}
	}

	/**
	 * Run the crawler via the scheduled manner.
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
	 * be manually called from: http://127.0.0.1:8182/refresh<br/>
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
}
