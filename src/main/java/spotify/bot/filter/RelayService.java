package spotify.bot.filter;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import spotify.bot.config.DeveloperMode;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.BotUtils;
import spotify.util.data.AlbumTrackPair;

@Service
public class RelayService {
	private static final String RELAY_FILE_NAME = DiscoveryBotUtils.BASE_CONFIG_PATH + "relay.properties";
	private static final String PROP_RELAY_URL = "RELAY_URL";
	private static final String PROP_WHITELISTED_ARTIST_IDS = "WHITELISTED_ARTIST_IDS";
	private static final String PROP_MESSAGE_MASK = "MESSAGE_MASK";

	private static final List<AlbumGroupExtended> WHITELISTED_ALBUM_GROUPS = List.of(
			AlbumGroupExtended.ALBUM,
			AlbumGroupExtended.SINGLE,
			AlbumGroupExtended.EP
	);

	private boolean active;
	private String relayUrl;
	private List<String> whitelistedArtistIds;
	private String messageMask;

	private final DiscoveryBotLogger log;

	RelayService(DiscoveryBotLogger botLogger) {
		this.log = botLogger;
	}

	@PostConstruct
	private void init() {
		this.active = false;
		if (!DeveloperMode.isRelayingDisabled()) {
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
		if (active && !DeveloperMode.isRelayingDisabled()) {
			newTracksByTargetPlaylist.values().stream()
				.flatMap(Collection::stream)
				.filter(this::isWhitelistedArtist)
				.filter(this::isWhitelistedAlbumGroup)
				.forEach(this::relayFilteredResult);
		}
	}

	private void relayFilteredResult(AlbumTrackPair atp) {
		String message = String.format(messageMask, BotUtils.getFirstArtistName(atp.getAlbum()), atp.getAlbum().getId());

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
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

	private boolean isWhitelistedAlbumGroup(AlbumTrackPair atp) {
		return WHITELISTED_ALBUM_GROUPS.contains(AlbumGroupExtended.fromAlbumGroup(atp.getAlbum().getAlbumGroup()));
	}
}
