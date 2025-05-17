package eu.ncalex42.simplexbot.simplex.model;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.Connection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;

public class GroupMessage {

    public static final int NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE = 500;

    private final long id;
    private final long groupId;
    private final String type;
    private final String text;
    private final GroupMember user;
    private final String itemTs;

    public GroupMessage(long id, long groupId, String type, String text, GroupMember user, String itemTs) {
        this.id = id;
        this.groupId = groupId;
        this.type = type;
        this.text = text;
        this.user = user;
        this.itemTs = itemTs;
    }

    public static List<GroupMessage> parseNewMessagesFromGroup(JSONObject tailResponse,
            List<GroupMessage> alreadyProcessedMessages, boolean retrieveDeprecatedMessages, Connection connection,
            List<String> contactsForReporting, List<String> groupsForReporting) {

        final String type = tailResponse.getJSONObject(SimplexConstants.KEY_RESP).getString(SimplexConstants.KEY_TYPE);
        if (!SimplexConstants.VALUE_CHAT_ITEMS.equals(type)) {
            Util.logError("Unexpected type: " + type, connection, contactsForReporting, groupsForReporting);
            return null;
        }

        final boolean firstRun = alreadyProcessedMessages.isEmpty();

        final JSONArray chatItems = tailResponse.getJSONObject(SimplexConstants.KEY_RESP)
                .getJSONArray(SimplexConstants.KEY_CHAT_ITEMS);

        final List<GroupMessage> result = new LinkedList<>();
        for (int i = 0; i < chatItems.length(); i++) {

            final JSONObject chatItem = chatItems.getJSONObject(i);

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

            final JSONObject messageContent = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                    .getJSONObject(SimplexConstants.KEY_CONTENT).getJSONObject(SimplexConstants.KEY_MSG_CONTENT);
            final String messageType = messageContent.getString(SimplexConstants.KEY_TYPE);
            final String messageText = messageContent.getString(SimplexConstants.KEY_TEXT);

            final JSONObject groupMember = chatItem.getJSONObject(SimplexConstants.KEY_CHAT_ITEM)
                    .getJSONObject(SimplexConstants.KEY_CHAT_DIR).getJSONObject(SimplexConstants.KEY_GROUP_MEMBER);
            final String localUserName = groupMember.getString(SimplexConstants.KEY_LOCAL_DISPLAY_NAME);
            final String displayUserName = groupMember.getJSONObject(SimplexConstants.KEY_MEMBER_PROFILE)
                    .getString(SimplexConstants.KEY_DISPLAY_NAME);
            final long groupMemberId = groupMember.getLong(SimplexConstants.KEY_GROUP_MEMBER_ID);
            final String memberRole = groupMember.getString(SimplexConstants.KEY_MEMBER_ROLE);
            final String memberStatus = groupMember.getString(SimplexConstants.KEY_MEMBER_STATUS);
            final boolean memberBlockedByAdmin = groupMember.getBoolean(SimplexConstants.KEY_BLOCKED_BY_ADMIN);

            final GroupMessage currentGroupMessage = new GroupMessage(messageId, groupId, messageType, messageText,
                    new GroupMember(localUserName, displayUserName, groupMemberId, memberRole, memberStatus,
                            memberBlockedByAdmin, null),
                    itemTs);

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
        }

        trimProcessedMessages(alreadyProcessedMessages);

        if (firstRun && !retrieveDeprecatedMessages) {
            return List.of();
        } else {
            return result;
        }
    }

    private static void trimProcessedMessages(List<GroupMessage> alreadyProcessedMessages) {

        while (alreadyProcessedMessages.size() > (NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE + 500)) { // +500 as safety buffer
            alreadyProcessedMessages.remove(0);
        }
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

    public GroupMember getUser() {
        return user;
    }

    public String getItemTs() {
        return itemTs;
    }
}
