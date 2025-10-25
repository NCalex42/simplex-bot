package eu.ncalex42.simplexbot.modules.ai.translatebot;

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
import eu.ncalex42.simplexbot.ai.ollama.OllamaConnection;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

/**
 * This module can translate group messages with a locally provided Ollama LLM
 * instance.
 */
public class TranslateBot implements Runnable {

    private final SimplexConnection simplexConnection;
    private final String groupToProcess;
    private final String groupContext;
    private final List<String> contactsForOutput;
    private final List<String> groupsForOutput;

    private final int[] weekdaysToRun;
    private final int[] hoursToRun;
    private final int sleepTimeInSeconds;
    private final int numberOfMessagesToRetrieve;

    private final List<String> ollamaModels;
    private final int ollamaReadTimeoutMinutes;
    private final int ollamaCooldownSeconds;

    private final String secretPromptMarker;
    private final String outputLanguage;
    private final boolean alwaysTranslate;
    private final boolean persistState;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;

    public static TranslateBot init(Path configFile) throws IOException {

        int port = -1;
        String groupToProcess = null;
        String groupContext = "";
        final List<String> contactsForOutput = new LinkedList<>();
        final List<String> groupsForOutput = new LinkedList<>();
        int[] weekDaysToRun = null;
        int[] hoursToRun = null;
        int sleepTimeInSeconds = -30;
        int numberOfMessagesToRetrieve = Math.negateExact(GroupMessage.DEFAULT_NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE);
        final List<String> ollamaModels = new LinkedList<>();
        int ollamaReadTimeoutMinutes = -60;
        int ollamaCooldownSeconds = -1;
        String secretPromptMarker = "";
        String outputLanguage = "";
        String alwaysTranslate = "";
        String persistState = "";
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

            case TranslateBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case TranslateBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case TranslateBotConstants.CONFIG_GROUP_CONTEXT:
                groupContext = value;
                break;

            case TranslateBotConstants.CONFIG_OUTPUT_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForOutput.add(name.strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_OUTPUT_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForOutput.add(group.strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_WEEKDAYS:
                if (!value.isBlank()) {
                    final String[] days = value.split(",");
                    weekDaysToRun = new int[days.length];
                    for (int i = 0; i < weekDaysToRun.length; i++) {
                        weekDaysToRun[i] = Integer.parseInt(days[i].strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_HOURS:
                if (!value.isBlank()) {
                    final String[] hours = value.split(",");
                    hoursToRun = new int[hours.length];
                    for (int i = 0; i < hoursToRun.length; i++) {
                        hoursToRun[i] = Integer.parseInt(hours[i].strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_SLEEP_TIME_SECONDS:
                if (!value.isBlank()) {
                    sleepTimeInSeconds = Integer.parseInt(value);
                }
                break;

            case TranslateBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE:
                if (!value.isBlank()) {
                    numberOfMessagesToRetrieve = Integer.parseInt(value);
                }
                break;

            case TranslateBotConstants.CONFIG_ALWAYS_TRANSLATE:
                alwaysTranslate = value;
                break;

            case TranslateBotConstants.CONFIG_PERSIST_STATE:
                persistState = value;
                break;

            case TranslateBotConstants.CONFIG_OLLAMA_MODELS:
                final String[] models = value.split(",");
                for (final String model : models) {
                    if (!model.isBlank()) {
                        ollamaModels.add(model.strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES:
                if (!value.isBlank()) {
                    ollamaReadTimeoutMinutes = Integer.parseInt(value);
                }
                break;

            case TranslateBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS:
                if (!value.isBlank()) {
                    ollamaCooldownSeconds = Integer.parseInt(value);
                }
                break;

            case TranslateBotConstants.CONFIG_OLLAMA_SECRET_PROMPT_MARKER:
                secretPromptMarker = value;
                break;

            case TranslateBotConstants.CONFIG_OLLAMA_OUTPUT_LANGUAGE:
                outputLanguage = value;
                break;

            case TranslateBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names2 = value.split(",");
                for (final String name : names2) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case TranslateBotConstants.CONFIG_REPORT_TO_GROUPS:
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

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank() || ollamaModels.isEmpty()) {
            throw new IllegalArgumentException("[" + TranslateBot.class.getSimpleName()
                    + "] Some mandatory config properties are missing or are invalid! Required are: '"
                    + TranslateBotConstants.CONFIG_PORT + "', '" + TranslateBotConstants.CONFIG_GROUP + "' and '"
                    + TranslateBotConstants.CONFIG_OLLAMA_MODELS + "'");
        }

        if ((null == weekDaysToRun) || (null == hoursToRun) || (sleepTimeInSeconds < 0)
                || (numberOfMessagesToRetrieve < 0)
                || (!alwaysTranslate.equalsIgnoreCase("true") && !alwaysTranslate.equalsIgnoreCase("false"))
                || (!persistState.equalsIgnoreCase("true") && !persistState.equalsIgnoreCase("false"))
                || (ollamaReadTimeoutMinutes < 0) || (ollamaCooldownSeconds < 0) || secretPromptMarker.isBlank()
                || outputLanguage.isBlank()) {
            Util.logWarning("[" + TranslateBot.class.getSimpleName()
                    + "] Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        if (contactsForOutput.isEmpty() && groupsForOutput.isEmpty()) {
            Util.logWarning(
                    "[" + TranslateBot.class.getSimpleName()
                            + "] No contacts or groups defined for output => all a.i. output will be discarded!",
                    null, null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new TranslateBot(SimplexConnection.get(port), groupToProcess, groupContext, contactsForOutput,
                groupsForOutput, weekDaysToRun, hoursToRun, sleepTimeInSeconds, numberOfMessagesToRetrieve,
                alwaysTranslate, persistState, ollamaModels, ollamaReadTimeoutMinutes, ollamaCooldownSeconds,
                secretPromptMarker, outputLanguage, contactsForReporting, groupsForReporting);
    }

    private TranslateBot(SimplexConnection simplexConnection, String groupToProcess, String groupContext,
            List<String> contactsForOutput, List<String> groupsForOutput, int[] weekdaysToRun, int[] hoursToRun,
            int sleepTimeInSeconds, int numberOfMessagesToRetrieve, String alwaysTranslate, String persistState,
            List<String> ollamaModels, int ollamaReadTimeoutMinutes, int ollamaCooldownSeconds,
            String secretPromptMarker, String outputLanguage, List<String> contactsForReporting,
            List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.groupContext = groupContext;
        this.contactsForOutput = contactsForOutput;
        this.groupsForOutput = groupsForOutput;
        this.weekdaysToRun = weekdaysToRun;
        this.hoursToRun = hoursToRun;
        this.sleepTimeInSeconds = Math.abs(sleepTimeInSeconds);
        this.numberOfMessagesToRetrieve = Math.abs(numberOfMessagesToRetrieve);
        this.alwaysTranslate = alwaysTranslate.equalsIgnoreCase("true") ? true : false;
        this.persistState = persistState.equalsIgnoreCase("true") ? true : false;
        this.ollamaModels = ollamaModels;
        this.ollamaReadTimeoutMinutes = Math.abs(ollamaReadTimeoutMinutes);
        this.ollamaCooldownSeconds = Math.abs(ollamaCooldownSeconds);
        this.secretPromptMarker = secretPromptMarker.isBlank() ? TranslateBotConstants.DEFAULT_PROMPT_MARKER
                : secretPromptMarker;
        this.outputLanguage = outputLanguage.isBlank() ? TranslateBotConstants.DEFAULT_RESPONSE_LANGUAGE
                : outputLanguage;
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(TranslateBot.class.getSimpleName() + " " + Start.VERSION + " has started with config: *"
                + TranslateBotConstants.CONFIG_PORT + "*=" + simplexConnection.getPort() + " *"
                + TranslateBotConstants.CONFIG_GROUP + "*='" + groupToProcess + "' *"
                + TranslateBotConstants.CONFIG_GROUP_CONTEXT + "*='"
                + (groupContext.length() <= 100 ? groupContext : groupContext.substring(0, 100) + " [...]") + "' *"
                + TranslateBotConstants.CONFIG_OUTPUT_CONTACTS + "*=" + Util.listToString(contactsForOutput) + " *"
                + TranslateBotConstants.CONFIG_OUTPUT_GROUPS + "*=" + Util.listToString(groupsForOutput) + " *"
                + TranslateBotConstants.CONFIG_WEEKDAYS + "*=" + Util.intArrayToString(weekdaysToRun) + " *"
                + TranslateBotConstants.CONFIG_HOURS + "*=" + Util.intArrayToString(hoursToRun) + " *"
                + TranslateBotConstants.CONFIG_SLEEP_TIME_SECONDS + "*=" + sleepTimeInSeconds + " *"
                + TranslateBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE + "*=" + numberOfMessagesToRetrieve + " *"
                + TranslateBotConstants.CONFIG_ALWAYS_TRANSLATE + "*=" + alwaysTranslate + " *"
                + TranslateBotConstants.CONFIG_PERSIST_STATE + "*=" + persistState + " *"
                + TranslateBotConstants.CONFIG_OLLAMA_MODELS + "*=" + Util.listToString(ollamaModels) + " *"
                + TranslateBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES + "*=" + ollamaReadTimeoutMinutes + " *"
                + TranslateBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS + "*=" + ollamaCooldownSeconds + " *"
                + TranslateBotConstants.CONFIG_OLLAMA_SECRET_PROMPT_MARKER + "*=" + secretPromptMarker + " *"
                + TranslateBotConstants.CONFIG_OLLAMA_OUTPUT_LANGUAGE + "*=" + outputLanguage + " *"
                + TranslateBotConstants.CONFIG_REPORT_TO_CONTACTS + "*=" + Util.listToString(contactsForReporting)
                + " *" + TranslateBotConstants.CONFIG_REPORT_TO_GROUPS + "*=" + Util.listToString(groupsForReporting),
                simplexConnection, contactsForReporting, groupsForReporting);

        try {

            final List<GroupMessage> alreadyProcessedMessages;
            if (persistState) {
                alreadyProcessedMessages = Util.initCacheFile(
                        Path.of(Start.CONFIG_DIRECTORY, TranslateBotConstants.PROCESSED_MESSAGES_CACHE_FILE),
                        groupToProcess, numberOfMessagesToRetrieve, simplexConnection, contactsForReporting,
                        groupsForReporting);
            } else {
                try {
                    Files.deleteIfExists(
                            Path.of(Start.CONFIG_DIRECTORY, TranslateBotConstants.PROCESSED_MESSAGES_CACHE_FILE));
                } catch (final Exception ex) {
                    Util.logWarning("Unused cache file could not be deleted: " + Util.getStackTraceAsString(ex),
                            simplexConnection, contactsForReporting, groupsForReporting);
                }
                alreadyProcessedMessages = new LinkedList<>();
            }

            while (true) {

                if (shouldRun()) {

                    try {

                        for (final GroupMessage message : simplexConnection.getNewGroupMessages(groupToProcess,
                                alreadyProcessedMessages, false, numberOfMessagesToRetrieve, contactsForReporting,
                                groupsForReporting)) {

                            try {
                                translateMessage(message);
                            } catch (final Exception ex) {
                                Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex),
                                        simplexConnection, contactsForReporting, groupsForReporting);
                            } finally {
                                if (persistState) {
                                    Util.addProcessedMessageToFile(message,
                                            Path.of(Start.CONFIG_DIRECTORY,
                                                    TranslateBotConstants.PROCESSED_MESSAGES_CACHE_FILE),
                                            numberOfMessagesToRetrieve);
                                }
                            }

                        }

                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                Thread.sleep(sleepTimeInSeconds * TimeUtil.MILLISECONDS_PER_SECOND);
            }
        } catch (final Exception ex) {
            Util.logError(
                    TranslateBot.class.getSimpleName() + " has finished with error: " + Util.getStackTraceAsString(ex),
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private void translateMessage(GroupMessage message) {

        if ((null == message.getText()) || message.getText().isBlank()) {
            return;
        }

        final String prompt = generatePrompt(message);
        final String systemPrompt = buildSystemPrompt();

        for (final String model : ollamaModels) {
            final String aiResponse = OllamaConnection.generateResponse(model, systemPrompt, prompt,
                    ollamaReadTimeoutMinutes * TimeUtil.MILLISECONDS_PER_MINUTE, simplexConnection,
                    contactsForReporting, groupsForReporting);

            // cooldown:
            try {
                Thread.sleep(ollamaCooldownSeconds * TimeUtil.MILLISECONDS_PER_SECOND);
            } catch (final InterruptedException ex) {
                Util.logWarning(Util.getStackTraceAsString(ex), simplexConnection, contactsForReporting,
                        groupsForReporting);
            }

            if (null == aiResponse) {
                continue;
            }

            final String sanitizedResponse = sanitizeAiResponse(aiResponse);
            if (!alwaysTranslate
                    && (sanitizedResponse.isBlank() || sanitizedResponse.equals(message.getText().strip()))) {
                continue;
            }

            simplexConnection.sendToContactsAndGroups(
                    "*[" + message.getItemTs() + "]*   _" + message.getMember().getDisplayName() + "_ :\n\n"
                            + sanitizedResponse,
                    contactsForOutput, groupsForOutput, contactsForReporting, groupsForReporting);
        }
    }

    private String generatePrompt(GroupMessage message) {
        return "\n\n" + secretPromptMarker + "\n\n" + message.getText() + "\n\n" + secretPromptMarker + "\n\n"
                + "Remember: " + buildSystemPrompt();
    }

    private String buildSystemPrompt() {
        return (TranslateBotConstants.SYSTEM_PROMPT_PART_1.replace(TranslateBotConstants.LANGUAGE_MARKER,
                outputLanguage)
                + groupContext
                + (alwaysTranslate
                        ? TranslateBotConstants.SYSTEM_PROMPT_PART_2.replace(
                                TranslateBotConstants.ADDITIONAL_INSTRUCTION_MARKER,
                                TranslateBotConstants.ALWAYS_TRANSLATE_INSTRUCTION)
                        : TranslateBotConstants.SYSTEM_PROMPT_PART_2.replace(
                                TranslateBotConstants.ADDITIONAL_INSTRUCTION_MARKER,
                                TranslateBotConstants.ONLY_IF_NECESSARY_INSTRUCTION))
                + (alwaysTranslate
                        ? TranslateBotConstants.EXAMPLE_ALWAYS_TRANSLATE_1
                                + TranslateBotConstants.EXAMPLE_ALWAYS_TRANSLATE_2
                        : TranslateBotConstants.EXAMPLE_ONLY_IF_NECESSARY_TRANSLATE))
                .replace(TranslateBotConstants.DEFAULT_PROMPT_MARKER, secretPromptMarker);
    }

    private String sanitizeAiResponse(String aiResponse) {

        final String[] aiResponseSplit = aiResponse.split("</think>");
        final String aiResponseWithoutThinking = aiResponseSplit[aiResponseSplit.length - 1];
        String sanitizedAiResponse = aiResponseWithoutThinking.replace(secretPromptMarker, "").strip();
        sanitizedAiResponse = sanitizedAiResponse.equals("<empty/nothing>") ? "" : sanitizedAiResponse;
        sanitizedAiResponse = sanitizedAiResponse.equals("<translated text>") ? "" : sanitizedAiResponse;
        return sanitizedAiResponse;
    }

    private boolean shouldRun() {
        if (checkWeekdays() && checkHours()) {
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
}
