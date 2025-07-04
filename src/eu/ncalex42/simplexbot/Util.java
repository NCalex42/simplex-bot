package eu.ncalex42.simplexbot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import eu.ncalex42.simplexbot.simplex.SimplexConnection;

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
}
