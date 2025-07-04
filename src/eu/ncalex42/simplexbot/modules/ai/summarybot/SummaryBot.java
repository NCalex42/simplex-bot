package eu.ncalex42.simplexbot.modules.ai.summarybot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.ai.ollama.OllamaConnection;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;
import eu.ncalex42.simplexbot.simplex.model.QuotedGroupMessage;

/**
 * This module can summarize group messages with a locally provided Ollama LLM
 * instance.
 */
public class SummaryBot implements Runnable {

    private final SimplexConnection simplexConnection;
    private final String groupToProcess;
    private final String groupContext;
    private final List<String> contactsForOutput;
    private final List<String> groupsForOutput;

    private final int[] weekdaysToRunDaily;
    private final int[] hoursToRunDaily;
    private final int[] weekdaysToRunWeekly;
    private final int[] hoursToRunWeekly;
    private final int sleepTimeInMinutes;
    private final int numberOfMessagesToRetrieve;
    private final boolean revealModelInOutput;
    private final boolean showAiWarningInOutput;

    private final List<String> ollamaDefaultModels;
    private final List<String> ollamaFallbackModels;
    private final int ollamaReadTimeoutMinutes;
    private final int ollamaCooldownSeconds;
    private final int defaultModelPromptCharacterLimit;
    private final String secretPromptMarker;
    private final String outputLanguage;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;
    private String lastRunDateDaily = "";
    private int lastRunHourDaily = -1;
    private String lastRunDateWeekly = "";
    private int lastRunHourWeekly = -1;

