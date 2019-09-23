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
	private static SpotifyApiSessionManager sessionManagerSingleton;
	private static SpotifyApi spotifyApi;
	
	/**
	 * Construct or create the singleton Spotify API session
	 * 
	 * @return
	 * @throws IOException
	 */
	public static SpotifyApi api() throws IOException {
		if (sessionManagerSingleton == null) {
			sessionManagerSingleton = new SpotifyApiSessionManager();
		}
		
		return sessionManagerSingleton.spotifyApi();
	}
	
	////////////////////////////////////////
	
	private SpotifyApiSessionManager() {
		try {
			// Set tokens, if preexisting
			spotifyApi().setAccessToken(Config.getInstance().getAccessToken());
			spotifyApi().setRefreshToken(Config.getInstance().getRefreshToken());
			
			// Try to login with the stored access tokens or re-authenticate
			try {
				refreshAccessToken();
			} catch (UnauthorizedException | BadRequestException e) {
				Config.log().warning("Access token expired or is invalid, please sign in again under this URL:");
				authenticate();			
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Private singleton function to bypass the session manager singleton above
	 * 
	 * @return
	 */
	private SpotifyApi spotifyApi() {
		try {
			if (spotifyApi == null) {
				spotifyApi = new SpotifyApi.Builder()
				.setClientId(Config.getInstance().getClientId())
				.setClientSecret(Config.getInstance().getClientSecret())
				.setRedirectUri(SpotifyHttpManager.makeUri(Config.getInstance().getCallbackUri()))
				.build();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return spotifyApi;
	}
	
	/**
	 * Authentication process (WIP: user needs to manually copy-paste both the URI as well as the return code)
	 * 
	 * @throws Exception
	 */
	private void authenticate() throws Exception {
		URI uri = SpotifyApiRequest.execute(spotifyApi().authorizationCodeUri().scope(Constants.SCOPES).build());
		Config.log().info(uri.toString());

		Scanner scanner = new Scanner(System.in);
		String code = scanner.nextLine().replace(spotifyApi().getRedirectURI().toString() + "?code=", "").trim();
		scanner.close();

		AuthorizationCodeCredentials acc = SpotifyApiRequest.execute(spotifyApi().authorizationCode(code).build());
		spotifyApi().setAccessToken(acc.getAccessToken());
		spotifyApi().setRefreshToken(acc.getRefreshToken());

		updateTokens();
	}

	/**
	 * Refresh the access token
	 * 
	 * @throws Exception
	 */
	private void refreshAccessToken() throws Exception {
		AuthorizationCodeCredentials acc = SpotifyApiRequest.execute(spotifyApi().authorizationCodeRefresh().build());
		spotifyApi().setAccessToken(acc.getAccessToken());
		updateTokens();
	}

	/**
	 * Store the access and refresh tokens in the INI file
	 * 
	 * @throws IOException
	 */
	private void updateTokens() throws IOException {
		Config.getInstance().updateTokens(spotifyApi().getAccessToken(), spotifyApi().getRefreshToken());
	}
}
