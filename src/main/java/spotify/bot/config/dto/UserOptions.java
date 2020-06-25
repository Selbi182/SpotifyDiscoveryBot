package spotify.bot.config.dto;

public class UserOptions {
	private boolean cacheFollowedArtists;
	private boolean batchPlaylistAddition;
	private boolean intelligentAppearsOnSearch;
	private boolean circularPlaylistFitting;
	private boolean epSeparation;
	private boolean remixSeparation;
	private boolean liveSeparation;

	public boolean isCacheFollowedArtists() {
		return cacheFollowedArtists;
	}

	public void setCacheFollowedArtists(boolean cacheFollowedArtists) {
		this.cacheFollowedArtists = cacheFollowedArtists;
	}

	public boolean isBatchPlaylistAddition() {
		return batchPlaylistAddition;
	}

	public void setBatchPlaylistAddition(boolean batchPlaylistAddition) {
		this.batchPlaylistAddition = batchPlaylistAddition;
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

	public boolean isRemixSeparation() {
		return remixSeparation;
	}

	public void setRemixSeparation(boolean remixSeparation) {
		this.remixSeparation = remixSeparation;
	}

	public boolean isLiveSeparation() {
		return liveSeparation;
	}

	public void setLiveSeparation(boolean liveSeparation) {
		this.liveSeparation = liveSeparation;
	}

	@Override
	public String toString() {
		return "UserOptions [cacheFollowedArtists=" + cacheFollowedArtists + ", batchPlaylistAddition=" + batchPlaylistAddition + ", intelligentAppearsOnSearch=" + intelligentAppearsOnSearch + ", circularPlaylistFitting="
			+ circularPlaylistFitting + ", epSeparation=" + epSeparation + ", remixSeparation=" + remixSeparation + ", liveSeparation=" + liveSeparation + "]";
	}

}
