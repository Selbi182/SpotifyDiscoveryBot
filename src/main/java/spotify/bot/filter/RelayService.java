package spotify.bot.filter;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.config.DeveloperMode;
import spotify.bot.config.dto.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.filter.remapper.Remapper;
import spotify.bot.filter.remapper.RereleaseRemapper;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumTrackPair;

@Service
public class RelayService {
	private static final String RELAY_FILE_NAME = "relay.properties";
	private static final String PROP_RELAY_URL = "RELAY_URL";
	private static final String PROP_WHITELISTED_ARTIST_IDS = "WHITELISTED_ARTIST_IDS";
	private static final String PROP_MESSAGE_MASK = "MESSAGE_MASK";

	private boolean active;
	private String relayUrl;
	private List<String> whitelistedArtistIds;
	private String messageMask;

	@Autowired
	private BotLogger log;

	@Autowired
	private RereleaseRemapper rereleaseRemapper;

	@PostConstruct
	private void init() {
		this.active = false;
		if (!DeveloperMode.isRelayingDisbled()) {
			File relayFile = new File(RELAY_FILE_NAME);
			if (relayFile.canRead()) {
				Properties relayProperties = new Properties();
				try {
					relayProperties.load(new FileInputStream(relayFile));
					this.relayUrl = relayProperties.getProperty(PROP_RELAY_URL);
					this.whitelistedArtistIds = Arrays.asList(relayProperties.getProperty(PROP_WHITELISTED_ARTIST_IDS).split(","));
					this.messageMask = relayProperties.getProperty(PROP_MESSAGE_MASK);
					this.active = true;
					log.info("Relaying enabled! Results will be forwarded to: " + relayUrl, false);
				} catch (Exception e) {
					e.printStackTrace();
					log.warning("Failed to parse " + RELAY_FILE_NAME + "!", false);
				}
			}
		}
		if (!active) {
			log.info("Relaying disabled", false);
		}
	}

	public void relayResults(Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist) {
		if (active && !DeveloperMode.isRelayingDisbled()) {
			newTracksByTargetPlaylist.values().stream()
				.flatMap(Collection::stream)
				.filter(this::isWhitelistedArtist)
				.filter(this::isNotReRelease)
				.forEach(this::relayFilteredResult);
		}
	}

	private void relayFilteredResult(AlbumTrackPair atp) {
		String message = String.format(messageMask, BotUtils.getFirstArtistName(atp.getAlbum()), atp.getAlbum().getId());

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity<String> entity = new HttpEntity<>(message, headers);

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.exchange(relayUrl, HttpMethod.POST, entity, String.class);
	}

	private boolean isWhitelistedArtist(AlbumTrackPair atp) {
		for (ArtistSimplified artist : atp.getAlbum().getArtists()) {
			String id = artist.getId();
			if (whitelistedArtistIds.contains(id)) {
				return true;
			}
		}
		return false;
	}

	private boolean isNotReRelease(AlbumTrackPair albumTrackPair) {
		return rereleaseRemapper.determineRemapAction(albumTrackPair) == Remapper.Action.NONE;
	}
}
