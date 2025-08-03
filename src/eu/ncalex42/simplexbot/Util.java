package eu.ncalex42.simplexbot;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

public class Util {

    private enum LogLevel {
        DEBUG, WARNING, ERROR
    }

    public static void log(String message, SimplexConnection simplexConnection, List<String> contacts,
            List<String> groups) {

        final String formattedMessage = formatMessage(message, LogLevel.DEBUG);
        System.out.println(formattedMessage);

        if (null == simplexConnection) {
            return;
        }

        simplexConnection.logToBotAdmins(formattedMessage, contacts, groups);
    }

    public static void logWarning(String message, SimplexConnection simplexConnection, List<String> contacts,
            List<String> groups) {

        final String formattedMessage = formatMessage(message, LogLevel.WARNING);
        System.out.println(formattedMessage);

        if (null == simplexConnection) {
            return;
        }

        simplexConnection.logToBotAdmins(formattedMessage, contacts, groups);
    }

    public static void logError(String message, SimplexConnection simplexConnection, List<String> contacts,
            List<String> groups) {
        final String formattedMessage = formatMessage(message, LogLevel.ERROR);

        System.err.println(formattedMessage);

        if (null == simplexConnection) {
            return;
        }

        simplexConnection.logToBotAdmins(formattedMessage, contacts, groups);
    }

    private static String formatMessage(String message, LogLevel level) {

        final StringBuilder sb = new StringBuilder();

        // timestamp:
        switch (level) {
        case ERROR:
            sb.append("!1 ");
            break;
        case WARNING:
            sb.append("!4 ");
            break;
        default:
            sb.append("*");
        }
        sb.append(TimeUtil.formatTimestamp());

        // level:
        switch (level) {
        case ERROR:
            sb.append(" [ERROR]!");
            break;
        case WARNING:
            sb.append(" [WARN]!");
            break;
        default:
            sb.append("*");
        }

        // module/thread name:
        sb.append(" *");
        sb.append(getThreadName());
        sb.append("* ");

        sb.append(message);

        return sb.toString();
    }

    public static String getThreadName() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    public static String intArrayToString(int[] array) {

        if (null == array) {
            return "null";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < (array.length - 1)) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String listToString(List<String> list) {

        if (null == list) {
            return "null";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < (list.size() - 1)) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String getStackTraceAsString(Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static void trimProcessedMessages(List<GroupMessage> alreadyProcessedMessages,
            int numberOfMessagesToRetrieve) {

        while (alreadyProcessedMessages.size() > (numberOfMessagesToRetrieve + 500)) { // +500 as safety buffer
            alreadyProcessedMessages.remove(0);
        }
    }

    public static List<GroupMessage> initCacheFile(Path fileName, String groupToProcess, int numberOfMessagesToRetrieve,
            SimplexConnection simplexConnection, List<String> contactsForReporting, List<String> groupsForReporting)
            throws IOException {

        if (!Files.exists(fileName)) {
            return createInitialCacheFile(fileName, groupToProcess, numberOfMessagesToRetrieve, simplexConnection,
                    contactsForReporting, groupsForReporting);
        }

        final List<String> content = Files.readAllLines(fileName, StandardCharsets.UTF_8);
        if (2 != content.size()) {
            Util.logWarning("Invalid cache file found, resetting cache: " + fileName.toAbsolutePath(),
                    simplexConnection, contactsForReporting, groupsForReporting);
            return createInitialCacheFile(fileName, groupToProcess, numberOfMessagesToRetrieve, simplexConnection,
                    contactsForReporting, groupsForReporting);
        }

        final int numberFromFile = Integer.parseInt(content.get(0).strip());
        if (numberOfMessagesToRetrieve > numberFromFile) {
            Util.logWarning("Outdated cache file found, resetting cache: " + fileName.toAbsolutePath(),
                    simplexConnection, contactsForReporting, groupsForReporting);
            return createInitialCacheFile(fileName, groupToProcess, numberOfMessagesToRetrieve, simplexConnection,
                    contactsForReporting, groupsForReporting);
        }

        return readAlreadyProcessedMessagesFromFile(fileName);
    }

    private static List<GroupMessage> createInitialCacheFile(Path fileName, String groupToProcess,
            int numberOfMessagesToRetrieve, SimplexConnection simplexConnection, List<String> contactsForReporting,
            List<String> groupsForReporting) throws IOException {

        final List<GroupMessage> pastMessages = new LinkedList<>();
        simplexConnection.getNewGroupMessages(groupToProcess, pastMessages, true, numberOfMessagesToRetrieve,
                contactsForReporting, groupsForReporting);

        writeAlreadyProcessedMessagesToFile(fileName, numberOfMessagesToRetrieve, pastMessages);

        return pastMessages;
    }

    public static List<GroupMessage> readAlreadyProcessedMessagesFromFile(Path fileName) throws IOException {

        final List<GroupMessage> result = new LinkedList<>();

        final String fileContent = Files.readString(fileName, StandardCharsets.UTF_8);
        final String messageCache = fileContent.split("\n", 2)[1];
        for (final String message : messageCache.split(";")) {
            if (message.isBlank()) {
                continue;
            }

            final String[] metaDataPair = message.split(",");
            if ((metaDataPair.length != 2) || metaDataPair[0].isBlank() || metaDataPair[1].isBlank()) {
                continue;
            }

            final long messageId = Long.parseLong(metaDataPair[0].strip());
            final long groupId = Long.parseLong(metaDataPair[1].strip());
            result.add(new GroupMessage(messageId, groupId, GroupMessage.TYPE_UNKNOWN, null, null, null, null));
        }

        return result;
    }

    public static void addProcessedMessageToFile(GroupMessage messageToAdd, Path fileName,
            int numberOfMessagesToRetrieve) throws IOException {

        final List<GroupMessage> alreadyProcessedMessages = readAlreadyProcessedMessagesFromFile(fileName);
        if (!alreadyProcessedMessages.contains(messageToAdd)) {
            alreadyProcessedMessages.add(messageToAdd);
            trimProcessedMessages(alreadyProcessedMessages, numberOfMessagesToRetrieve);
            writeAlreadyProcessedMessagesToFile(fileName, numberOfMessagesToRetrieve, alreadyProcessedMessages);
        }
    }

    private static void writeAlreadyProcessedMessagesToFile(Path fileName, int numberOfMessagesToRetrieve,
            final List<GroupMessage> alreadyProcessedMessages) throws IOException {

        final StringBuilder messageIds = new StringBuilder();
        messageIds.append(numberOfMessagesToRetrieve);
        messageIds.append("\n");
        for (final GroupMessage message : alreadyProcessedMessages) {
            messageIds.append(message.getId());
            messageIds.append(",");
            messageIds.append(message.getGroupId());
            messageIds.append(";");
        }

        Files.writeString(fileName, messageIds, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.SYNC, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
