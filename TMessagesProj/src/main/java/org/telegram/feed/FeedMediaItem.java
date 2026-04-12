package org.telegram.feed;

import androidx.annotation.NonNull;

import org.telegram.messenger.MessageObject;

public class FeedMediaItem {

    public enum Type {
        PHOTO,
        VIDEO,
        ROUND_VIDEO,
        OTHER
    }

    @NonNull
    public final Type type;
    @NonNull
    public final String label;
    @NonNull
    public final MessageObject sourceMessage;

    public FeedMediaItem(@NonNull Type type, @NonNull String label, @NonNull MessageObject sourceMessage) {
        this.type = type;
        this.label = label;
        this.sourceMessage = sourceMessage;
    }
}
