package spotify.bot.config.dto;

public class UserOptions {
	private boolean cacheFollowedArtists;
	private boolean intelligentAppearsOnSearch;
	private boolean circularPlaylistFitting;
	private boolean epSeparation;
	private boolean liveSeparation;

	public boolean isCacheFollowedArtists() {
		return cacheFollowedArtists;
	}

	public void setCacheFollowedArtists(boolean cacheFollowedArtists) {
		this.cacheFollowedArtists = cacheFollowedArtists;
	}

	public boolean isIntelligentAppearsOnSearch() {
		return intelligentAppearsOnSearch;
	}

	public void setIntelligentAppearsOnSearch(boolean intelligentAppearsOnSearch) {
		this.intelligentAppearsOnSearch = intelligentAppearsOnSearch;
	}

	public boolean isCircularPlaylistFitting() {
		return circularPlaylistFitting;
	}

	public void setCircularPlaylistFitting(boolean circularPlaylistFitting) {
		this.circularPlaylistFitting = circularPlaylistFitting;
	}

	public boolean isEpSeparation() {
		return epSeparation;
	}

	public void setEpSeparation(boolean epSeparation) {
		this.epSeparation = epSeparation;
	}

	public boolean isLiveSeparation() {
		return liveSeparation;
	}

	public void setLiveSeparation(boolean liveSeparation) {
		this.liveSeparation = liveSeparation;
	}

	@Override
	public String toString() {
		return "UserOptionsConfig [cacheFollowedArtists=" + cacheFollowedArtists + ", intelligentAppearsOnSearch=" + intelligentAppearsOnSearch + ", circularPlaylistFitting="
			+ circularPlaylistFitting + ", epSeparation=" + epSeparation + ", liveSeparation=" + liveSeparation + "]";
	}
}
