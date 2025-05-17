package eu.ncalex42.simplexbot.modules.moderatebot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.Connection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

/**
 * This module can report/moderate messages and block group members based on
 * keywords from a blacklist file.
 */
public class ModerateBot implements Runnable {

    private final Connection connection;
    private final String groupToProcess;

    private final int sleepTimeInSeconds;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;

    private final boolean blockImages;
    private final boolean blockVideos;
    private final boolean blockFiles;
    private final boolean blockLinks;
    private final boolean blockVoice;
    private final List<String> keywordBlockBlacklist;
    private final Map<String, Pattern> regexBlockBlacklist;
    private final List<String> userBlockBlacklist;

    private final boolean moderateImages;
    private final boolean moderateVideos;
    private final boolean moderateFiles;
    private final boolean moderateLinks;
    private final boolean moderateVoice;
    private final List<String> keywordModerateBlacklist;
    private final Map<String, Pattern> regexModerateBlacklist;
    private final List<String> userModerateBlacklist;

    private final boolean reportImages;
    private final boolean reportVideos;
    private final boolean reportFiles;
    private final boolean reportLinks;
    private final boolean reportVoice;
    private final List<String> keywordReportBlacklist;
    private final Map<String, Pattern> regexReportBlacklist;
    private final List<String> userReportBlacklist;

    private final PriorityBlockingQueue<MessageActionItem> actionQueue = new PriorityBlockingQueue<>();

