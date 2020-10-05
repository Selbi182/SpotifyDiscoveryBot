package spotify.bot.filter.remapper;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AudioFeatures;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.services.TrackService;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@Component
public class LiveRemapper implements Remapper {

	private final TrackService trackService;

	public LiveRemapper(TrackService trackService) {
		this.trackService = trackService;
	}

	private final static Pattern LIVE_MATCHER = Pattern.compile("\\b(LIVE|SHOW|TOUR)\\b", Pattern.CASE_INSENSITIVE);
	private final static double LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD_DEFINITE = 0.9;
	private final static double LIVENESS_THRESHOLD = 0.55;
	private final static double LIVENESS_THRESHOLD_LESSER = 0.4;
	private final static double EPSILON = 0.01;
	private final static int MIN_SONG_COUNT_FOR_SHORTCUT = 3;

	@Override
	public AlbumGroupExtended getAlbumGroup() {
		return AlbumGroupExtended.LIVE;
	}

	/**
	 * Any non-extended album group except appears_on qualifies as relevant for Live
	 * remapping
	 */
	@Override
	public boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended) {
		if (!albumGroupExtended.equals(AlbumGroupExtended.APPEARS_ON)) {
			try {
				AlbumGroup albumGroup = albumGroupExtended.asAlbumGroup();
				return albumGroup != null;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		return false;
	}

	@Override
	public Action determineRemapAction(AlbumTrackPair atp) {
		return Action.of(qualifiesAsRemappable(atp.getAlbum().getName(), atp.getTracks()));
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
	private boolean qualifiesAsRemappable(String albumTitle, List<TrackSimplified> tracks) {
		double trackCount = tracks.size();
		double liveTracks = tracks.stream().filter(t -> LIVE_MATCHER.matcher(t.getName()).find()).count();
		double liveTrackPercentage = liveTracks / trackCount;
		if (trackCount > MIN_SONG_COUNT_FOR_SHORTCUT && liveTrackPercentage > LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD_DEFINITE) {
			return true;
		}

		boolean hasLiveInTitle = LIVE_MATCHER.matcher(albumTitle).find();
		boolean hasLiveInTracks = liveTrackPercentage > EPSILON;

		if (hasLiveInTitle || hasLiveInTracks) {
			List<AudioFeatures> audioFeatures = trackService.getAudioFeatures(tracks);
			double averageLiveness = audioFeatures.stream()
				.filter(Objects::nonNull)
				.mapToDouble(AudioFeatures::getLiveness)
				.average()
				.orElseGet(() -> 0.0);
			boolean isLive = averageLiveness > LIVENESS_THRESHOLD;
			if (!isLive && hasLiveInTitle) {
				isLive = averageLiveness >= LIVENESS_THRESHOLD_LESSER;
			}
			return isLive;
		}
		return false;
	}
}
