package spotify.bot.api;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import spotify.bot.config.Config;
import spotify.bot.util.BotLogger;
import spotify.bot.util.Constants;

@Component
@RestController
public class SpotifyApiAuthorization {

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	/**
	 * Log in to Spotify
	 * 
	 * @throws SpotifyWebApiException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public void login() throws SpotifyWebApiException, InterruptedException, IOException {
		try {
			authorizationCodeRefresh();
		} catch (IOException | SQLException | BadRequestException e) {
			authenticate();
		}
	}

	/**
	 * Log-in lock
	 */
	private static Object lock = new Object();

	///////////////////////

	/**
	 * Authentication process
	 * 
	 * @param api
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 * 
	 * @throws Exception
	 */
	private void authenticate() throws SpotifyWebApiException, IOException, InterruptedException {
		URI uri = SpotifyCall.execute(spotifyApi.authorizationCodeUri().scope(Constants.SCOPES));
		try {
			if (!Desktop.isDesktopSupported()) {
				throw new HeadlessException();
			}
			Desktop.getDesktop().browse(uri);
		} catch (IOException | HeadlessException e) {
			log.warning("Couldn't open browser window. Plase login at this URL:");
			log.warning(uri.toString());
		}
		synchronized (lock) {
			lock.wait();
		}
	}

	/**
	 * Callback receiver for logins
	 * 
	 * @param code
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/login-callback")
	private ResponseEntity<String> loginCallback(@RequestParam String code) throws Exception {
		try {
			AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCode(code));
			updateTokens(acc);
			synchronized (lock) {
				lock.notify();
			}
			return new ResponseEntity<String>("Successfully logged in!", HttpStatus.OK);
		} catch (BadRequestException e) {
			return new ResponseEntity<String>("Response code is invalid!", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Refresh the access token
	 * 
	 * @param api
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 * @throws SQLException
	 */
	private void authorizationCodeRefresh() throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCodeRefresh());
		updateTokens(acc);
	}

	/**
	 * Store the access and refresh tokens in the database
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	private void updateTokens(AuthorizationCodeCredentials acc) throws IOException, SQLException {
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
		config.updateTokens(accessToken, refreshToken);
	}
}
