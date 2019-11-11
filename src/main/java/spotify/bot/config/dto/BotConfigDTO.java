package spotify.bot.config.dto;

import java.util.Date;

public class BotConfigDTO {
	private String clientId;
	private String clientSecret;
	private String callbackUri;
	private Integer newNotificationTimeout;
	private Integer artistCacheTimeout;
	private Date artistCacheLastUpdated;

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

	public Integer getNewNotificationTimeout() {
		return newNotificationTimeout;
	}

	public void setNewNotificationTimeout(Integer newNotificationTimeout) {
		this.newNotificationTimeout = newNotificationTimeout;
	}

	public Integer getArtistCacheTimeout() {
		return artistCacheTimeout;
	}

	public void setArtistCacheTimeout(Integer artistCacheTimeout) {
		this.artistCacheTimeout = artistCacheTimeout;
	}

	public Date getArtistCacheLastUpdated() {
		return artistCacheLastUpdated;
	}

	public void setArtistCacheLastUpdated(Date artistCacheLastUpdated) {
		this.artistCacheLastUpdated = artistCacheLastUpdated;
	}

	@Override
	public String toString() {
		return "BotConfigDTO [clientId=" + clientId + ", clientSecret=" + clientSecret + ", callbackUri=" + callbackUri + ", newNotificationTimeout=" + newNotificationTimeout + ", artistCacheTimeout="
			+ artistCacheTimeout + ", artistCacheLastUpdated=" + artistCacheLastUpdated + "]";
	}
}
