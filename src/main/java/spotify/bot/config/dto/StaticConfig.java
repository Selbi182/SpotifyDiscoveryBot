package spotify.bot.config.dto;

import java.util.Date;

public class StaticConfig {
	private int lookbackDays;
	private int newNotificationTimeout;
	private int artistCacheTimeout;
	private Date artistCacheLastUpdated;
	private boolean restartBeforeFriday;

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

	public boolean isRestartBeforeFriday() {
		return restartBeforeFriday;
	}

	public void setRestartBeforeFriday(boolean restartBeforeFriday) {
		this.restartBeforeFriday = restartBeforeFriday;
	}

	@Override
	public String toString() {
		return "StaticConfig [lookbackDays=" + lookbackDays + ", newNotificationTimeout=" + newNotificationTimeout + ", artistCacheTimeout=" + artistCacheTimeout + ", artistCacheLastUpdated=" + artistCacheLastUpdated
				+ ", restartBeforeFriday=" + restartBeforeFriday + "]";
	}
}
