package org.telegram.litegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LitegramVaultActivity extends BaseFragment {

    public static final int MODE_DELETED = 0;

    private static final int COLOR_PURPLE = 0xFF7B5EA7;

    private LinearLayout listContainer;
    private TextView emptyView;
    private int loadedCount = 0;
    private static final int PAGE_SIZE = 50;
    private static final int MAX_COLLAPSED_LINES = 6;

    private MediaPlayer activePlayer;
    private View activePlayerRow;
    private Handler progressHandler = new Handler(Looper.getMainLooper());

    public LitegramVaultActivity(int mode) {
        super();
    }

    public LitegramVaultActivity(Bundle args) {
        super(args);
    }

    private int c(int key) {
        return getThemedColor(key);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(c(Theme.key_actionBarDefault));
        actionBar.setTitleColor(c(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(c(Theme.key_actionBarDefaultIcon), false);
        actionBar.setCastShadows(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.LitegramVaultDeleted));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(c(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(80));

        scrollView.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(LocaleController.getString(R.string.LitegramVaultDeletedEmpty));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        loadMessages();

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopActivePlayer();
    }

    private void stopActivePlayer() {
        progressHandler.removeCallbacksAndMessages(null);
        if (activePlayer != null) {
            try {
                activePlayer.stop();
                activePlayer.release();
            } catch (Exception ignored) {}
            activePlayer = null;
        }
        activePlayerRow = null;
    }

    private void loadMessages() {
        List<LitegramStorage.SavedMessage> items =
                LitegramStorage.getInstance().getDeletedMessages(PAGE_SIZE, loadedCount);
        loadedCount += items.size();

        if (items.isEmpty() && loadedCount == 0) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        Context context = listContainer.getContext();
        for (LitegramStorage.SavedMessage msg : items) {
            listContainer.addView(createMessageCard(context, msg));
        }

        if (items.size() == PAGE_SIZE) {
            TextView loadMore = new TextView(context);
            loadMore.setText(LocaleController.getString(R.string.LitegramVaultLoadMore));
            loadMore.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            loadMore.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));
            loadMore.setTypeface(AndroidUtilities.bold());
            loadMore.setGravity(Gravity.CENTER);
            loadMore.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
            loadMore.setOnClickListener(v -> {
                listContainer.removeView(v);
                loadMessages();
            });
            listContainer.addView(loadMore);
        }
    }

    private View createMessageCard(Context context, LitegramStorage.SavedMessage msg) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        bg.setColor(c(Theme.key_windowBackgroundWhite));
        card.setBackground(bg);
        int hp = AndroidUtilities.dp(14);
        card.setPadding(hp, AndroidUtilities.dp(12), hp, AndroidUtilities.dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = AndroidUtilities.dp(12);
        cardLp.rightMargin = AndroidUtilities.dp(12);
        cardLp.bottomMargin = AndroidUtilities.dp(8);
        card.setLayoutParams(cardLp);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout headerText = new LinearLayout(context);
        headerText.setOrientation(LinearLayout.VERTICAL);

        TextView senderView = new TextView(context);
        senderView.setText(msg.senderName != null && !msg.senderName.isEmpty() ? msg.senderName : "ID: " + msg.fromId);
        senderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        senderView.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));
        senderView.setTypeface(AndroidUtilities.bold());
        senderView.setMaxLines(1);
        senderView.setEllipsize(TextUtils.TruncateAt.END);
        final long senderId = msg.fromId;
        senderView.setOnClickListener(v -> openUserChat(senderId));
        headerText.addView(senderView);

        if (msg.chatTitle != null && !msg.chatTitle.isEmpty()
                && !msg.chatTitle.equals(msg.senderName)) {
            TextView chatView = new TextView(context);
            chatView.setText(msg.chatTitle);
            chatView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            chatView.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));
            chatView.setMaxLines(1);
            chatView.setEllipsize(TextUtils.TruncateAt.END);
            final long dialogId = msg.dialogId;
            chatView.setOnClickListener(v -> openUserChat(dialogId));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = AndroidUtilities.dp(1);
            headerText.addView(chatView, clp);
        }

        header.addView(headerText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView dateView = new TextView(context);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault());
            dateView.setText(sdf.format(new Date((long) msg.date * 1000)));
        } catch (Exception e) {
            dateView.setText("");
        }
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        dateView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText4));
        header.addView(dateView);

        card.addView(header);

        if (msg.text != null && !msg.text.isEmpty()) {
            TextView textView = new TextView(context);
            textView.setText(msg.text);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
            textView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = AndroidUtilities.dp(8);
            card.addView(textView, tlp);

            textView.post(() -> {
                if (textView.getLineCount() > MAX_COLLAPSED_LINES) {
                    textView.setMaxLines(MAX_COLLAPSED_LINES);
                    textView.setEllipsize(TextUtils.TruncateAt.END);

                    TextView expandBtn = new TextView(context);
                    expandBtn.setText(LocaleController.getString(R.string.LitegramVaultExpand));
                    expandBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    expandBtn.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));
                    expandBtn.setTypeface(AndroidUtilities.bold());
                    expandBtn.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                    final boolean[] expanded = {false};
                    expandBtn.setOnClickListener(v -> {
                        expanded[0] = !expanded[0];
                        if (expanded[0]) {
                            textView.setMaxLines(Integer.MAX_VALUE);
                            textView.setEllipsize(null);
                            expandBtn.setText(LocaleController.getString(R.string.LitegramVaultCollapse));
                        } else {
                            textView.setMaxLines(MAX_COLLAPSED_LINES);
                            textView.setEllipsize(TextUtils.TruncateAt.END);
                            expandBtn.setText(LocaleController.getString(R.string.LitegramVaultExpand));
                        }
                    });

                    int idx = card.indexOfChild(textView);
                    LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    card.addView(expandBtn, idx + 1, elp);
                }
            });
        }

        if (msg.mediaType == LitegramStorage.MEDIA_VOICE) {
            card.addView(createVoicePlayer(context, msg));
        }

        card.setOnLongClickListener(v -> {
            showMessageOptions(msg);
            return true;
        });

        return card;
    }

    private View createVoicePlayer(Context context, LitegramStorage.SavedMessage msg) {
        LinearLayout playerRow = new LinearLayout(context);
        playerRow.setOrientation(LinearLayout.HORIZONTAL);
        playerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = AndroidUtilities.dp(10);
        playerRow.setLayoutParams(plp);

        GradientDrawable playerBg = new GradientDrawable();
        playerBg.setCornerRadius(AndroidUtilities.dp(12));
        playerBg.setColor(ColorUtils.setAlphaComponent(COLOR_PURPLE, 0x1A));
        playerRow.setBackground(playerBg);
        playerRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8),
                AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        int btnSize = AndroidUtilities.dp(36);
        View playBtn = new View(context) {
            boolean playing = false;
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
            }
            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.min(cx, cy);

                Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bgPaint.setColor(COLOR_PURPLE);
                canvas.drawCircle(cx, cy, r, bgPaint);

                if (playing) {
                    float bw = r * 0.22f;
                    float bh = r * 0.6f;
                    float gap = r * 0.16f;
                    canvas.drawRect(cx - gap - bw, cy - bh, cx - gap, cy + bh, paint);
                    canvas.drawRect(cx + gap, cy - bh, cx + gap + bw, cy + bh, paint);
                } else {
                    Path tri = new Path();
                    float s = r * 0.7f;
                    float ox = cx + r * 0.1f;
                    tri.moveTo(ox - s * 0.4f, cy - s * 0.5f);
                    tri.lineTo(ox - s * 0.4f, cy + s * 0.5f);
                    tri.lineTo(ox + s * 0.5f, cy);
                    tri.close();
                    canvas.drawPath(tri, paint);
                }
            }
            void setPlaying(boolean p) {
                playing = p;
                invalidate();
            }
            boolean isPlaying() { return playing; }
        };
        playBtn.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
        playerRow.addView(playBtn);

        LinearLayout seekCol = new LinearLayout(context);
        seekCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sclp.leftMargin = AndroidUtilities.dp(8);
        playerRow.addView(seekCol, sclp);

        SeekBar seekBar = new android.widget.SeekBar(context);
        seekBar.setMax(100);
        seekBar.setProgress(0);
        seekBar.getThumb().mutate().setColorFilter(COLOR_PURPLE, android.graphics.PorterDuff.Mode.SRC_IN);
        seekBar.getProgressDrawable().mutate().setColorFilter(COLOR_PURPLE, android.graphics.PorterDuff.Mode.SRC_IN);
        seekCol.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView timeLabel = new TextView(context);
        timeLabel.setText("\uD83C\uDFA4 " + LocaleController.getString(R.string.LitegramVaultVoice));
        timeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        timeLabel.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText4));
        timeLabel.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
        seekCol.addView(timeLabel);

        final boolean[] userSeeking = {false};

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && activePlayer != null && activePlayerRow == playerRow) {
                    int dur = activePlayer.getDuration();
                    activePlayer.seekTo(dur * progress / 100);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) { userSeeking[0] = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) { userSeeking[0] = false; }
        });

        playBtn.setOnClickListener(v -> {
            String filePath = msg.mediaPath;
            boolean hasFile = filePath != null && !filePath.isEmpty()
                    && !filePath.startsWith("@doc:")
                    && new File(filePath).exists();

            if (!hasFile && msg.docData != null && msg.docData.length > 0) {
                Toast.makeText(context, "Загрузка...", Toast.LENGTH_SHORT).show();
                final View btn = v;
                new Thread(() -> {
                    String downloaded = LitegramVault.downloadVoiceFile(
                            msg.docData, msg.dialogId, msg.mid, currentAccount);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (downloaded != null) {
                            msg.mediaPath = downloaded;
                            LitegramStorage.getInstance().updateMediaPath(msg.mid, msg.dialogId, downloaded);
                            btn.performClick();
                        } else {
                            Toast.makeText(context,
                                    LocaleController.getString(R.string.LitegramVaultCantOpen),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
                return;
            }

            if (!hasFile && filePath != null && filePath.startsWith("@doc:")) {
                String resolved = LitegramVault.retryResolveVoice(filePath, msg.dialogId, msg.mid);
                if (resolved != null) {
                    msg.mediaPath = resolved;
                    filePath = resolved;
                    hasFile = true;
                }
            }

            if (!hasFile) {
                try {
                    Toast.makeText(context,
                            LocaleController.getString(R.string.LitegramVaultCantOpen),
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
                return;
            }
            final String finalPath = filePath;

            if (activePlayer != null && activePlayerRow == playerRow) {
                if (activePlayer.isPlaying()) {
                    activePlayer.pause();
                    ((View) v).invalidate();
                    setPlayBtnState(playBtn, false);
                    progressHandler.removeCallbacksAndMessages(null);
                } else {
                    activePlayer.start();
                    setPlayBtnState(playBtn, true);
                    startProgressUpdater(seekBar, timeLabel, playBtn, playerRow, userSeeking);
                }
                return;
            }

            stopActivePlayer();
            resetOtherPlayers();

            try {
                activePlayer = new MediaPlayer();
                activePlayer.setDataSource(finalPath);
                activePlayer.prepare();
                activePlayer.start();
                activePlayerRow = playerRow;
                setPlayBtnState(playBtn, true);

                activePlayer.setOnCompletionListener(mp -> {
                    setPlayBtnState(playBtn, false);
                    seekBar.setProgress(0);
                    timeLabel.setText(formatDuration(activePlayer.getDuration()));
                    progressHandler.removeCallbacksAndMessages(null);
                });

                startProgressUpdater(seekBar, timeLabel, playBtn, playerRow, userSeeking);
            } catch (Exception e) {
                Toast.makeText(context,
                        LocaleController.getString(R.string.LitegramVaultCantOpen),
                        Toast.LENGTH_SHORT).show();
            }
        });

        return playerRow;
    }

    private void setPlayBtnState(View btn, boolean playing) {
        try {
            java.lang.reflect.Method m = btn.getClass().getDeclaredMethod("setPlaying", boolean.class);
            m.invoke(btn, playing);
        } catch (Exception ignored) {
            btn.invalidate();
        }
    }

    private void resetOtherPlayers() {
        if (listContainer == null) return;
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            View child = listContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                resetPlayerInCard((LinearLayout) child);
            }
        }
    }

    private void resetPlayerInCard(LinearLayout card) {
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getChildCount() >= 2) {
                    View first = row.getChildAt(0);
                    View second = row.getChildAt(1);
                    if (second instanceof LinearLayout) {
                        LinearLayout seekCol = (LinearLayout) second;
                        for (int j = 0; j < seekCol.getChildCount(); j++) {
                            if (seekCol.getChildAt(j) instanceof SeekBar) {
                                ((SeekBar) seekCol.getChildAt(j)).setProgress(0);
                            }
                        }
                        setPlayBtnState(first, false);
                    }
                }
            }
        }
    }

    private void startProgressUpdater(SeekBar seekBar, TextView timeLabel, View playBtn,
                                      View playerRow, boolean[] userSeeking) {
        progressHandler.removeCallbacksAndMessages(null);
        Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (activePlayer != null && activePlayerRow == playerRow) {
                    try {
                        if (activePlayer.isPlaying() && !userSeeking[0]) {
                            int pos = activePlayer.getCurrentPosition();
                            int dur = activePlayer.getDuration();
                            if (dur > 0) {
                                seekBar.setProgress(pos * 100 / dur);
                            }
                            timeLabel.setText(formatDuration(pos) + " / " + formatDuration(dur));
                        }
                    } catch (Exception ignored) {}
                    progressHandler.postDelayed(this, 200);
                }
            }
        };
        progressHandler.post(updater);
    }

    private static String formatDuration(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private void showMessageOptions(LitegramStorage.SavedMessage msg) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(16));

        final BottomSheet[] holder = new BottomSheet[1];

        if (msg.text != null && !msg.text.isEmpty()) {
            root.addView(createSheetRow(ctx, LocaleController.getString(R.string.LitegramVaultCopyText), () -> {
                if (holder[0] != null) holder[0].dismiss();
                AndroidUtilities.addToClipboard(msg.text);
                Toast.makeText(ctx, LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
            }));
        }

        root.addView(createSheetRow(ctx, LocaleController.getString(R.string.LitegramVaultDelete), () -> {
            if (holder[0] != null) holder[0].dismiss();
            if (activePlayer != null) stopActivePlayer();
            LitegramStorage.getInstance().deleteDeletedMessage(msg.mid, msg.dialogId);
            if (msg.mediaPath != null && !msg.mediaPath.isEmpty()) {
                File f = new File(msg.mediaPath);
                if (f.exists()) f.delete();
            }
            refreshList();
        }));

        builder.setCustomView(root);
        holder[0] = builder.create();
        showDialog(holder[0]);
    }

    private View createSheetRow(Context ctx, String text, Runnable onClick) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(14),
                AndroidUtilities.dp(22), AndroidUtilities.dp(14));
        tv.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private void refreshList() {
        stopActivePlayer();
        if (listContainer != null) {
            listContainer.removeAllViews();
            loadedCount = 0;
            loadMessages();
        }
    }

    private void openUserChat(long id) {
        if (id == 0) return;
        Bundle args = new Bundle();
        if (id > 0) {
            args.putLong("user_id", id);
        } else {
            args.putLong("chat_id", -id);
        }
        presentFragment(new ChatActivity(args));
    }
}
