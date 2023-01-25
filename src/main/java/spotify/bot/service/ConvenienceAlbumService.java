package spotify.bot.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.api.BotException;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.services.AlbumService;
import spotify.services.UserService;

@Service
public class ConvenienceAlbumService {
  private final AlbumService albumService;
  private final UserService userService;
  private final PlaylistStoreConfig playlistStoreConfig;

  ConvenienceAlbumService(AlbumService albumService, UserService userService, PlaylistStoreConfig playlistStoreConfig) {
    this.albumService = albumService;
    this.userService = userService;
    this.playlistStoreConfig = playlistStoreConfig;
  }

  /**
   * Fetch all albums of the given artists. (Note: This will very likely take up
   * the majority of the crawling process, as it requires firing at least one
   * Spotify Web API request for EVERY SINGLE ARTIST!)
   */
  public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> followedArtists) throws BotException {
    Collection<AlbumGroup> enabledAlbumGroups = playlistStoreConfig.getEnabledAlbumGroups();
    CountryCode marketOfCurrentUser = userService.getMarketOfCurrentUser();
    return albumService.getAllAlbumsOfArtists(followedArtists, enabledAlbumGroups, marketOfCurrentUser);
  }

}
