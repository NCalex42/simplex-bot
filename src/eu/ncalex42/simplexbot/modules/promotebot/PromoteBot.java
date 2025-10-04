package eu.ncalex42.simplexbot.modules.promotebot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import eu.ncalex42.simplexbot.Start;
import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;

/**
 * This module changes the role of every member in a given group from 'observer'
 * to 'member'.
 */
public class PromoteBot implements Runnable {

    private final SimplexConnection simplexConnection;
    private final String groupToProcess;
    private final List<String> contactsForOutput;
    private final List<String> groupsForOutput;

    private final int[] weekdaysToRun;
    private final int[] hoursToRun;
    private final int sleepTimeInMinutes;
    private final int minWaitTimePerMemberInDays;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;

    private String lastRunDate = "";
    private int lastRunHour = -1;

    public static PromoteBot init(Path configFile) throws IOException {

        int port = -1;
        String groupToProcess = null;
        final List<String> contactsForOutput = new LinkedList<>();
        final List<String> groupsForOutput = new LinkedList<>();
        int[] weekDaysToRun = null;
        int[] hoursToRun = null;
        int sleepTimeInMinutes = -59;
        int minWaitTimePerMemberInDays = -1;
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

            case PromoteBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case PromoteBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case PromoteBotConstants.CONFIG_OUTPUT_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForOutput.add(name.strip());
                    }
                }
                break;

            case PromoteBotConstants.CONFIG_OUTPUT_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForOutput.add(group.strip());
                    }
                }
                break;

            case PromoteBotConstants.CONFIG_WEEKDAYS:
                if (!value.isBlank()) {
                    final String[] days = value.split(",");
                    weekDaysToRun = new int[days.length];
                    for (int i = 0; i < weekDaysToRun.length; i++) {
                        weekDaysToRun[i] = Integer.parseInt(days[i].strip());
                    }
                }
                break;

            case PromoteBotConstants.CONFIG_HOURS:
                if (!value.isBlank()) {
                    final String[] hours = value.split(",");
                    hoursToRun = new int[hours.length];
                    for (int i = 0; i < hoursToRun.length; i++) {
                        hoursToRun[i] = Integer.parseInt(hours[i].strip());
                    }
                }
                break;

            case PromoteBotConstants.CONFIG_SLEEP_TIME_MINUTES:
                if (!value.isBlank()) {
                    sleepTimeInMinutes = Integer.parseInt(value);
                }
                break;

            case PromoteBotConstants.CONFIG_MIN_WAIT_TIME_DAYS:
                if (!value.isBlank()) {
                    minWaitTimePerMemberInDays = Integer.parseInt(value);
                }
                break;

            case PromoteBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names2 = value.split(",");
                for (final String name : names2) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case PromoteBotConstants.CONFIG_REPORT_TO_GROUPS:
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

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank()) {
            throw new IllegalArgumentException("[" + PromoteBot.class.getSimpleName()
                    + "] Some mandatory config properties are missing or are invalid! Required are: '"
                    + PromoteBotConstants.CONFIG_PORT + "' and '" + PromoteBotConstants.CONFIG_GROUP + "'");
        }

        if ((null == weekDaysToRun) || (null == hoursToRun) || (sleepTimeInMinutes < 0)
                || (minWaitTimePerMemberInDays < 0)) {
            Util.logWarning("[" + PromoteBot.class.getSimpleName()
                    + "] Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new PromoteBot(SimplexConnection.get(port), groupToProcess, contactsForOutput, groupsForOutput,
                weekDaysToRun, hoursToRun, sleepTimeInMinutes, minWaitTimePerMemberInDays, contactsForReporting,
                groupsForReporting);
    }

    private PromoteBot(SimplexConnection simplexConnection, String groupToProcess, List<String> contactsForOutput,
            List<String> groupsForOutput, int[] weekdaysToRun, int[] hoursToRun, int sleepTimeInMinutes,
            int minWaitTimePerMemberInDays, List<String> contactsForReporting, List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.contactsForOutput = contactsForOutput;
        this.groupsForOutput = groupsForOutput;
        this.weekdaysToRun = weekdaysToRun;
        this.hoursToRun = hoursToRun;
        this.sleepTimeInMinutes = Math.abs(sleepTimeInMinutes);
        this.minWaitTimePerMemberInDays = Math.abs(minWaitTimePerMemberInDays);
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(PromoteBot.class.getSimpleName() + " " + Start.VERSION + " has started with config: *"
                + PromoteBotConstants.CONFIG_PORT + "*=" + simplexConnection.getPort() + " *"
                + PromoteBotConstants.CONFIG_GROUP + "*='" + groupToProcess + "' *"
                + PromoteBotConstants.CONFIG_OUTPUT_CONTACTS + "*=" + Util.listToString(contactsForOutput) + " *"
                + PromoteBotConstants.CONFIG_OUTPUT_GROUPS + "*=" + Util.listToString(groupsForOutput) + " *"
                + PromoteBotConstants.CONFIG_WEEKDAYS + "*=" + Util.intArrayToString(weekdaysToRun) + " *"
                + PromoteBotConstants.CONFIG_HOURS + "*=" + Util.intArrayToString(hoursToRun) + " *"
                + PromoteBotConstants.CONFIG_SLEEP_TIME_MINUTES + "*=" + sleepTimeInMinutes + " *"
                + PromoteBotConstants.CONFIG_MIN_WAIT_TIME_DAYS + "*=" + minWaitTimePerMemberInDays + " *"
                + PromoteBotConstants.CONFIG_REPORT_TO_CONTACTS + "*=" + Util.listToString(contactsForReporting) + " *"
                + PromoteBotConstants.CONFIG_REPORT_TO_GROUPS + "*=" + Util.listToString(groupsForReporting),
                simplexConnection, contactsForReporting, groupsForReporting);

        try {
            while (true) {

                if (shouldRun()) {

                    Util.log(
                            "Promoting members in group '" + groupToProcess + "' from '" + GroupMember.ROLE_OBSERVER
                                    + "' to '" + GroupMember.ROLE_MEMBER + "'",
                            simplexConnection, contactsForReporting, groupsForReporting);

                    try {

                        int countOfPromotedMembers = 0;
                        final List<GroupMember> members = simplexConnection.getGroupMembers(groupToProcess,
                                contactsForReporting, groupsForReporting);

                        final long nowInSeconds = TimeUtil.getUtcSeconds();
                        for (final GroupMember member : members) {

                            // ignore members that left:
                            if (!member.isPresent()) {
                                continue;
                            }

                            if (!GroupMember.ROLE_OBSERVER.equalsIgnoreCase(member.getRole())) {
                                continue;
                            }

                            final String memberConnectedTs = member.getCreatedAt();
                            if ((null != memberConnectedTs) && !memberConnectedTs.isBlank()) {
                                if (Math.abs(nowInSeconds - TimeUtil.timestampWithNanosToUtcSeconds(
                                        memberConnectedTs)) < (minWaitTimePerMemberInDays * TimeUtil.SECONDS_PER_DAY)) {
                                    continue;
                                }
                            } else {
                                Util.logWarning(
                                        "Member *'" + member.getDisplayName() + "'* [" + member.getLocalName()
                                                + "] in group *'" + groupToProcess + "'* is not connected!",
                                        simplexConnection, contactsForReporting, groupsForReporting);
                            }

                            if (!simplexConnection.changeGroupMemberRole(groupToProcess, member.getLocalName(),
                                    GroupMember.ROLE_MEMBER, contactsForReporting, groupsForReporting)) {
                                continue;
                            }

                            countOfPromotedMembers++;
                        }

                        final String successMessage = "!2 Promoted! " + countOfPromotedMembers + " member(s) in group '"
                                + groupToProcess + "'";
                        Util.log(successMessage, simplexConnection, contactsForReporting, groupsForReporting);
                        if (countOfPromotedMembers > 0) {
                            Util.outputToContactsAndGroups(successMessage, simplexConnection, contactsForOutput,
                                    groupsForOutput, contactsForReporting, groupsForReporting);
                        }

                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                Thread.sleep(sleepTimeInMinutes * TimeUtil.MILLISECONDS_PER_MINUTE);
            }
        } catch (final Exception ex) {
            Util.logError(
                    PromoteBot.class.getSimpleName() + " has finished with error: " + Util.getStackTraceAsString(ex),
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private boolean shouldRun() {
        if (checkWeekdays() && checkHours() && checkLastRun()) {
            lastRunDate = TimeUtil.getDate();
            lastRunHour = TimeUtil.getHourOfDay();
            return true;
        }
        return false;
    }

    private boolean checkWeekdays() {

        if (null == weekdaysToRun) {
            return true;
        }

        for (final int day : weekdaysToRun) {
            if (TimeUtil.getDayOfWeek() == day) {
                return true;
            }
        }

        return false;
    }

    private boolean checkHours() {

        if (null == hoursToRun) {
            return true;
        }

        for (final int hour : hoursToRun) {
            if (TimeUtil.getHourOfDay() == hour) {
                return true;
            }
        }

        return false;
    }

    private boolean checkLastRun() {
        return (lastRunHour != TimeUtil.getHourOfDay()) || !lastRunDate.equals(TimeUtil.getDate());
    }
}
