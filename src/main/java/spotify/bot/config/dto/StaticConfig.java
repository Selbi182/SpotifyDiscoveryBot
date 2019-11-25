package spotify.bot.config.dto;

import java.util.Date;

import com.neovisionaries.i18n.CountryCode;

public class StaticConfig {
	private CountryCode market;
	private int lookbackDays;
	private int newNotificationTimeout;
	private int artistCacheTimeout;
	private Date artistCacheLastUpdated;

	public CountryCode getMarket() {
		return market;
	}

	public void setMarket(CountryCode market) {
		this.market = market;
	}

	public int getLookbackDays() {
		return lookbackDays;
	}

	public void setLookbackDays(int lookbackDays) {
		this.lookbackDays = lookbackDays;
	}

	public int getNewNotificationTimeout() {
		return newNotificationTimeout;
	}

	public void setNewNotificationTimeout(int newNotificationTimeout) {
		this.newNotificationTimeout = newNotificationTimeout;
	}

	public int getArtistCacheTimeout() {
		return artistCacheTimeout;
	}

	public void setArtistCacheTimeout(int artistCacheTimeout) {
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
		return "StaticConfig [market=" + market + ", lookbackDays=" + lookbackDays + ", newNotificationTimeout=" + newNotificationTimeout + ", artistCacheTimeout=" + artistCacheTimeout
			+ ", artistCacheLastUpdated=" + artistCacheLastUpdated + "]";
	}
}
