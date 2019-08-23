package spotify.bot.api;

import java.io.IOException;
import java.net.URI;
import java.util.Scanner;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import spotify.bot.Config;
import spotify.bot.util.Constants;

public class SpotifyApiSessionManager {

	protected static class ApiWrapper {
		private static SpotifyApi spotifyApi;
		
		/**
		 * Construct or create the singleton Spotify API session
		 * 
		 * @return
		 * @throws IOException
		 */
		public static SpotifyApi api() throws IOException {
			if (spotifyApi == null) {
				spotifyApi = new SpotifyApi.Builder()
					.setClientId(Config.getInstance().getClientId())
					.setClientSecret(Config.getInstance().getClientSecret())
					.setRedirectUri(SpotifyHttpManager.makeUri(Config.getInstance().getCallbackUri()))
					.build();
			}
			return spotifyApi;
		}
	}

	public SpotifyApi api() throws IOException {
		return ApiWrapper.api();
	}
	
	////////////////////////////////////
	
	public SpotifyApiSessionManager() throws Exception {
		// Set tokens, if preexisting
		ApiWrapper.api().setAccessToken(Config.getInstance().getAccessToken());
		ApiWrapper.api().setRefreshToken(Config.getInstance().getRefreshToken());

		// Try to login with the stored access tokens or re-authenticate
		try {
			refreshAccessToken();
		} catch (UnauthorizedException | BadRequestException e) {
			Config.log().warning("Access token expired or is invalid, please sign in again under this URL:");
			authenticate();
		}
	}

	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * 
	 * @throws Exception
	 */
	private void authenticate() throws Exception {
		URI uri = SpotifyApiRequest.execute(ApiWrapper.api().authorizationCodeUri().scope(Constants.SCOPES).build());
		Config.log().info(uri.toString());

		Scanner scanner = new Scanner(System.in);
		String code = scanner.nextLine().replace(ApiWrapper.api().getRedirectURI().toString() + "?code=", "").trim();
		scanner.close();

		AuthorizationCodeCredentials acc = SpotifyApiRequest.execute(ApiWrapper.api().authorizationCode(code).build());
		ApiWrapper.api().setAccessToken(acc.getAccessToken());
		ApiWrapper.api().setRefreshToken(acc.getRefreshToken());

		updateTokens();
	}

	/**
	 * Refresh the access token
	 * 
	 * @throws Exception
	 */
	private void refreshAccessToken() throws Exception {
		AuthorizationCodeCredentials acc = SpotifyApiRequest.execute(ApiWrapper.api().authorizationCodeRefresh().build());
		ApiWrapper.api().setAccessToken(acc.getAccessToken());
		updateTokens();
	}

	/**
	 * Store the access and refresh tokens in the INI file
	 * 
	 * @throws IOException
	 */
	private void updateTokens() throws IOException {
		Config.getInstance().updateTokens(ApiWrapper.api().getAccessToken(), ApiWrapper.api().getRefreshToken());
	}
}
