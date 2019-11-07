package spotify.bot.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wrapper.spotify.enums.ReleaseDatePrecision;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.config.Config;

@Component
public class ReleaseValidator {
	private Set<String> validDates;
	private String validMonthDate;

	@Autowired
	private Config config;

	@PostConstruct
	public void calculateValidDates() {
		Calendar cal = Calendar.getInstance();

		SimpleDateFormat monthPrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_MONTH);
		this.validMonthDate = monthPrecision.format(cal.getTime());

		SimpleDateFormat datePrecision = new SimpleDateFormat(Constants.RELEASE_DATE_FORMAT_DAY);
		this.validDates = new HashSet<>();
		for (int i = 0; i < config.getLookbackDays(); i++) {
			validDates.add(datePrecision.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_MONTH, -1);
		}
	}

	/**
	 * Returns true if the album's given release date is within the previously
	 * specified lookbackDays range
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
