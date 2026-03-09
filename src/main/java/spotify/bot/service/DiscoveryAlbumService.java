package spotify.bot.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.requests.data.artists.GetArtistsAlbumsRequest;
import spotify.api.SpotifyCall;
import spotify.api.events.SpotifyApiException;
import spotify.bot.util.DiscoveryBotLogger;

@Service
public class DiscoveryAlbumService {
  private static final int MAX_ALBUM_FETCH_LIMIT = 10;

  private final SpotifyApi spotifyApi;
  private final DiscoveryBotLogger log;

  DiscoveryAlbumService(SpotifyApi spotifyApi, DiscoveryBotLogger log) {
    this.spotifyApi = spotifyApi;
    this.log = log;
  }

  /**
   * Fetch all albums of the given artists. (Note: This will very likely take up
   * the majority of the crawling process, as it requires firing at least one
   * Spotify Web API request for EVERY SINGLE ARTIST!)
   */
  public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> followedArtists, boolean showProgress) throws SpotifyApiException {
    //CountryCode marketOfCurrentUser = userService.getMarketOfCurrentUser();
    CountryCode marketOfCurrentUser = CountryCode.DE; // TODO placeholder? maybe permanent

    int done = 0;
    List<AlbumSimplified> results = new ArrayList<>();
    for (String artist : followedArtists) {
      List<AlbumSimplified> albumIdsOfSingleArtist = getAlbumIdsOfSingleArtist(artist, marketOfCurrentUser);
      results.addAll(albumIdsOfSingleArtist);
      if (showProgress) {
        done++;
        log.debug(done + " / " + followedArtists.size());
      }
    }
    return results;
  }

  /**
   * Return the albums of a single given artist with the original ID intact
   *
   * @param artistId the artist ID to check up
   * @param market the market to check for
   * @return the albums
   */
  private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, CountryCode market) throws SpotifyApiException {
    List<AlbumSimplified> resultList = new ArrayList<>();

    // It's generally very unlikely an artist will ever release more than 10 new things at once (and if they do, it's usually crap anyway).
    // Therefore, since Spotify's releases are sorted from newest, we can simply search the 10 most recent releases for albums and singles,
    // and we're guaranteed to get relevant results without having to spam the API. Results may vary until further field tesing though.

    GetArtistsAlbumsRequest.Builder builder =
      spotifyApi.getArtistsAlbums(artistId)
        .market(market)
        .limit(MAX_ALBUM_FETCH_LIMIT);

    for (String group : List.of("album", "single")) {
      AlbumSimplified[] items = SpotifyCall.execute(
        builder.include_groups(group)
      ).getItems();

      Collections.addAll(resultList, items);
    }
    return resultList;
  }

}
