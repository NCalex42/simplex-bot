package eu.ncalex42.simplexbot.modules.ai.moderatebot;

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
import eu.ncalex42.simplexbot.ai.ollama.OllamaConstants;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

/**
 * This module can report/moderate messages and downgrade/block group members
 * based on evaluation from a locally provided Ollama LLM instance about given
 * topics/queries from a config file.
 */
public class AiModerateBot implements Runnable {

    private final SimplexConnection simplexConnection;
    private final String groupToProcess;
    private final String groupContext;
    private final List<String> contactsForOutput;
    private final List<String> groupsForOutput;

    private final int[] weekdaysToRun;
    private final int[] hoursToRun;
    private final int sleepTimeInSeconds;
    private final int numberOfMessagesToRetrieve;

    private final List<Topic> topics;

    private final String ollamaPort;
    private final List<String> ollamaModels;
    private final int ollamaReadTimeoutMinutes;
    private final int ollamaCooldownSeconds;
    private final int maxPromptCharacterLimit;

    private final String secretPromptMarker;
    private final boolean persistState;

    private final List<String> contactsForReporting;
    private final List<String> groupsForReporting;

    public static AiModerateBot init(Path configFile) throws IOException {

        int port = -1;
        String groupToProcess = null;
        String groupContext = "";
        final List<String> contactsForOutput = new LinkedList<>();
        final List<String> groupsForOutput = new LinkedList<>();
        int[] weekDaysToRun = null;
        int[] hoursToRun = null;
        int sleepTimeInSeconds = -30;
        int numberOfMessagesToRetrieve = Math.negateExact(GroupMessage.DEFAULT_NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE);
        String ollamaPort = "";
        final List<String> ollamaModels = new LinkedList<>();
        int ollamaReadTimeoutMinutes = -60;
        int ollamaCooldownSeconds = -1;
        int maxPromptCharacterLimit = -1;
        String secretPromptMarker = "";
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

            case AiModerateBotConstants.CONFIG_PORT:
                port = Integer.parseInt(value);
                break;

            case AiModerateBotConstants.CONFIG_GROUP:
                groupToProcess = value;
                break;

            case AiModerateBotConstants.CONFIG_GROUP_CONTEXT:
                groupContext = value;
                break;

            case AiModerateBotConstants.CONFIG_OUTPUT_CONTACTS:
                final String[] names = value.split(",");
                for (final String name : names) {
                    if (!name.isBlank()) {
                        contactsForOutput.add(name.strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_OUTPUT_GROUPS:
                final String[] groups = value.split(",");
                for (final String group : groups) {
                    if (!group.isBlank()) {
                        groupsForOutput.add(group.strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_WEEKDAYS:
                if (!value.isBlank()) {
                    final String[] days = value.split(",");
                    weekDaysToRun = new int[days.length];
                    for (int i = 0; i < weekDaysToRun.length; i++) {
                        weekDaysToRun[i] = Integer.parseInt(days[i].strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_HOURS:
                if (!value.isBlank()) {
                    final String[] hours = value.split(",");
                    hoursToRun = new int[hours.length];
                    for (int i = 0; i < hoursToRun.length; i++) {
                        hoursToRun[i] = Integer.parseInt(hours[i].strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_SLEEP_TIME_SECONDS:
                if (!value.isBlank()) {
                    sleepTimeInSeconds = Integer.parseInt(value);
                }
                break;

            case AiModerateBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE:
                if (!value.isBlank()) {
                    numberOfMessagesToRetrieve = Integer.parseInt(value);
                }
                break;

            case AiModerateBotConstants.CONFIG_PERSIST_STATE:
                persistState = value;
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_PORT:
                ollamaPort = value;
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_MODELS:
                final String[] models = value.split(",");
                for (final String model : models) {
                    if (!model.isBlank()) {
                        ollamaModels.add(model.strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES:
                if (!value.isBlank()) {
                    ollamaReadTimeoutMinutes = Integer.parseInt(value);
                }
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS:
                if (!value.isBlank()) {
                    ollamaCooldownSeconds = Integer.parseInt(value);
                }
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_MAX_PROMPT_CHARACTER_LIMIT:
                if (!value.isBlank()) {
                    maxPromptCharacterLimit = Integer.parseInt(value);
                }
                break;

            case AiModerateBotConstants.CONFIG_OLLAMA_SECRET_PROMPT_MARKER:
                secretPromptMarker = value;
                break;

            case AiModerateBotConstants.CONFIG_REPORT_TO_CONTACTS:
                final String[] names2 = value.split(",");
                for (final String name : names2) {
                    if (!name.isBlank()) {
                        contactsForReporting.add(name.strip());
                    }
                }
                break;

            case AiModerateBotConstants.CONFIG_REPORT_TO_GROUPS:
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

        // read topics file containing thresholds:
        final List<Topic> topicsList = new LinkedList<>();
        final Path topicsFile = configFile.getParent().resolve(AiModerateBotConstants.TOPICS_FILE_NAME);
        if (Files.exists(topicsFile)) {
            for (final String line : Files.lines(topicsFile, StandardCharsets.UTF_8).collect(Collectors.toList())) {

                final String[] split = line.split(";", 2);
                if (2 != split.length) {
                    continue;
                }

                final String[] thresholds = split[0].split(",");
                if (4 != thresholds.length) {
                    continue;
                }

                int reportingThreshold;
                int moderationThreshold;
                int downgradingThreshold;
                int blockingThreshold;

                try {
                    reportingThreshold = Integer.parseInt(thresholds[0].strip());
                    moderationThreshold = Integer.parseInt(thresholds[1].strip());
                    downgradingThreshold = Integer.parseInt(thresholds[2].strip());
                    blockingThreshold = Integer.parseInt(thresholds[3].strip());
                } catch (final NumberFormatException nfe) {
                    continue;
                }

                final String topicText = split[1].strip();
                if (topicText.isBlank()) {
                    continue;
                }

                topicsList.add(new Topic(topicText, reportingThreshold, moderationThreshold, downgradingThreshold,
                        blockingThreshold));
            }
        }

        if (topicsList.isEmpty()) {
            throw new IllegalArgumentException("[" + AiModerateBot.class.getSimpleName()
                    + "] No topics found in config file '" + topicsFile.toAbsolutePath() + "', but are required!");
        }

        if ((port < 0) || (null == groupToProcess) || groupToProcess.isBlank() || ollamaModels.isEmpty()) {
            throw new IllegalArgumentException("[" + AiModerateBot.class.getSimpleName()
                    + "] Some mandatory config properties are missing or are invalid! Required are: '"
                    + AiModerateBotConstants.CONFIG_PORT + "', '" + AiModerateBotConstants.CONFIG_GROUP + "' and '"
                    + AiModerateBotConstants.CONFIG_OLLAMA_MODELS + "'");
        }

        if ((null == weekDaysToRun) || (null == hoursToRun) || (sleepTimeInSeconds < 0)
                || (numberOfMessagesToRetrieve < 0)
                || (!persistState.equalsIgnoreCase("true") && !persistState.equalsIgnoreCase("false"))
                || (ollamaPort.isBlank()) || (ollamaReadTimeoutMinutes < 0) || (ollamaCooldownSeconds < 0)
                || (maxPromptCharacterLimit < 0) || secretPromptMarker.isBlank()) {
            Util.logWarning("[" + AiModerateBot.class.getSimpleName()
                    + "] Some config properties are missing or are invalid, using defaults!", null, null, null);
        }

        SimplexConnection.initSimplexConnection(port);
        return new AiModerateBot(SimplexConnection.get(port), groupToProcess, groupContext, topicsList,
                contactsForOutput, groupsForOutput, weekDaysToRun, hoursToRun, sleepTimeInSeconds,
                numberOfMessagesToRetrieve, persistState, ollamaPort, ollamaModels, ollamaReadTimeoutMinutes,
                ollamaCooldownSeconds, maxPromptCharacterLimit, secretPromptMarker, contactsForReporting,
                groupsForReporting);
    }

    private AiModerateBot(SimplexConnection simplexConnection, String groupToProcess, String groupContext,
            List<Topic> topics, List<String> contactsForOutput, List<String> groupsForOutput, int[] weekdaysToRun,
            int[] hoursToRun, int sleepTimeInSeconds, int numberOfMessagesToRetrieve, String persistState,
            String ollamaPort, List<String> ollamaModels, int ollamaReadTimeoutMinutes, int ollamaCooldownSeconds,
            int maxPromptCharacterLimit, String secretPromptMarker, List<String> contactsForReporting,
            List<String> groupsForReporting) {
        this.simplexConnection = simplexConnection;
        this.groupToProcess = groupToProcess;
        this.groupContext = groupContext;
        this.topics = topics;
        this.contactsForOutput = contactsForOutput;
        this.groupsForOutput = groupsForOutput;
        this.weekdaysToRun = weekdaysToRun;
        this.hoursToRun = hoursToRun;
        this.sleepTimeInSeconds = Math.abs(sleepTimeInSeconds);
        this.numberOfMessagesToRetrieve = Math.abs(numberOfMessagesToRetrieve);
        this.persistState = persistState.equalsIgnoreCase("true") ? true : false;
        this.ollamaPort = ollamaPort.isBlank() ? OllamaConstants.OLLAMA_DEFAULT_PORT : ollamaPort;
        this.ollamaModels = ollamaModels;
        this.ollamaReadTimeoutMinutes = Math.abs(ollamaReadTimeoutMinutes);
        this.ollamaCooldownSeconds = Math.abs(ollamaCooldownSeconds);
        this.maxPromptCharacterLimit = Math.max(maxPromptCharacterLimit, 0);
        this.secretPromptMarker = secretPromptMarker.isBlank() ? AiModerateBotConstants.DEFAULT_PROMPT_MARKER
                : secretPromptMarker;
        this.contactsForReporting = contactsForReporting;
        this.groupsForReporting = groupsForReporting;
    }

    @Override
    public void run() {

        Util.log(AiModerateBot.class.getSimpleName() + " " + Start.VERSION + " has started with config: *"
                + AiModerateBotConstants.CONFIG_PORT + "*=" + simplexConnection.getPort() + " *"
                + AiModerateBotConstants.CONFIG_GROUP + "*='" + groupToProcess + "' *"
                + AiModerateBotConstants.CONFIG_GROUP_CONTEXT + "*='"
                + (groupContext.length() <= 100 ? groupContext : groupContext.substring(0, 100) + " [...]") + "' *"
                + "TOPICS*=" + Util.listToString(topics) + " *" + AiModerateBotConstants.CONFIG_OUTPUT_CONTACTS + "*="
                + Util.listToString(contactsForOutput) + " *" + AiModerateBotConstants.CONFIG_OUTPUT_GROUPS + "*="
                + Util.listToString(groupsForOutput) + " *" + AiModerateBotConstants.CONFIG_WEEKDAYS + "*="
                + Util.intArrayToString(weekdaysToRun) + " *" + AiModerateBotConstants.CONFIG_HOURS + "*="
                + Util.intArrayToString(hoursToRun) + " *" + AiModerateBotConstants.CONFIG_SLEEP_TIME_SECONDS + "*="
                + sleepTimeInSeconds + " *" + AiModerateBotConstants.CONFIG_NUMBER_OF_MESSAGES_TO_RETRIEVE + "*="
                + numberOfMessagesToRetrieve + " *" + AiModerateBotConstants.CONFIG_PERSIST_STATE + "*=" + persistState
                + " *" + AiModerateBotConstants.CONFIG_OLLAMA_PORT + "*=" + ollamaPort + " *"
                + AiModerateBotConstants.CONFIG_OLLAMA_MODELS + "*=" + Util.listToString(ollamaModels) + " *"
                + AiModerateBotConstants.CONFIG_OLLAMA_READ_TIMEOUT_MINUTES + "*=" + ollamaReadTimeoutMinutes + " *"
                + AiModerateBotConstants.CONFIG_OLLAMA_COOLDOWN_SECONDS + "*=" + ollamaCooldownSeconds + " *"
                + AiModerateBotConstants.CONFIG_OLLAMA_MAX_PROMPT_CHARACTER_LIMIT + "*=" + maxPromptCharacterLimit
                + " *" + AiModerateBotConstants.CONFIG_OLLAMA_SECRET_PROMPT_MARKER + "*=" + secretPromptMarker + " *"
                + AiModerateBotConstants.CONFIG_REPORT_TO_CONTACTS + "*=" + Util.listToString(contactsForReporting)
                + " *" + AiModerateBotConstants.CONFIG_REPORT_TO_GROUPS + "*=" + Util.listToString(groupsForReporting),
                simplexConnection, contactsForReporting, groupsForReporting);

        try {

            final List<GroupMessage> alreadyProcessedMessages;
            if (persistState) {
                alreadyProcessedMessages = Util.initCacheFile(
                        Path.of(Start.CONFIG_DIRECTORY, AiModerateBotConstants.PROCESSED_MESSAGES_CACHE_FILE),
                        groupToProcess, numberOfMessagesToRetrieve, simplexConnection, contactsForReporting,
                        groupsForReporting);
            } else {
                try {
                    Files.deleteIfExists(
                            Path.of(Start.CONFIG_DIRECTORY, AiModerateBotConstants.PROCESSED_MESSAGES_CACHE_FILE));
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
                                processMessage(message);
                            } catch (final Exception ex) {
                                Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex),
                                        simplexConnection, contactsForReporting, groupsForReporting);
                            } finally {
                                if (persistState) {
                                    Util.addProcessedMessageToFile(message,
                                            Path.of(Start.CONFIG_DIRECTORY,
                                                    AiModerateBotConstants.PROCESSED_MESSAGES_CACHE_FILE),
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
                    AiModerateBot.class.getSimpleName() + " has finished with error: " + Util.getStackTraceAsString(ex),
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private void processMessage(GroupMessage message) {

        if ((null == message.getText()) || message.getText().isBlank()) {
            return;
        }

        final String systemPrompt = buildSystemPrompt() + "\n\n<end of system prompt>\n\n";

        for (final Topic topic : topics) {

            final String prompt = generatePrompt(message.getText(), topic.getText());

            for (final String model : ollamaModels) {
                final String aiResponse = OllamaConnection.generateResponse(ollamaPort, model, systemPrompt, prompt,
                        maxPromptCharacterLimit, ollamaReadTimeoutMinutes * TimeUtil.MILLISECONDS_PER_MINUTE,
                        simplexConnection, contactsForReporting, groupsForReporting);

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
                final int evaluation = retrieveEvaluation(sanitizedResponse, model, prompt);

                if ((evaluation >= topic.getBlockingThreshold()) && (topic.getBlockingThreshold() > 0)) {
                    try {
                        blockMember(message, topic.getText(), evaluation, sanitizedResponse);
                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                if ((evaluation >= topic.getDowngradingThreshold()) && (topic.getDowngradingThreshold() > 0)) {
                    try {
                        downgradeMember(message, topic.getText(), evaluation, sanitizedResponse);
                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                if ((evaluation >= topic.getModerationThreshold()) && (topic.getModerationThreshold() > 0)) {
                    try {
                        moderateMessage(message, topic.getText(), evaluation, sanitizedResponse);
                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

                if ((evaluation >= topic.getReportingThreshold()) && (topic.getReportingThreshold() > 0)) {
                    try {
                        reportMessage(message, topic.getText(), evaluation, sanitizedResponse);
                    } catch (final Exception ex) {
                        Util.logError("Unexpected exception: " + Util.getStackTraceAsString(ex), simplexConnection,
                                contactsForReporting, groupsForReporting);
                    }
                }

            }
        }
    }

    private String buildSystemPrompt() {
        return AiModerateBotConstants.SYSTEM_PROMPT_PART_1 + groupContext + AiModerateBotConstants.SYSTEM_PROMPT_PART_2
                .replace(AiModerateBotConstants.DEFAULT_PROMPT_MARKER, secretPromptMarker);
    }

    private String generatePrompt(String message, String topicText) {
        return "The actual given TOPIC/QUERY is: \"" + topicText + "\"\n\nThe actual given MESSAGE is: "
                + secretPromptMarker + "\n\n" + message + "\n\n" + secretPromptMarker + "\n\n" + "Remember: "
                + buildSystemPrompt();
    }

    private String sanitizeAiResponse(String aiResponse) {

        final String[] aiResponseSplit = aiResponse.split("</think>");
        final String aiResponseWithoutThinking = aiResponseSplit[aiResponseSplit.length - 1];
        final String sanitizedAiResponse = aiResponseWithoutThinking.replace(secretPromptMarker, "").strip();
        return sanitizedAiResponse;
    }

    private int retrieveEvaluation(String aiResponse, String model, String prompt) {

        if (aiResponse.isEmpty()) {
            Util.logError(
                    "Ollama returned an empty response for model '" + model + "'! *Prompt (length = " + prompt.length()
                            + " characters) started with:*\n"
                            + (prompt.length() <= 200 ? prompt : prompt.substring(0, 200) + " [...]"),
                    simplexConnection, contactsForReporting, groupsForReporting);
            return -1;
        }

        int number = -1;
        try {
            number = Integer.parseInt(aiResponse.substring(0, 1));
        } catch (final NumberFormatException nfe) {
            // ignore
        }

        switch (number) {
        case 1: // fall through
        case 2: // fall through
        case 3: // fall through
        case 4: // fall through
        case 5:
            return number;
        default:
            Util.logError(
                    "Ollama returned an invalid response for model '" + model + "'!\n\n*Prompt (length = "
                            + prompt.length() + " characters) started with:*\n"
                            + (prompt.length() <= 200 ? prompt : prompt.substring(0, 200) + " [...]")
                            + "\n\n*Response started with:*\n"
                            + (aiResponse.length() <= 200 ? aiResponse : aiResponse.substring(0, 200) + " [...]"),
                    simplexConnection, contactsForReporting, groupsForReporting);
            return -1;
        }
    }

    private void blockMember(GroupMessage groupMessage, String topicText, int evaluation, String aiResponse) {

        final String messageToReport = "!6 Blocking! member *'" + groupMessage.getMember().getDisplayName() + "'* ["
                + groupMessage.getMember().getLocalName() + "] in group *'" + groupToProcess
                + "'* because of evaluation " + Topic.getTextForThreshold(evaluation) + " for topic _\""
                + (topicText.length() <= 200 ? topicText : topicText.substring(0, 200) + " [...]") + "\"_!"
                + (aiResponse.length() <= 1 ? ""
                        : "\n\nADDITIONAL A.I. OUTPUT:\n"
                                + (aiResponse.length() <= 2000 ? aiResponse : aiResponse.substring(0, 2000) + " [...]"))
                + "\n\nORIGINAL MESSAGE:";

        writeMessages(messageToReport, groupMessage.getText());

        if (groupMessage.getMember().hasPrivileges()) {
            final String warnMessage = "Member has privileges: !2 BLOCKING IS REJECTED!";
            Util.logWarning(warnMessage, simplexConnection, contactsForReporting, groupsForReporting);
            Util.outputToContactsAndGroups(warnMessage, simplexConnection, contactsForOutput, groupsForOutput,
                    contactsForReporting, groupsForReporting);
            return;
        }

        // Check latest member status to avoid multiple blocking:
        final List<GroupMember> groupMemberList = simplexConnection.getGroupMembers(groupToProcess,
                contactsForReporting, groupsForReporting);
        final GroupMember updatedMember = groupMemberList.get(groupMemberList.indexOf(groupMessage.getMember()));
        if (updatedMember.isBlocked()) {
            return;
        }

        simplexConnection.blockForAll(groupToProcess, groupMessage.getMember().getLocalName(), contactsForReporting,
                groupsForReporting);
    }

    private void downgradeMember(GroupMessage groupMessage, String topicText, int evaluation, String aiResponse) {

        final String messageToReport = "!6 Downgrading! member *'" + groupMessage.getMember().getDisplayName() + "'* ["
                + groupMessage.getMember().getLocalName() + "] in group *'" + groupToProcess
                + "'* because of evaluation " + Topic.getTextForThreshold(evaluation) + " for topic _\""
                + (topicText.length() <= 200 ? topicText : topicText.substring(0, 200) + " [...]") + "\"_!"
                + (aiResponse.length() <= 1 ? ""
                        : "\n\nADDITIONAL A.I. OUTPUT:\n"
                                + (aiResponse.length() <= 2000 ? aiResponse : aiResponse.substring(0, 2000) + " [...]"))
                + "\n\nORIGINAL MESSAGE:";

        writeMessages(messageToReport, groupMessage.getText());

        if (groupMessage.getMember().hasPrivileges()) {
            final String warnMessage = "Member has privileges: !2 DOWNGRADING IS REJECTED!";
            Util.logWarning(warnMessage, simplexConnection, contactsForReporting, groupsForReporting);
            Util.outputToContactsAndGroups(warnMessage, simplexConnection, contactsForOutput, groupsForOutput,
                    contactsForReporting, groupsForReporting);
            return;
        }

        // Check latest member status to avoid multiple downgrading:
        final List<GroupMember> groupMemberList = simplexConnection.getGroupMembers(groupToProcess,
                contactsForReporting, groupsForReporting);
        final GroupMember updatedMember = groupMemberList.get(groupMemberList.indexOf(groupMessage.getMember()));
        if (updatedMember.isPresent() && !GroupMember.ROLE_OBSERVER.equals(updatedMember.getRole())) {
            simplexConnection.changeGroupMemberRole(groupToProcess, groupMessage.getMember().getLocalName(),
                    GroupMember.ROLE_OBSERVER, contactsForReporting, groupsForReporting);
        } else {
            Util.logWarning(
                    "Member *'" + groupMessage.getMember().getDisplayName() + "'* ["
                            + groupMessage.getMember().getLocalName() + "] in group *'" + groupToProcess
                            + "'* is already downgraded or not present!",
                    simplexConnection, contactsForReporting, groupsForReporting);
        }
    }

    private void moderateMessage(GroupMessage groupMessage, String topicText, int evaluation, String aiResponse) {

        final String messageToReport = "!4 Moderating! message of member *'" + groupMessage.getMember().getDisplayName()
                + "'* [" + groupMessage.getMember().getLocalName() + "] in group *'" + groupToProcess
                + "'* because of evaluation " + Topic.getTextForThreshold(evaluation) + " for topic _\""
                + (topicText.length() <= 200 ? topicText : topicText.substring(0, 200) + " [...]") + "\"_!"
                + (aiResponse.length() <= 1 ? ""
                        : "\n\nADDITIONAL A.I. OUTPUT:\n"
                                + (aiResponse.length() <= 2000 ? aiResponse : aiResponse.substring(0, 2000) + " [...]"))
                + "\n\nORIGINAL MESSAGE:";

        writeMessages(messageToReport, groupMessage.getText());

        if (groupMessage.getMember().hasPrivileges()) {
            final String warnMessage = "Member has privileges: !2 MODERATION IS REJECTED!";
            Util.logWarning(warnMessage, simplexConnection, contactsForReporting, groupsForReporting);
            Util.outputToContactsAndGroups(warnMessage, simplexConnection, contactsForOutput, groupsForOutput,
                    contactsForReporting, groupsForReporting);
            return;
        }

        simplexConnection.moderateGroupMessage(groupMessage.getGroupId(), groupMessage.getId(), contactsForReporting,
                groupsForReporting);
    }

    private void reportMessage(GroupMessage groupMessage, String topicText, int evaluation, String aiResponse) {

        final String messageToReport = "!5 Reporting! message of member *'" + groupMessage.getMember().getDisplayName()
                + "'* [" + groupMessage.getMember().getLocalName() + "] in group *'" + groupToProcess
                + "'* because of evaluation " + Topic.getTextForThreshold(evaluation) + " for topic _\""
                + (topicText.length() <= 200 ? topicText : topicText.substring(0, 200) + " [...]") + "\"_!"
                + (aiResponse.length() <= 1 ? ""
                        : "\n\nADDITIONAL A.I. OUTPUT:\n"
                                + (aiResponse.length() <= 2000 ? aiResponse : aiResponse.substring(0, 2000) + " [...]"))
                + "\n\nORIGINAL MESSAGE:";

        writeMessages(messageToReport, groupMessage.getText());
    }

    private void writeMessages(String messageToReport, String groupMessageText) {

        Util.log(messageToReport, simplexConnection, contactsForReporting, groupsForReporting);
        Util.outputToContactsAndGroups(messageToReport, simplexConnection, contactsForOutput, groupsForOutput,
                contactsForReporting, groupsForReporting);

        System.out.println(groupMessageText);
        simplexConnection.logToBotAdmins(groupMessageText, contactsForReporting, groupsForReporting);
        simplexConnection.sendToContactsAndGroups(groupMessageText, contactsForOutput, groupsForOutput,
                contactsForReporting, groupsForReporting);
    }

    private boolean shouldRun() {
        return checkWeekdays() && checkHours();
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
