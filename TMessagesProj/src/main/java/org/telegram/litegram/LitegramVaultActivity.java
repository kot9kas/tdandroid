package org.telegram.litegram;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LitegramVaultActivity extends BaseFragment {

    public static final int MODE_DELETED = 0;
    public static final int MODE_ONCE = 1;

    private int mode;
    private LinearLayout listContainer;
    private TextView emptyView;
    private int loadedCount = 0;
    private static final int PAGE_SIZE = 50;

    public LitegramVaultActivity(int mode) {
        super();
        this.mode = mode;
    }

    public LitegramVaultActivity(Bundle args) {
        super(args);
        if (args != null) {
            this.mode = args.getInt("vault_mode", MODE_DELETED);
        }
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
        actionBar.setTitle(mode == MODE_DELETED
                ? LocaleController.getString(R.string.LitegramVaultDeleted)
                : LocaleController.getString(R.string.LitegramVaultOnce));
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
        emptyView.setText(mode == MODE_DELETED
                ? LocaleController.getString(R.string.LitegramVaultDeletedEmpty)
                : LocaleController.getString(R.string.LitegramVaultOnceEmpty));
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

    private void loadMessages() {
        List<LitegramStorage.SavedMessage> items;
        if (mode == MODE_DELETED) {
            items = LitegramStorage.getInstance().getDeletedMessages(PAGE_SIZE, loadedCount);
        } else {
            items = LitegramStorage.getInstance().getOnceMedia(PAGE_SIZE, loadedCount);
        }
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
        senderView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        senderView.setTypeface(AndroidUtilities.bold());
        senderView.setMaxLines(1);
        senderView.setEllipsize(TextUtils.TruncateAt.END);
        headerText.addView(senderView);

        if (msg.chatTitle != null && !msg.chatTitle.isEmpty()
                && !msg.chatTitle.equals(msg.senderName)) {
            TextView chatView = new TextView(context);
            chatView.setText(msg.chatTitle);
            chatView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            chatView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
            chatView.setMaxLines(1);
            chatView.setEllipsize(TextUtils.TruncateAt.END);
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
            textView.setMaxLines(6);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = AndroidUtilities.dp(8);
            card.addView(textView, tlp);
        }

        if (msg.mediaType != LitegramStorage.MEDIA_NONE) {
            LinearLayout mediaRow = new LinearLayout(context);
            mediaRow.setOrientation(LinearLayout.HORIZONTAL);
            mediaRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = AndroidUtilities.dp(8);

            TextView mediaLabel = new TextView(context);
            mediaLabel.setText(getMediaTypeLabel(msg.mediaType));
            mediaLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            mediaLabel.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));

            GradientDrawable mediaBadge = new GradientDrawable();
            mediaBadge.setCornerRadius(AndroidUtilities.dp(8));
            mediaBadge.setColor(ColorUtils.setAlphaComponent(
                    c(Theme.key_windowBackgroundWhiteBlueText4), 0x1A));
            mediaLabel.setBackground(mediaBadge);
            mediaLabel.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4),
                    AndroidUtilities.dp(8), AndroidUtilities.dp(4));

            mediaRow.addView(mediaLabel);

            if (msg.mediaPath != null && !msg.mediaPath.isEmpty()) {
                File f = new File(msg.mediaPath);
                if (f.exists()) {
                    TextView openBtn = new TextView(context);
                    openBtn.setText(LocaleController.getString(R.string.LitegramVaultOpen));
                    openBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    openBtn.setTextColor(c(Theme.key_featuredStickers_addButton));
                    openBtn.setTypeface(AndroidUtilities.bold());
                    LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    olp.leftMargin = AndroidUtilities.dp(12);
                    mediaRow.addView(openBtn, olp);

                    openBtn.setOnClickListener(v -> openFile(f));
                }
            }

            card.addView(mediaRow, mlp);
        }

        card.setOnLongClickListener(v -> {
            showMessageOptions(msg);
            return true;
        });

        return card;
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

        if (msg.mediaPath != null && !msg.mediaPath.isEmpty() && new File(msg.mediaPath).exists()) {
            root.addView(createSheetRow(ctx, LocaleController.getString(R.string.LitegramVaultOpenMedia), () -> {
                if (holder[0] != null) holder[0].dismiss();
                openFile(new File(msg.mediaPath));
            }));
        }

        root.addView(createSheetRow(ctx, LocaleController.getString(R.string.LitegramVaultDelete), () -> {
            if (holder[0] != null) holder[0].dismiss();
            if (mode == MODE_DELETED) {
                LitegramStorage.getInstance().deleteDeletedMessage(msg.mid, msg.dialogId);
            } else {
                LitegramStorage.getInstance().deleteOnceMedia(msg.mid, msg.dialogId);
                if (msg.mediaPath != null) {
                    File f = new File(msg.mediaPath);
                    if (f.exists()) f.delete();
                }
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
        if (listContainer != null) {
            listContainer.removeAllViews();
            loadedCount = 0;
            loadMessages();
        }
    }

    private void openFile(File file) {
        try {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = getMimeType(file.getName());
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(intent);
        } catch (Exception e) {
            try {
                Context ctx = getParentActivity();
                if (ctx != null) {
                    Toast.makeText(ctx,
                            LocaleController.getString(R.string.LitegramVaultCantOpen),
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        }
    }

    private static String getMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        return "*/*";
    }

    private static String getMediaTypeLabel(int type) {
        switch (type) {
            case LitegramStorage.MEDIA_PHOTO: return "\uD83D\uDDBC Photo";
            case LitegramStorage.MEDIA_VIDEO: return "\uD83C\uDFA5 Video";
            case LitegramStorage.MEDIA_VOICE: return "\uD83C\uDFA4 Voice";
            case LitegramStorage.MEDIA_ROUND: return "\u23FA Round";
            case LitegramStorage.MEDIA_DOCUMENT: return "\uD83D\uDCC4 Document";
            case LitegramStorage.MEDIA_STICKER: return "\uD83D\uDCCC Sticker";
            case LitegramStorage.MEDIA_GIF: return "GIF";
            default: return "\uD83D\uDCCE Media";
        }
    }
}
