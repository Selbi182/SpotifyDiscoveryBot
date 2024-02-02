package spotify.bot.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.ExternalUrl;
import spotify.bot.config.properties.PlaylistStoreConfig.PlaylistStore;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.SpotifyUtils;
import spotify.util.data.AlbumTrackPair;

@Service
public class ForwarderService {
  @Value("${spotify.discovery.crawl.forwarder.url:#{null}}")
  private String forwarderUrl;

  @Value("${spotify.discovery.crawl.forwarder.message_mask:#{null}}")
  private String forwarderMessageMask;

  @Value("${spotify.discovery.crawl.forwarder.whitelisted_artist_ids:#{null}}")
  private String[] forwarderWhitelistedArtists;

  @Value("${spotify.discovery.crawl.forwarder.whitelisted_types:#{null}}")
  private String[] forwarderWhitelistedTypes;

  private boolean active;

  private final DiscoveryBotLogger log;
  private final FeatureControl featureControl;

  ForwarderService(DiscoveryBotLogger botLogger, FeatureControl featureControl) {
    this.log = botLogger;
    this.featureControl = featureControl;
  }

  @PostConstruct
  private void init() {
    this.active = false;
    if (featureControl.isForwarderEnabled() && forwarderUrl != null) {
      this.active = true;
      log.info("Forwarder: Enabled! New releases will be forwarded to: " + forwarderUrl, false);
      if (hasRestrictedArtists()) {
        log.info("Forwarder: Artists restricted to these IDS: " + String.join(", ", Arrays.asList(forwarderWhitelistedArtists)));
      }
      if (hasRestrictedTypes()) {
        log.info("Forwarder: Types restricted to: " + String.join(", ", Arrays.asList(forwarderWhitelistedTypes)).toUpperCase());
      }
    }
  }

  public void forwardResults(Map<PlaylistStore, List<AlbumTrackPair>> newTracksByTargetPlaylist) {
    if (active) {
      List<String> whitelistedTypes = Arrays.asList(forwarderWhitelistedTypes);
      for (PlaylistStore ps : newTracksByTargetPlaylist.keySet()) {
        AlbumGroupExtended albumGroupExtended = ps.getAlbumGroupExtended();
        if (!hasRestrictedTypes() || whitelistedTypes.contains(albumGroupExtended.toString())) {
          newTracksByTargetPlaylist.values().stream()
            .flatMap(Collection::stream)
            .filter(this::isWhitelistedArtist)
            .forEach(this::forwardAlbum);
        }
      }
    }
  }

  private void forwardAlbum(AlbumTrackPair atp) {
    AlbumSimplified album = atp.getAlbum();

    ExternalUrl externalUrls = album.getExternalUrls();
    String albumLink = externalUrls != null && externalUrls.get("spotify") != null
      ? externalUrls.get("spotify")
      : album.getHref();

    String message = forwarderMessageMask != null
      ? String.format(forwarderMessageMask, SpotifyUtils.getFirstArtistName(album), albumLink)
      : albumLink;

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    HttpEntity<String> entity = new HttpEntity<>(message, headers);

    RestTemplate restTemplate = new RestTemplate();
    restTemplate.exchange(forwarderUrl, HttpMethod.POST, entity, String.class);
  }

  private boolean isWhitelistedArtist(AlbumTrackPair atp) {
    if (!hasRestrictedArtists()) {
      return true;
    }

    return Arrays.stream(atp.getAlbum().getArtists())
      .map(ArtistSimplified::getId)
      .anyMatch(id -> Arrays.asList(forwarderWhitelistedArtists).contains(id));
  }

  private boolean hasRestrictedTypes() {
    return forwarderWhitelistedTypes != null && forwarderWhitelistedTypes.length > 0;
  }

  private boolean hasRestrictedArtists() {
    return forwarderWhitelistedArtists != null && forwarderWhitelistedArtists.length > 0;
  }
}
