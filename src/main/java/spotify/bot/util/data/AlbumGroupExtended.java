package spotify.bot.util.data;

import java.util.HashMap;
import java.util.Map;

import se.michaelthelin.spotify.enums.AlbumGroup;

public enum AlbumGroupExtended {
	ALBUM("album"),
	APPEARS_ON("appears_on"),
	COMPILATION("compilation"),
	SINGLE("single"),

	EP("ep", true),
	REMIX("remix", true),
	LIVE("live", true),
	RE_RELEASE("re_release", true);

	private static final Map<String, AlbumGroupExtended> map = new HashMap<>();

	static {
		for (AlbumGroupExtended albumGroupExtended : AlbumGroupExtended.values()) {
			map.put(albumGroupExtended.group, albumGroupExtended);
		}
	}

	private final String group;
	private final boolean extendedType;

	AlbumGroupExtended(final String group) {
		this(group, false);
	}

	AlbumGroupExtended(final String group, final boolean extendedType) {
		this.group = group;
		this.extendedType = extendedType;
	}

	/**
	 * Return the extended album group representation of the default album group.
	 * 
	 * @param albumGroup
	 * @return
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
	 * 
	 * @return
	 */
	public String getGroupName() {
		return group;
	}

	/**
	 * Get the (uppercase) String representation of this group used for the database
	 * 
	 * @return
	 */
	public String getDatabaseName() {
		return getGroupName().toUpperCase();
	}

	/**
	 * Indicates whether or not this album group is a special extended type (EP,
	 * Remixe, Live)
	 * 
	 * @return
	 */
	public boolean isExtendedType() {
		return extendedType;
	}
}
