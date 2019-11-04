package spotify.bot.api;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.concurrent.Callable;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadGatewayException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.InternalServerErrorException;
import com.wrapper.spotify.exceptions.detailed.NotFoundException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.requests.IRequest;

import spotify.bot.config.BotLogger;
import spotify.bot.config.Config;
import spotify.bot.util.Constants;

@Configuration
public class SpotifyApiWrapper {
	
	@Autowired
	private Config config;
	
	@Autowired
	private BotLogger log;
	
	private SpotifyApi spotifyApi;

	@PostConstruct
	public void init() {
		try {
			// Set tokens, if preexisting
			api().setAccessToken(config.getAccessToken());
			api().setRefreshToken(config.getRefreshToken());
			
			// Try to login with the stored access tokens or re-authenticate
			try {
				refreshAccessToken();
			} catch (UnauthorizedException | BadRequestException | NullPointerException e) {
				log.warning("Access token expired or is invalid, please sign in again under this URL:");
				authenticate();
			}
		} catch (Exception e) {
			log.stackTrace(e);
		}
	}
	
	/**
	 * Get the current SpotifyApi instance
	 * 
	 * @return
	 */
	public SpotifyApi api() {
		try {
			if (spotifyApi == null) {
				spotifyApi = new SpotifyApi.Builder()
					.setClientId(config.getClientId())
					.setClientSecret(config.getClientSecret())
					.setRedirectUri(SpotifyHttpManager.makeUri(config.getCallbackUri()))
					.build();
			}
		} catch (Exception e) {
			log.stackTrace(e);
		}
		return spotifyApi;
	}
	
	///////////////////////
	

	/**
	 * Executes a greedy API request, meaning that on potential <i>429 Too many
	 * requests</i> errors the request will be retried until it succeeds. Any
	 * attempts will be delayed by the response body's given <code>retryAfter</code>
	 * parameter, in seconds.
	 * 
	 * @param request
	 * @return
	 * @throws Exception 
	 */
	public <T> T execute(IRequest<T> request) {
		try {
			return execute(new Callable<T>() {
				@Override
				public T call() throws Exception {
					return request.execute();
				}
			});			
		} catch (Exception e) {
			log.stackTrace(e);
			return null;
		}
	}

	/**
	 * Executes a greedy API request, wrapped in a <code>Callable</code>, meaning
	 * that on potential <i>429 Too many requests</i> errors the request will be
	 * retried until it succeeds. Any attempts will be delayed by the response
	 * body's given <code>retryAfter</code> parameter, in seconds. 5xx errors will be
	 * given a fixed one-minute retry timeout.
	 * 
	 * @param request
	 * @return
	 * @throws Exception 
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public <T> T execute(Callable<T> callable) {
		try {
			while (true) {
				try {
					T t = callable.call();
					return t;
				} catch (TooManyRequestsException e) {
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.RETRY_TIMEOUT_4XX);
				} catch (InternalServerErrorException | BadGatewayException | NotFoundException e) {
					Thread.sleep(Constants.RETRY_TIMEOUT_5XX);
				}
			}			
		} catch (Exception e) {
			log.stackTrace(e);
			return null;
		}
	}
	
	///////////////////////
	
	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * 
	 * @throws Exception
	 */
	private void authenticate() throws Exception {
		URI uri = execute(api().authorizationCodeUri().scope(Constants.SCOPES).build());
		log.info(uri.toString());

		Scanner scanner = new Scanner(System.in);
		String code = scanner.nextLine().replace(api().getRedirectURI().toString() + "?code=", "").trim();
		scanner.close();

		AuthorizationCodeCredentials acc = execute(api().authorizationCode(code).build());
		api().setAccessToken(acc.getAccessToken());
		api().setRefreshToken(acc.getRefreshToken());

		updateTokens();
	}

	/**
	 * Refresh the access token
	 * @throws SQLException 
	 * @throws IOException 
	 * 
	 * @throws Exception
	 */
	private void refreshAccessToken() throws IOException, SQLException, BadRequestException, UnauthorizedException {
		if (api().getAccessToken() == null || api().getRefreshToken() == null) {
			throw new BadRequestException("Tokens aren't set");
		}
		
		AuthorizationCodeCredentials acc = execute(api().authorizationCodeRefresh().build());
		api().setAccessToken(acc.getAccessToken());
		updateTokens();
	}

	/**
	 * Store the access and refresh tokens in the INI file
	 * 
	 * @throws IOException
	 * @throws SQLException 
	 */
	private void updateTokens() throws IOException, SQLException {
		config.updateTokens(api().getAccessToken(), api().getRefreshToken());
	}
}
