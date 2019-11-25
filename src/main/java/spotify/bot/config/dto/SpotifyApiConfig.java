package spotify.bot.config.dto;

public class SpotifyApiConfig {
	private String clientId;
	private String clientSecret;
	private String callbackUri;
	private String accessToken;
	private String refreshToken;

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getCallbackUri() {
		return callbackUri;
	}

	public void setCallbackUri(String callbackUri) {
		this.callbackUri = callbackUri;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	@Override
	public String toString() {
		return "SpotifyApiConfig [clientId=" + clientId + ", clientSecret=" + clientSecret + ", callbackUri=" + callbackUri + ", accessToken=" + accessToken + ", refreshToken=" + refreshToken + "]";
	}
}
