package org.telegram.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
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
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.net.URLEncoder;
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
    private static final int ROUND_SIZE_DP = 280;
    private static final int MEDIA_MARGIN_DP = 8;
    private static final int MEDIA_RADIUS_DP = 18;

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

    private FrameLayout mediaContainer;
    private BackupImageView mediaPreviewView;
    private FrameLayout roundContainer;
    private BackupImageView roundPreviewView;

    private LinearLayout dotsContainer;

    private VideoPlayer videoPlayer;
    private android.view.TextureView videoTextureView;
    private android.view.TextureView roundVideoTextureView;
    private ImageView pauseOverlay;
    private boolean isVideoPlaying = false;

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

    private LinearLayout reactionsRow;
    private TextView viewsLabel;

    private View bottomGradientView;
    private LinearLayout messageCard;
    private BackupImageView msgAvatar;
    private TextView msgChannelName;
    private TextView msgDate;
    private TextView msgBody;
    private TextView msgExpandToggle;
    private boolean msgExpandedText = false;
    private ScrollView msgTextScroll;
    private LinearLayout msgReactionsRow;
    private TextView msgViews;

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
        releaseVideoPlayer();
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
            private boolean isTouchInsideView(MotionEvent ev, View view) {
                if (view == null || view.getVisibility() != View.VISIBLE) return false;
                int[] loc = new int[2];
                view.getLocationInWindow(loc);
                float tx = ev.getRawX(), ty = ev.getRawY();
                return tx >= loc[0] && tx <= loc[0] + view.getWidth()
                        && ty >= loc[1] && ty <= loc[1] + view.getHeight();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (isTouchInsideView(ev, expandToggleView)
                        || isTouchInsideView(ev, msgExpandToggle)
                        || isTouchInsideView(ev, msgTextScroll)
                        || isTouchInsideView(ev, messageCard)) {
                    return false;
                }
                if (textScrollView != null && textScrollView.getVisibility() == View.VISIBLE && expandedText) {
                    if (isTouchInsideView(ev, textScrollView)) {
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
                if (!isTouchInsideView(ev, expandToggleView)
                        && !isTouchInsideView(ev, msgExpandToggle)
                        && !isTouchInsideView(ev, messageCard)) {
                    gestureDetector.onTouchEvent(ev);
                }
                return true;
            }
        };
        root.setBackgroundColor(0xFF000000);

        int tabBarPx = AndroidUtilities.dp(TAB_BAR_TOTAL_DP) + AndroidUtilities.navigationBarHeight;

        postCard = new FrameLayout(context);
        postCard.setBackgroundColor(0xFF0A0A0A);
        postCard.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        postCard.setClipToPadding(true);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        cardLp.bottomMargin = tabBarPx;
        root.addView(postCard, cardLp);

        // -- Thumbnail container (rounded corners + padding) --
        mediaContainer = new FrameLayout(context);
        mediaContainer.setClipToOutline(true);
        mediaContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        AndroidUtilities.dp(MEDIA_RADIUS_DP));
            }
        });
        mediaPreviewView = new BackupImageView(context);
        mediaPreviewView.setRoundRadius(0);
        mediaPreviewView.getImageReceiver().setAspectFit(true);
        mediaContainer.addView(mediaPreviewView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        postCard.addView(mediaContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.CENTER, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP));

        videoTextureView = new android.view.TextureView(context);
        videoTextureView.setOpaque(false);
        videoTextureView.setVisibility(View.VISIBLE);
        videoTextureView.setAlpha(0f);
        postCard.addView(videoTextureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.CENTER, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP, MEDIA_MARGIN_DP));
        videoTextureView.setClipToOutline(true);
        videoTextureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        AndroidUtilities.dp(MEDIA_RADIUS_DP));
            }
        });

        // -- Round thumbnail --
        roundContainer = new FrameLayout(context);
        int roundPx = AndroidUtilities.dp(ROUND_SIZE_DP);
        roundPreviewView = new BackupImageView(context);
        roundPreviewView.setRoundRadius(roundPx / 2);
        roundContainer.addView(roundPreviewView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(AndroidUtilities.dp(3), 0xFF7B3DB5);
        ring.setColor(0x00000000);
        roundContainer.setBackground(ring);
        roundContainer.setClipToOutline(true);
        roundContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        postCard.addView(roundContainer, LayoutHelper.createFrame(
                ROUND_SIZE_DP, ROUND_SIZE_DP, Gravity.CENTER));

        roundVideoTextureView = new android.view.TextureView(context);
        roundVideoTextureView.setOpaque(false);
        roundVideoTextureView.setVisibility(View.VISIBLE);
        roundVideoTextureView.setAlpha(0f);
        postCard.addView(roundVideoTextureView, LayoutHelper.createFrame(
                ROUND_SIZE_DP - 6, ROUND_SIZE_DP - 6, Gravity.CENTER));
        roundVideoTextureView.setClipToOutline(true);
        roundVideoTextureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });

        // -- Pause overlay --
        pauseOverlay = new ImageView(context);
        pauseOverlay.setImageResource(R.drawable.ic_action_pause);
        pauseOverlay.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        pauseOverlay.setAlpha(0f);
        pauseOverlay.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(28), 0x66000000));
        pauseOverlay.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        postCard.addView(pauseOverlay, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

        // -- Dot indicators --
        dotsContainer = new LinearLayout(context);
        dotsContainer.setOrientation(LinearLayout.HORIZONTAL);
        dotsContainer.setGravity(Gravity.CENTER);
        dotsContainer.setVisibility(View.GONE);
        postCard.addView(dotsContainer, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 180));

        // -- Bottom gradient --
        bottomGradientView = new View(context);
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
        bottomGradientView.setBackground(grad);
        postCard.addView(bottomGradientView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 350, Gravity.BOTTOM));

        // -- Bottom info (TikTok-style) --
        bottomInfo = new LinearLayout(context);
        bottomInfo.setOrientation(LinearLayout.VERTICAL);
        bottomInfo.setPadding(AndroidUtilities.dp(14), 0,
                AndroidUtilities.dp(64), AndroidUtilities.dp(12));

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

        reactionsRow = new LinearLayout(context);
        reactionsRow.setOrientation(LinearLayout.HORIZONTAL);
        reactionsRow.setGravity(Gravity.CENTER_VERTICAL);
        reactionsRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams rrLp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        rrLp.topMargin = AndroidUtilities.dp(8);
        bottomInfo.addView(reactionsRow, rrLp);

        viewsLabel = new TextView(context);
        viewsLabel.setTextColor(0x99FFFFFF);
        viewsLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        viewsLabel.setSingleLine(true);
        viewsLabel.setShadowLayer(4, 0, 1, 0xAA000000);
        viewsLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams vlLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        vlLp.topMargin = AndroidUtilities.dp(4);
        bottomInfo.addView(viewsLabel, vlLp);

        postCard.addView(bottomInfo, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));

        // -- Right actions --
        LinearLayout actionsCol = new LinearLayout(context);
        actionsCol.setOrientation(LinearLayout.VERTICAL);
        actionsCol.setGravity(Gravity.CENTER_HORIZONTAL);

        likeAction = buildActionButton(context, R.drawable.feed_heart);
        likeIcon = (ImageView) likeAction.getChildAt(0);
        likeLabel = (TextView) likeAction.getChildAt(1);
        likeIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        likeAction.setOnClickListener(v -> onLike());
        actionsCol.addView(likeAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        commentAction = buildActionButton(context, R.drawable.feed_comment);
        commentIcon = (ImageView) commentAction.getChildAt(0);
        commentLabel = (TextView) commentAction.getChildAt(1);
        commentIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        commentAction.setOnClickListener(v -> onComment());
        actionsCol.addView(commentAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        forwardAction = buildActionButton(context, R.drawable.feed_forward);
        forwardIcon = (ImageView) forwardAction.getChildAt(0);
        forwardLabel = (TextView) forwardAction.getChildAt(1);
        forwardIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        forwardAction.setOnClickListener(v -> onForward());
        actionsCol.addView(forwardAction, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        postCard.addView(actionsCol, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM, 0, 0, 4, 100));

        // -- Message card (for text-only posts, fills entire postCard area) --
        messageCard = new LinearLayout(context);
        messageCard.setOrientation(LinearLayout.VERTICAL);
        messageCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        messageCard.setVisibility(View.GONE);

        LinearLayout mcHeader = new LinearLayout(context);
        mcHeader.setOrientation(LinearLayout.HORIZONTAL);
        mcHeader.setGravity(Gravity.CENTER_VERTICAL);

        msgAvatar = new BackupImageView(context);
        msgAvatar.setRoundRadius(AndroidUtilities.dp(18));
        msgAvatar.setOnClickListener(v -> openCurrentChannelPost());
        mcHeader.addView(msgAvatar, LayoutHelper.createLinear(36, 36));

        msgChannelName = new TextView(context);
        msgChannelName.setTextColor(Color.WHITE);
        msgChannelName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        msgChannelName.setTypeface(AndroidUtilities.bold());
        msgChannelName.setSingleLine(true);
        msgChannelName.setOnClickListener(v -> openCurrentChannelPost());
        LinearLayout.LayoutParams mcnLp = LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f);
        mcnLp.leftMargin = AndroidUtilities.dp(10);
        mcHeader.addView(msgChannelName, mcnLp);

        msgDate = new TextView(context);
        msgDate.setTextColor(0x99FFFFFF);
        msgDate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        msgDate.setSingleLine(true);
        mcHeader.addView(msgDate, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        messageCard.addView(mcHeader, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View mcDivider = new View(context);
        mcDivider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams divLp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 1);
        divLp.topMargin = AndroidUtilities.dp(12);
        messageCard.addView(mcDivider, divLp);

        msgTextScroll = new ScrollView(context);
        msgTextScroll.setVerticalScrollBarEnabled(false);
        msgTextScroll.setFillViewport(false);
        msgBody = new TextView(context);
        msgBody.setTextColor(0xFFF0F0F5);
        msgBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        msgBody.setLineSpacing(AndroidUtilities.dp(4), 1f);
        msgTextScroll.addView(msgBody, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams mtLp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f);
        mtLp.topMargin = AndroidUtilities.dp(12);
        messageCard.addView(msgTextScroll, mtLp);

        msgExpandToggle = new TextView(context);
        msgExpandToggle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        msgExpandToggle.setTextColor(0xFF7B8EFF);
        msgExpandToggle.setTypeface(AndroidUtilities.bold());
        msgExpandToggle.setVisibility(View.GONE);
        msgExpandToggle.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        msgExpandToggle.setOnClickListener(v -> {
            msgExpandedText = !msgExpandedText;
            msgBody.setMaxLines(msgExpandedText ? Integer.MAX_VALUE : Integer.MAX_VALUE);
            if (!msgExpandedText) {
                msgTextScroll.scrollTo(0, 0);
            }
            msgExpandToggle.setText(msgExpandedText
                    ? LocaleController.getString(R.string.FeedCollapseText)
                    : LocaleController.getString(R.string.FeedExpandText));
        });
        messageCard.addView(msgExpandToggle, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        msgReactionsRow = new LinearLayout(context);
        msgReactionsRow.setOrientation(LinearLayout.HORIZONTAL);
        msgReactionsRow.setGravity(Gravity.CENTER_VERTICAL);
        msgReactionsRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams mrrLp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        mrrLp.topMargin = AndroidUtilities.dp(10);
        messageCard.addView(msgReactionsRow, mrrLp);

        msgViews = new TextView(context);
        msgViews.setTextColor(0x77FFFFFF);
        msgViews.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        msgViews.setSingleLine(true);
        msgViews.setVisibility(View.GONE);
        LinearLayout.LayoutParams mvLp = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        mvLp.topMargin = AndroidUtilities.dp(6);
        messageCard.addView(msgViews, mvLp);

        postCard.addView(messageCard, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // -- Empty state --
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

    private LinearLayout buildActionButton(Context ctx, int iconRes) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10),
                AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(iconRes);
        icon.setElevation(AndroidUtilities.dp(4));
        wrap.addView(icon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(ICON_SIZE_DP), AndroidUtilities.dp(ICON_SIZE_DP)));
        TextView lbl = new TextView(ctx);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        lbl.setTypeface(AndroidUtilities.bold());
        lbl.setGravity(Gravity.CENTER);
        lbl.setShadowLayer(6, 0, 1, 0xCC000000);
        lbl.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        wrap.addView(lbl, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        return wrap;
    }

    // ==================== DOT INDICATORS ====================

    private void updateDots(FeedItem item) {
        dotsContainer.removeAllViews();
        if (item.mediaItems.size() <= 1) { dotsContainer.setVisibility(View.GONE); return; }
        dotsContainer.setVisibility(View.VISIBLE);
        Context ctx = dotsContainer.getContext();
        for (int i = 0; i < item.mediaItems.size(); i++) {
            View dot = new View(ctx);
            boolean active = i == currentMediaIndex;
            int size = AndroidUtilities.dp(active ? 8 : 6);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(active ? Color.WHITE : 0x88FFFFFF);
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
            final int idx = i;
            dot.setOnClickListener(v -> {
                if (idx == currentMediaIndex) return;
                releaseVideoPlayer();
                currentMediaIndex = idx;
                renderCurrentMedia(items.get(currentIndex));
                tryAutoPlayVideo(items.get(currentIndex));
                updateDots(items.get(currentIndex));
            });
            dotsContainer.addView(dot, lp);
        }
    }

    // ==================== VIDEO PLAYBACK (MediaController pattern) ====================

    private void tryAutoPlayVideo(FeedItem item) {
        releaseVideoPlayer();

        if (item.mediaItems.isEmpty()) return;
        int idx = Math.max(0, Math.min(currentMediaIndex, item.mediaItems.size() - 1));
        FeedMediaItem media = item.mediaItems.get(idx);
        if (media.type != FeedMediaItem.Type.VIDEO && media.type != FeedMediaItem.Type.ROUND_VIDEO)
            return;
        if (media.sourceMessage == null || media.sourceMessage.getDocument() == null)
            return;

        boolean isRound = media.type == FeedMediaItem.Type.ROUND_VIDEO;
        android.view.TextureView targetTexture = isRound ? roundVideoTextureView : videoTextureView;

        Uri videoUri = buildVideoUri(media.sourceMessage);
        if (videoUri == null) return;

        if (targetTexture.isAvailable()) {
            startVideoPlayer(targetTexture, videoUri);
        } else {
            targetTexture.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int w, int h) {
                    startVideoPlayer(targetTexture, videoUri);
                    targetTexture.setSurfaceTextureListener(null);
                }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture s, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture s) { return true; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture s) {}
            });
        }
    }

    private void startVideoPlayer(android.view.TextureView targetTexture, Uri videoUri) {
        try {
            videoPlayer = new VideoPlayer();
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override public void onStateChanged(boolean playWhenReady, int state) {}
                @Override public void onError(VideoPlayer player, Exception e) {
                    FileLog.e("FeedActivity video error", e);
                }
                @Override public void onVideoSizeChanged(int w, int h, int rot, float pix) {}
                @Override public void onRenderedFirstFrame() {
                    AndroidUtilities.runOnUIThread(() -> targetTexture.setAlpha(1f));
                }
                @Override public boolean onSurfaceDestroyed(android.graphics.SurfaceTexture st) { return false; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {}
            });

            videoPlayer.setTextureView(targetTexture);
            videoPlayer.preparePlayer(videoUri, "other");
            videoPlayer.setLooping(true);
            videoPlayer.play();
            isVideoPlaying = true;
            targetTexture.setAlpha(1f);
        } catch (Exception e) {
            FileLog.e("FeedActivity tryAutoPlay", e);
            releaseVideoPlayer();
        }
    }

    private Uri buildVideoUri(MessageObject messageObject) {
        if (messageObject == null || messageObject.getDocument() == null) return null;

        TLRPC.Document document = messageObject.getDocument();
        File cacheFile = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);

        if (cacheFile != null && cacheFile.exists()) {
            return Uri.fromFile(cacheFile);
        }

        File attachFile = FileLoader.getInstance(currentAccount).getPathToAttach(document);
        if (attachFile != null && attachFile.exists()) {
            return Uri.fromFile(attachFile);
        }

        try {
            int reference = FileLoader.getInstance(currentAccount).getFileReference(messageObject);
            String params = "?account=" + currentAccount +
                    "&id=" + document.id +
                    "&hash=" + document.access_hash +
                    "&dc=" + document.dc_id +
                    "&size=" + document.size +
                    "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                    "&rid=" + reference +
                    "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                    "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
            return Uri.parse("tg://" + messageObject.getFileName() + params);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void toggleVideoPlayback() {
        if (videoPlayer == null) return;

        if (isVideoPlaying) {
            videoPlayer.pause();
            isVideoPlaying = false;
            pauseOverlay.setImageResource(R.drawable.ic_action_play);
            pauseOverlay.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            pauseOverlay.animate().alpha(1f).setDuration(150).start();
        } else {
            videoPlayer.play();
            isVideoPlaying = true;
            pauseOverlay.setImageResource(R.drawable.ic_action_pause);
            pauseOverlay.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            pauseOverlay.animate().alpha(1f).setDuration(150).withEndAction(() ->
                    pauseOverlay.animate().alpha(0f).setStartDelay(400).setDuration(300).start()
            ).start();
        }
    }

    private void releaseVideoPlayer() {
        if (videoPlayer != null) {
            try { videoPlayer.releasePlayer(true); } catch (Exception ignored) {}
            videoPlayer = null;
        }
        isVideoPlaying = false;
        if (videoTextureView != null) {
            videoTextureView.setAlpha(0f);
            videoTextureView.setSurfaceTextureListener(null);
        }
        if (roundVideoTextureView != null) {
            roundVideoTextureView.setAlpha(0f);
            roundVideoTextureView.setSurfaceTextureListener(null);
        }
        if (pauseOverlay != null) pauseOverlay.setAlpha(0f);
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
            int dA = a.messageObject != null && a.messageObject.messageOwner != null ? a.messageObject.messageOwner.date : 0;
            int dB = b.messageObject != null && b.messageObject.messageOwner != null ? b.messageObject.messageOwner.date : 0;
            if (dA != dB) return Integer.compare(dA, dB);
            return Integer.compare(a.messageId, b.messageId);
        });
        if (items.size() > MAX_FEED_ITEMS) {
            List<FeedItem> t = new ArrayList<>(items.subList(items.size() - MAX_FEED_ITEMS, items.size()));
            items.clear(); items.addAll(t);
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
                        currentIndex = i; found = true; break;
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
            messageCard.setVisibility(View.GONE);
            releaseVideoPlayer();
            return;
        }
        postCard.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
        FeedItem item = items.get(currentIndex);
        if (animated) animatePostIn();

        ActionAvailabilityResolver.ActionAvailability av = ActionAvailabilityResolver.resolve(item);

        boolean isTextOnly = item.mediaItems.isEmpty();

        if (isTextOnly) {
            renderMessageCardMode(item, av);
        } else {
            renderTikTokMode(item, av);
        }

        updateReactionState(item);
        scheduleMarkCurrentAsRead();
    }

    private void renderTikTokMode(FeedItem item, ActionAvailabilityResolver.ActionAvailability av) {
        messageCard.setVisibility(View.GONE);
        postCard.setBackgroundColor(0xFF0A0A0A);
        mediaContainer.setVisibility(View.VISIBLE);
        bottomGradientView.setVisibility(View.VISIBLE);
        bottomInfo.setVisibility(View.VISIBLE);

        channelNameView.setText("@" + item.channelTitle);
        updateChannelAvatar(item);
        updateDate(item);

        boolean hasText = !TextUtils.isEmpty(item.previewText);
        textScrollView.setVisibility(View.VISIBLE);
        expandToggleView.setVisibility(View.GONE);
        if (hasText) {
            bodyView.setText(item.previewText);
            bodyView.setTextColor(Color.WHITE);
            bodyView.setMaxLines(expandedText ? Integer.MAX_VALUE : 3);
            textScrollView.getLayoutParams().height = expandedText
                    ? AndroidUtilities.dp(200) : LinearLayout.LayoutParams.WRAP_CONTENT;
            textScrollView.scrollTo(0, 0);
            updateExpandToggle();
        } else {
            bodyView.setText(LocaleController.getString(R.string.FeedNoDescription));
            bodyView.setTextColor(0x66FFFFFF);
            bodyView.setMaxLines(1);
            textScrollView.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
        }

        renderCurrentMedia(item);
        tryAutoPlayVideo(item);
        updateDots(item);

        commentAction.setAlpha(av.canComment ? 1f : 0.4f);
        forwardAction.setAlpha(av.canForward ? 1f : 0.4f);
    }

    private void renderMessageCardMode(FeedItem item, ActionAvailabilityResolver.ActionAvailability av) {
        releaseVideoPlayer();
        mediaContainer.setVisibility(View.GONE);
        roundContainer.setVisibility(View.GONE);
        dotsContainer.setVisibility(View.GONE);
        bottomGradientView.setVisibility(View.GONE);
        bottomInfo.setVisibility(View.GONE);
        messageCard.setVisibility(View.VISIBLE);
        postCard.setBackgroundColor(0xFF1E1E2E);

        msgChannelName.setText(item.channelTitle);
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(item.chatId);
        AvatarDrawable ad = new AvatarDrawable();
        if (chat != null) {
            ad.setInfo(chat);
            msgAvatar.getImageReceiver().setCurrentAccount(currentAccount);
            msgAvatar.setForUserOrChat(chat, ad);
        }

        if (item.messageObject != null && item.messageObject.messageOwner != null) {
            long ms = (long) item.messageObject.messageOwner.date * 1000L;
            msgDate.setText(DateUtils.getRelativeTimeSpanString(ms,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
        } else {
            msgDate.setText("");
        }

        boolean hasText = !TextUtils.isEmpty(item.previewText);
        msgTextScroll.setVisibility(hasText ? View.VISIBLE : View.GONE);
        msgExpandToggle.setVisibility(View.GONE);
        msgExpandedText = false;
        if (hasText) {
            msgBody.setText(item.previewText);
            msgBody.setMaxLines(Integer.MAX_VALUE);
            msgTextScroll.scrollTo(0, 0);
        }

        if (item.messageObject != null && item.messageObject.messageOwner != null) {
            TLRPC.Message msg = item.messageObject.messageOwner;
            buildInlineReactionsInto(msgReactionsRow, msg);
            if (msg.views > 0) {
                msgViews.setVisibility(View.VISIBLE);
                msgViews.setText("\uD83D\uDC41 " + formatCount(msg.views));
            } else {
                msgViews.setVisibility(View.GONE);
            }
        } else {
            msgReactionsRow.setVisibility(View.GONE);
            msgViews.setVisibility(View.GONE);
        }

        commentAction.setAlpha(av.canComment ? 1f : 0.4f);
        forwardAction.setAlpha(av.canForward ? 1f : 0.4f);
    }

    private void updateReactionState(FeedItem item) {
        if (item.messageObject == null || item.messageObject.messageOwner == null) {
            likeIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            likeLabel.setText(LocaleController.getString(R.string.FeedLike));
            commentLabel.setText(LocaleController.getString(R.string.FeedComment));
            forwardLabel.setText(LocaleController.getString(R.string.FeedForward));
            reactionsRow.setVisibility(View.GONE);
            viewsLabel.setVisibility(View.GONE);
            return;
        }
        TLRPC.Message msg = item.messageObject.messageOwner;

        int totalReactions = 0;
        boolean isLiked = false;
        if (msg.reactions != null && msg.reactions.results != null) {
            for (TLRPC.ReactionCount rc : msg.reactions.results) {
                totalReactions += rc.count;
                if (rc.chosen) isLiked = true;
            }
        }
        likeIcon.setColorFilter(isLiked ? null : new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        likeLabel.setText(totalReactions > 0 ? formatCount(totalReactions) : LocaleController.getString(R.string.FeedLike));
        int commentCount = msg.replies != null ? msg.replies.replies : 0;
        commentLabel.setText(commentCount > 0 ? formatCount(commentCount) : LocaleController.getString(R.string.FeedComment));
        int fwdCount = msg.forwards;
        forwardLabel.setText(fwdCount > 0 ? formatCount(fwdCount) : LocaleController.getString(R.string.FeedForward));

        buildInlineReactions(msg);
        updateViewsCount(msg);
    }

    private void buildInlineReactions(TLRPC.Message msg) {
        buildInlineReactionsInto(reactionsRow, msg);
    }

    private void buildInlineReactionsInto(LinearLayout target, TLRPC.Message msg) {
        target.removeAllViews();
        if (msg.reactions == null || msg.reactions.results == null || msg.reactions.results.isEmpty()) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setVisibility(View.VISIBLE);
        Context ctx = target.getContext();
        for (TLRPC.ReactionCount rc : msg.reactions.results) {
            String emoji = "";
            if (rc.reaction instanceof TLRPC.TL_reactionEmoji) {
                emoji = ((TLRPC.TL_reactionEmoji) rc.reaction).emoticon;
            }
            if (emoji.isEmpty()) continue;

            LinearLayout pill = new LinearLayout(ctx);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(AndroidUtilities.dp(12));
            pillBg.setColor(rc.chosen ? 0x44FF4466 : 0x33FFFFFF);
            pill.setBackground(pillBg);
            pill.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4),
                    AndroidUtilities.dp(8), AndroidUtilities.dp(4));

            TextView emojiView = new TextView(ctx);
            emojiView.setText(emoji);
            emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pill.addView(emojiView, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView countView = new TextView(ctx);
            countView.setText(formatCount(rc.count));
            countView.setTextColor(Color.WHITE);
            countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            countView.setTypeface(AndroidUtilities.bold());
            LinearLayout.LayoutParams cLp = LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            cLp.leftMargin = AndroidUtilities.dp(4);
            pill.addView(countView, cLp);

            LinearLayout.LayoutParams pLp = LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            pLp.rightMargin = AndroidUtilities.dp(6);
            target.addView(pill, pLp);
        }
    }

    private void updateViewsCount(TLRPC.Message msg) {
        if (msg.views > 0) {
            viewsLabel.setVisibility(View.VISIBLE);
            viewsLabel.setText("\uD83D\uDC41 " + formatCount(msg.views));
        } else {
            viewsLabel.setVisibility(View.GONE);
        }
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
        mediaContainer.setVisibility(View.GONE);
        roundContainer.setVisibility(View.GONE);
        if (item.mediaItems.isEmpty()) return;
        currentMediaIndex = Math.max(0, Math.min(currentMediaIndex, item.mediaItems.size() - 1));
        FeedMediaItem media = item.mediaItems.get(currentMediaIndex);
        boolean isRound = media.type == FeedMediaItem.Type.ROUND_VIDEO;
        BackupImageView target = isRound ? roundPreviewView : mediaPreviewView;
        if ((media.type == FeedMediaItem.Type.PHOTO || media.type == FeedMediaItem.Type.VIDEO || isRound)
                && media.sourceMessage.photoThumbs != null && !media.sourceMessage.photoThumbs.isEmpty()
                && media.sourceMessage.photoThumbsObject != null) {
            TLRPC.PhotoSize best = FileLoader.getClosestPhotoSizeWithSize(media.sourceMessage.photoThumbs, 1280);
            if (best != null) {
                if (isRound) roundContainer.setVisibility(View.VISIBLE);
                else mediaContainer.setVisibility(View.VISIBLE);
                target.setImage(ImageLocation.getForObject(best, media.sourceMessage.photoThumbsObject),
                        isRound ? (ROUND_SIZE_DP + "_" + ROUND_SIZE_DP) : "1280_1280",
                        (Drawable) null, media.sourceMessage.messageOwner);
                return;
            }
        }
        if (media.sourceMessage.getDocument() != null && media.sourceMessage.getDocument().thumbs != null
                && !media.sourceMessage.getDocument().thumbs.isEmpty()) {
            TLRPC.Document doc = media.sourceMessage.getDocument();
            TLRPC.PhotoSize th = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 1280);
            if (th != null) {
                if (isRound) roundContainer.setVisibility(View.VISIBLE);
                else mediaContainer.setVisibility(View.VISIBLE);
                target.setImage(ImageLocation.getForDocument(th, doc),
                        isRound ? (ROUND_SIZE_DP + "_" + ROUND_SIZE_DP) : "1280_1280",
                        (Drawable) null, doc);
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
        if (media.type == FeedMediaItem.Type.VIDEO || media.type == FeedMediaItem.Type.ROUND_VIDEO) {
            toggleVideoPlayback();
            return;
        }
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
        ReactionsLayoutInBubble.VisibleReaction heart = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon("\u2764");
        r.add(heart);
        SendMessagesHelper.getInstance(currentAccount).sendReaction(
                item.messageObject, r, heart, false, true, this,
                () -> AndroidUtilities.runOnUIThread(() -> {
                    likeIcon.setColorFilter(null);
                    Toast.makeText(getContext(), LocaleController.getString(R.string.FeedLikeDone), Toast.LENGTH_SHORT).show();
                }));
    }

    private void onComment() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        ActionAvailabilityResolver.ActionAvailability av = ActionAvailabilityResolver.resolve(item);
        if (!av.canComment) { Toast.makeText(getContext(), av.commentReason, Toast.LENGTH_SHORT).show(); return; }
        Bundle args = new Bundle();
        args.putLong("chat_id", item.chatId);
        if (item.messageId > 0) args.putInt("message_id", item.messageId);
        presentFragment(new ChatActivity(args));
    }

    private void onForward() {
        if (items.isEmpty()) return;
        FeedItem item = items.get(currentIndex);
        ActionAvailabilityResolver.ActionAvailability av = ActionAvailabilityResolver.resolve(item);
        if (!av.canForward) { Toast.makeText(getContext(), av.forwardReason, Toast.LENGTH_SHORT).show(); return; }
        if (item.messageObject == null) return;
        ArrayList<MessageObject> a = new ArrayList<>();
        a.add(item.messageObject);
        showDialog(new ShareAlert(getContext(), a, null, false, null, true, getResourceProvider()));
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
        int target = next ? Math.min(items.size() - 1, currentIndex + 1) : Math.max(0, currentIndex - 1);
        if (target == currentIndex) return;
        firstLoad = false;
        releaseVideoPlayer();
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
        if (item.mediaItems.size() <= 1) { if (next) openCurrentChannelPost(); return; }
        releaseVideoPlayer();
        int target = next ? (currentMediaIndex + 1) % item.mediaItems.size()
                : (currentMediaIndex - 1 + item.mediaItems.size()) % item.mediaItems.size();
        View animTarget = roundContainer.getVisibility() == View.VISIBLE ? roundContainer : mediaContainer;
        float outX = next ? -AndroidUtilities.dp(30) : AndroidUtilities.dp(30);
        animTarget.animate().translationX(outX).alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    currentMediaIndex = target;
                    renderCurrentMedia(items.get(currentIndex));
                    tryAutoPlayVideo(items.get(currentIndex));
                    updateDots(items.get(currentIndex));
                    animTarget.setTranslationX(-outX);
                    animTarget.animate().translationX(0f).alpha(1f).setDuration(160).start();
                }).start();
    }

    // ==================== MARK AS READ ====================

    private void markCurrentAsRead(FeedItem item) {
        if (!canMarkAsRead || item.messageObject == null || item.messageObject.messageOwner == null
                || !item.messageObject.messageOwner.unread) return;
        String key = item.dialogId + "_" + item.messageId;
        if (readMarks.contains(key)) return;
        readMarks.add(key);
        int left = Math.max(0, unreadLeftByDialog.getOrDefault(item.dialogId, item.unreadCount) - 1);
        unreadLeftByDialog.put(item.dialogId, left);
        MessagesController.getInstance(currentAccount).markDialogAsRead(
                item.dialogId, item.messageId, item.messageId,
                item.messageObject.messageOwner.date, false, 0, 1, true, 0);
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
        releaseVideoPlayer();
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
            if (!item.mediaItems.isEmpty()) { onMediaClick(); return true; }
            return false;
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                               float velocityX, float velocityY) {
            float dX = e2.getX() - e1.getX(), dY = e2.getY() - e1.getY();
            float aX = Math.abs(dX), aY = Math.abs(dY);
            if (aX > aY && aX > AndroidUtilities.dp(60)) {
                if (dX < 0 && velocityX < -280) switchMedia(true);
                else if (dX > 0 && velocityX > 280) switchMedia(false);
                return true;
            }
            if (aY > aX && aY > AndroidUtilities.dp(60)) {
                if (dY < 0 && velocityY < -320) { switchPost(true); return true; }
                else if (dY > 0 && velocityY > 320) { switchPost(false); return true; }
            }
            return false;
        }
    }
}
