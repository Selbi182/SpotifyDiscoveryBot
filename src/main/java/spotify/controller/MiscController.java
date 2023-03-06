package spotify.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import spotify.bot.DiscoveryBotCrawler;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.util.SpotifyUtils;

@RestController
@Component
@EnableScheduling
public class MiscController {

	private final static int SHUTDOWN_RETRY_SLEEP = 10 * 1000;

	private final DiscoveryBotCrawler crawler;
	private final DiscoveryBotLogger log;

	MiscController(DiscoveryBotCrawler discoveryBotCrawler, DiscoveryBotLogger botLogger) {
		this.crawler = discoveryBotCrawler;
		this.log = botLogger;
	}

	@RequestMapping("/")
	public ModelAndView showLogView() {
		return new ModelAndView("/log.html");
	}

	/**
	 * Returns the contents of the most recent log entries split by
	 * separating lines (---).
	 * 
	 * @param limit (optional) maximum number of log blocks to return from the
	 *              bottom of the log (default: 10); Use -1 to return the entire log
	 * @return a ResponseEntity containing the entire log content as a List of
	 *         blocks, or an error
	 */
	@RequestMapping("/logblocks")
	public ResponseEntity<List<List<String>>> showLogBlocks(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
		try {
			List<String> readLog = log.readLog(-1);

			List<List<String>> groupedLog = new ArrayList<>();
			List<String> currentBlock = new ArrayList<>();
			for (String logLine : readLog) {
				if (logLine.contains("---")) {
					groupedLog.add(currentBlock);
					currentBlock = new ArrayList<>();
				} else {
					currentBlock.add(logLine);
				}
			}
			if (!currentBlock.isEmpty()) {
				groupedLog.add(currentBlock);
			}

			List<List<String>> collect = groupedLog.stream()
				.filter(l -> !l.isEmpty())
				.collect(Collectors.toList());
			Collections.reverse(collect);
			if (limit != null && limit >= 0) {
				collect = collect.subList(0, Math.min(limit, collect.size()));
			}
			return ResponseEntity.ok(collect);
		} catch (IOException e) {
			log.stackTrace(e);
			return ResponseEntity.notFound().build();
		}
	}

	@RequestMapping("/clearlog")
	public ResponseEntity<?> clearLog() {
		if (log.clearLog()) {
			return ResponseEntity.ok("Log was successfully cleared!");
		}
		return ResponseEntity.notFound().build();
	}

	/**
	 * Shut down the bot. If a crawl is still in progress, the shutdown process will
	 * be retried every ten seconds.
	 */
	@RequestMapping("/shutdown")
	public void shutdown(@RequestParam(value = "message", defaultValue = "Shutting down Spotify bot by manual request...") String message) {
		if (message != null && !message.isEmpty()) {
			log.info(message, false);
		}
		while (!crawler.isReady()) {
			log.warning("Can't stop application during a crawl! Trying again in 10 seconds...", false);
			SpotifyUtils.sneakySleep(SHUTDOWN_RETRY_SLEEP);
		}
		System.exit(0);
	}
}
