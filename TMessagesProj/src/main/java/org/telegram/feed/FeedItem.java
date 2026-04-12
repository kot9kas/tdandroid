package org.telegram.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.List;

public class FeedItem {
    public final long dialogId;
    public final long chatId;
    public final int messageId;
    @NonNull public final String channelTitle;
    @NonNull public final String previewText;
    @Nullable public final MessageObject messageObject;
    public final int unreadCount;
    @NonNull public final List<FeedMediaItem> mediaItems;

    public FeedItem(
            long dialogId,
            long chatId,
            int messageId,
            @NonNull String channelTitle,
            @NonNull String previewText,
            @Nullable MessageObject messageObject,
            int unreadCount,
            @NonNull List<FeedMediaItem> mediaItems
    ) {
        this.dialogId = dialogId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.channelTitle = channelTitle;
        this.previewText = previewText;
        this.messageObject = messageObject;
        this.unreadCount = unreadCount;
        this.mediaItems = new ArrayList<>(mediaItems);
    }
}
