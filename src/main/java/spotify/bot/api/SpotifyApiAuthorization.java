package spotify.bot.api;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import spotify.bot.api.events.LoggedInEvent;
import spotify.bot.config.ConfigUpdate;
import spotify.bot.util.BotLogger;

@Component
@RestController
public class SpotifyApiAuthorization {

	protected final static String LOGIN_CALLBACK_URI = "/login-callback";

	private final static String SCOPES = "user-follow-read playlist-modify-private";

	private final static int LOGIN_TIMEOUT = 10;

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private ConfigUpdate configUpdate;

	@Autowired
	private BotLogger log;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@PostConstruct
	private void initSpotifyCall() {
		SpotifyCall.spotifyApiAuthorization = this;
	}

	/**
	 * Log in to Spotify. Retry up to ten times with exponentially increasing sleep
	 * intervals on an error.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initialLogin() {
		refresh();
		applicationEventPublisher.publishEvent(new LoggedInEvent(this));
	}

	public String refresh() {
		try {
			return authorizationCodeRefresh();
		} catch (HttpConnectTimeoutException | SQLException e) {
			authenticate();
			return refresh();
		}
	}

	///////////////////////

	/**
	 * Authentication mutex to be used while the user is being prompted to log in
	 */
	private static Semaphore lock = new Semaphore(0);

	/**
	 * Authentication process
	 */
	private void authenticate() throws BotException {
		URI uri = SpotifyCall.execute(spotifyApi.authorizationCodeUri().scope(SCOPES));
		try {
			if (!Desktop.isDesktopSupported()) {
				throw new HeadlessException();
			}
			Desktop.getDesktop().browse(uri);
		} catch (IOException | HeadlessException e) {
			log.warning("Couldn't open browser window. Please login at this URL:", false);
			System.out.println(uri.toString());
		}
		try {
			boolean loggedIn = lock.tryAcquire(LOGIN_TIMEOUT, TimeUnit.MINUTES);
			if (!loggedIn) {
				log.error("Login timeout! Shutting down application in case of a Spotify Web API anomaly!", false);
				System.exit(1);
			}
		} catch (InterruptedException e) {
			throw new BotException(e);
		}
	}

	/**
	 * Callback receiver for logins
	 * 
	 * @param code
	 * @return
	 */
	@RequestMapping(LOGIN_CALLBACK_URI)
	private ResponseEntity<String> loginCallback(@RequestParam String code) throws BotException, SQLException {
		AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCode(code));
		updateTokens(acc);
		lock.release();
		return new ResponseEntity<String>("Successfully logged in!", HttpStatus.OK);
	}

	///////////////////////

	/**
	 * Refresh the access token
	 */
	private String authorizationCodeRefresh() throws HttpConnectTimeoutException, SQLException {
		try {
			AuthorizationCodeCredentials acc = Executors.newSingleThreadExecutor()
				.submit(() -> SpotifyCall.execute(spotifyApi.authorizationCodeRefresh()))
				.get(LOGIN_TIMEOUT, TimeUnit.SECONDS);
			updateTokens(acc);
			return acc.getAccessToken();
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			String msg = "Failed to automatically refresh access token after " + LOGIN_TIMEOUT + " seconds. A manual (re-)login might be required.";
			log.error(msg, false);
			throw new HttpConnectTimeoutException(msg);
		}
	}

	/**
	 * Store the access and refresh tokens in the database
	 */
	private void updateTokens(AuthorizationCodeCredentials acc) throws SQLException {
		String accessToken = spotifyApi.getAccessToken();
		if (acc.getAccessToken() != null) {
			accessToken = acc.getAccessToken();
		}
		String refreshToken = spotifyApi.getRefreshToken();
		if (acc.getRefreshToken() != null) {
			refreshToken = acc.getRefreshToken();
		}

		spotifyApi.setAccessToken(accessToken);
		spotifyApi.setRefreshToken(refreshToken);
		configUpdate.updateTokens(accessToken, refreshToken);
	}
}
