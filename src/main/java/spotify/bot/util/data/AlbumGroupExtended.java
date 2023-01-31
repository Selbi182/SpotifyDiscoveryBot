package spotify.bot.util.data;

import java.util.HashMap;
import java.util.Map;

import se.michaelthelin.spotify.enums.AlbumGroup;

public enum AlbumGroupExtended {
	ALBUM("album", "Albums"),
	APPEARS_ON("appears_on", "Appears On"),
	COMPILATION("compilation", "Compilations"),
	SINGLE("single", "Singles"),

	EP("ep", true, "EPs"),
	REMIX("remix", true, "Remixes"),
	LIVE("live", true, "Live"),
	RE_RELEASE("re_release", true, "Re-Releases");

	private static final Map<String, AlbumGroupExtended> map = new HashMap<>();

	static {
		for (AlbumGroupExtended albumGroupExtended : AlbumGroupExtended.values()) {
			map.put(albumGroupExtended.group, albumGroupExtended);
		}
	}

	private final String group;
	private final boolean extendedType;
	private final String humanName;

	AlbumGroupExtended(final String group, String humanName) {
		this(group, false, humanName);
	}

	AlbumGroupExtended(final String group, final boolean extendedType, String humanName) {
		this.group = group;
		this.extendedType = extendedType;
		this.humanName = humanName;
	}

	/**
	 * Return the extended album group representation of the default album group.
	 */
	public static AlbumGroupExtended fromAlbumGroup(AlbumGroup albumGroup) {
		return map.get(albumGroup.getGroup());
	}

	/**
	 * Return the base album group representation of the given extended album group.
	 * 
	 * @return the base album group
	 * @throws IllegalArgumentException if this is an extended album group
	 */
	public AlbumGroup asAlbumGroup() throws IllegalArgumentException {
		AlbumGroup ag = AlbumGroup.keyOf(this.group);
		if (ag == null || isExtendedType()) {
			throw new IllegalArgumentException("This is a special album group and cannot be converted back into a regular one!");
		}
		return ag;
	}

	/**
	 * Get the (lowercase) String representation of this group
	 */
	public String getGroupName() {
		return group;
	}

	/**
	 * Indicates whether this album group is a special extended type (EP, Remix, Live)
	 */
	public boolean isExtendedType() {
		return extendedType;
	}

	/**
	 * Get a human-readable name of this album group (intended for playlist creation)
	 */
	public String getHumanName() {
		return humanName;
	}
}
