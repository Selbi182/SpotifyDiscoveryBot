package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class RemixRemapper implements Remapper {

	private final static Pattern REMIX_MATCHER = Pattern.compile("\\bREMIX(ES)?\\b", Pattern.CASE_INSENSITIVE);
	private final static double REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD = 0.5;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.REMIX;
	}

	/**
	 * Any non-extended album group qualifies as relevant for Remix remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		try {
			AlbumGroup albumGroup = albumGroupExtended.asAlbumGroup();
			return albumGroup != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Returns true if the release OR at least half of the release's tracks have
	 * "REMIX" in their titles (one word, case insenstive)
	 */
	@Override
	public boolean qualifiesAsRemappable(AlbumTrackPair atp) {
		return qualifiesAsRemappable(atp.getAlbum().getName(), atp.getTracks().stream().map(TrackSimplified::getName).collect(Collectors.toList()));
	}

	public boolean qualifiesAsRemappable(String albumTitle, List<String> trackTitles) {
		boolean hasRemixInTitle = REMIX_MATCHER.matcher(albumTitle).find();
		if (!hasRemixInTitle) {
			double trackCountRemix = trackTitles.stream().filter(t -> REMIX_MATCHER.matcher(t).find()).count();
			double trackCount = trackTitles.size();
			return (trackCountRemix / trackCount) >= REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD;
		}
		return true;
	}
}
