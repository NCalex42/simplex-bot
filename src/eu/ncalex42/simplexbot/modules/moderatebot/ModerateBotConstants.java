package eu.ncalex42.simplexbot.modules.moderatebot;

public class ModerateBotConstants {

    public static final String CFG_FILE_NAME = "moderate-bot.txt";
    static final String BLOCK_BLACKLIST_FILENAME = "moderate-bot-block-blacklist.txt";
    static final String MODERATE_BLACKLIST_FILENAME = "moderate-bot-moderate-blacklist.txt";
    static final String REPORT_BLACKLIST_FILENAME = "moderate-bot-report-blacklist.txt";
    static final String PROCESSED_MESSAGES_CACHE_FILE = "moderate-bot-message-cache.tmp";

    static final String CONFIG_PORT = "port";
    static final String CONFIG_GROUP = "group";
    static final String CONFIG_OUTPUT_CONTACTS = "output-contacts";
    static final String CONFIG_OUTPUT_GROUPS = "output-groups";
    static final String CONFIG_SLEEP_TIME_SECONDS = "sleep-time-seconds";
    static final String CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE = "number-of-messages-to-retrieve";
    static final String CONFIG_PERSIST_STATE = "persist-state";
    static final String CONFIG_REPORT_TO_CONTACTS = "report-to-contacts";
    static final String CONFIG_REPORT_TO_GROUPS = "report-to-groups";

    static final String IMAGE_KEYWORD = "@image";
    static final String VIDEO_KEYWORD = "@video";
    static final String FILE_KEYWORD = "@file";
    static final String LINK_KEYWORD = "@link";
    static final String VOICE_KEYWORD = "@voice";
    static final String REGEX_KEYWORD = "@regex";
    static final String USER_KEYWORD = "@user";
}