    public static SummaryBot init(Path configFile) throws IOException {

        int port = -1;
        String groupToProcess = null;
        String groupContext = "";
        final List<String> contactsForOutput = new LinkedList<>();
        final List<String> groupsForOutput = new LinkedList<>();
        int[] weekDaysToRunDaily = null;
        int[] hoursToRunDaily = null;
        int[] weekDaysToRunWeekly = null;
        int[] hoursToRunWeekly = null;
        int sleepTimeInMinutes = -59;
        int numberOfMessagesToRetrieve = -3000;
        String revealModelInOutput = "";
        String showAiWarningInOutput = "";
        final List<String> ollamaDefaultModels = new LinkedList<>();
        final List<String> ollamaFallbackModels = new LinkedList<>();
        int ollamaReadTimeoutMinutes = -60;
        int ollamaCooldownSeconds = -1;
        int defaultModelPromptCharacterLimit = -1;
        String secretPromptMarker = "";
        String outputLanguage = "";
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

            case SummaryBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case SummaryBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case SummaryBotConstants.CONFIG_GROUP_CONTEXT:
                groupContext = value;
                break;

            case SummaryBotConstants.CONFIG_OUTPUT_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForOutput.add(name.strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_OUTPUT_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForOutput.add(group.strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_WEEKDAYS_DAILY:
                if (!value.isBlank()) {
                    final String[] days = value.split(",");
                    weekDaysToRunDaily = new int[days.length];
                    for (int i = 0; i < weekDaysToRunDaily.length; i++) {
                        weekDaysToRunDaily[i] = Integer.parseInt(days[i].strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_HOURS_DAILY:
                if (!value.isBlank()) {
                    final String[] hours = value.split(",");
                    hoursToRunDaily = new int[hours.length];
                    for (int i = 0; i < hoursToRunDaily.length; i++) {
                        hoursToRunDaily[i] = Integer.parseInt(hours[i].strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_WEEKDAYS_WEEKLY:
                if (!value.isBlank()) {
                    final String[] days = value.split(",");
                    weekDaysToRunWeekly = new int[days.length];
                    for (int i = 0; i < weekDaysToRunWeekly.length; i++) {
                        weekDaysToRunWeekly[i] = Integer.parseInt(days[i].strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_HOURS_WEEKLY:
                if (!value.isBlank()) {
                    final String[] hours = value.split(",");
                    hoursToRunWeekly = new int[hours.length];
                    for (int i = 0; i < hoursToRunWeekly.length; i++) {
                        hoursToRunWeekly[i] = Integer.parseInt(hours[i].strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_SLEEP_TIME_MINUTES:
                if (!value.isBlank()) {
                    sleepTimeInMinutes = Integer.parseInt(value);
                }
                break;

            case SummaryBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE:
                if (!value.isBlank()) {
                    numberOfMessagesToRetrieve = Integer.parseInt(value);
                }
                break;

            case SummaryBotConstants.CONFIG_REVEAL_MODEL_IN_OUTPUT:
                revealModelInOutput = value;
                break;

            case SummaryBotConstants.CONFIG_SHOW_AI_WARNING_IN_OUTPUT:
                showAiWarningInOutput = value;
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_DEFAULT_MODELS:
                final String[] defaultModels = value.split(",");
                for (final String model : defaultModels) {
                    if (!model.isBlank()) {
                        ollamaDefaultModels.add(model.strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_FALLBACK_MODELS:
                final String[] fallbackModels = value.split(",");
                for (final String model : fallbackModels) {
                    if (!model.isBlank()) {
                        ollamaFallbackModels.add(model.strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES:
                if (!value.isBlank()) {
                    ollamaReadTimeoutMinutes = Integer.parseInt(value);
                }
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS:
                if (!value.isBlank()) {
                    ollamaCooldownSeconds = Integer.parseInt(value);
                }
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_DEFAULT_MODEL_PROMPT_CHARACTER_LIMIT:
                if (!value.isBlank()) {
                    defaultModelPromptCharacterLimit = Integer.parseInt(value);
                }
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_SECRECT_PROMPT_MARKER:
                secretPromptMarker = value;
                break;

            case SummaryBotConstants.CONFIG_OLLAMA_OUTPUT_LANGUAGE:
                outputLanguage = value;
                break;

            case SummaryBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names2 = value.split(",");
                for (final String name : names2) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case SummaryBotConstants.CONFIG_REPORT_TO_GROUPS:
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

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank() || ollamaDefaultModels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Some mandatory config properties are missing or are invalid! Required are: '"
                            + SummaryBotConstants.CONFIG_PORT + "', '" + SummaryBotConstants.CONFIG_GROUP + "' and '"
                            + SummaryBotConstants.CONFIG_OLLAMA_DEFAULT_MODELS + "'");
        }

        if ((null == weekDaysToRunDaily) || (null == hoursToRunDaily) || (null == weekDaysToRunWeekly)
                || (null == hoursToRunWeekly) || (sleepTimeInMinutes < 0) || (numberOfMessagesToRetrieve < 0)
                || (!revealModelInOutput.equalsIgnoreCase("true") && !revealModelInOutput.equalsIgnoreCase("false"))
                || (!showAiWarningInOutput.equalsIgnoreCase("true") && !showAiWarningInOutput.equalsIgnoreCase("false"))
                || (ollamaReadTimeoutMinutes < 0) || (ollamaCooldownSeconds < 0)
                || (defaultModelPromptCharacterLimit < 0) || secretPromptMarker.isBlank() || outputLanguage.isBlank()) {
            Util.logWarning("Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        if (contactsForOutput.isEmpty() && groupsForOutput.isEmpty()) {
            Util.logWarning("No contacts or groups defined for output => all a.i. summaries will be discarded!", null,
                    null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new SummaryBot(SimplexConnection.get(port), groupToProcess, groupContext, contactsForOutput,
                groupsForOutput, weekDaysToRunDaily, hoursToRunDaily, weekDaysToRunWeekly, hoursToRunWeekly,
                sleepTimeInMinutes, numberOfMessagesToRetrieve, revealModelInOutput, showAiWarningInOutput,
                ollamaDefaultModels, ollamaFallbackModels, ollamaReadTimeoutMinutes, ollamaCooldownSeconds,
                defaultModelPromptCharacterLimit, secretPromptMarker, outputLanguage, contactsForReporting,
                groupsForReporting);
    }

    public SummaryBot(SimplexConnection simplexConnection, String groupToProcess, String groupContext,
            List<String> contactsForOutput, List<String> groupsForOutput, int[] weekdaysToRunDaily,
            int[] hoursToRunDaily, int[] weekdaysToRunWeekly, int[] hoursToRunWeekly, int sleepTimeInMinutes,
            int numberOfMessagesToRetrieve, String revealModelInOutput, String showAiWarningInOutput,
            List<String> ollamaDefaultModels, List<String> ollamaFallbackModels, int ollamaReadTimeoutMinutes,
            int ollamaCooldownSeconds, int defaultModelPromptCharacterLimit, String secretPromptMarker,
            String outputLanguage, List<String> contactsForReporting, List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.groupContext = groupContext;
        this.contactsForOutput = contactsForOutput;
        this.groupsForOutput = groupsForOutput;
        this.weekdaysToRunDaily = weekdaysToRunDaily;
        this.hoursToRunDaily = hoursToRunDaily;
        this.weekdaysToRunWeekly = weekdaysToRunWeekly;
        this.hoursToRunWeekly = hoursToRunWeekly;
        this.sleepTimeInMinutes = Math.abs(sleepTimeInMinutes);
        this.numberOfMessagesToRetrieve = Math.abs(numberOfMessagesToRetrieve);
        this.revealModelInOutput = revealModelInOutput.equalsIgnoreCase("false") ? false : true;
        this.showAiWarningInOutput = showAiWarningInOutput.equalsIgnoreCase("false") ? false : true;
        this.ollamaDefaultModels = ollamaDefaultModels;
        this.ollamaFallbackModels = ollamaFallbackModels;
        this.ollamaReadTimeoutMinutes = Math.abs(ollamaReadTimeoutMinutes);
        this.ollamaCooldownSeconds = Math.abs(ollamaCooldownSeconds);
        this.defaultModelPromptCharacterLimit = Math.max(defaultModelPromptCharacterLimit, 0);
        this.secretPromptMarker = secretPromptMarker.isBlank() ? SummaryBotConstants.DEFAULT_PROMPT_MARKER
                : secretPromptMarker;
        this.outputLanguage = outputLanguage.isBlank() ? SummaryBotConstants.DEFAULT_RESPONSE_LANGUAGE : outputLanguage;
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(SummaryBot.class.getSimpleName() + " has started with config: *" + SummaryBotConstants.CONFIG_PORT
                + "*=" + simplexConnection.getPort() + " *" + SummaryBotConstants.CONFIG_GROUP + "*='" + groupToProcess
                + "' *" + SummaryBotConstants.CONFIG_GROUP_CONTEXT + "*='"
                + (groupContext.length() <= 100 ? groupContext : groupContext.substring(0, 100) + " [...]") + "' *"
                + SummaryBotConstants.CONFIG_OUTPUT_CONTACTS + "*=" + Util.listToString(contactsForOutput) + " *"
                + SummaryBotConstants.CONFIG_OUTPUT_GROUPS + "*=" + Util.listToString(groupsForOutput) + " *"
                + SummaryBotConstants.CONFIG_WEEKDAYS_DAILY + "*=" + Util.intArrayToString(weekdaysToRunDaily) + " *"
                + SummaryBotConstants.CONFIG_HOURS_DAILY + "*=" + Util.intArrayToString(hoursToRunDaily) + " *"
                + SummaryBotConstants.CONFIG_WEEKDAYS_WEEKLY + "*=" + Util.intArrayToString(weekdaysToRunWeekly) + " *"
                + SummaryBotConstants.CONFIG_HOURS_WEEKLY + "*=" + Util.intArrayToString(hoursToRunWeekly) + " *"
                + SummaryBotConstants.CONFIG_SLEEP_TIME_MINUTES + "*=" + sleepTimeInMinutes + " *"
                + SummaryBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE + "*=" + numberOfMessagesToRetrieve + " *"
                + SummaryBotConstants.CONFIG_REVEAL_MODEL_IN_OUTPUT + "*=" + revealModelInOutput + " *"
                + SummaryBotConstants.CONFIG_SHOW_AI_WARNING_IN_OUTPUT + "*=" + showAiWarningInOutput + " *"
                + SummaryBotConstants.CONFIG_OLLAMA_DEFAULT_MODELS + "*=" + Util.listToString(ollamaDefaultModels)
                + " *" + SummaryBotConstants.CONFIG_OLLAMA_FALLBACK_MODELS + "*="
                + Util.listToString(ollamaFallbackModels) + " *"
                + SummaryBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES + "*=" + ollamaReadTimeoutMinutes + " *"
                + SummaryBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS + "*=" + ollamaCooldownSeconds + " *"
                + SummaryBotConstants.CONFIG_OLLAMA_DEFAULT_MODEL_PROMPT_CHARACTER_LIMIT + "*="
                + defaultModelPromptCharacterLimit + " *" + SummaryBotConstants.CONFIG_OLLAMA_SECRECT_PROMPT_MARKER
                + "*=" + secretPromptMarker + " *" + SummaryBotConstants.CONFIG_OLLAMA_OUTPUT_LANGUAGE + "*="
                + outputLanguage + " *" + SummaryBotConstants.CONFIG_REPORT_TO_CONTACTS + "*="
                + Util.listToString(contactsForReporting) + " *" + SummaryBotConstants.CONFIG_REPORT_TO_GROUPS + "*="
                + Util.listToString(groupsForReporting), simplexConnection, contactsForReporting, groupsForReporting);

        try {
            while (true) {

                final boolean runDaily = shouldRunDaily();
                final boolean runWeekly = shouldRunWeekly();

                if (runDaily || runWeekly) {

                    try {

                        final List<GroupMessage> messages = simplexConnection.getNewGroupMessages(groupToProcess,
                                new LinkedList<>(), true, numberOfMessagesToRetrieve, contactsForReporting,
                                groupsForReporting);

                        final String timestamp = TimeUtil.formatUtcTimestamp();

                        final StringBuilder dailyPrompt = new StringBuilder();
                        final StringBuilder weeklyPrompt = new StringBuilder();
                        generatePrompts(messages, runDaily ? dailyPrompt : null, runWeekly ? weeklyPrompt : null);

                        generateAiSummaries(dailyPrompt, "!5 day!", timestamp);
                        generateAiSummaries(weeklyPrompt, "!3 week!", timestamp);

                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                Thread.sleep(sleepTimeInMinutes * TimeUtil.MILLISECONDS_PER_MINUTE);
            }
        } catch (final Exception ex) {
            Util.logError(
                    SummaryBot.class.getSimpleName() + " has finished with error: " + Util.getStackTraceAsString(ex),
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private void generatePrompts(List<GroupMessage> messages, StringBuilder dailyPrompt, StringBuilder weeklyPrompt) {

        int dailyCounter = 0;
        int weeklyCounter = 0;

        final StringBuilder tmpDailyPrompt = new StringBuilder();
        final StringBuilder tmpWeeklyPrompt = new StringBuilder();

        for (final GroupMessage message : messages) {

            final String timestamp = message.getItemTs();
            final long tsInSeconds = TimeUtil.timestampToUtcSeconds(timestamp);
            final long nowInSeconds = TimeUtil.getUtcSeconds();
            final long distanceToNow = nowInSeconds - tsInSeconds;
            if (distanceToNow < 0) {
                Util.logError(
                        "Message from the future detected from member '" + message.getMember().getDisplayName() + "' ["
                                + message.getMember().getLocalName() + "] in group '" + groupToProcess + "' !",
                        simplexConnection, contactsForReporting, groupsForReporting);
                continue;
            }

            if ((null != dailyPrompt) && (distanceToNow < TimeUtil.SECONDS_PER_DAY)) {
                tmpDailyPrompt.append(formatMessageForAiSummary(message));
                dailyCounter++;
            }

            if ((null != weeklyPrompt) && (distanceToNow < TimeUtil.SECONDS_PER_WEEK)) {
                tmpWeeklyPrompt.append(formatMessageForAiSummary(message));
                weeklyCounter++;
            }
        }

        if (0 != tmpDailyPrompt.length()) {
            dailyPrompt.append(tmpDailyPrompt);
            dailyPrompt.append("\n\n" + secretPromptMarker + "\n\n");
            dailyPrompt.append("Remember: " + buildSystemPrompt());
        }
        if (0 != tmpWeeklyPrompt.length()) {
            weeklyPrompt.append(tmpWeeklyPrompt);
            weeklyPrompt.append("\n\n" + secretPromptMarker + "\n\n");
            weeklyPrompt.append("Remember: " + buildSystemPrompt());
        }

        Util.log(
                "Generated prompt(s) for *" + dailyCounter + "* messages ("
                        + ((0 == dailyCounter) ? 0 : dailyPrompt.length()) + " characters) of the last !5 day! and *"
                        + weeklyCounter + "* messages (" + ((0 == weeklyCounter) ? 0 : weeklyPrompt.length())
                        + " characters) of the last !3 week! from group *'" + groupToProcess + "'*",
                simplexConnection, contactsForOutput, groupsForOutput);
    }

    private String formatMessageForAiSummary(GroupMessage message) {

        final String messageType = message.getType().strip();
        if (!SimplexConstants.VALUE_MSG_CONTENT_TYPE_TEXT.equals(messageType)
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_IMAGE.equals(messageType)
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VIDEO.equals(messageType)
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_FILE.equals(messageType)
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_LINK.equals(messageType)
                && !SimplexConstants.VALUE_MSG_CONTENT_TYPE_VOICE.equals(messageType)) {
            return "";
        }

        return "\n\n| " + message.getMember().getDisplayName() + " ;<" + messageType + ">; "
                + formatQuotedMessage(message) + "::: \n" + message.getText();
    }

    private String formatQuotedMessage(GroupMessage message) {

        final QuotedGroupMessage quotedMessage = message.getQuotedGroupMessage();
        if (null == quotedMessage) {
            return "";
        }

        final GroupMember member = quotedMessage.getMember();
        final String displayName = null == member ? "" : member.getDisplayName();
        final String textPreview = quotedMessage.getText().length() > 30
                ? quotedMessage.getText().substring(0, 29) + "[...]"
                : quotedMessage.getText();

        return "[-> '" + displayName + "' <" + quotedMessage.getType() + "> \"" + textPreview + "\"]";
    }

    private void generateAiSummaries(StringBuilder promptBuilder, String timeframe, String timestamp) {

        final String prompt = promptBuilder.toString();
        if (prompt.isBlank()) {
            return;
        }

        final boolean fallbackOnly = (prompt.length() > defaultModelPromptCharacterLimit)
                && (defaultModelPromptCharacterLimit > 0);
        final List<String> models = fallbackOnly ? ollamaFallbackModels : ollamaDefaultModels;
        boolean success = false;

        for (final String model : models) {
            success |= generateAiSummaryWithOllama(model, prompt, timeframe, timestamp);
        }

        if (!success && !fallbackOnly) {
            // second try with fallback models:
            for (final String model : ollamaFallbackModels) {
                generateAiSummaryWithOllama(model, prompt, timeframe, timestamp);
            }
        }
    }

    private boolean generateAiSummaryWithOllama(final String model, final String prompt, String timeframe,
            String timestamp) {

        final String aiResponse = OllamaConnection.generateResponse(model,
                buildSystemPrompt() + "\n\n" + secretPromptMarker, prompt,
                ollamaReadTimeoutMinutes * TimeUtil.MILLISECONDS_PER_MINUTE, simplexConnection, contactsForReporting,
                groupsForReporting);

        // cooldown:
        try {
            Thread.sleep(ollamaCooldownSeconds * TimeUtil.MILLISECONDS_PER_SECOND);
        } catch (final InterruptedException ex) {
            Util.logWarning(Util.getStackTraceAsString(ex), simplexConnection, contactsForOutput, groupsForOutput);
        }

        if (null == aiResponse) {
            return false;
        }

        if (showAiWarningInOutput) {
            simplexConnection.sendToContactsAndGroups(SummaryBotConstants.AI_WARNING_MESSAGE, contactsForOutput,
                    groupsForOutput, contactsForReporting, groupsForReporting);
        }

        simplexConnection.sendToContactsAndGroups(
                "*" + timestamp + "*" + "*[* !4 A.I. summary! " + (revealModelInOutput ? "by *" + model + "* " : "")
                        + "of the last " + timeframe + " from group *'" + groupToProcess + "'* *]*\n\n"
                        + sanitizeAiResponse(aiResponse),
                contactsForOutput, groupsForOutput, contactsForReporting, groupsForReporting);

        return true;
    }

    private String buildSystemPrompt() {
        return SummaryBotConstants.SYSTEM_PROMPT_PART_1.replace(SummaryBotConstants.DEFAULT_PROMPT_MARKER,
                secretPromptMarker) + groupContext
                + SummaryBotConstants.SYSTEM_PROMPT_PART_2.replace(SummaryBotConstants.LANGUAGE_MARKER, outputLanguage);
    }

    private String sanitizeAiResponse(String aiResponse) {
        final String[] aiResponseSplit = aiResponse.split("</think>");
        final String aiResponseWithoutThinking = aiResponseSplit[aiResponseSplit.length - 1].strip();
        return aiResponseWithoutThinking.replace("**", "*").replace(secretPromptMarker, "*****");
    }

    private boolean shouldRunDaily() {
        if (checkWeekdays(weekdaysToRunDaily) && checkHours(hoursToRunDaily)
                && checkLastRun(lastRunHourDaily, lastRunDateDaily)) {
            lastRunDateDaily = TimeUtil.getDate();
            lastRunHourDaily = TimeUtil.getHourOfDay();
            return true;
        }
        return false;
    }

    private boolean shouldRunWeekly() {
        if (checkWeekdays(weekdaysToRunWeekly) && checkHours(hoursToRunWeekly)
                && checkLastRun(lastRunHourWeekly, lastRunDateWeekly)) {
            lastRunDateWeekly = TimeUtil.getDate();
            lastRunHourWeekly = TimeUtil.getHourOfDay();
            return true;
        }
        return false;
    }

    private boolean checkWeekdays(int[] weekdaysToRun) {

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

    private boolean checkHours(int[] hoursToRun) {

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

    private boolean checkLastRun(int lastRunHour, String lastRunDate) {
        return (lastRunHour != TimeUtil.getHourOfDay()) || !lastRunDate.equals(TimeUtil.getDate());
    }
}
