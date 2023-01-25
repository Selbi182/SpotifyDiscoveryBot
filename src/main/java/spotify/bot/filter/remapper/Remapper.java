package spotify.bot.filter.remapper;

import spotify.bot.util.data.AlbumGroupExtended;
import spotify.util.data.AlbumTrackPair;

public interface Remapper {

	/**
	 * An Action describes what to do with a release after
	 * {@link Remapper#determineRemapAction} has been called
	 */
	enum Action {

		/**
		 * Denotes that this release is no remappable candidate -> leave it untouched
		 */
		NONE,

		/**
		 * Denotes that this release is a remappable candidate and should be remapped
		 * into their respective playlist
		 */
		REMAP,

		/**
		 * Denotes that this release should be filtered entirely from the releases to
		 * be added. While the majority of filtering is done in other classes, there may
		 * be cases where it's more efficient to do it in the remapping service.
		 */
		ERASE;

		/**
		 * Convenience function to convert a boolean into the most common Actions
		 * 
		 * @param remap the boolean to apply
		 * @return {@link Action#REMAP} if true, {@link Action#NONE} if false
		 */
		static Action of(boolean remap) {
			return remap ? REMAP : NONE;
		}
	}

	/**
	 * Fetch the AlbumGroupExtended of this remapper
	 * 
	 * @return the extended album group
	 */
	AlbumGroupExtended getAlbumGroup();

	/**
	 * Returns true if the given AlbumGroupExtended is allowed for this remapper
	 * (e.g. EPs only allow Singles)
	 * 
	 * @param albumGroupExtended the extended album group to check
	 * @return true if this is a valid type
	 */
	boolean isAllowedAlbumGroup(AlbumGroupExtended albumGroupExtended);

	/**
	 * Check if the given release is a remappable candidate and return the
	 * {@link Action} to be applied. This is the main logic of the remappers and
	 * largely implementation-specific; see the respective implementation Javadocs
	 * for more details.
	 *
	 * @param atp the AlbumTrackPair
	 * @return the action to be applied
	 */
	Action determineRemapAction(AlbumTrackPair atp);
}
