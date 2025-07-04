package eu.ncalex42.simplexbot.simplex.model;

import org.json.JSONObject;

import eu.ncalex42.simplexbot.simplex.SimplexConstants;

public class QuotedGroupMessage {

    private final long id;
    private final String type;
    private final String text;
    private final GroupMember member;

    public QuotedGroupMessage(long id, String type, String text, GroupMember member) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.member = member;
    }

    public long getId() {
        return id;
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

    static QuotedGroupMessage parseQuotedGroupMessage(JSONObject quotedGroupMessageJson) {

        final long messageId = quotedGroupMessageJson.optLong(SimplexConstants.KEY_ITEM_ID);

        final JSONObject messageContent = quotedGroupMessageJson.getJSONObject(SimplexConstants.KEY_CONTENT);
        final String messageType = messageContent.getString(SimplexConstants.KEY_TYPE);
        final String messageText = messageContent.getString(SimplexConstants.KEY_TEXT);

        JSONObject groupMemberJson = null;
        final JSONObject chatDir = quotedGroupMessageJson.optJSONObject(SimplexConstants.KEY_CHAT_DIR);
        if (null != chatDir) {
            groupMemberJson = chatDir.optJSONObject(SimplexConstants.KEY_GROUP_MEMBER);
        }

        return new QuotedGroupMessage(messageId, messageType, messageText,
                null == groupMemberJson ? null : GroupMember.parseGroupMember(groupMemberJson));
    }
}
