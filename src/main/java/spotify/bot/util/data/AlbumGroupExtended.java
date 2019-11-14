package spotify.bot.util.data;

import java.util.HashMap;
import java.util.Map;

import com.wrapper.spotify.enums.AlbumGroup;

public enum AlbumGroupExtended {
	ALBUM("album"),
	APPEARS_ON("appears_on"),
	COMPILATION("compilation"),
	SINGLE("single"),
	
	EP("ep", true),
	LIVE("live", true),
	TRASH("trash", true);

	private static final Map<String, AlbumGroupExtended> map = new HashMap<>();

	static {
		for (AlbumGroupExtended albumGroup : AlbumGroupExtended.values()) {
			map.put(albumGroup.group, albumGroup);
		}
	}

	public final String group;
	public final boolean specialType;

	AlbumGroupExtended(final String group) {
		this(group, false);
	}
	
	AlbumGroupExtended(final String group, final boolean specialType) {
		this.group = group;
		this.specialType = specialType;
	}

	public static AlbumGroupExtended keyOf(String type) {
		return map.get(type);
	}
	
	public static AlbumGroupExtended fromAlbumGroup(AlbumGroup albumGroup) {
		return map.get(albumGroup.getGroup());
	}

	public String getGroup() {
		return group;
	}
}
