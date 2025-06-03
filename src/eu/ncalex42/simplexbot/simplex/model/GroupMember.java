package eu.ncalex42.simplexbot.simplex.model;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.Connection;
import eu.ncalex42.simplexbot.simplex.SimplexConstants;

public class GroupMember {

    public static final String ROLE_OBSERVER = "observer";
    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_MODERATOR = "moderator";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_OWNER = "owner";

    public static final String STATUS_REMOVED = "removed";
    public static final String STATUS_LEFT = "left";

    private final String localName;
    private final String displayName;
    private final long groupMemberId;
    private final String role;
    private final String status;
    private final boolean blockedByAdmin;
    private final String createdAt; // optional: can be null or empty!

    public GroupMember(String localName, String displayName, long groupMemberId, String role, String status,
            boolean blockedByAdmin, String createdAt) {

        if (null == localName) {
            throw new IllegalArgumentException("'localName' required for " + GroupMember.class.getSimpleName() + "!");
        }
        this.localName = localName;
        this.displayName = displayName;
        this.groupMemberId = groupMemberId;
        this.role = role;
        this.status = status;
        this.blockedByAdmin = blockedByAdmin;
        this.createdAt = createdAt;
    }

    public String getLocalName() {
        return localName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getGroupMemberId() {
        return groupMemberId;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPresent() {
        return !STATUS_LEFT.equals(status) && !STATUS_REMOVED.equals(status);
    }

    public boolean hasPrivileges() {
        return ROLE_OWNER.equals(role) || ROLE_ADMIN.equals(role) || ROLE_MODERATOR.equals(role);
    }

    public boolean isBlocked() {
        return blockedByAdmin;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public int hashCode() {
        return localName.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof GroupMember) {
            return localName.equals(((GroupMember) other).getLocalName());
        }
        return false;
    }

    public static List<GroupMember> parseMembers(JSONObject groupMemberResponse, Connection simplexConnection,
            List<String> contactsForReporting, List<String> groupsForReporting) {

        JSONObject resp = groupMemberResponse.getJSONObject(SimplexConstants.KEY_RESP);
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
        if (!SimplexConstants.VALUE_GROUP_MEMBERS.equals(type)) {
            Util.logError("Unexpected type: " + type, simplexConnection, contactsForReporting, groupsForReporting);
            return null;
        }

        final JSONArray members = resp.getJSONObject(SimplexConstants.KEY_GROUP)
                .getJSONArray(SimplexConstants.KEY_MEMBERS);

        final List<GroupMember> result = new LinkedList<>();
        for (int i = 0; i < members.length(); i++) {
            final JSONObject member = members.getJSONObject(i);
            final String localDisplayName = member.getString(SimplexConstants.KEY_LOCAL_DISPLAY_NAME);
            final String displayName = member.getJSONObject(SimplexConstants.KEY_MEMBER_PROFILE)
                    .getString(SimplexConstants.KEY_DISPLAY_NAME);
            final long groupMemberId = member.getLong(SimplexConstants.KEY_GROUP_MEMBER_ID);
            final String role = member.getString(SimplexConstants.KEY_MEMBER_ROLE);
            final String status = member.getString(SimplexConstants.KEY_MEMBER_STATUS);
            final boolean blockedByAdmin = member.getBoolean(SimplexConstants.KEY_BLOCKED_BY_ADMIN);
            final String createdAt = member.optString(SimplexConstants.KEY_CREATED_AT);
            result.add(new GroupMember(localDisplayName, displayName, groupMemberId, role, status, blockedByAdmin,
                    createdAt));
        }

        return result;
    }
}
