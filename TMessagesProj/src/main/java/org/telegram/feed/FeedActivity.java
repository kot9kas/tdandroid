package org.telegram.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, NotificationCenter.NotificationCenterDelegate {

    private static final int MAX_FEED_ITEMS = 100;
    private static final int TAB_BAR_TOTAL_DP = 72;
    private static final int ICON_SIZE_DP = 30;

    private final List<FeedItem> items = new ArrayList<>();
    private final Map<Long, Integer> unreadLeftByDialog = new HashMap<>();
    private final Set<String> readMarks = new HashSet<>();
    private final Map<Long, ArrayList<MessageObject>> loadedChannelMessages = new HashMap<>();
    private final Set<Long> pendingChannelIds = new HashSet<>();

    private int currentIndex = 0;
    private int currentMediaIndex = 0;
    private boolean expandedText = false;
    private boolean isSwitchAnimating = false;
    private boolean canMarkAsRead = false;
    private boolean firstLoad = true;
    private Runnable markReadRunnable;
    private GestureDetector gestureDetector;

    private FrameLayout postCard;
    private LinearLayout emptyStateView;

    private BackupImageView mediaPreviewView;
    private FrameLayout roundContainer;
    private BackupImageView roundPreviewView;
    private TextView mediaCounterView;

    private BackupImageView channelAvatarView;
    private TextView channelNameView;
    private TextView dateView;
    private LinearLayout bottomInfo;
    private ScrollView textScrollView;
    private TextView bodyView;
    private TextView expandToggleView;

    private ImageView likeIcon;
    private TextView likeLabel;
    private ImageView commentIcon;
    private TextView commentLabel;
    private ImageView forwardIcon;
    private TextView forwardLabel;
    private LinearLayout likeAction;
    private LinearLayout commentAction;
    private LinearLayout forwardAction;

    public FeedActivity() { super(); }
    public FeedActivity(Bundle args) { super(args); }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDidLoad);
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            long dialogId = (Long) args[0];
            int guid = (Integer) args[10];
            if (guid == getClassGuid() && pendingChannelIds.contains(dialogId)) {
                ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[2];
                if (messages != null && !messages.isEmpty()) {
                    loadedChannelMessages.put(dialogId, new ArrayList<>(messages));
                    buildFeedFromLoadedMessages();
                    renderItem(false);
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        boolean isTab = getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
        if (isTab) {
            actionBar.setVisibility(View.GONE);
        } else {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString(R.string.MainTabsFeed));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
        }

        gestureDetector = new GestureDetector(context, new FeedGestureListener());

        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (textScrollView != null && textScrollView.getVisibility() == View.VISIBLE && expandedText) {
                    int[] loc = new int[2];
                    textScrollView.getLocationInWindow(loc);
                    float tx = ev.getRawX(), ty = ev.getRawY();
                    if (tx >= loc[0] && tx <= loc[0] + textScrollView.getWidth()
                            && ty >= loc[1] && ty <= loc[1] + textScrollView.getHeight()) {
                        return false;
                    }
                }
                gestureDetector.onTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_MOVE && ev.getHistorySize() > 0) {
                    if (Math.abs(ev.getY() - ev.getHistoricalY(0)) > AndroidUtilities.dp(8))
                        return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                gestureDetector.onTouchEvent(ev);
                return true;
            }
        };
        root.setBackgroundColor(0xFF000000);

        int tabBarPx = AndroidUtilities.dp(TAB_BAR_TOTAL_DP) + AndroidUtilities.navigationBarHeight;

        // ===== POST CARD =====
        postCard = new FrameLayout(context);
        postCard.setBackgroundColor(0xFF0A0A0A);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        cardLp.bottomMargin = tabBarPx;
        root.addView(postCard, cardLp);

        // -- Media (aspect-fit) --
        mediaPreviewView = new BackupImageView(context);
        mediaPreviewView.setRoundRadius(0);
        mediaPreviewView.getImageReceiver().setAspectFit(true);
        postCard.addView(mediaPreviewView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        // -- Round video --
        int roundSize = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(80),
                AndroidUtilities.dp(240));
        roundContainer = new FrameLayout(context);
        roundPreviewView = new BackupImageView(context);
        roundPreviewView.setRoundRadius(roundSize / 2);
        roundContainer.addView(roundPreviewView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(AndroidUtilities.dp(3), 0xFF7B3DB5);
        ring.setColor(0x00000000);
        roundContainer.setBackground(ring);
        postCard.addView(roundContainer, LayoutHelper.createFrame(roundSize, roundSize, Gravity.CENTER));

        // -- Media counter --
        mediaCounterView = new TextView(context);
        mediaCounterView.setTextColor(Color.WHITE);
        mediaCounterView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        mediaCounterView.setTypeface(AndroidUtilities.bold());
        mediaCounterView.setBackground(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(10), 0x88000000));
        mediaCounterView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3),
                AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        postCard.addView(mediaCounterView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.END, 0, 12, 12, 0));

        // ===== BOTTOM GRADIENT =====
        View bottomGradient = new View(context);
        PaintDrawable grad = new PaintDrawable();
        grad.setShape(new RectShape());
        grad.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, height,
                        new int[]{0x00000000, 0x00000000, 0xDD000000},
                        new float[]{0f, 0.2f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        bottomGradient.setBackground(grad);
        postCard.addView(bottomGradient, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 350, Gravity.BOTTOM));

        // ===== BOTTOM INFO (TikTok-style) =====
        bottomInfo = new LinearLayout(context);
        bottomInfo.setOrientation(LinearLayout.VERTICAL);
        bottomInfo.setPadding(AndroidUtilities.dp(14), 0,
                AndroidUtilities.dp(64), AndroidUtilities.dp(12));

        // Channel row
        LinearLayout channelRow = new LinearLayout(context);
        channelRow.setOrientation(LinearLayout.HORIZONTAL);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);

        channelAvatarView = new BackupImageView(context);
        channelAvatarView.setRoundRadius(AndroidUtilities.dp(14));
        channelAvatarView.setOnClickListener(v -> openCurrentChannelPost());
        channelRow.addView(channelAvatarView, LayoutHelper.createLinear(28, 28));

        channelNameView = new TextView(context);
        channelNameView.setTextColor(Color.WHITE);
        channelNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        channelNameView.setTypeface(AndroidUtilities.bold());
        channelNameView.setSingleLine(true);
        channelNameView.setShadowLayer(8, 0, 2, 0xDD000000);
        channelNameView.setOnClickListener(v -> openCurrentChannelPost());
        LinearLayout.LayoutParams cnLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        cnLp.leftMargin = AndroidUtilities.dp(8);
        channelRow.addView(channelNameView, cnLp);

        dateView = new TextView(context);
        dateView.setTextColor(0xBBFFFFFF);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        dateView.setSingleLine(true);
        dateView.setShadowLayer(6, 0, 1, 0xCC000000);
        LinearLayout.LayoutParams dtLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        dtLp.leftMargin = AndroidUtilities.dp(8);
        channelRow.addView(dateView, dtLp);

        bottomInfo.addView(channelRow, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Text body in a ScrollView with max height
        textScrollView = new ScrollView(context);
        textScrollView.setVerticalScrollBarEnabled(false);
        textScrollView.setFillViewport(false);

        bodyView = new TextView(context);
        bodyView.setTextColor(Color.WHITE);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bodyView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        bodyView.setShadowLayer(6, 0, 1, 0xCC000000);
        bodyView.setMaxLines(3);
        textScrollView.addView(bodyView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams tsLp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        tsLp.topMargin = AndroidUtilities.dp(8);
        bottomInfo.addView(textScrollView, tsLp);

        expandToggleView = new TextView(context);
        expandToggleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        expandToggleView.setTextColor(0xDDFFFFFF);
        expandToggleView.setTypeface(AndroidUtilities.bold());
        expandToggleView.setShadowLayer(6, 0, 1, 0xCC000000);
        expandToggleView.setVisibility(View.GONE);
        expandToggleView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        expandToggleView.setOnClickListener(v -> toggleExpandedText());
        bottomInfo.addView(expandToggleView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        postCard.addView(bottomInfo, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));

        // ===== RIGHT ACTIONS (ImageView icons, center-vertical) =====
        LinearLayout actionsCol = new LinearLayout(context);
        actionsCol.setOrientation(LinearLayout.VERTICAL);
        actionsCol.setGravity(Gravity.CENTER_HORIZONTAL);

        likeAction = new LinearLayout(context);
        likeAction.setOrientation(LinearLayout.VERTICAL);
        likeAction.setGravity(Gravity.CENTER_HORIZONTAL);
        likeAction.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10),
                AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        likeIcon = new ImageView(context);
        likeIcon.setImageResource(R.drawable.feed_heart);
        likeIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        likeIcon.setElevation(AndroidUtilities.dp(4));
        likeAction.addView(likeIcon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(ICON_SIZE_DP), AndroidUtilities.dp(ICON_SIZE_DP)));
        likeLabel = new TextView(context);
        likeLabel.setTextColor(Color.WHITE);
        likeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        likeLabel.setTypeface(AndroidUtilities.bold());
        likeLabel.setGravity(Gravity.CENTER);
        likeLabel.setShadowLayer(6, 0, 1, 0xCC000000);
        likeLabel.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        likeAction.addView(likeLabel, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        likeAction.setOnClickListener(v -> onLike());
        actionsCol.addView(likeAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        commentAction = new LinearLayout(context);
        commentAction.setOrientation(LinearLayout.VERTICAL);
        commentAction.setGravity(Gravity.CENTER_HORIZONTAL);
        commentAction.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10),
                AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        commentIcon = new ImageView(context);
        commentIcon.setImageResource(R.drawable.feed_comment);
        commentIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        commentIcon.setElevation(AndroidUtilities.dp(4));
        commentAction.addView(commentIcon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(ICON_SIZE_DP), AndroidUtilities.dp(ICON_SIZE_DP)));
        commentLabel = new TextView(context);
        commentLabel.setTextColor(Color.WHITE);
        commentLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        commentLabel.setTypeface(AndroidUtilities.bold());
        commentLabel.setGravity(Gravity.CENTER);
        commentLabel.setShadowLayer(6, 0, 1, 0xCC000000);
        commentLabel.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        commentAction.addView(commentLabel, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        commentAction.setOnClickListener(v -> onComment());
        actionsCol.addView(commentAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        forwardAction = new LinearLayout(context);
        forwardAction.setOrientation(LinearLayout.VERTICAL);
        forwardAction.setGravity(Gravity.CENTER_HORIZONTAL);
        forwardAction.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10),
                AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        forwardIcon = new ImageView(context);
        forwardIcon.setImageResource(R.drawable.feed_forward);
        forwardIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        forwardIcon.setElevation(AndroidUtilities.dp(4));
        forwardAction.addView(forwardIcon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(ICON_SIZE_DP), AndroidUtilities.dp(ICON_SIZE_DP)));
        forwardLabel = new TextView(context);
        forwardLabel.setTextColor(Color.WHITE);
        forwardLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        forwardLabel.setTypeface(AndroidUtilities.bold());
        forwardLabel.setGravity(Gravity.CENTER);
        forwardLabel.setShadowLayer(6, 0, 1, 0xCC000000);
        forwardLabel.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        forwardAction.addView(forwardLabel, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        forwardAction.setOnClickListener(v -> onForward());
        actionsCol.addView(forwardAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        postCard.addView(actionsCol, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        // ===== EMPTY STATE =====
        emptyStateView = new LinearLayout(context);
        emptyStateView.setOrientation(LinearLayout.VERTICAL);
        emptyStateView.setGravity(Gravity.CENTER);
        emptyStateView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);

        TextView emptyIcon = new TextView(context);
        emptyIcon.setText("\u2728");
        emptyIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 56);
        emptyStateView.addView(emptyIcon, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView emptyTitle = new TextView(context);
        emptyTitle.setText(LocaleController.getString(R.string.FeedEmptyTitle));
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setTextColor(Color.WHITE);
        emptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        etLp.topMargin = AndroidUtilities.dp(16);
        emptyStateView.addView(emptyTitle, etLp);

        TextView emptyDesc = new TextView(context);
        emptyDesc.setText(LocaleController.getString(R.string.FeedEmptyDesc));
        emptyDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyDesc.setTextColor(0x88FFFFFF);
        emptyDesc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams edLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        edLp.topMargin = AndroidUtilities.dp(8);
        emptyStateView.addView(emptyDesc, edLp);

        root.addView(emptyStateView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        firstLoad = true;
        requestChannelMessages();
        renderItem(false);

        fragmentView = root;
        return fragmentView;
    }

    // ==================== DATA ====================

    private void requestChannelMessages() {
        MessagesController c = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.Dialog> dialogs = c.getDialogs(0);
        pendingChannelIds.clear();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog d = dialogs.get(i);
            if (d == null || !DialogObject.isChatDialog(d.id) || !DialogObject.isChannel(d))
                continue;
            long chatId = -d.id;
            TLRPC.Chat chat = c.getChat(chatId);
            if (chat == null || TextUtils.isEmpty(chat.title)
                    || !ChatObject.isChannelAndNotMegaGroup(chat)) continue;
            if (d.unread_count <= 0) continue;
            pendingChannelIds.add(d.id);
            int count = Math.min(Math.max(d.unread_count + 5, 20), 100);
            c.loadMessages(d.id, 0, false, count, 0, 0, true, 0,
                    getClassGuid(), 0, d.top_message, 0, 0, 0, 0, false);
        }
        buildFeedFromLoadedMessages();
    }

    private void buildFeedFromLoadedMessages() {
        String savedKey = "";
        if (!firstLoad && !items.isEmpty() && currentIndex < items.size()) {
            FeedItem cur = items.get(currentIndex);
            savedKey = cur.dialogId + "_" + cur.messageId;
        }

        items.clear();
        MessagesController c = MessagesController.getInstance(currentAccount);
        for (Long dialogId : pendingChannelIds) {
            TLRPC.Chat chat = c.getChat(-dialogId);
            if (chat == null) continue;
            TLRPC.Dialog dialog = c.dialogs_dict.get(dialogId);
            if (dialog == null) continue;
            ArrayList<MessageObject> msgs = loadedChannelMessages.get(dialogId);
            if (msgs == null || msgs.isEmpty()) {
                ArrayList<MessageObject> dm = c.dialogMessage.get(dialogId);
                if (dm != null && !dm.isEmpty()) msgs = dm;
            }
            if (msgs == null || msgs.isEmpty()) continue;
            int unread = Math.max(0, dialog.unread_count);
            Set<Long> grp = new HashSet<>();
            Set<Integer> ids = new HashSet<>();
            for (int j = 0; j < msgs.size(); j++) {
                MessageObject m = msgs.get(j);
                if (m == null || m.messageOwner == null || m.getId() <= 0) continue;
                if (!m.messageOwner.unread) continue;
                if (m.isVoice() || m.messageOwner.action != null) continue;
                long g = m.getGroupId();
                if (g != 0) { if (grp.contains(g)) continue; grp.add(g); }
                else if (ids.contains(m.getId())) continue;
                ids.add(m.getId());
                items.add(new FeedItem(dialogId, -dialogId, m.getId(), chat.title,
                        FeedDataProvider.getPreview(m), m, unread,
                        FeedDataProvider.collectMedia(msgs, m)));
            }
        }

        Collections.sort(items, (a, b) -> {
            int dA = a.messageObject != null && a.messageObject.messageOwner != null
                    ? a.messageObject.messageOwner.date : 0;
            int dB = b.messageObject != null && b.messageObject.messageOwner != null
                    ? b.messageObject.messageOwner.date : 0;
            if (dA != dB) return Integer.compare(dA, dB);
            return Integer.compare(a.messageId, b.messageId);
        });

        if (items.size() > MAX_FEED_ITEMS) {
            List<FeedItem> t = new ArrayList<>(
                    items.subList(items.size() - MAX_FEED_ITEMS, items.size()));
            items.clear();
            items.addAll(t);
        }

        unreadLeftByDialog.clear();
        for (FeedItem it : items)
            if (!unreadLeftByDialog.containsKey(it.dialogId))
                unreadLeftByDialog.put(it.dialogId, it.unreadCount);

        if (firstLoad) {
            currentIndex = 0;
        } else {
            boolean found = false;
            if (!savedKey.isEmpty()) {
                for (int i = 0; i < items.size(); i++) {
                    if ((items.get(i).dialogId + "_" + items.get(i).messageId).equals(savedKey)) {
                        currentIndex = i;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) currentIndex = 0;
        }

        if (items.isEmpty()) currentIndex = 0;
        else currentIndex = Math.min(currentIndex, items.size() - 1);
        currentMediaIndex = 0;
        expandedText = false;
    }

    // ==================== RENDERING ====================

    private void renderItem(boolean animated) {
        if (items.isEmpty()) {
            postCard.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
            return;
        }
        postCard.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
        FeedItem item = items.get(currentIndex);
        if (animated) animatePostIn();

        ActionAvailabilityResolver.ActionAvailability av =
                ActionAvailabilityResolver.resolve(item);

        channelNameView.setText("@" + item.channelTitle);
        updateChannelAvatar(item);
        updateDate(item);

        boolean hasText = !TextUtils.isEmpty(item.previewText);
        textScrollView.setVisibility(hasText ? View.VISIBLE : View.GONE);
        expandToggleView.setVisibility(View.GONE);
        if (hasText) {
            bodyView.setText(item.previewText);
            bodyView.setMaxLines(expandedText ? Integer.MAX_VALUE : 3);
            if (expandedText) {
                textScrollView.getLayoutParams().height = AndroidUtilities.dp(200);
            } else {
                textScrollView.getLayoutParams().height =
                        LinearLayout.LayoutParams.WRAP_CONTENT;
            }
            textScrollView.scrollTo(0, 0);
            updateExpandToggle();
        }

        renderCurrentMedia(item);
        updateReactionState(item);
        scheduleMarkCurrentAsRead();

        commentAction.setAlpha(av.canComment ? 1f : 0.4f);
        forwardAction.setAlpha(av.canForward ? 1f : 0.4f);
    }

    private void updateReactionState(FeedItem item) {
        if (item.messageObject == null || item.messageObject.messageOwner == null) {
            likeIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            likeLabel.setText(LocaleController.getString(R.string.FeedLike));
            commentLabel.setText(LocaleController.getString(R.string.FeedComment));
            forwardLabel.setText(LocaleController.getString(R.string.FeedForward));
            return;
        }

        TLRPC.Message msg = item.messageObject.messageOwner;

        // Like state + count
        int totalReactions = 0;
        boolean isLiked = false;
        if (msg.reactions != null && msg.reactions.results != null) {
            for (TLRPC.ReactionCount rc : msg.reactions.results) {
                totalReactions += rc.count;
                if (rc.chosen) {
                    isLiked = true;
                }
            }
        }
        if (isLiked) {
            likeIcon.setColorFilter(null);
        } else {
            likeIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }
        likeLabel.setText(totalReactions > 0 ? formatCount(totalReactions)
                : LocaleController.getString(R.string.FeedLike));

        // Comment count
        int commentCount = 0;
        if (msg.replies != null) {
            commentCount = msg.replies.replies;
        }
        commentLabel.setText(commentCount > 0 ? formatCount(commentCount)
                : LocaleController.getString(R.string.FeedComment));

        // Forward count
        int fwdCount = msg.forwards;
        forwardLabel.setText(fwdCount > 0 ? formatCount(fwdCount)
                : LocaleController.getString(R.string.FeedForward));
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }

    private void updateChannelAvatar(FeedItem item) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(item.chatId);
        AvatarDrawable ad = new AvatarDrawable();
        if (chat != null) {
            ad.setInfo(chat);
            channelAvatarView.getImageReceiver().setCurrentAccount(currentAccount);
            channelAvatarView.setForUserOrChat(chat, ad);
        } else {
            channelAvatarView.setImage((ImageLocation) null, "28_28", ad, null);
        }
    }

    private void updateDate(FeedItem item) {
        if (item.messageObject != null && item.messageObject.messageOwner != null) {
            long ms = (long) item.messageObject.messageOwner.date * 1000L;
            dateView.setText(DateUtils.getRelativeTimeSpanString(ms,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
        } else {
            dateView.setText("");
        }
    }

    private void renderCurrentMedia(FeedItem item) {
        mediaPreviewView.setVisibility(View.GONE);
        roundContainer.setVisibility(View.GONE);
        mediaCounterView.setVisibility(View.GONE);

        if (item.mediaItems.isEmpty()) return;
        currentMediaIndex = Math.max(0, Math.min(currentMediaIndex, item.mediaItems.size() - 1));
        FeedMediaItem media = item.mediaItems.get(currentMediaIndex);

        if (item.mediaItems.size() > 1) {
            mediaCounterView.setVisibility(View.VISIBLE);
            mediaCounterView.setText((currentMediaIndex + 1) + "/" + item.mediaItems.size());
        }

        boolean isRound = media.type == FeedMediaItem.Type.ROUND_VIDEO;
        BackupImageView target = isRound ? roundPreviewView : mediaPreviewView;

        if ((media.type == FeedMediaItem.Type.PHOTO || media.type == FeedMediaItem.Type.VIDEO
                || isRound)
                && media.sourceMessage.photoThumbs != null
                && !media.sourceMessage.photoThumbs.isEmpty()
                && media.sourceMessage.photoThumbsObject != null) {
            TLRPC.PhotoSize best = FileLoader.getClosestPhotoSizeWithSize(
                    media.sourceMessage.photoThumbs, AndroidUtilities.dp(720));
            if (best != null) {
                if (isRound) roundContainer.setVisibility(View.VISIBLE);
                else mediaPreviewView.setVisibility(View.VISIBLE);
                target.setImage(
                        ImageLocation.getForObject(best, media.sourceMessage.photoThumbsObject),
                        isRound ? "240_240" : "720_720",
                        (Drawable) null, media.sourceMessage.messageOwner);
                return;
            }
        }

        if (media.sourceMessage.getDocument() != null
                && media.sourceMessage.getDocument().thumbs != null
                && !media.sourceMessage.getDocument().thumbs.isEmpty()) {
            TLRPC.Document doc = media.sourceMessage.getDocument();
            TLRPC.PhotoSize th = FileLoader.getClosestPhotoSizeWithSize(
                    doc.thumbs, AndroidUtilities.dp(720));
            if (th != null) {
                if (isRound) roundContainer.setVisibility(View.VISIBLE);
                else mediaPreviewView.setVisibility(View.VISIBLE);
                target.setImage(ImageLocation.getForDocument(th, doc),
                        isRound ? "240_240" : "720_720", (Drawable) null, doc);
            }
        }
    }

    private void updateExpandToggle() {
        bodyView.post(() -> {
            if (bodyView.getLineCount() > 3 || expandedText) {
                expandToggleView.setVisibility(View.VISIBLE);
                expandToggleView.setText(expandedText
                        ? LocaleController.getString(R.string.FeedCollapseText)
                        : LocaleController.getString(R.string.FeedExpandText));
            } else {
                expandToggleView.setVisibility(View.GONE);
            }
        });
    }

    private void toggleExpandedText() {
        expandedText = !expandedText;
        bodyView.setMaxLines(expandedText ? Integer.MAX_VALUE : 3);
        if (expandedText) {
            textScrollView.getLayoutParams().height = AndroidUtilities.dp(200);
        } else {
            textScrollView.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
            textScrollView.scrollTo(0, 0);
        }
        textScrollView.requestLayout();
        updateExpandToggle();
    }

    // ==================== ACTIONS ====================

    private void onMediaClick() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        if (item.mediaItems.isEmpty()) { openCurrentChannelPost(); return; }
        int idx = Math.max(0, Math.min(currentMediaIndex, item.mediaItems.size() - 1));
        FeedMediaItem media = item.mediaItems.get(idx);
        if (media.sourceMessage == null) return;
        Activity p = getParentActivity();
        if (p == null) return;
        PhotoViewer.getInstance().setParentActivity(p);
        ArrayList<MessageObject> a = new ArrayList<>();
        a.add(media.sourceMessage);
        PhotoViewer.getInstance().openPhoto(a, 0, item.dialogId, 0, 0,
                new PhotoViewer.EmptyPhotoViewerProvider());
    }

    private void onLike() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        if (item.messageObject == null) return;
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> r = new ArrayList<>();
        ReactionsLayoutInBubble.VisibleReaction heart =
                ReactionsLayoutInBubble.VisibleReaction.fromEmojicon("\u2764");
        r.add(heart);
        SendMessagesHelper.getInstance(currentAccount).sendReaction(
                item.messageObject, r, heart, false, true, this,
                () -> AndroidUtilities.runOnUIThread(() -> {
                    likeIcon.setColorFilter(null);
                    Toast.makeText(getContext(),
                            LocaleController.getString(R.string.FeedLikeDone),
                            Toast.LENGTH_SHORT).show();
                }));
    }

    private void onComment() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        ActionAvailabilityResolver.ActionAvailability av =
                ActionAvailabilityResolver.resolve(item);
        if (!av.canComment) {
            Toast.makeText(getContext(), av.commentReason, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", item.chatId);
        if (item.messageId > 0) args.putInt("message_id", item.messageId);
        presentFragment(new ChatActivity(args));
    }

    private void onForward() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        ActionAvailabilityResolver.ActionAvailability av =
                ActionAvailabilityResolver.resolve(item);
        if (!av.canForward) {
            Toast.makeText(getContext(), av.forwardReason, Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.messageObject == null) return;
        ArrayList<MessageObject> a = new ArrayList<>();
        a.add(item.messageObject);
        showDialog(new ShareAlert(getContext(), a, null, false, null, true,
                getResourceProvider()));
    }

    private void openCurrentChannelPost() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        Bundle args = new Bundle();
        args.putLong("chat_id", item.chatId);
        if (item.messageId > 0) args.putInt("message_id", item.messageId);
        presentFragment(new ChatActivity(args));
    }

    // ==================== NAVIGATION ====================

    private void switchPost(boolean next) {
        if (items.isEmpty() || isSwitchAnimating) return;
        int target = next ? Math.min(items.size() - 1, currentIndex + 1)
                : Math.max(0, currentIndex - 1);
        if (target == currentIndex) return;
        firstLoad = false;
        isSwitchAnimating = true;
        float outY = next ? -AndroidUtilities.dp(50) : AndroidUtilities.dp(50);
        postCard.animate().translationY(outY).alpha(0f).setDuration(150)
                .withEndAction(() -> {
                    currentIndex = target;
                    currentMediaIndex = 0;
                    expandedText = false;
                    postCard.setTranslationY(-outY);
                    renderItem(false);
                    scheduleMarkCurrentAsRead();
                    postCard.animate().translationY(0f).alpha(1f).setDuration(200)
                            .withEndAction(() -> isSwitchAnimating = false).start();
                }).start();
    }

    private void animatePostIn() {
        postCard.setAlpha(0f);
        postCard.setTranslationY(AndroidUtilities.dp(30));
        postCard.animate().alpha(1f).translationY(0f).setDuration(200).start();
    }

    private void switchMedia(boolean next) {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        if (item.mediaItems.size() <= 1) {
            if (next) openCurrentChannelPost();
            return;
        }
        int target = next ? (currentMediaIndex + 1) % item.mediaItems.size()
                : (currentMediaIndex - 1 + item.mediaItems.size()) % item.mediaItems.size();
        View animTarget = roundContainer.getVisibility() == View.VISIBLE
                ? roundContainer : mediaPreviewView;
        float outX = next ? -AndroidUtilities.dp(30) : AndroidUtilities.dp(30);
        animTarget.animate().translationX(outX).alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    currentMediaIndex = target;
                    renderCurrentMedia(items.get(currentIndex));
                    animTarget.setTranslationX(-outX);
                    animTarget.animate().translationX(0f).alpha(1f).setDuration(160).start();
                }).start();
    }

    // ==================== MARK AS READ ====================

    private void markCurrentAsRead(FeedItem item) {
        if (!canMarkAsRead || item.messageObject == null
                || item.messageObject.messageOwner == null
                || !item.messageObject.messageOwner.unread) return;
        String key = item.dialogId + "_" + item.messageId;
        if (readMarks.contains(key)) return;
        readMarks.add(key);
        int left = Math.max(0,
                unreadLeftByDialog.getOrDefault(item.dialogId, item.unreadCount) - 1);
        unreadLeftByDialog.put(item.dialogId, left);
        MessagesController.getInstance(currentAccount).markDialogAsRead(
                item.dialogId, item.messageId, item.messageId,
                item.messageObject.messageOwner.date, false, 0, 0, true, 0);
    }

    private void scheduleMarkCurrentAsRead() {
        AndroidUtilities.cancelRunOnUIThread(markReadRunnable);
        if (!canMarkAsRead || items.isEmpty()) return;
        final int idx = currentIndex;
        markReadRunnable = () -> {
            if (!canMarkAsRead || items.isEmpty() || idx != currentIndex) return;
            markCurrentAsRead(items.get(currentIndex));
        };
        AndroidUtilities.runOnUIThread(markReadRunnable, 800);
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        canMarkAsRead = true;
        firstLoad = true;
        loadedChannelMessages.clear();
        requestChannelMessages();
        renderItem(false);
        scheduleMarkCurrentAsRead();
    }

    @Override
    public void onPause() {
        super.onPause();
        canMarkAsRead = false;
        AndroidUtilities.cancelRunOnUIThread(markReadRunnable);
    }

    // ==================== GESTURE LISTENER ====================

    private class FeedGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent e) { return true; }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (items.isEmpty()) return false;
            if (e.getX() > postCard.getWidth() - AndroidUtilities.dp(64)) return false;
            FeedItem item = items.get(currentIndex);
            if (!item.mediaItems.isEmpty()) {
                onMediaClick();
                return true;
            }
            return false;
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                               float velocityX, float velocityY) {
            float dX = e2.getX() - e1.getX();
            float dY = e2.getY() - e1.getY();
            float aX = Math.abs(dX), aY = Math.abs(dY);
            if (aX > aY && aX > AndroidUtilities.dp(60)) {
                if (dX < 0 && velocityX < -280) switchMedia(true);
                else if (dX > 0 && velocityX > 280) switchMedia(false);
                return true;
            }
            if (aY > aX && aY > AndroidUtilities.dp(60)) {
                if (dY < 0 && velocityY < -320) {
                    switchPost(true);
                    return true;
                } else if (dY > 0 && velocityY > 320) {
                    switchPost(false);
                    return true;
                }
            }
            return false;
        }
    }
}
