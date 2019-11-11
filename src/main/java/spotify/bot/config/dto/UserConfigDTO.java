package spotify.bot.config.dto;

import com.neovisionaries.i18n.CountryCode;

public class UserConfigDTO {
	private String accessToken;
	private String refreshToken;
	private boolean intelligentAppearsOnSearch;
	private CountryCode market;
	private int lookbackDays;
	private boolean circularPlaylistFitting;

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

	public boolean isIntelligentAppearsOnSearch() {
		return intelligentAppearsOnSearch;
	}

	public void setIntelligentAppearsOnSearch(boolean intelligentAppearsOnSearch) {
		this.intelligentAppearsOnSearch = intelligentAppearsOnSearch;
	}

	public CountryCode getMarket() {
		return market;
	}

	public void setMarket(CountryCode market) {
		this.market = market;
	}

	public int getLookbackDays() {
		return lookbackDays;
	}

	public void setLookbackDays(Integer lookbackDays) {
		this.lookbackDays = lookbackDays;
	}

	public boolean isCircularPlaylistFitting() {
		return circularPlaylistFitting;
	}

	public void setCircularPlaylistFitting(boolean circularPlaylistFitting) {
		this.circularPlaylistFitting = circularPlaylistFitting;
	}

	@Override
	public String toString() {
		return "UserConfigDTO [accessToken=" + accessToken + ", refreshToken=" + refreshToken + ", intelligentAppearsOnSearch=" + intelligentAppearsOnSearch + ", market=" + market + ", lookbackDays="
			+ lookbackDays + ", circularPlaylistFitting=" + circularPlaylistFitting + "]";
	}
}
