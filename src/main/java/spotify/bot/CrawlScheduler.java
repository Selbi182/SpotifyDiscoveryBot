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
import spotify.bot.util.Constants;

@RestController
@Component
@EnableScheduling
public class CrawlScheduler {

	@Autowired
	private SpotifyDiscoveryBotCrawler crawler;

	@Autowired
	private BotLogger log;

	/**	
	 * Run the scheduler every nth minute, starting at minute :01 to offset Spotify's timezone deviation.
	 * Can be manually refreshed at: http://localhost:8080/refresh
	 * 
	 * @return 
	 * @throws Exception
	 */
	@Scheduled(cron = "0 " + Constants.SEARCH_INTERVAL_IN_MINUTES + "/1 * * * *")
	@RequestMapping("/refresh")
	public ResponseEntity<String> runScheduler() throws Exception {
		Map<AlbumGroup, Integer> results = crawler.runCrawler();
		String response = BotUtils.compileResultString(results);
		if (response != null) {
			log.info(response);
			return new ResponseEntity<String>(response, HttpStatus.CREATED);
		}
		return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
	}
}
