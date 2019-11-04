package spotify.bot.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.config.Config;

public class ReleaseValidator {
	private static ReleaseValidator instance;
	private Set<String> validDates;
	private String validMonthDate;
	
	private static Config config;
	
	/**
	 * Initialize the utility class's configuration
	 * 
	 * @param config
	 */
	public static void initializeUtilConfig(Config config) {
		ReleaseValidator.config = config;
	}
	
	private ReleaseValidator(int lookbackDays) {		
		Calendar cal = Calendar.getInstance();
		
		SimpleDateFormat monthPrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_MONTH);
		this.validMonthDate = monthPrecision.format(cal.getTime());
				
		SimpleDateFormat datePrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_DAY);
		this.validDates = new HashSet<>();
		for (int i = 0; i < lookbackDays; i++) {
			validDates.add(datePrecision.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
	}
	
	public static ReleaseValidator getInstance() {
		if (instance == null) {
			instance = new ReleaseValidator(config.getLookbackDays());
		}
		return instance;
	}
	
	/**
	 * Returns true if the album's given release date is within the previously specified lookbackDays range
	 * 
	 * @param a
	 * @return
	 */
	public boolean isValidDate(AlbumSimplified a) {
		if (a != null) {
			if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.DAY)) {
				return validDates.contains(a.getReleaseDate());
			} else if (a.getReleaseDatePrecision().equals(ReleaseDatePrecision.MONTH)) {
				return validMonthDate.equals(a.getReleaseDate());
			}
		}
		return false;
	}
}
