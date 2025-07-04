package eu.ncalex42.simplexbot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    public static final int MILLISECONDS_PER_MINUTE = 60 * 1000;
    public static final long MILLISECONDS_PER_SECOND = 1000L;
    public static final long SECONDS_PER_HOUR = 60L * 60L;
    public static final long SECONDS_PER_DAY = 60L * 60L * 24L;
    public static final long SECONDS_PER_WEEK = 60L * 60L * 24L * 7L;

    public static String formatTimestamp() {

        final LocalDateTime timestamp = LocalDateTime.now();
        return "[" + timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "]";
    }

    public static String formatUtcTimestamp() {

        return "[" + getUtcTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC]";
    }

    public static long getUtcSeconds() {

        return getUtcTime().toEpochSecond();
    }

    public static ZonedDateTime getUtcTime() {

        final ZonedDateTime timestamp = ZonedDateTime.now();
        return timestamp.withZoneSameInstant(ZoneId.of("Z"));
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

        final LocalDateTime timestamp = LocalDateTime.now();
        return timestamp.getDayOfWeek().getValue();
    }

    public static int getHourOfDay() {

        final LocalDateTime timestamp = LocalDateTime.now();
        return timestamp.getHour();
    }

    public static String getDate() {

        final LocalDateTime timestamp = LocalDateTime.now();
        final int year = timestamp.getYear();
        final int month = timestamp.getMonthValue();
        final int day = timestamp.getDayOfMonth();
        return year + "-" + month + "-" + day;
    }
}
