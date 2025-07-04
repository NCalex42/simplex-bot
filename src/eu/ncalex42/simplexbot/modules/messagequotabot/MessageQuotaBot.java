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

import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
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

    private final int messageQuotaPerHour;
    private final int messageQuotaPerDay;
    private final int spamQuotaPerHour;
    private final int spamQuotaPerDay;
    private final Map<GroupMember, List<GroupMessage>> quotaRecord = new HashMap<>();
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
        int sleepTimeInSeconds = -30;
        String silentmode = "";
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

            case MessageQuotaBotConstants.CONFIG_SLEEP_TIME_SECONDS:
                if (!value.isBlank()) {
                    sleepTimeInSeconds = Integer.parseInt(value);
                }
                break;

            case MessageQuotaBotConstants.CONFIG_SILENTMODE:
                silentmode = value;
                break;

            case MessageQuotaBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case MessageQuotaBotConstants.CONFIG_REPORT_TO_GROUPS:
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

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank() || (messageQuotaPerHour < 0)
                || (messageQuotaPerDay < 0) || (spamQuotaPerHour < 0) || (spamQuotaPerDay < 0)) {
            throw new IllegalArgumentException(
                    "Some mandatory config properties are missing or are invalid! Required are: '"
                            + MessageQuotaBotConstants.CONFIG_PORT + "', '" + MessageQuotaBotConstants.CONFIG_GROUP
                            + "', '" + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_HOUR + "', '"
                            + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_DAY + "', '"
                            + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_HOUR + "' and '"
                            + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_DAY + "'");
        }

        if ((sleepTimeInSeconds < 0)
                || (!silentmode.equalsIgnoreCase("true") && !silentmode.equalsIgnoreCase("false"))) {
            Util.logWarning("Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new MessageQuotaBot(SimplexConnection.get(port), groupToProcess, messageQuotaPerHour, messageQuotaPerDay,
                spamQuotaPerHour, spamQuotaPerDay, sleepTimeInSeconds, silentmode, contactsForReporting,
                groupsForReporting);
    }

    public MessageQuotaBot(SimplexConnection simplexConnection, String groupToProcess, int messageQuotaPerHour,
            int messageQuotaPerDay, int spamQuotaPerHour, int spamQuotaPerDay, int sleepTimeInSeconds,
            String silentMode, List<String> contactsForReporting, List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.messageQuotaPerHour = messageQuotaPerHour;
        this.messageQuotaPerDay = messageQuotaPerDay;
        this.spamQuotaPerHour = spamQuotaPerHour;
        this.spamQuotaPerDay = spamQuotaPerDay;
        this.sleepTimeInSeconds = Math.abs(sleepTimeInSeconds);
        this.silentMode = silentMode.equalsIgnoreCase("false") ? false : true;
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(MessageQuotaBot.class.getSimpleName() + " has started with config: *"
                + MessageQuotaBotConstants.CONFIG_PORT + "*=" + simplexConnection.getPort() + " *"
                + MessageQuotaBotConstants.CONFIG_GROUP + "*='" + groupToProcess + "' *"
                + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_HOUR + "*=" + messageQuotaPerHour + " *"
                + MessageQuotaBotConstants.CONFIG_MESSAGE_QUOTA_PER_DAY + "*=" + messageQuotaPerDay + " *"
                + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_HOUR + "*=" + spamQuotaPerHour + " *"
                + MessageQuotaBotConstants.CONFIG_SPAM_QUOTA_PER_DAY + "*=" + spamQuotaPerDay + " *"
                + MessageQuotaBotConstants.CONFIG_SLEEP_TIME_SECONDS + "*=" + sleepTimeInSeconds + " *"
                + MessageQuotaBotConstants.CONFIG_SILENTMODE + "*=" + silentMode + " *"
                + MessageQuotaBotConstants.CONFIG_REPORT_TO_CONTACTS + "*=" + Util.listToString(contactsForReporting)
                + " *" + MessageQuotaBotConstants.CONFIG_REPORT_TO_GROUPS + "*="
                + Util.listToString(groupsForReporting), simplexConnection, contactsForReporting, groupsForReporting);

        final List<GroupMessage> alreadyProcessedMessages = new LinkedList<>();

        try {
            while (true) {
                try {

                    final List<GroupMessage> newMessages = simplexConnection.getNewGroupMessages(groupToProcess,
                            alreadyProcessedMessages, true, contactsForReporting, groupsForReporting);

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
            int messageCountWithinHour = 0;
            int messageCountWithinDay = 0;
            final long nowInSeconds = TimeUtil.getUtcSeconds();
            final Map<String, Integer> spamCountWithinHourMap = new HashMap<>();
            final Map<String, Integer> spamCountWithinDayMap = new HashMap<>();

            for (int i = 0; i < messageListOfMember.size(); i++) {

                final String messageText = messageListOfMember.get(i).getText();
                final String timestamp = messageListOfMember.get(i).getItemTs();
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
                    increaseSpamCounter(messageText, spamCountWithinHourMap);
                }

                if (distanceToNow < TimeUtil.SECONDS_PER_DAY) {
                    messageCountWithinDay++;
                    increaseSpamCounter(messageText, spamCountWithinDayMap);
                } else {
                    itemsToDelete.add(i);
                }
            }

            // check spam count:
            int maxSpamCountWithinHour = 0;
            for (final Integer spamCount : spamCountWithinHourMap.values()) {
                maxSpamCountWithinHour = (spamCount > maxSpamCountWithinHour) ? spamCount : maxSpamCountWithinHour;
            }
            int maxSpamCountWithinDay = 0;
            for (final Integer spamCount : spamCountWithinDayMap.values()) {
                maxSpamCountWithinDay = (spamCount > maxSpamCountWithinDay) ? spamCount : maxSpamCountWithinDay;
            }
            final boolean spam = (maxSpamCountWithinHour > spamQuotaPerHour)
                    || (maxSpamCountWithinDay > spamQuotaPerDay);

            // check quota:
            if ((messageCountWithinHour > messageQuotaPerHour) || (messageCountWithinDay > messageQuotaPerDay)
                    || spam) {
                if (member.isPresent() && !member.hasPrivileges()) {
                    if (!GroupMember.ROLE_OBSERVER.equals(member.getRole())) {
                        downgradeMember(member, messageCountWithinHour, messageCountWithinDay, maxSpamCountWithinHour,
                                maxSpamCountWithinDay, spam);
                    } else {
                        Util.logWarning(
                                "Member *'" + member.getDisplayName() + "'* [" + member.getLocalName() + "] in group *'"
                                        + groupToProcess + "'* is already downgraded: messageCountWithinHour="
                                        + messageCountWithinHour + " messageCountWithinDay=" + messageCountWithinDay
                                        + " maxSpamCountWithinHour=" + maxSpamCountWithinHour
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
            int maxSpamCountWithinHour, int maxSpamCountWithinDay, boolean spam) {

        Util.log(
                "!6 Downgrading! member *'" + member.getDisplayName() + "'* [" + member.getLocalName() + "] in group *'"
                        + groupToProcess + "'* to *OBSERVER* : messageCountWithinHour=" + messageCountWithinHour
                        + " messageCountWithinDay=" + messageCountWithinDay + " maxSpamCountWithinHour="
                        + maxSpamCountWithinHour + " maxSpamCountWithinDay=" + maxSpamCountWithinDay,
                simplexConnection, contactsForReporting, groupsForReporting);

        if (!simplexConnection.changeGroupMemberRole(groupToProcess, member.getLocalName(), GroupMember.ROLE_OBSERVER,
                contactsForReporting, groupsForReporting)) {
            return;
        }

        if (!silentMode) {
            final String reason = spam ? "Spam detected" : "Message quota reached";
            simplexConnection.sendToGroup(groupToProcess,
                    "!1 " + reason + "! by member '" + member.getDisplayName() + "' => downgrading to 'observer'!",
                    contactsForReporting, groupsForReporting);
        }
    }
}
