package spotify.bot.filter.remapper;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AudioFeatures;

import spotify.bot.api.services.TrackService;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class LiveRemapper implements Remapper {

	@Autowired
	private TrackService trackService;

	private final static Pattern LIVE_MATCHER = Pattern.compile("\\bLIVE\\b", Pattern.CASE_INSENSITIVE);
	private final static double LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD = 0.5;
	private final static double LIVENESS_THRESHOLD = 0.5;
	private final static double LIVENESS_THRESHOLD_LESSER = 0.3;
	private final static double EPSILON = 0.01;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.LIVE;
	}

	/**
	 * Any non-extended album group qualifies as relevant for Live remapping
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
	 * Returns true if the given release qualifies as live release. The definition
	 * of a live release is a release that fulfills ANY of the following attributes:
	 * <ul>
	 * <li>At least half of the songs of this release have a combined "liveness"
	 * average of 50% or more</li>
	 * <li>If the word "LIVE" is included in the release title, the required
	 * liveness threshold is reduced to 25%</li>
	 * </ul>
	 * The liveness value is determined by the Spotify API for each individual song.
	 * It gives a vague idea how probable it is for the song to be live. Hints like
	 * recording quality and audience cheers are used.
	 * 
	 * @param atp
	 * @return
	 */
	@Override
	public boolean qualifiesAsRemappable(AlbumTrackPair atp) {
		double trackCount = atp.getTracks().size();
		double liveTracks = atp.getTracks().stream().filter(t -> LIVE_MATCHER.matcher(t.getName()).find()).count();
		double liveTrackPercentage = liveTracks / trackCount;

		boolean hasLiveInTitle = LIVE_MATCHER.matcher(atp.getAlbum().getName()).find();
		boolean hasLiveInTracks = liveTrackPercentage < EPSILON;

		if (hasLiveInTitle || hasLiveInTracks) {
			if (hasLiveInTitle && (liveTrackPercentage >= LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD)) {
				return true;
			}
			List<AudioFeatures> audioFeatures = trackService.getAudioFeatures(atp.getTracks());
			double averageLiveness = audioFeatures.stream().mapToDouble(AudioFeatures::getLiveness).average().getAsDouble();
			boolean isLive = averageLiveness >= LIVENESS_THRESHOLD;
			if (!isLive && hasLiveInTitle) {
				isLive = averageLiveness >= LIVENESS_THRESHOLD_LESSER;
			}
			return isLive;
		}
		return false;
	}
}
