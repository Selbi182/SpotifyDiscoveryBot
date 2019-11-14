package spotify.bot.util.data;

import java.util.HashMap;
import java.util.Map;

import com.wrapper.spotify.enums.AlbumGroup;

public enum AlbumGroupExtended {
	ALBUM("album"), APPEARS_ON("appears_on"), COMPILATION("compilation"), SINGLE("single"),

	EP("ep", true), LIVE("live", true), TRASH("trash", true);

	private static final Map<String, AlbumGroupExtended> map = new HashMap<>();

	static {
		for (AlbumGroupExtended albumGroup : AlbumGroupExtended.values()) {
			map.put(albumGroup.group, albumGroup);
		}
	}

	private final String group;
	private final boolean specialType;

	AlbumGroupExtended(final String group) {
		this(group, false);
	}

	AlbumGroupExtended(final String group, final boolean specialType) {
		this.group = group;
		this.specialType = specialType;
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

	public AlbumGroup asAlbumGroup() {
		AlbumGroup ag = AlbumGroup.keyOf(this.group);
		if (ag == null) {
			throw new IllegalArgumentException("This is a special album group an cannot be converted back into a regular one!");
		}
		return ag;
	}

	/**
	 * Get the (lowercase) String representation of this group
	 * 
	 * @return
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * Indicates whether or not this album group is a special type (EPs, Live,
	 * Trash, etc.)
	 * 
	 * @return
	 */
	public boolean isSpecialType() {
		return specialType;
	}
}
