package eu.ncalex42.simplexbot.simplex.model;

import java.time.format.DateTimeParseException;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;

public class GroupMessage {

    public static final int DEFAULT_NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE = 500;

    private final long id;
    private final long groupId;
    private final String type;
    private final String text;
    private final GroupMember member;
    private final String itemTs;
    private final QuotedGroupMessage quotedGroupMessage;

    public GroupMessage(long id, long groupId, String type, String text, GroupMember member, String itemTs,
            QuotedGroupMessage quotedGroupMessage) {
        this.id = id;
        this.groupId = groupId;
        this.type = type;
        this.text = text;
        this.member = member;
        this.itemTs = itemTs;
        this.quotedGroupMessage = quotedGroupMessage;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id + groupId);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof GroupMessage) {
            final GroupMessage otherMessage = (GroupMessage) other;
            return (id == otherMessage.getId()) && (groupId == otherMessage.getGroupId());
        }
        return false;
    }

    public long getId() {
        return id;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public GroupMember getMember() {
        return member;
    }

    public String getItemTs() {
        return itemTs;
    }

    public QuotedGroupMessage getQuotedGroupMessage() {
        return quotedGroupMessage;
    }

    public static List<GroupMessage> parseNewMessagesFromGroup(JSONObject tailResponse,
            List<GroupMessage> alreadyProcessedMessages, boolean retrieveDeprecatedMessages,
            int numberOfMessagesToRetrieve, SimplexConnection simplexConnection, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        JSONObject resp = tailResponse.getJSONObject(SimplexConstants.KEY_RESP);
        String type;
        final JSONObject left = resp.optJSONObject(SimplexConstants.KEY_LEFT);
        if (null == left) {
            final JSONObject right = resp.optJSONObject(SimplexConstants.KEY_RIGHT);
            if (null == right) {
                // SimpleX < 6.4
                type = resp.getString(SimplexConstants.KEY_TYPE);
            } else {
                // SimpleX >= 6.4
                type = right.getString(SimplexConstants.KEY_TYPE);
                resp = right;
            }
        } else {
            // SimpleX >= 6.4
            type = left.getString(SimplexConstants.KEY_TYPE);
            resp = left;
        }
        if (!SimplexConstants.VALUE_CHAT_ITEMS.equals(type)) {
            Util.logError("Unexpected type: " + type, simplexConnection, contactsForReporting, groupsForReporting);
            return null;
        }

        final boolean firstRun = alreadyProcessedMessages.isEmpty();

        final JSONArray chatItems = resp.getJSONArray(SimplexConstants.KEY_CHAT_ITEMS);

        final List<GroupMessage> result = new LinkedList<>();
        for (int i = 0; i < chatItems.length(); i++) {

            final JSONObject chatItem = chatItems.getJSONObject(i);

            try {

                // check chatItem type:
                final String chatItemType = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_INFO)
                        .getString(SimplexConstants.KEY_TYPE);
                if (!SimplexConstants.VALUE_GROUP.equals(chatItemType)) {
                    continue;
                }

                // check chatItem content type:
                final String chatItemContentType = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                        .getJSONObject(SimplexConstants.KEY_CONTENT).getString(SimplexConstants.KEY_TYPE);
                if (!SimplexConstants.VALUE_CONTENT_TYPE_MSG_CONTENT.equals(chatItemContentType)) {
                    continue;
                }

                final long groupId = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_INFO)
                        .getJSONObject(SimplexConstants.KEY_GROUP_INFO).getLong(SimplexConstants.KEY_GROUP_ID);

                final JSONObject messageMeta = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                        .getJSONObject(SimplexConstants.KEY_META);
                final long messageId = messageMeta.getLong(SimplexConstants.KEY_ITEM_ID);
                final String itemTs = messageMeta.getString(SimplexConstants.KEY_ITEM_TS);

                // itemTs should not contain nanoseconds:
                if (isUnexpectedTimestampFormat(itemTs)) {
                    continue;
                }

                final JSONObject messageContent = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                        .getJSONObject(SimplexConstants.KEY_CONTENT).getJSONObject(SimplexConstants.KEY_MSG_CONTENT);
                final String messageType = messageContent.getString(SimplexConstants.KEY_TYPE);
                final String messageText = messageContent.getString(SimplexConstants.KEY_TEXT);

                final JSONObject groupMember = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                        .getJSONObject(SimplexConstants.KEY_CHAT_DIR).getJSONObject(SimplexConstants.KEY_GROUP_MEMBER);

                final JSONObject quotedItem = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                        .optJSONObject(SimplexConstants.KEY_QUOTED_ITEM);
                QuotedGroupMessage quotedGroupMessage = null;
                if (null != quotedItem) {
                    quotedGroupMessage = QuotedGroupMessage.parseQuotedGroupMessage(quotedItem);
                }

                final GroupMessage currentGroupMessage = new GroupMessage(messageId, groupId, messageType, messageText,
                        GroupMember.parseGroupMember(groupMember), itemTs, quotedGroupMessage);

                // check if message is new or contains new edits:
                final int indexOfCurrentMessage = alreadyProcessedMessages.indexOf(currentGroupMessage);
                if (-1 != indexOfCurrentMessage) {

                    final GroupMessage alreadyProcessedMessage = alreadyProcessedMessages.get(indexOfCurrentMessage);
                    if (alreadyProcessedMessage.getText().equals(currentGroupMessage.getText())) {
                        continue; // no changes
                    }

                    // replace old text with new text:
                    alreadyProcessedMessages.remove(indexOfCurrentMessage);
                    alreadyProcessedMessages.add(indexOfCurrentMessage, currentGroupMessage);
                } else {
                    alreadyProcessedMessages.add(currentGroupMessage);
                }

                result.add(currentGroupMessage);

            } catch (final JSONException jsonException) {
                Util.logError(Util.getStackTraceAsString(jsonException), simplexConnection, contactsForReporting,
                        groupsForReporting);
                Util.logError("Unexpected JSON:\n" + chatItem.toString(2), simplexConnection, contactsForReporting,
                        groupsForReporting);
                throw new IllegalStateException("Unexpected JSON!");
            }
        }

        trimProcessedMessages(alreadyProcessedMessages, numberOfMessagesToRetrieve);

        if (firstRun && !retrieveDeprecatedMessages) {
            return List.of();
        } else {
            return result;
        }
    }

    private static boolean isUnexpectedTimestampFormat(String itemTs) {

        try {
            TimeUtil.timestampToUtcSeconds(itemTs);
        } catch (final DateTimeParseException ex) {
            return true;
        }
        return false;
    }

    private static void trimProcessedMessages(List<GroupMessage> alreadyProcessedMessages,
            int numberOfMessagesToRetrieve) {

        while (alreadyProcessedMessages.size() > (numberOfMessagesToRetrieve + 500)) { // +500 as safety buffer
            alreadyProcessedMessages.remove(0);
        }
    }
}
