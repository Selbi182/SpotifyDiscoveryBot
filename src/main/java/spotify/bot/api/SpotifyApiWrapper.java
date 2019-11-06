package spotify.bot.api;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import spotify.bot.config.BotLogger;
import spotify.bot.config.Config;
import spotify.bot.util.Constants;

@RestController
@Configuration
public class SpotifyApiWrapper {

	@Autowired
	private Config config;

	@Autowired
	private BotLogger log;

	@Autowired
	ApplicationEventPublisher applicationEventPublisher;

	private SpotifyApi spotifyApi;

	///////////////////////

	/**
	 * Get the current SpotifyApi instance
	 * 
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public SpotifyApi api() {
		if (spotifyApi == null) {
			createSpotifyApiInstance();
		}
		return spotifyApi;
	}

	///////////////////////

	/**
	 * Create a fresh API instance
	 */
	private void createSpotifyApiInstance() {
		try {
			spotifyApi = new SpotifyApi.Builder()
				.setClientId(config.getClientId())
				.setClientSecret(config.getClientSecret())
				.setRedirectUri(SpotifyHttpManager.makeUri(config.getCallbackUri()))
				.build();

			// Get stored tokens
			spotifyApi.setAccessToken(config.getAccessToken());
			spotifyApi.setRefreshToken(config.getRefreshToken());

			// Try to login with the stored tokens or re-authenticate
			try {
				refreshAccessToken();
			} catch (IOException | SQLException | BadRequestException e) {
				authenticate();
			}
		} catch (Exception e) {
			log.stackTrace(e);
		}
	}

	/**
	 * Authentication process
	 * 
	 * @throws Exception
	 */
	private void authenticate() throws Exception {
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
			return new ResponseEntity<String>("Successfully logged in!", HttpStatus.OK);
		} catch (BadRequestException e) {
			return new ResponseEntity<String>("Response code is invalid!", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Refresh the access token
	 * 
	 * @throws UnauthorizedException
	 * 
	 * @throws Exception
	 */
	public void refreshAccessToken() throws Exception {
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
