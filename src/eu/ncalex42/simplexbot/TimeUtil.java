package eu.ncalex42.simplexbot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    public static final long SECONDS_PER_DAY = 60L * 60L * 24L;
    public static final long SECONDS_PER_HOUR = 60L * 60L;

    public static String formatTimeStamp() {

        final LocalDateTime timeStamp = LocalDateTime.now();
        return "[" + timeStamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "]";
    }

    public static String formatUtcTimeStamp() {

        return "[" + getUtcTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC]";
    }

    public static long getUtcSeconds() {

        return getUtcTime().toEpochSecond();
    }

    public static ZonedDateTime getUtcTime() {

        final ZonedDateTime timeStamp = ZonedDateTime.now();
        return timeStamp.withZoneSameInstant(ZoneId.of("Z"));
    }

    public static long timestampToUtcSeconds(String timestamp) {

        final LocalDateTime dateTime = LocalDateTime.parse(timestamp,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
        return dateTime.toEpochSecond(ZoneOffset.of("Z"));
    }

    public static long timestampWithNanosToUtcSeconds(String timestamp) {

        final LocalDateTime dateTime = LocalDateTime.parse(timestamp,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nX"));
        return dateTime.toEpochSecond(ZoneOffset.of("Z"));
    }

    /**
     * 1= Monday, 7=Sunday
     *
     * @return day of week as int
     */
    public static int getDayOfWeek() {

        final LocalDateTime timeStamp = LocalDateTime.now();
        return timeStamp.getDayOfWeek().getValue();
    }

    public static int getHourOfDay() {

        final LocalDateTime timeStamp = LocalDateTime.now();
        return timeStamp.getHour();
    }
}