    public static ModerateBot init(Path configFile) throws IOException {

        // read config file:
        int port = -1;
        String groupToProcess = null;
        int sleepTimeInSeconds = -60;
        final List<String> contactsForReporting = new LinkedList<>();
        final List<String> groupsForReporting = new LinkedList<>();

        for (final String line : Files.lines(configFile, StandardCharsets.UTF_8).collect(Collectors.toList())) {

            if (!line.contains("=")) {
                continue;
            }

            final String[] splittedLine = line.split("=", 2);
            final String key = splittedLine[0].strip();
            final String value = splittedLine[1].strip();

            switch (key.toLowerCase(Locale.US)) {

            case ModerateBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case ModerateBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case ModerateBotConstants.CONFIG_SLEEP_TIME_SECONDS:
                if (!value.isBlank()) {
                    sleepTimeInSeconds = Integer.parseInt(value);
                }
                break;

            case ModerateBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case ModerateBotConstants.CONFIG_REPORT_TO_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForReporting.add(group.strip());
                    }
                }
                break;

            default: // ignore
            }
        }

        // read block-blacklist file:
        final List<String> keywordBlockBlacklist = new LinkedList<>();
        final Map<String, Pattern> regexBlockBlacklist = new HashMap<>();
        final List<String> userBlockBlacklist = new LinkedList<>();
        boolean blockImages = false;
        boolean blockVideos = false;
        boolean blockFiles = false;
        boolean blockLinks = false;
        boolean blockVoice = false;
        final Path blockListFile = configFile.getParent().resolve(ModerateBotConstants.BLOCK_BLACKLIST_FILENAME);
        if (Files.exists(blockListFile)) {
            for (String line : Files.lines(blockListFile, StandardCharsets.UTF_8).collect(Collectors.toList())) {

                if (line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("\"#")) {
                    line = line.substring(1);
                }

                if (line.startsWith(ModerateBotConstants.IMAGE_KEYWORD)) {
                    blockImages = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VIDEO_KEYWORD)) {
                    blockVideos = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.FILE_KEYWORD)) {
                    blockFiles = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.LINK_KEYWORD)) {
                    blockLinks = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VOICE_KEYWORD)) {
                    blockVoice = true;
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.REGEX_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.REGEX_KEYWORD.length()).strip();
                    regexBlockBlacklist.put(line, Pattern.compile(line));
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.USER_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.USER_KEYWORD.length()).strip();
                    userBlockBlacklist.add(line);
                    continue;
                }

                if (!line.isBlank()) {
                    keywordBlockBlacklist.add(line.toLowerCase());
                    continue;
                }
            }
        }

        // read moderate-blacklist file:
        final List<String> keywordModerateBlacklist = new LinkedList<>();
        final Map<String, Pattern> regexModerateBlacklist = new HashMap<>();
        final List<String> userModerateBlacklist = new LinkedList<>();
        boolean moderateImages = false;
        boolean moderateVideos = false;
        boolean moderateFiles = false;
        boolean moderateLinks = false;
        boolean moderateVoice = false;
        final Path moderateBlacklist = configFile.getParent().resolve(ModerateBotConstants.MODERATE_BLACKLIST_FILENAME);
        if (Files.exists(moderateBlacklist)) {
            for (String line : Files.lines(moderateBlacklist, StandardCharsets.UTF_8).collect(Collectors.toList())) {

                if (line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("\"#")) {
                    line = line.substring(1);
                }

                if (line.startsWith(ModerateBotConstants.IMAGE_KEYWORD)) {
                    moderateImages = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VIDEO_KEYWORD)) {
                    moderateVideos = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.FILE_KEYWORD)) {
                    moderateFiles = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.LINK_KEYWORD)) {
                    moderateLinks = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VOICE_KEYWORD)) {
                    moderateVoice = true;
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.REGEX_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.REGEX_KEYWORD.length()).strip();
                    regexModerateBlacklist.put(line, Pattern.compile(line));
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.USER_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.USER_KEYWORD.length()).strip();
                    userModerateBlacklist.add(line);
                    continue;
                }

                if (!line.isBlank()) {
                    keywordModerateBlacklist.add(line.toLowerCase());
                    continue;
                }
            }
        }

        // read report-blacklist file:
        final List<String> keywordReportBlacklist = new LinkedList<>();
        final Map<String, Pattern> regexReportBlacklist = new HashMap<>();
        final List<String> userReportBlacklist = new LinkedList<>();
        boolean reportImages = false;
        boolean reportVideos = false;
        boolean reportFiles = false;
        boolean reportLinks = false;
        boolean reportVoice = false;
        final Path reportBlacklist = configFile.getParent().resolve(ModerateBotConstants.REPORT_BLACKLIST_FILENAME);
        if (Files.exists(reportBlacklist)) {
            for (String line : Files.lines(reportBlacklist, StandardCharsets.UTF_8).collect(Collectors.toList())) {

                if (line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("\"#")) {
                    line = line.substring(1);
                }

                if (line.startsWith(ModerateBotConstants.IMAGE_KEYWORD)) {
                    reportImages = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VIDEO_KEYWORD)) {
                    reportVideos = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.FILE_KEYWORD)) {
                    reportFiles = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.LINK_KEYWORD)) {
                    reportLinks = true;
                    continue;
                }
                if (line.startsWith(ModerateBotConstants.VOICE_KEYWORD)) {
                    reportVoice = true;
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.REGEX_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.REGEX_KEYWORD.length()).strip();
                    regexReportBlacklist.put(line, Pattern.compile(line));
                    continue;
                }

                if (line.startsWith(ModerateBotConstants.USER_KEYWORD + " ")) {
                    line = line.substring(ModerateBotConstants.USER_KEYWORD.length()).strip();
                    userReportBlacklist.add(line);
                    continue;
                }

                if (!line.isBlank()) {
                    keywordReportBlacklist.add(line.toLowerCase());
                    continue;
                }
            }
        }

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank()) {
            throw new IllegalArgumentException(
                    "Some mandatory config properties are missing or are invalid! Required are: '"
                            + ModerateBotConstants.CONFIG_PORT + "' and '" + ModerateBotConstants.CONFIG_GROUP + "'");
        }

        if (sleepTimeInSeconds < 0) {
            Util.logWarning("Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        Connection.initSimplexConnection(port);
        return new ModerateBot(Connection.get(port), groupToProcess, sleepTimeInSeconds, contactsForReporting,
                groupsForReporting, blockImages, blockVideos, blockFiles, blockLinks, blockVoice, keywordBlockBlacklist,
                regexBlockBlacklist, userBlockBlacklist, moderateImages, moderateVideos, moderateFiles, moderateLinks,
                moderateVoice, keywordModerateBlacklist, regexModerateBlacklist, userModerateBlacklist, reportImages,
                reportVideos, reportFiles, reportLinks, reportVoice, keywordReportBlacklist, regexReportBlacklist,
                userReportBlacklist);
    }

    public ModerateBot(Connection connection, String groupToProcess, int sleepTimeInSeconds,
            List<String> contactsForReporting, List<String> groupsForReporting, boolean blockImages,
            boolean blockVideos, boolean blockFiles, boolean blockLinks, boolean blockVoice,
            List<String> keywordBlockBlacklist, Map<String, Pattern> regexBlockBlacklist,
            List<String> userBlockBlacklist, boolean moderateImages, boolean moderateVideos, boolean moderateFiles,
            boolean moderateLinks, boolean moderateVoice, List<String> keywordModerateBlacklist,
            Map<String, Pattern> regexModerateBlacklist, List<String> userModerateBlacklist, boolean reportImages,
            boolean reportVideos, boolean reportFiles, boolean reportLinks, boolean reportVoice,
            List<String> keywordReportBlacklist, Map<String, Pattern> regexReportBlacklist,
            List<String> userReportBlacklist) {
        this.connection = connection;
        this.groupToProcess = groupToProcess;
        this.sleepTimeInSeconds = Math.abs(sleepTimeInSeconds);
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
        this.blockImages = blockImages;
        this.blockVideos = blockVideos;
        this.blockFiles = blockFiles;
        this.blockLinks = blockLinks;
        this.blockVoice = blockVoice;
        this.keywordBlockBlacklist = keywordBlockBlacklist;
        this.regexBlockBlacklist = regexBlockBlacklist;
        this.userBlockBlacklist = userBlockBlacklist;
        this.moderateImages = moderateImages;
        this.moderateVideos = moderateVideos;
        this.moderateFiles = moderateFiles;
        this.moderateLinks = moderateLinks;
        this.moderateVoice = moderateVoice;
        this.keywordModerateBlacklist = keywordModerateBlacklist;
        this.regexModerateBlacklist = regexModerateBlacklist;
        this.userModerateBlacklist = userModerateBlacklist;
        this.reportImages = reportImages;
        this.reportVideos = reportVideos;
        this.reportFiles = reportFiles;
        this.reportLinks = reportLinks;
        this.reportVoice = reportVoice;
        this.keywordReportBlacklist = keywordReportBlacklist;
        this.regexReportBlacklist = regexReportBlacklist;
        this.userReportBlacklist = userReportBlacklist;
    }

    @Override
    public void run() {

        Util.log(ModerateBot.class.getSimpleName() + " has started with config: " + ModerateBotConstants.CONFIG_PORT
                + "=" + connection.getPort() + " " + ModerateBotConstants.CONFIG_GROUP + "='" + groupToProcess + "' "
                + ModerateBotConstants.CONFIG_SLEEP_TIME_SECONDS + "=" + sleepTimeInSeconds + " "
                + ModerateBotConstants.CONFIG_REPORT_TO_CONTACTS + "=" + Util.listToString(contactsForReporting) + " "
                + ModerateBotConstants.CONFIG_REPORT_TO_GROUPS + "=" + Util.listToString(groupsForReporting)
                + " blockImages=" + blockImages + " blockVideos=" + blockVideos + " blockFiles=" + blockFiles
                + " blockLinks=" + blockLinks + " blockVoice=" + blockVoice + " keywordBlockBlacklist.size="
                + keywordBlockBlacklist.size() + " regexBlockBlacklist.size=" + regexBlockBlacklist.size()
                + " userBlockBlacklist.size=" + userBlockBlacklist.size() + " moderateImages=" + moderateImages
                + " moderateVideos=" + moderateVideos + " moderateFiles=" + moderateFiles + " moderateLinks="
                + moderateLinks + " moderateVoice=" + moderateVoice + " keywordModerateBlacklist.size="
                + keywordModerateBlacklist.size() + " regexModerateBlacklist.size=" + regexModerateBlacklist.size()
                + " userModerateBlacklist.size=" + userModerateBlacklist.size() + " reportImages=" + reportImages
                + " reportVideos=" + reportVideos + " reportFiles=" + reportFiles + " reportLinks=" + reportLinks
                + " reportVoice=" + reportVoice + " keywordReportBlacklist.size=" + keywordReportBlacklist.size()
                + " regexReportBlacklist.size=" + regexReportBlacklist.size() + " userReportBlacklist.size="
                + userReportBlacklist.size(), connection, contactsForReporting, groupsForReporting);

        final Thread moderateActionThread = new Thread(new ModerateActionRunnable(),
                ModerateBot.class.getSimpleName() + "." + ModerateActionRunnable.class.getSimpleName());
        moderateActionThread.start();

        final List<GroupMessage> alreadyProcessedMessages = new LinkedList<>();

        try {
            while (true) {
                try {

                    for (final GroupMessage message : connection.getNewGroupMessages(groupToProcess,
                            alreadyProcessedMessages, false, contactsForReporting, groupsForReporting)) {

                        try {
                            checkBlocking(message);
                        } catch (final Exception ex) {
                            Util.logError("Unexpected exception: " + ex.toString(), connection, contactsForReporting,
                                    groupsForReporting);
                            ex.printStackTrace();
                        }

                        try {
                            checkModeration(message);
                        } catch (final Exception ex) {
                            Util.logError("Unexpected exception: " + ex.toString(), connection, contactsForReporting,
                                    groupsForReporting);
                            ex.printStackTrace();
                        }

                        try {
                            checkReporting(message);
                        } catch (final Exception ex) {
                            Util.logError("Unexpected exception: " + ex.toString(), connection, contactsForReporting,
                                    groupsForReporting);
                            ex.printStackTrace();
                        }
                    }

                } catch (final Exception ex) {
                    Util.logError("Unexpected exception: " + ex.toString(), connection, contactsForReporting,
                            groupsForReporting);
                    ex.printStackTrace();
                }

                Thread.sleep(sleepTimeInSeconds * 1000);
            }

        } catch (final Exception ex) {
            Util.logError(ModerateBot.class.getSimpleName() + " has finished with error: " + ex.toString(), connection,
                    contactsForReporting, groupsForReporting);
            ex.printStackTrace();
        }
    }

    private void checkBlocking(GroupMessage message) {

        // only process defined message types:
        if (!SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_TEXT.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(message.getType())) {
            return;
        }

        if (blockImages && SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*IMAGE*"));
            return;
        }

        if (blockVideos && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*VIDEO*"));
            return;
        }

        if (blockFiles && SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*FILE*"));
            return;
        }

        if (blockLinks && SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*LINK*"));
            return;
        }

        if (blockVoice && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*VOICE*"));
            return;
        }

        for (final String keyword : keywordBlockBlacklist) {
            if (message.getText().toLowerCase().contains(keyword.toLowerCase())) {
                actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*KEYWORD* '" + keyword + "'"));
                return;
            }
        }

        for (final String regex : regexBlockBlacklist.keySet()) {
            if (regexBlockBlacklist.get(regex).matcher(message.getText()).matches()) {
                actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*REGEX* '" + regex + "'"));
                return;
            }
        }

        for (final String user : userBlockBlacklist) {
            if (message.getUser().getDisplayName().equals(user)) {
                actionQueue.add(new MessageActionItem(ModerateAction.BLOCK, message, "*USER* '" + user + "'"));
                return;
            }
        }
    }

    private void checkModeration(GroupMessage message) {

        // only process defined message types:
        if (!SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_TEXT.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(message.getType())
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(message.getType())) {
            return;
        }

        if (moderateImages && SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*IMAGE*"));
            return;
        }

        if (moderateVideos && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*VIDEO*"));
            return;
        }

        if (moderateFiles && SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*FILE*"));
            return;
        }

        if (moderateLinks && SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*LINK*"));
            return;
        }

        if (moderateVoice && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*VOICE*"));
            return;
        }

        for (final String keyword : keywordModerateBlacklist) {
            if (message.getText().toLowerCase().contains(keyword.toLowerCase())) {
                actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*KEYWORD* '" + keyword + "'"));
                return;
            }
        }

        for (final String regex : regexModerateBlacklist.keySet()) {
            if (regexModerateBlacklist.get(regex).matcher(message.getText()).matches()) {
                actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*REGEX* '" + regex + "'"));
                return;
            }
        }

        for (final String user : userModerateBlacklist) {
            if (message.getUser().getDisplayName().equals(user)) {
                actionQueue.add(new MessageActionItem(ModerateAction.MODERATE, message, "*USER* '" + user + "'"));
                return;
            }
        }
    }

    private void checkReporting(GroupMessage message) {

        if (reportImages && SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*IMAGE*"));
            return;
        }

        if (reportVideos && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*VIDEO*"));
            return;
        }

        if (reportFiles && SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*FILE*"));
            return;
        }

        if (reportLinks && SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*LINK*"));
            return;
        }

        if (reportVoice && SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(message.getType())) {
            actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*VOICE*"));
            return;
        }

        for (final String keyword : keywordReportBlacklist) {
            if (message.getText().toLowerCase().contains(keyword.toLowerCase())) {
                actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*KEYWORD* '" + keyword + "'"));
                return;
            }
        }

        for (final String regex : regexReportBlacklist.keySet()) {
            if (regexReportBlacklist.get(regex).matcher(message.getText()).matches()) {
                actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*REGEX* '" + regex + "'"));
                return;
            }
        }

        for (final String user : userReportBlacklist) {
            if (message.getUser().getDisplayName().equals(user)) {
                actionQueue.add(new MessageActionItem(ModerateAction.REPORT, message, "*USER* '" + user + "'"));
                return;
            }
        }
    }

    private class ModerateActionRunnable implements Runnable {

        @Override
        public void run() {

            try {

                while (true) {
                    final MessageActionItem messageAction = actionQueue.take();
                    try {
                        switch (messageAction.getAction()) {
                        case BLOCK:
                            blockUser(messageAction.getMessage(), messageAction.getReason());
                            break;
                        case MODERATE:
                            moderateMessage(messageAction.getMessage(), messageAction.getReason());
                            break;
                        case REPORT:
                            reportMessage(messageAction.getMessage(), messageAction.getReason());
                            break;
                        default:
                            throw new IllegalStateException("unknown " + ModerateAction.class.getSimpleName() + ": "
                                    + messageAction.getAction());
                        }
                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + ex.toString(), connection, contactsForReporting,
                                groupsForReporting);
                        ex.printStackTrace();
                    }
                }

            } catch (final Exception ex) {

                Util.logError(
                        ModerateActionRunnable.class.getSimpleName() + " has finished with error: " + ex.toString(),
                        connection, contactsForReporting, groupsForReporting);
                ex.printStackTrace();
            }
        }

        private void blockUser(GroupMessage message, String reason) {

            Util.log(
                    "!6 Blocking! member *'" + message.getUser().getDisplayName() + "'* ["
                            + message.getUser().getLocalName() + "] in group *'" + groupToProcess + "'* because of "
                            + reason + " !" + (message.getText().isEmpty() ? "" : " Original message:"),
                    connection, contactsForReporting, groupsForReporting);
            if (!message.getText().isEmpty()) {
                System.out.println(message.getText());
                connection.logToBotAdmins(message.getText(), contactsForReporting, groupsForReporting);
            }

            if (message.getUser().hasPrivileges()) {
                Util.logWarning("Member has privileges: !2 BLOCKING IS REJECTED!", connection, contactsForReporting,
                        groupsForReporting);
                return;
            }

            // Check latest member status to avoid multiple blocking:
            final List<GroupMember> groupMemberList = connection.getGroupMembers(groupToProcess, contactsForReporting,
                    groupsForReporting);
            final GroupMember updatedMember = groupMemberList.get(groupMemberList.indexOf(message.getUser()));
            if (updatedMember.isBlocked()) {
                return;
            }

            connection.blockForAll(groupToProcess, message.getUser().getLocalName(), contactsForReporting,
                    groupsForReporting);
        }

        private void moderateMessage(GroupMessage message, String reason) {

            Util.log(
                    "!4 Moderating! message of member *'" + message.getUser().getDisplayName() + "'* ["
                            + message.getUser().getLocalName() + "] in group *'" + groupToProcess + "'* because of "
                            + reason + " !" + (message.getText().isEmpty() ? "" : " Original message:"),
                    connection, contactsForReporting, groupsForReporting);
            if (!message.getText().isEmpty()) {
                System.out.println(message.getText());
                connection.logToBotAdmins(message.getText(), contactsForReporting, groupsForReporting);
            }

            if (message.getUser().hasPrivileges()) {
                Util.logWarning("Member has privileges: !2 MODERATION IS REJECTED!", connection, contactsForReporting,
                        groupsForReporting);
                return;
            }

            connection.moderateGroupMessage(message.getGroupId(), message.getId(), contactsForReporting,
                    groupsForReporting);
        }

        private void reportMessage(GroupMessage message, String reason) {

            Util.log(
                    "!5 Reporting! message of member *'" + message.getUser().getDisplayName() + "'* ["
                            + message.getUser().getLocalName() + "] in group *'" + groupToProcess + "'* because of "
                            + reason + " !" + (message.getText().isEmpty() ? "" : " Original message:"),
                    connection, contactsForReporting, groupsForReporting);
            if (!message.getText().isEmpty()) {
                System.out.println(message.getText());
                connection.logToBotAdmins(message.getText(), contactsForReporting, groupsForReporting);
            }
        }
    }
}
