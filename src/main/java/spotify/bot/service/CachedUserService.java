package spotify.bot.service;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.services.UserService;

/**
 * Convenience class to only store the user once (since they will never change after login).
 * This saves on requests to the Spotify API.
 */
@Service
public class CachedUserService {
  private User user;

  private final UserService userService;

  CachedUserService(UserService userService) {
    this.userService = userService;
  }

  public User getUser() {
    if (user == null) {
      user = userService.getCurrentUser();
    }
    return user;
  }

  public CountryCode getUserMarket() {
    return getUser().getCountry();
  }

  public String getUserId() {
    return getUser().getId();
  }
}
