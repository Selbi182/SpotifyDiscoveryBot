package spotify.bot.config.dto;

public class UserOptions {
	private boolean intelligentAppearsOnSearch;
	private boolean circularPlaylistFitting;
	private boolean epSeparation;
	private boolean remixSeparation;
	private boolean liveSeparation;
	private boolean rereleaseSeparation;

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

	public boolean isRereleaseSeparation() {
		return rereleaseSeparation;
	}

	public void setRereleaseSeparation(boolean rereleaseSeparation) {
		this.rereleaseSeparation = rereleaseSeparation;
	}

	@Override
	public String toString() {
		return "UserOptions [intelligentAppearsOnSearch=" + intelligentAppearsOnSearch + ", circularPlaylistFitting="
			+ circularPlaylistFitting + ", epSeparation=" + epSeparation + ", remixSeparation=" + remixSeparation + ", liveSeparation=" + liveSeparation + ", rereleaseSeparation=" + rereleaseSeparation + "]";
	}

}
