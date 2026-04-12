package org.telegram.feed;

import androidx.annotation.NonNull;

import org.telegram.messenger.MessageObject;

public class ActionAvailabilityResolver {

    public static final class ActionAvailability {
        public final boolean canLike;
        public final boolean canComment;
        public final boolean canForward;
        @NonNull public final String commentReason;
        @NonNull public final String forwardReason;

        ActionAvailability(
                boolean canLike,
                boolean canComment,
                boolean canForward,
                @NonNull String commentReason,
                @NonNull String forwardReason
        ) {
            this.canLike = canLike;
            this.canComment = canComment;
            this.canForward = canForward;
            this.commentReason = commentReason;
            this.forwardReason = forwardReason;
        }
    }

    @NonNull
    public static ActionAvailability resolve(FeedItem item) {
        MessageObject message = item.messageObject;
        if (message == null) {
            return new ActionAvailability(false, false, false, "Comments unavailable", "Forward unavailable");
        }

        boolean canLike = message.canSetReaction();
        boolean canComment = message.canViewThread() && message.isComments();
        boolean canForward = message.canForwardMessage();

        return new ActionAvailability(
                canLike,
                canComment,
                canForward,
                canComment ? "" : "Comments are disabled for this post",
                canForward ? "" : "Forwarding is disabled for this post"
        );
    }
}
