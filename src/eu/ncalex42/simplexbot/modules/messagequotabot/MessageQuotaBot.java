package eu.ncalex42.simplexbot.modules.messagequotabot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import eu.ncalex42.simplexbot.Start;
import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

/**
 * This module can downgrade members to observers if they exceed their message
 * quota.
 */
public class MessageQuotaBot implements Runnable {

    private final SimplexConnection simplexConnection;
    private final String groupToProcess;
    private final int sleepTimeInSeconds;
    private final int numberOfMessagesToRetrieve;

    private final int messageQuotaPerHour;
    private final int messageQuotaPerDay;
    private final int mediaQuotaPerHour;
    private final int mediaQuotaPerDay;
    private final int spamQuotaPerHour;
    private final int spamQuotaPerDay;
    private final Map<GroupMember, List<GroupMessage>> quotaRecord = new HashMap<>();
    private final List<String> contactsForOutput;
    private final List<String> groupsForOutput;
    private final boolean silentMode;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;

    public static MessageQuotaBot init(Path configFile) throws IOException {

        // read config file:
        int port = -1;
        String groupToProcess = null;
        int messageQuotaPerHour = -1;
        int messageQuotaPerDay = -1;
        int spamQuotaPerHour = -1;
        int spamQuotaPerDay = -1;
        int mediaQuotaPerHour = -1;
        int mediaQuotaPerDay = -1;
        final List<String> contactsForOutput = new LinkedList<>();
        final List<String> groupsForOutput = new LinkedList<>();
        int sleepTimeInSeconds = -30;
        String silentmode = "";
        int numberOfMessagesToRetrieve = Math.negateExact(GroupMessage.DEFAULT_NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE);
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

            case MessageQuotaBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case MessageQuotaBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_HOUR:
                messageQuotaPerHour = Integer.parseInt(value);
                break;

            case MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_DAY:
                messageQuotaPerDay = Integer.parseInt(value);
                break;

            case MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_HOUR:
                spamQuotaPerHour = Integer.parseInt(value);
                break;

            case MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_DAY:
                spamQuotaPerDay = Integer.parseInt(value);
                break;

            case MessageQuotaBotConstants.CONFIG_MEDIA_QUOTA_PER_HOUR:
                if (!value.isBlank()) {
                    mediaQuotaPerHour = Integer.parseInt(value);
                }
                break;

            case MessageQuotaBotConstants.CONFIG_MEDIA_QUOTA_PER_DAY:
                if (!value.isBlank()) {
                    mediaQuotaPerDay = Integer.parseInt(value);
                }
                break;

            case MessageQuotaBotConstants.CONFIG_OUTPUT_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForOutput.add(name.strip());
                    }
                }
                break;

            case MessageQuotaBotConstants.CONFIG_OUTPUT_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForOutput.add(group.strip());
                    }
                }
                break;

            case MessageQuotaBotConstants.CONFIG_SLEEP_TIME_SECONDS:
                if (!value.isBlank()) {
                    sleepTimeInSeconds = Integer.parseInt(value);
                }
                break;

            case MessageQuotaBotConstants.CONFIG_SILENTMODE:
                silentmode = value;
                break;

            case MessageQuotaBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE:
                if (!value.isBlank()) {
                    numberOfMessagesToRetrieve = Integer.parseInt(value);
                }
                break;

            case MessageQuotaBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names2 = value.split(",");
                for (final String name : names2) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case MessageQuotaBotConstants.CONFIG_REPORT_TO_GROUPS:
                final String[] groups2 = value.split(",");
                for (final String group : groups2) {
                    if (!group.isBlank()) {
                        groupsForReporting.add(group.strip());
                    }
                }
                break;

            default: // ignore
            }
        }

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank() || (messageQuotaPerHour < 0)
                || (messageQuotaPerDay < 0) || (spamQuotaPerHour < 0) || (spamQuotaPerDay < 0)) {
            throw new IllegalArgumentException("[" + MessageQuotaBot.class.getSimpleName()
                    + "] Some mandatory config properties are missing or are invalid! Required are: '"
                    + MessageQuotaBotConstants.CONFIG_PORT + "', '" + MessageQuotaBotConstants.CONFIG_GROUP + "', '"
                    + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_HOUR + "', '"
                    + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_DAY + "', '"
                    + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_HOUR + "' and '"
                    + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_DAY + "'");
        }

        if ((mediaQuotaPerHour < 0) || (mediaQuotaPerDay < 0) || (sleepTimeInSeconds < 0)
                || (!silentmode.equalsIgnoreCase("true") && !silentmode.equalsIgnoreCase("false"))
                || (numberOfMessagesToRetrieve < 0)) {
            Util.logWarning("[" + MessageQuotaBot.class.getSimpleName()
                    + "] Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new MessageQuotaBot(SimplexConnection.get(port), groupToProcess, messageQuotaPerHour, messageQuotaPerDay,
                mediaQuotaPerHour, mediaQuotaPerDay, spamQuotaPerHour, spamQuotaPerDay, contactsForOutput,
                groupsForOutput, sleepTimeInSeconds, silentmode, numberOfMessagesToRetrieve, contactsForReporting,
                groupsForReporting);
    }

    private MessageQuotaBot(SimplexConnection simplexConnection, String groupToProcess, int messageQuotaPerHour,
            int messageQuotaPerDay, int mediaQuotaPerHour, int mediaQuotaPerDay, int spamQuotaPerHour,
            int spamQuotaPerDay, List<String> contactsForOutput, List<String> groupsForOutput, int sleepTimeInSeconds,
            String silentMode, int numberOfMessagesToRetrieve, List<String> contactsForReporting,
            List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.messageQuotaPerHour = messageQuotaPerHour;
        this.messageQuotaPerDay = messageQuotaPerDay;
        this.mediaQuotaPerHour = mediaQuotaPerHour < 0 ? Integer.MAX_VALUE : mediaQuotaPerHour;
        this.mediaQuotaPerDay = mediaQuotaPerDay < 0 ? Integer.MAX_VALUE : mediaQuotaPerDay;
        this.spamQuotaPerHour = spamQuotaPerHour;
        this.spamQuotaPerDay = spamQuotaPerDay;
        this.contactsForOutput = contactsForOutput;
        this.groupsForOutput = groupsForOutput;
        this.sleepTimeInSeconds = Math.abs(sleepTimeInSeconds);
        this.silentMode = silentMode.equalsIgnoreCase("false") ? false : true;
        this.numberOfMessagesToRetrieve = Math.abs(numberOfMessagesToRetrieve);
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(MessageQuotaBot.class.getSimpleName() + " " + Start.VERSION + " has started with config: *"
                + MessageQuotaBotConstants.CONFIG_PORT + "*=" + simplexConnection.getPort() + " *"
                + MessageQuotaBotConstants.CONFIG_GROUP + "*='" + groupToProcess + "' *"
                + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_HOUR + "*=" + messageQuotaPerHour + " *"
                + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_DAY + "*=" + messageQuotaPerDay + " *"
                + MessageQuotaBotConstants.CONFIG_MEDIA_QUOTA_PER_HOUR + "*="
                + (Integer.MAX_VALUE == mediaQuotaPerHour ? "n/a" : mediaQuotaPerHour) + " *"
                + MessageQuotaBotConstants.CONFIG_MEDIA_QUOTA_PER_DAY + "*="
                + (Integer.MAX_VALUE == mediaQuotaPerDay ? "n/a" : mediaQuotaPerDay) + " *"
                + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_HOUR + "*=" + spamQuotaPerHour + " *"
                + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_DAY + "*=" + spamQuotaPerDay + " *"
                + MessageQuotaBotConstants.CONFIG_OUTPUT_CONTACTS + "*=" + Util.listToString(contactsForOutput) + " *"
                + MessageQuotaBotConstants.CONFIG_OUTPUT_GROUPS + "*=" + Util.listToString(groupsForOutput) + " *"
                + MessageQuotaBotConstants.CONFIG_SLEEP_TIME_SECONDS + "*=" + sleepTimeInSeconds + " *"
                + MessageQuotaBotConstants.CONFIG_SILENTMODE + "*=" + silentMode + " *"
                + MessageQuotaBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE + "*=" + numberOfMessagesToRetrieve
                + " *" + MessageQuotaBotConstants.CONFIG_REPORT_TO_CONTACTS + "*="
                + Util.listToString(contactsForReporting) + " *" + MessageQuotaBotConstants.CONFIG_REPORT_TO_GROUPS
                + "*=" + Util.listToString(groupsForReporting), simplexConnection, contactsForReporting,
                groupsForReporting);

        final List<GroupMessage> alreadyProcessedMessages = new LinkedList<>();

        try {
            while (true) {
                try {

                    final List<GroupMessage> newMessages = simplexConnection.getNewGroupMessages(groupToProcess,
                            alreadyProcessedMessages, true, numberOfMessagesToRetrieve, contactsForReporting,
                            groupsForReporting);

                    if (!newMessages.isEmpty()) {
                        for (final GroupMessage message : newMessages) {

                            try {
                                addNewMessage(message);
                            } catch (final Exception ex) {
                                Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex),
                                        simplexConnection, contactsForReporting, groupsForReporting);
                            }
                        }

                        processMessages();
                    }

                } catch (final Exception ex) {
                    Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                            contactsForReporting, groupsForReporting);
                }

                Thread.sleep(sleepTimeInSeconds * TimeUtil.MILLISECONDS_PER_SECOND);
            }

        } catch (final Exception ex) {
            Util.logError(
                    MessageQuotaBot.class.getSimpleName() + " has finished with error: "
                            + Util.getStackTraceAsString(ex),
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private void addNewMessage(GroupMessage message) {

        final GroupMember member = message.getMember();
        List<GroupMessage> messageListOfMember = quotaRecord.get(member);
        if (null == messageListOfMember) {
            messageListOfMember = new LinkedList<>();
        }

        messageListOfMember.add(message);

        quotaRecord.remove(member); // remove old member with possibly deprecated group role
        quotaRecord.put(member, messageListOfMember); // add member with current group role and updated message list
    }

    private void processMessages() {

        final List<GroupMember> membersToClear = new LinkedList<>();
        for (final GroupMember member : quotaRecord.keySet()) {

            final List<GroupMessage> messageListOfMember = quotaRecord.get(member);
            final List<Integer> itemsToDelete = new LinkedList<>();
            final long nowInSeconds = TimeUtil.getUtcSeconds();
            int messageCountWithinHour = 0;
            int messageCountWithinDay = 0;
            int mediaCountWithinHour = 0;
            int mediaCountWithinDay = 0;
            final Map<String, Integer> spamCountWithinHourMap = new HashMap<>();
            final Map<String, Integer> spamCountWithinDayMap = new HashMap<>();

            for (int i = 0; i < messageListOfMember.size(); i++) {

                final String messageText = messageListOfMember.get(i).getText();
                final String timestamp = messageListOfMember.get(i).getItemTs();
                final String messageType = messageListOfMember.get(i).getType();
                final long tsInSeconds = TimeUtil.timestampToUtcSeconds(timestamp);
                final long distanceToNow = nowInSeconds - tsInSeconds;
                if (distanceToNow < 0) {
                    Util.logError(
                            "Message from the future detected from member '" + member.getDisplayName() + "' ["
                                    + member.getLocalName() + "] in group '" + groupToProcess + "' !",
                            simplexConnection, contactsForReporting, groupsForReporting);
                }

                if (distanceToNow < TimeUtil.SECONDS_PER_HOUR) {

                    messageCountWithinHour++;

                    mediaCountWithinHour = increaseMediaCounter(messageType, mediaCountWithinHour);

                    increaseSpamCounter(messageText, spamCountWithinHourMap);
                }

                if (distanceToNow < TimeUtil.SECONDS_PER_DAY) {

                    messageCountWithinDay++;

                    mediaCountWithinDay = increaseMediaCounter(messageType, mediaCountWithinDay);

                    increaseSpamCounter(messageText, spamCountWithinDayMap);
                } else {
                    itemsToDelete.add(i);
                }
            }

            // check message quota:
            final boolean messageQuotaReached = (messageCountWithinHour > messageQuotaPerHour)
                    || (messageCountWithinDay > messageQuotaPerDay);

            // check media quota:
            final boolean mediaQuotaReached = (mediaCountWithinHour > mediaQuotaPerHour)
                    || (mediaCountWithinDay > mediaQuotaPerDay);

            // check spam quota:
            int maxSpamCountWithinHour = 0;
            for (final Integer spamCount : spamCountWithinHourMap.values()) {
                maxSpamCountWithinHour = (spamCount > maxSpamCountWithinHour) ? spamCount : maxSpamCountWithinHour;
            }
            int maxSpamCountWithinDay = 0;
            for (final Integer spamCount : spamCountWithinDayMap.values()) {
                maxSpamCountWithinDay = (spamCount > maxSpamCountWithinDay) ? spamCount : maxSpamCountWithinDay;
            }
            final boolean spamQuotaReached = (maxSpamCountWithinHour > spamQuotaPerHour)
                    || (maxSpamCountWithinDay > spamQuotaPerDay);

            if (messageQuotaReached || mediaQuotaReached || spamQuotaReached) {
                if (member.isPresent() && !member.hasPrivileges()) {
                    if (!GroupMember.ROLE_OBSERVER.equals(member.getRole())) {
                        downgradeMember(member, messageCountWithinHour, messageCountWithinDay, mediaCountWithinHour,
                                mediaCountWithinDay, maxSpamCountWithinHour, maxSpamCountWithinDay, spamQuotaReached,
                                mediaQuotaReached);
                    } else {
                        Util.logWarning(
                                "Member *'" + member.getDisplayName() + "'* [" + member.getLocalName() + "] in group *'"
                                        + groupToProcess + "'* is already downgraded: messageCountWithinHour="
                                        + messageCountWithinHour + " messageCountWithinDay=" + messageCountWithinDay
                                        + " mediaCountWithinHour=" + mediaCountWithinHour + " mediaCountWithinDay="
                                        + mediaCountWithinDay + " maxSpamCountWithinHour=" + maxSpamCountWithinHour
                                        + " maxSpamCountWithinDay=" + maxSpamCountWithinDay,
                                simplexConnection, contactsForReporting, groupsForReporting);
                    }
                }

                membersToClear.add(member);

            } else {

                // remove obsolete messages from the list:
                Collections.reverse(itemsToDelete);
                for (final int i : itemsToDelete) {
                    messageListOfMember.remove(i);
                }

                if (messageListOfMember.isEmpty()) {
                    membersToClear.add(member);
                }
            }
        }

        for (final GroupMember member : membersToClear) {
            quotaRecord.remove(member);
        }
    }

    private int increaseMediaCounter(String messageType, int mediaCounter) {

        if (SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equalsIgnoreCase(messageType)
                || SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equalsIgnoreCase(messageType)
                || SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equalsIgnoreCase(messageType)
                || SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equalsIgnoreCase(messageType)
                || SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equalsIgnoreCase(messageType)) {

            return ++mediaCounter;
        }

        return mediaCounter;
    }

    private void increaseSpamCounter(String messageText, Map<String, Integer> spamCountMap) {

        if ((null == messageText) || messageText.isEmpty()) {
            return;
        }

        if (null == spamCountMap.get(messageText)) {
            spamCountMap.put(messageText, 1);
        } else {
            spamCountMap.put(messageText, spamCountMap.get(messageText) + 1);
        }
    }

    private void downgradeMember(GroupMember member, int messageCountWithinHour, int messageCountWithinDay,
            int mediaCountWithinHour, int mediaCountWithinDay, int maxSpamCountWithinHour, int maxSpamCountWithinDay,
            boolean spamQuotaReached, boolean mediaQuotaReached) {

        final String downgradeMessage = "!6 Downgrading! member *'" + member.getDisplayName() + "'* ["
                + member.getLocalName() + "] in group *'" + groupToProcess
                + "'* to *OBSERVER* : messageCountWithinHour=" + messageCountWithinHour + " messageCountWithinDay="
                + messageCountWithinDay + " mediaCountWithinHour=" + mediaCountWithinHour + " mediaCountWithinDay="
                + mediaCountWithinDay + " maxSpamCountWithinHour=" + maxSpamCountWithinHour + " maxSpamCountWithinDay="
                + maxSpamCountWithinDay;
        Util.log(downgradeMessage, simplexConnection, contactsForReporting, groupsForReporting);
        Util.outputToContactsAndGroups(downgradeMessage, simplexConnection, contactsForOutput, groupsForOutput,
                contactsForReporting, groupsForReporting);

        if (!simplexConnection.changeGroupMemberRole(groupToProcess, member.getLocalName(), GroupMember.ROLE_OBSERVER,
                contactsForReporting, groupsForReporting)) {
            return;
        }

        if (!silentMode) {
            final String reason;
            if (spamQuotaReached) {
                reason = "Spam detected";
            } else if (mediaQuotaReached) {
                reason = "Media quota reached";
            } else {
                reason = "Message quota reached";
            }

            simplexConnection.sendToGroup(groupToProcess,
                    "!1 " + reason + "! by member '" + member.getDisplayName() + "' => downgrading to 'observer'!",
                    contactsForReporting, groupsForReporting);
        }
    }
}
