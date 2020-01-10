package spotify.bot.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import spotify.bot.crawler.SpotifyDiscoveryBotCrawler;
import spotify.bot.util.BotLogger;

@RestController
@Component
public class MiscEndpoints {

	@Autowired
	private BotLogger log;

	@Autowired
	private SpotifyDiscoveryBotCrawler crawler;

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
	 * Try to shut down the bot.
	 */
	@RequestMapping("/shutdown")
	public ResponseEntity<String> shutdown() {
		log.info("Manual shutdown requested!");
		if (!crawler.isReady()) {
			System.exit(0);
		}
		return new ResponseEntity<String>("Can't stop application during a crawl!", HttpStatus.LOCKED);
	}
}
