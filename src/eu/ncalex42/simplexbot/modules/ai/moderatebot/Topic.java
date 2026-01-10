package eu.ncalex42.simplexbot.modules.ai.moderatebot;

/**
 * Represents a topic with associated thresholds for reporting, moderation,
 * downgrading, and blocking.
 */
public class Topic {

    private final String text;
    private final int reportingThreshold;
    private final int moderationThreshold;
    private final int downgradingThreshold;
    private final int blockingThreshold;

    public Topic(String text, int reportingThreshold, int moderationThreshold, int downgradingThreshold,
            int blockingThreshold) {

        this.text = text;
        this.reportingThreshold = setThreshold(reportingThreshold);
        this.moderationThreshold = setThreshold(moderationThreshold);
        this.downgradingThreshold = setThreshold(downgradingThreshold);
        this.blockingThreshold = setThreshold(blockingThreshold);
    }

    /**
     * Allowed threshold values: <br>
     * - 0 --> disabled <br>
     * - 1 --> "definitely no" <br>
     * - 2 --> "probably no" <br>
     * - 3 --> "maybe/don't know" <br>
     * - 4 --> "probably yes" <br>
     * - 5 --> "definitely yes"
     *
     * @param input user input from config
     * @return valid threshold
     */
    private static int setThreshold(int input) {
        if (input < 0) {
            return 0;
        }
        if (input > 5) {
            return 5;
        }
        return input;
    }

    public static String getTextForThreshold(int threshold) {
        switch (threshold) {
        case 1:
            return "*[DEFINITELY NO]*";
        case 2:
            return "*[PROBABLY NO]*";
        case 3:
            return "*[MAYBE/DON'T KNOW]*";
        case 4:
            return "*[PROBABLY YES]*";
        case 5:
            return "*[DEFINITELY YES]*";
        default:
            return "*INVALID!*";
        }
    }

    public String getText() {
        return text;
    }

    public int getReportingThreshold() {
        return reportingThreshold;
    }

    public int getModerationThreshold() {
        return moderationThreshold;
    }

    public int getDowngradingThreshold() {
        return downgradingThreshold;
    }

    public int getBlockingThreshold() {
        return blockingThreshold;
    }

    @Override
    public String toString() {
        return reportingThreshold + "," + moderationThreshold + "," + downgradingThreshold + "," + blockingThreshold
                + ";" + (text.length() <= 100 ? text : text.substring(0, 100) + " [...]");
    }

}
