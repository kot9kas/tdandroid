package org.telegram.feed;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedDataProvider {

    @NonNull
    static String getPreview(MessageObject source) {
        if (source == null) return "";
        boolean hasMedia = source.isPhoto() || source.isVideo() || source.isRoundVideo()
                || source.isSticker() || source.isGif()
                || (source.messageOwner != null && source.messageOwner.media != null
                    && !(source.messageOwner.media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaEmpty));
        if (hasMedia) {
            if (source.caption != null && !TextUtils.isEmpty(source.caption)) {
                return source.caption.toString();
            }
            return "";
        }
        if (!TextUtils.isEmpty(source.messageText)) {
            return source.messageText.toString();
        }
        return "";
    }

    @NonNull
    static List<FeedMediaItem> collectMedia(ArrayList<MessageObject> allMessages, MessageObject source) {
        ArrayList<FeedMediaItem> media = new ArrayList<>();
        if (source == null) {
            return media;
        }
        long groupId = source.getGroupId();
        Set<Integer> added = new HashSet<>();

        if (groupId != 0 && allMessages != null) {
            for (int i = 0; i < allMessages.size(); i++) {
                MessageObject message = allMessages.get(i);
                if (message == null || message.getGroupId() != groupId) {
                    continue;
                }
                addMediaIfSupported(media, message, added);
            }
        } else {
            addMediaIfSupported(media, source, added);
        }
        return media;
    }

    static void addMediaIfSupported(List<FeedMediaItem> out, MessageObject message, Set<Integer> addedIds) {
        if (message == null || message.getId() == 0 || addedIds.contains(message.getId())) {
            return;
        }
        FeedMediaItem.Type type;
        String label;
        if (message.isPhoto()) {
            type = FeedMediaItem.Type.PHOTO;
            label = LocaleController.getString(R.string.FeedMediaPhoto);
        } else if (message.isVideo()) {
            type = FeedMediaItem.Type.VIDEO;
            label = LocaleController.getString(R.string.FeedMediaVideo);
        } else if (message.isRoundVideo()) {
            type = FeedMediaItem.Type.ROUND_VIDEO;
            label = LocaleController.getString(R.string.FeedMediaRound);
        } else if (message.messageOwner != null && message.messageOwner.media != null) {
            type = FeedMediaItem.Type.OTHER;
            label = LocaleController.getString(R.string.FeedMediaAttachment);
        } else {
            return;
        }
        out.add(new FeedMediaItem(type, label, message));
        addedIds.add(message.getId());
    }
}
