package eu.ncalex42.simplexbot.modules.moderatebot;

import eu.ncalex42.simplexbot.simplex.model.GroupMessage;

public class MessageActionItem implements Comparable<MessageActionItem> {

    private final ModerateAction action;
    private final GroupMessage message;
    private final String reason;

    public MessageActionItem(ModerateAction action, GroupMessage message, String reason) {
        this.action = action;
        this.message = message;
        this.reason = reason;
    }

    public ModerateAction getAction() {
        return action;
    }

    public GroupMessage getMessage() {
        return message;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public int compareTo(MessageActionItem other) {

        switch (action) {
        case BLOCK:
            switch (other.getAction()) {
            case BLOCK:
                return 0;
            case MODERATE:
                return -1;
            case REPORT:
                return -1;
            default:
                throw new IllegalStateException(
                        "unknown " + ModerateAction.class.getSimpleName() + ": " + other.getAction());
            }
        case MODERATE:
            switch (other.getAction()) {
            case BLOCK:
                return 1;
            case MODERATE:
                return 0;
            case REPORT:
                return -1;
            default:
                throw new IllegalStateException(
                        "unknown " + ModerateAction.class.getSimpleName() + ": " + other.getAction());
            }
        case REPORT:
            switch (other.getAction()) {
            case BLOCK:
                return 1;
            case MODERATE:
                return 1;
            case REPORT:
                return 0;
            default:
                throw new IllegalStateException(
                        "unknown " + ModerateAction.class.getSimpleName() + ": " + other.getAction());
            }
        default:
            throw new IllegalStateException("unknown " + ModerateAction.class.getSimpleName() + ": " + action);
        }
    }
}
