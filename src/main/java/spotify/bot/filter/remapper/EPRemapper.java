package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class EPRemapper implements Remapper {

	private final static Pattern EP_MATCHER = Pattern.compile("\\bE\\W?P\\W?\\b");
	private final static int EP_SONG_COUNT_THRESHOLD = 5;
	private final static int EP_DURATION_THRESHOLD = 20 * 60 * 1000;
	private final static int EP_SONG_COUNT_THRESHOLD_LESSER = 3;
	private final static int EP_DURATION_THRESHOLD_LESSER = 10 * 60 * 1000;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.EP;
	}

	/**
	 * For EP remapping only Singles are relevant
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		return AlbumGroupExtended.SINGLE.equals(albumGroupExtended);
	}

	/**
	 * Returns true if the given single qualifies as EP. The definition of an EP is
	 * a single that fulfills ANY of the following attributes:
	 * <ul>
	 * <li>"EP" appears in the album title (uppercase, single word, may contain a
	 * single symbol in between and after the letters)</li>
	 * <li>min 5 songs</li>
	 * <li>min 20 minutes</li>
	 * <li>min 3 songs AND min 10 minutes AND none of the songs are named after the
	 * release title*</li>
	 * </ul>
	 * *The great majority of EPs are covered by the first three strategies. The
	 * last one for really silly edge cases in which an artist may release an EP
	 * that is is too similar to a slightly fancier single by numbers alone.
	 * 
	 * @param atp
	 * @return
	 */
	@Override
	public boolean qualifiesAsRemappable(AlbumTrackPair atp) {
		return qualifiesAsRemappable(atp.getAlbum().getName(), atp.getTracks());
	}

	public boolean qualifiesAsRemappable(String albumTitle, List<TrackSimplified> tracks) {
		if (EP_MATCHER.matcher(albumTitle).find()) {
			return true;
		}
		int trackCount = tracks.size();
		int totalDurationMs = tracks.stream().mapToInt(TrackSimplified::getDurationMs).sum();
		if (trackCount >= EP_SONG_COUNT_THRESHOLD || totalDurationMs >= EP_DURATION_THRESHOLD) {
			return true;
		}
		if (trackCount >= EP_SONG_COUNT_THRESHOLD_LESSER && totalDurationMs >= EP_DURATION_THRESHOLD_LESSER) {
			return true;
		}
		return false;
	}
}
