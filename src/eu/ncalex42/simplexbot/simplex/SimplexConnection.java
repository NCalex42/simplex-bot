package eu.ncalex42.simplexbot.simplex;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;

import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.model.GroupMember;
import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

public class SimplexConnection {

    private static ConcurrentHashMap<Integer, SimplexConnection> connections = new ConcurrentHashMap<>();

    private final int port;
    private volatile WebSocket websocket;
    private final ConcurrentLinkedQueue<String> responses = new ConcurrentLinkedQueue<>();

    public static synchronized void initSimplexConnection(int port) {

        if (connections.containsKey(port)) {
            return;
        }

        final SimplexConnection connection = new SimplexConnection(port);
        connections.put(port, connection);
        connection.connect();

        Util.log("Connected to port " + port, null, null, null);
    }

    public static SimplexConnection get(int port) {
        return connections.get(port);
    }

    private SimplexConnection(int port) {
        this.port = port;
    }

    private void connect() {

        final HttpClient client = HttpClient.newHttpClient();
        final URI websocketUri = URI.create("ws://localhost:" + port);

        websocket = client.newWebSocketBuilder().buildAsync(websocketUri, new Listener() {

            StringBuilder responseBuffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                Util.log("Connected to the websocket", null, null, null);
                Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {

                responseBuffer.append(data);
                if (last) {
                    final String response = responseBuffer.toString();
                    if (response.contains("\"corrId\"")) { // only store responses that we requested
                        responses.add(response);
                    }
                    responseBuffer = new StringBuilder();
                }
                return Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                Util.logWarning("Received bytedata from the websocket: " + data, null, null, null);
                return Listener.super.onBinary(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                Util.logWarning("Disconnected from the websocket!", null, null, null);
                return Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable throwable) {
                Util.logError("Error received from the websocket: " + throwable.toString(), null, null, null);
                Listener.super.onError(webSocket, throwable);
            }
        }).join();
    }

    public int getPort() {
        return port;
    }

    public boolean sendToContact(String contactName, String message, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject sendToContactResponse = apiSendToContact(contactName, message);
        final String sendToContactErrorMessage = retrieveError(sendToContactResponse);
        if (null != sendToContactErrorMessage) {
            Util.logError(sendToContactErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    public boolean sendToGroup(String groupName, String message, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject sendToGroupResponse = apiSendToGroup(groupName, message);
        final String sendToGroupErrorMessage = retrieveError(sendToGroupResponse);
        if (null != sendToGroupErrorMessage) {
            Util.logError(sendToGroupErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    public List<GroupMember> getGroupMembers(String groupName, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject groupMembersResponse = apiGetMembersFromGroup(groupName);

        final String groupMembersErrorMessage = retrieveError(groupMembersResponse);
        if (null != groupMembersErrorMessage) {
            Util.logError(groupMembersErrorMessage, this, contactsForReporting, groupsForReporting);
            throw new IllegalStateException("Error while retrieving group members!");
        }

        try {

            final List<GroupMember> members = GroupMember.parseGroupMembers(groupMembersResponse, this,
                    contactsForReporting, groupsForReporting);
            if (null == members) {
                Util.logError("Unexpected response for 'getGroupMembers':\n" + groupMembersResponse.toString(2), this,
                        contactsForReporting, groupsForReporting);
                throw new IllegalStateException("Unexpected response!");
            }

            if (members.isEmpty()) {
                throw new IllegalStateException("Empty group member list!");
            }

            return members;

        } catch (final JSONException jsonException) {
            Util.logError(Util.getStackTraceAsString(jsonException), this, contactsForReporting, groupsForReporting);
            Util.logError("Unexpected JSON:\n" + groupMembersResponse.toString(2), this, contactsForReporting,
                    groupsForReporting);
            throw new IllegalStateException("Unexpected JSON!");
        }
    }

    public boolean changeGroupMemberRole(String groupName, String memberName, String role,
            List<String> contactsForReporting, List<String> groupsForReporting) {

        final JSONObject roleChangeResponse = apiChangeGroupMemberRole(groupName, memberName, role);
        final String roleChangeErrorMessage = retrieveError(roleChangeResponse);
        if (null != roleChangeErrorMessage) {
            Util.logError(roleChangeErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    public List<GroupMessage> getNewGroupMessages(String groupName, List<GroupMessage> alreadyProcessedMessages,
            boolean retrieveDeprecatedMessages, List<String> contactsForReporting, List<String> groupsForReporting) {
        return getNewGroupMessages(groupName, alreadyProcessedMessages, retrieveDeprecatedMessages,
                GroupMessage.DEFAULT_NUMBER_OF_GROUPMESSAGES_TO_RETRIEVE, contactsForReporting, groupsForReporting);
    }

    public List<GroupMessage> getNewGroupMessages(String groupName, List<GroupMessage> alreadyProcessedMessages,
            boolean retrieveDeprecatedMessages, int numberOfMessagesToRetrieve, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject tailResponse = apiGetLatestMessagesFromGroup(groupName, numberOfMessagesToRetrieve);

        final String tailErrorMessage = retrieveError(tailResponse);
        if (null != tailErrorMessage) {
            Util.logError(tailErrorMessage, this, contactsForReporting, groupsForReporting);
            return List.of();
        }

        try {

            final List<GroupMessage> newMessages = GroupMessage.parseNewMessagesFromGroup(tailResponse,
                    alreadyProcessedMessages, retrieveDeprecatedMessages, numberOfMessagesToRetrieve, this,
                    contactsForReporting, groupsForReporting);
            if (null == newMessages) {
                Util.logError("Unexpected response for 'getNewGroupMessages':\n" + tailResponse.toString(2), this,
                        contactsForReporting, groupsForReporting);
                throw new IllegalStateException("Unexpected response!");
            }
            return newMessages;

        } catch (final JSONException jsonException) {
            Util.logError(Util.getStackTraceAsString(jsonException), this, contactsForReporting, groupsForReporting);
            Util.logError("Unexpected JSON:\n" + tailResponse.toString(2), this, contactsForReporting,
                    groupsForReporting);
            throw new IllegalStateException("Unexpected JSON!");
        }
    }

    public boolean moderateGroupMessage(long groupId, long messageId, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject moderateResponse = apiModerateGroupMessage(groupId, messageId);
        final String moderateErrorMessage = retrieveError(moderateResponse);
        if (null != moderateErrorMessage) {
            Util.logError(moderateErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    public boolean blockForAll(String groupName, String memberName, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject blockForAllResponse = apiBlockForAll(groupName, memberName);
        final String blockForAllErrorMessage = retrieveError(blockForAllResponse);
        if (null != blockForAllErrorMessage) {
            Util.logError(blockForAllErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    public boolean removeMemberFromGroup(String groupName, String memberName, List<String> contactsForReporting,
            List<String> groupsForReporting) {

        final JSONObject removeFromGroupResponse = apiRemoveFromGroup(groupName, memberName);
        final String removeFromGroupErrorMessage = retrieveError(removeFromGroupResponse);
        if (null != removeFromGroupErrorMessage) {
            Util.logError(removeFromGroupErrorMessage, this, contactsForReporting, groupsForReporting);
            return false;
        }
        return true;
    }

    /**
     * Send logmessage to configured bot-admins from module config.
     *
     * @param logMessage the message to send
     * @param contacts   list of contacts to send message to
     * @param groups     list of groups to send message to
     */
    public void logToBotAdmins(String logMessage, List<String> contacts, List<String> groups) {
        sendToContactsAndGroups(logMessage, contacts, groups, null, null);
    }

    public void sendToContactsAndGroups(String message, List<String> contacts, List<String> groups,
            List<String> contactsForReporting, List<String> groupsForReporting) {

        if (null != contacts) {
            for (final String contact : contacts) {
                sendToContact(contact, message, contactsForReporting, groupsForReporting);
            }
        }

        if (null != groups) {
            for (final String group : groups) {
                sendToGroup(group, message, contactsForReporting, groupsForReporting);
            }
        }
    }

    private JSONObject apiSendToContact(String contactName, String message) {
        return send("@'" + contactName + "' " + message);
    }

    @SuppressWarnings("unused")
    private JSONObject apiGetLatestMessagesFromContact(String contactName, int count) {
        return send("/tail @'" + contactName + "' " + count);
    }

    private JSONObject apiSendToGroup(String groupName, String message) {
        return send("#'" + groupName + "' " + message);
    }

    private JSONObject apiGetMembersFromGroup(String groupName) {
        return send("/members '" + groupName + "'");
    }

    private JSONObject apiChangeGroupMemberRole(String groupName, String memberName, String role) {
        return send("/member role #'" + groupName + "' '" + memberName + "' " + role);
    }

    private JSONObject apiGetLatestMessagesFromGroup(String groupName, int count) {
        return send("/tail #'" + groupName + "' " + count);
    }

    private JSONObject apiModerateGroupMessage(long groupId, long messageId) {
        return send("/_delete member item #" + groupId + " " + messageId);
    }

    private JSONObject apiBlockForAll(String groupName, String memberName) {
        return send("/block for all #'" + groupName + "' @'" + memberName + "'");
    }

    private JSONObject apiRemoveFromGroup(String groupName, String memberName) {
        return send("/remove '" + groupName + "' '" + memberName + "'");
    }

    private synchronized JSONObject send(String command) {

        final long cId = System.currentTimeMillis();
        final JSONObject request = new JSONObject(
                Map.of(SimplexConstants.KEY_CORR_ID, String.valueOf(cId), SimplexConstants.KEY_CMD, command));

        websocket.sendText(request.toString(), true);

        final long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < (TimeUtil.MILLISECONDS_PER_MINUTE)) { // 60 seconds timeout!
            final String responseString = responses.poll();

            if (null != responseString) {
                try {
                    final JSONObject jsonResponse = new JSONObject(responseString);

                    if (String.valueOf(cId).equals(jsonResponse.get(SimplexConstants.KEY_CORR_ID))) {
                        return jsonResponse;
                    }

                } catch (final Exception ex) {
                    Util.logError("Invalid response for command '" + command + "': '" + responseString + "'", null,
                            null, null);
                    Util.logError(ex.toString(), null, null, null);
                    ex.printStackTrace();
                    return null;
                }
            }
        }

        // timeout reached
        Util.logError("Timeout reached for command: " + command, null, null, null);
        return null;
    }

    private String retrieveError(JSONObject simplexResponse) {

        if (null == simplexResponse) {
            return "ERROR: No valid response received!";
        }

        JSONObject resp = simplexResponse.optJSONObject(SimplexConstants.KEY_RESP);
        if (null == resp) {
            return "ERROR: Response does not contain '" + SimplexConstants.KEY_RESP + "':" + simplexResponse.toString();
        }

        String type;
        final JSONObject left = resp.optJSONObject(SimplexConstants.KEY_LEFT);
        if (null == left) {
            final JSONObject right = resp.optJSONObject(SimplexConstants.KEY_RIGHT);
            if (null == right) {
                // SimpleX < 6.4
                type = resp.optString(SimplexConstants.KEY_TYPE);
            } else {
                // SimpleX >= 6.4
                type = right.optString(SimplexConstants.KEY_TYPE);
                resp = right;
            }
        } else {
            // SimpleX >= 6.4
            type = left.optString(SimplexConstants.KEY_TYPE);
            resp = left;
        }

        if ((null == type) || type.isBlank()) {
            return "ERROR: Response does not contain '" + SimplexConstants.KEY_TYPE + "':" + simplexResponse.toString();
        }

        if (SimplexConstants.VALUE_CHAT_CMD_ERROR.equals(type)) {
            return SimplexConstants.VALUE_CHAT_CMD_ERROR + ": " + resp.optString(SimplexConstants.KEY_CHAT_ERROR);
        }

        // no error found:
        return null;
    }
}
