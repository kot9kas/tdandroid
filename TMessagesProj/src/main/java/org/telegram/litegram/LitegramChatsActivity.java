package org.telegram.litegram;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.List;

public class LitegramChatsActivity extends BaseFragment {

    private static final int COLOR_PURPLE = 0xFF7B5EA7;
    private static final int COLOR_ACCENT = 0xFFAB7CFF;
    private static final int[] TIMER_VALUES = {0, 30, 60, 300, 900, 3600};

    private LinearLayout listContainer;
    private TextView emptyView;

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
        actionBar.setTitle(LocaleController.getString(R.string.LitegramChats));
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
        emptyView.setText(LocaleController.getString(R.string.LitegramChatsEmpty));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextView addBtn = new TextView(context);
        addBtn.setText(LocaleController.getString(R.string.LitegramChatsAdd));
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        addBtn.setTextColor(0xFFFFFFFF);
        addBtn.setTypeface(AndroidUtilities.bold());
        addBtn.setGravity(Gravity.CENTER);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(AndroidUtilities.dp(24));
        addBg.setColor(COLOR_PURPLE);
        addBtn.setBackground(addBg);
        addBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12),
                AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        addBtn.setOnClickListener(v -> showAddMenu());
        FrameLayout.LayoutParams addLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        addLp.bottomMargin = AndroidUtilities.dp(20);
        root.addView(addBtn, addLp);

        refreshList();

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        listContainer.addView(createSettingsSection(context));
        listContainer.addView(createGroupsSection(context));
        listContainer.addView(createLockedFoldersSection(context));
        listContainer.addView(createLockedChatsSection(context));
    }

    // ==================== GLOBAL SETTINGS ====================

    private View createSettingsSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        return section;
    }

    private void showTimerPicker(int current, OnTimerSelected listener) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        CharSequence[] labels = new CharSequence[TIMER_VALUES.length];
        for (int i = 0; i < TIMER_VALUES.length; i++) {
            String text = autolockLabel(TIMER_VALUES[i]);
            if (TIMER_VALUES[i] == current) {
                labels[i] = withGreenCheck(text);
            } else {
                labels[i] = text;
            }
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setTitle(LocaleController.getString(R.string.LitegramChatsAutolock));
        builder.setItems(labels, (d, which) -> listener.onSelected(TIMER_VALUES[which]));
        showDialog(builder.create());
    }

    interface OnTimerSelected {
        void onSelected(int seconds);
    }

    private String autolockLabel(int seconds) {
        if (seconds == 0) return LocaleController.getString(R.string.LitegramChatsAutolockAlways);
        if (seconds < 60) return seconds + " " + LocaleController.getString(R.string.LitegramChatsAutolockSec);
        if (seconds < 3600) return (seconds / 60) + " " + LocaleController.getString(R.string.LitegramChatsAutolockMin);
        return (seconds / 3600) + " " + LocaleController.getString(R.string.LitegramChatsAutolockHour);
    }

    private CharSequence withGreenCheck(String text) {
        String full = text + "  \u2714";
        SpannableString ss = new SpannableString(full);
        int start = text.length() + 2;
        ss.setSpan(new ForegroundColorSpan(0xFF4CAF50), start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.RelativeSizeSpan(1.15f), start, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    interface ToggleListener {
        void onToggle(org.telegram.ui.Components.Switch sw, boolean checked);
    }

    private View createToggleRow(Context context, String text, boolean checked, ToggleListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14),
                AndroidUtilities.dp(21), AndroidUtilities.dp(14));

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        label.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        org.telegram.ui.Components.Switch sw = new org.telegram.ui.Components.Switch(context);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        sw.setChecked(checked, false);
        sw.setOnCheckedChangeListener((view, isChecked) -> listener.onToggle(sw, isChecked));
        row.addView(sw, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(37), AndroidUtilities.dp(20)));

        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked(), true));
        return row;
    }

    // ==================== LOCK GROUPS ====================

    private View createGroupsSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, AndroidUtilities.dp(8), 0, 0);

        List<Integer> groupIds = LitegramChatLocks.getInstance().getAllGroupIds();
        if (groupIds.isEmpty()) return section;

        section.addView(createSectionHeader(context,
                LocaleController.getString(R.string.LitegramGroupProtected)));

        for (int gid : groupIds) {
            section.addView(createGroupRow(context, gid));
        }
        return section;
    }

    private View createGroupRow(Context context, int groupId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        String name = locks.getGroupName(groupId);
        int chatCount = locks.getGroupChats(groupId).size();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(AndroidUtilities.dp(14));
        rowBg.setColor(c(Theme.key_windowBackgroundWhite));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.leftMargin = AndroidUtilities.dp(12);
        rowLp.rightMargin = AndroidUtilities.dp(12);
        rowLp.bottomMargin = AndroidUtilities.dp(6);
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_groups);
        icon.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT, PorterDuff.Mode.SRC_IN));
        row.addView(icon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(44), AndroidUtilities.dp(44)));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.leftMargin = AndroidUtilities.dp(12);
        row.addView(textCol, tcLp);

        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.bold());
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText(chatCount + " " + LocaleController.getString("Chats", R.string.Chats).toLowerCase());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        textCol.addView(sub);

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        row.addView(arrow, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(20), AndroidUtilities.dp(20)));

        row.setOnClickListener(v -> verifyThenShowGroupSettings(groupId, name));
        return row;
    }

    private void verifyThenShowGroupSettings(int groupId, String name) {
        long entityId = 0x7E00000000L + groupId;
        if (LitegramChatLocks.getInstance().isSettingsUnlockedNow(entityId)) {
            showGroupSettings(groupId, name);
            return;
        }

        List<Long> groupChats = LitegramChatLocks.getInstance().getGroupChats(groupId);
        long fakeDialogId = groupChats.isEmpty() ? 0 : groupChats.get(0);

        LitegramPinDialog.showVerify(getParentActivity(), fakeDialogId, (pin, dlg) -> {
            if ("__biometric__".equals(pin) || LitegramChatLocks.getInstance().checkGroupPin(groupId, pin)) {
                LitegramChatLocks.getInstance().markSettingsUnlocked(entityId);
                dlg.dismiss();
                showGroupSettings(groupId, name);
            } else {
                dlg.showWrongPin();
            }
        });
    }

    private void showGroupSettings(int groupId, String groupName) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder b = new BottomSheet.Builder(ctx);
        b.setTitle(groupName);

        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.LitegramGroupAddChat),
                LocaleController.getString(R.string.LitegramGroupRemoveChat),
                LocaleController.getString(R.string.LitegramChatsAutolock),
                LocaleController.getString(R.string.LitegramChatsHidePreview),
                LocaleController.getString(R.string.LitegramChatsChangePin),
                LocaleController.getString(R.string.LitegramFoldersRemoveLock),
        };
        int[] icons = new int[]{
                R.drawable.msg_addbot,
                R.drawable.msg_delete,
                R.drawable.msg_autodelete,
                R.drawable.msg_mute,
                R.drawable.msg_permissions,
                R.drawable.msg_reset
        };

        b.setItems(items, icons, (dlg, which) -> {
            if (which == 0) {
                addChatToGroupPicker(groupId);
            } else if (which == 1) {
                removeChatFromGroupPicker(groupId);
            } else if (which == 2) {
                showGroupTimerPicker(groupId);
            } else if (which == 3) {
                toggleGroupHidePreview(groupId);
            } else if (which == 4) {
                changeGroupPin(groupId);
            } else if (which == 5) {
                confirmRemoveGroup(groupId, groupName);
            }
        });
        showDialog(b.create());
    }

    private void showGroupTimerPicker(int groupId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        int current = locks.getGroupTimer(groupId);
        int effective = current >= 0 ? current : 300;
        showTimerPicker(effective, seconds -> {
            locks.setGroupTimer(groupId, seconds);
            for (long cid : locks.getGroupChats(groupId)) {
                locks.setChatAutolockSeconds(cid, seconds);
            }
            refreshList();
        });
    }

    private void toggleGroupHidePreview(int groupId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        int current = locks.getGroupHide(groupId);
        boolean nowHidden = current == 1;
        int newValue = nowHidden ? 0 : 1;
        locks.setGroupHide(groupId, newValue);
        for (long cid : locks.getGroupChats(groupId)) {
            locks.setChatHidePreview(cid, newValue);
        }
        Toast.makeText(getParentActivity(),
                nowHidden ? LocaleController.getString(R.string.LitegramChatsHideOff)
                         : LocaleController.getString(R.string.LitegramChatsHideOn),
                Toast.LENGTH_SHORT).show();
        refreshList();
    }

    private void addChatToGroupPicker(int groupId) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", 3);
        args.putBoolean("canSelectTopics", false);
        args.putBoolean("litegramPinPicker", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) return true;
            fragment.finishFragment();
            LitegramChatLocks locks = LitegramChatLocks.getInstance();
            int skipped = 0;
            for (MessagesStorage.TopicKey tk : dids) {
                long did = tk.dialogId;
                if (locks.isLocked(did) || (locks.findGroupForChat(did) >= 0 && locks.findGroupForChat(did) != groupId)) {
                    skipped++;
                    continue;
                }
                locks.addChatToGroup(groupId, did);
            }
            if (skipped > 0) {
                Toast.makeText(getParentActivity(),
                        LocaleController.getString(R.string.LitegramChatsAlreadyLocked),
                        Toast.LENGTH_SHORT).show();
            }
            refreshList();
            return true;
        });
        presentFragment(picker);
    }

    private void removeChatFromGroupPicker(int groupId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        List<Long> chats = locks.getGroupChats(groupId);
        if (chats.isEmpty()) return;

        Context ctx = getParentActivity();
        if (ctx == null) return;

        CharSequence[] names = new CharSequence[chats.size()];
        int[] icons = new int[chats.size()];
        for (int i = 0; i < chats.size(); i++) {
            names[i] = chatName(chats.get(i));
            icons[i] = R.drawable.msg_delete;
        }

        BottomSheet.Builder bs = new BottomSheet.Builder(ctx);
        bs.setTitle(LocaleController.getString(R.string.LitegramGroupRemoveChat));
        bs.setItems(names, icons, (dlg, which) -> {
            long chatId = chats.get(which);
            locks.removeChatFromGroup(groupId, chatId);
            if (locks.getGroupChats(groupId).isEmpty()) {
                locks.removeGroup(groupId);
            }
            refreshList();
        });
        showDialog(bs.create());
    }

    private void changeGroupPin(int groupId) {
        LitegramPinDialog.show(getParentActivity(), LitegramPinDialog.MODE_SET, 0, pin -> {
            LitegramChatLocks.getInstance().setGroupPin(groupId, pin);
            Toast.makeText(getParentActivity(), "PIN updated", Toast.LENGTH_SHORT).show();
            refreshList();
        });
    }

    private void confirmRemoveGroup(int groupId, String name) {
        AlertDialog.Builder ab = new AlertDialog.Builder(getParentActivity());
        ab.setTitle(name);
        ab.setMessage(LocaleController.getString(R.string.LitegramFoldersRemoveLockConfirm));
        ab.setPositiveButton(LocaleController.getString("OK", R.string.OK), (d, w) -> {
            LitegramChatLocks.getInstance().removeGroup(groupId);
            Toast.makeText(getParentActivity(),
                    LocaleController.getString(R.string.LitegramFolderUnprotected),
                    Toast.LENGTH_SHORT).show();
            refreshList();
        });
        ab.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(ab.create());
    }

    // ==================== LOCKED FOLDERS ====================

    private View createLockedFoldersSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        List<Integer> lockedFolderIds = locks.getLockedFolderIds();

        if (!lockedFolderIds.isEmpty()) {
            section.addView(createSectionHeader(context,
                    LocaleController.getString(R.string.LitegramFoldersProtected)));

            java.util.ArrayList<MessagesController.DialogFilter> filters =
                    MessagesController.getInstance(currentAccount).getDialogFilters();

            for (int filterId : lockedFolderIds) {
                String name = null;
                for (MessagesController.DialogFilter f : filters) {
                    if (f.id == filterId) {
                        name = f.name;
                        break;
                    }
                }
                if (name == null) name = "Folder #" + filterId;
                section.addView(createFolderRow(context, filterId, name));
            }
        }

        return section;
    }

    private View createFolderRow(Context context, int filterId, String folderName) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(AndroidUtilities.dp(14));
        rowBg.setColor(c(Theme.key_windowBackgroundWhite));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.leftMargin = AndroidUtilities.dp(12);
        rowLp.rightMargin = AndroidUtilities.dp(12);
        rowLp.bottomMargin = AndroidUtilities.dp(6);
        row.setLayoutParams(rowLp);

        ImageView folderIcon = new ImageView(context);
        folderIcon.setImageResource(R.drawable.msg_folders);
        folderIcon.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT, PorterDuff.Mode.SRC_IN));
        row.addView(folderIcon, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(44), AndroidUtilities.dp(44)));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.leftMargin = AndroidUtilities.dp(12);
        row.addView(textCol, tcLp);

        TextView nameView = new TextView(context);
        nameView.setText(folderName);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(nameView);

        TextView subtitle = new TextView(context);
        subtitle.setText(LocaleController.getString(R.string.LitegramFoldersPinProtected));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        textCol.addView(subtitle);

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        row.addView(arrow, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(20), AndroidUtilities.dp(20)));

        row.setOnClickListener(v -> showFolderSettings(filterId, folderName));
        return row;
    }

    private void showFolderSettings(int filterId, String folderName) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        LitegramChatLocks locks = LitegramChatLocks.getInstance();

        Runnable openSheet = () -> {
            BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
            builder.setTitle(folderName);
            CharSequence[] items = new CharSequence[]{
                    LocaleController.getString(R.string.LitegramChatsAutolock),
                    LocaleController.getString(R.string.LitegramChatsChangePin),
                    LocaleController.getString(R.string.LitegramFoldersRemoveLock),
            };
            int[] icons = new int[]{R.drawable.msg_autodelete, R.drawable.msg_edit, R.drawable.msg_delete};
            builder.setItems(items, icons, (d, which) -> {
                if (which == 0) {
                    showFolderTimerPicker(filterId);
                } else if (which == 1) {
                    changeFolderPin(filterId);
                } else if (which == 2) {
                    confirmRemoveFolderLock(filterId, folderName);
                }
            });
            showDialog(builder.create());
        };

        long entityId = LitegramChatLocks.folderDialogId(filterId);
        if (locks.isSettingsUnlockedNow(entityId)) {
            openSheet.run();
            return;
        }

        LitegramPinDialog.showVerify(ctx, entityId, (pin, dlg) -> {
            if ("__biometric__".equals(pin) || locks.checkFolderPin(filterId, pin)) {
                locks.markSettingsUnlocked(entityId);
                dlg.dismiss();
                openSheet.run();
            } else {
                dlg.showWrongPin();
            }
        });
    }

    private void showFolderTimerPicker(int filterId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        int current = locks.getFolderAutolockSeconds(filterId);
        int effective = current >= 0 ? current : 300;
        showTimerPicker(effective, seconds -> {
            locks.setFolderAutolockSeconds(filterId, seconds);
            refreshList();
        });
    }

    private void changeFolderPin(int filterId) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        LitegramPinDialog.show(ctx, LitegramPinDialog.MODE_SET,
                LitegramChatLocks.folderDialogId(filterId), pin -> {
            LitegramChatLocks.getInstance().setFolderLock(filterId, pin);
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramChatsPinChanged),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmRemoveFolderLock(int filterId, String folderName) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        AlertDialog.Builder ab = new AlertDialog.Builder(ctx);
        ab.setTitle(LocaleController.getString(R.string.LitegramFoldersRemoveLock));
        ab.setMessage(LocaleController.formatString(R.string.LitegramFoldersRemoveLockConfirm, folderName));
        ab.setPositiveButton(LocaleController.getString(R.string.LitegramFoldersRemoveLock), (d, w) -> {
            LitegramChatLocks.getInstance().removeFolderLock(filterId);
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramFolderUnprotected),
                    Toast.LENGTH_SHORT).show();
            refreshList();
        });
        ab.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(ab.create());
    }

    private void openFolderPicker() {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        java.util.ArrayList<MessagesController.DialogFilter> filters =
                MessagesController.getInstance(currentAccount).getDialogFilters();
        LitegramChatLocks locks = LitegramChatLocks.getInstance();

        java.util.ArrayList<MessagesController.DialogFilter> available = new java.util.ArrayList<>();
        for (MessagesController.DialogFilter f : filters) {
            if (!f.isDefault() && !locks.isFolderLocked(f.id)) {
                available.add(f);
            }
        }

        if (available.isEmpty()) {
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramFoldersNoFolders),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] names = new CharSequence[available.size()];
        for (int i = 0; i < available.size(); i++) {
            names[i] = available.get(i).name;
        }

        BottomSheet.Builder b = new BottomSheet.Builder(ctx);
        b.setTitle(LocaleController.getString(R.string.LitegramFoldersAdd));
        b.setItems(names, (d, which) -> {
            MessagesController.DialogFilter chosen = available.get(which);
            LitegramPinDialog.show(ctx, LitegramPinDialog.MODE_SET,
                    LitegramChatLocks.folderDialogId(chosen.id), pin -> {
                locks.setFolderLock(chosen.id, pin);
                offerBiometric(pin);
                Toast.makeText(ctx,
                        LocaleController.getString(R.string.LitegramFolderProtected),
                        Toast.LENGTH_SHORT).show();
                refreshList();
            });
        });
        showDialog(b.create());
    }

    // ==================== LOCKED CHATS LIST ====================

    private View createLockedChatsSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        List<Long> lockedIds = locks.getLockedDialogIds();

        // exclude chats that belong to a group
        java.util.Set<Long> groupChats = new java.util.HashSet<>();
        for (int gid : locks.getAllGroupIds()) {
            groupChats.addAll(locks.getGroupChats(gid));
        }
        lockedIds.removeAll(groupChats);

        boolean hasGroups = !locks.getAllGroupIds().isEmpty();
        if (lockedIds.isEmpty() && locks.getLockedFolderIds().isEmpty() && !hasGroups) {
            emptyView.setVisibility(View.VISIBLE);
            return section;
        }
        emptyView.setVisibility(View.GONE);

        if (lockedIds.isEmpty()) return section;

        section.addView(createSectionHeader(context,
                LocaleController.getString(R.string.LitegramChatsProtected)));

        for (long dialogId : lockedIds) {
            section.addView(createChatRow(context, dialogId));
        }
        return section;
    }

    // ==================== CHAT ROW (tap -> verify PIN -> settings) ====================

    private View createChatRow(Context context, long dialogId) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(AndroidUtilities.dp(14));
        rowBg.setColor(c(Theme.key_windowBackgroundWhite));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.leftMargin = AndroidUtilities.dp(12);
        rowLp.rightMargin = AndroidUtilities.dp(12);
        rowLp.bottomMargin = AndroidUtilities.dp(6);
        row.setLayoutParams(rowLp);

        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(22));
        row.addView(avatar, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(44), AndroidUtilities.dp(44)));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.leftMargin = AndroidUtilities.dp(12);
        row.addView(textCol, tcLp);

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(nameView);

        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        int timer = locks.getEffectiveAutolockSeconds(dialogId);
        String sub = LocaleController.getString(R.string.LitegramChatsPinProtected)
                + " \u00B7 " + autolockLabel(timer);

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(sub);
        textCol.addView(subtitleView);

        setDialogInfo(avatar, nameView, dialogId);

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        row.addView(arrow, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(20), AndroidUtilities.dp(20)));

        row.setOnClickListener(v -> verifyThenShowChatSettings(dialogId, nameView.getText().toString()));

        return row;
    }

    private void verifyThenShowChatSettings(long dialogId, String chatName) {
        if (LitegramChatLocks.getInstance().isSettingsUnlockedNow(dialogId)) {
            showChatSettings(dialogId, chatName);
            return;
        }

        Context ctx = getParentActivity();
        if (ctx == null) return;

        LitegramPinDialog.showVerify(ctx, dialogId, (pin, dlg) -> {
            if ("__biometric__".equals(pin) || LitegramChatLocks.getInstance().checkPin(dialogId, pin)) {
                LitegramChatLocks.getInstance().markSettingsUnlocked(dialogId);
                dlg.dismiss();
                showChatSettings(dialogId, chatName);
            } else {
                dlg.showWrongPin();
            }
        });
    }

    // ==================== PER-CHAT SETTINGS SHEET ====================

    private void showChatSettings(long dialogId, String chatName) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        LitegramChatLocks locks = LitegramChatLocks.getInstance();
        int chatTimer = locks.getChatAutolockSeconds(dialogId);
        String timerStr = chatTimer >= 0
                ? autolockLabel(chatTimer)
                : autolockLabel(300) + " (" + LocaleController.getString(R.string.LitegramChatsTimerDefault) + ")";

        boolean hideEnabled = locks.isEffectiveHidePreview(dialogId);
        String hideState = hideEnabled
                ? LocaleController.getString(R.string.LitegramChatsHideOn)
                : LocaleController.getString(R.string.LitegramChatsHideOff);

        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.LitegramChatsTimerChat) + ": " + timerStr,
                LocaleController.getString(R.string.LitegramChatsHideChatMessages) + ": " + hideState,
                LocaleController.getString(R.string.LitegramChatsChangePin),
                LocaleController.getString(R.string.LitegramChatsRemoveLock),
        };
        int[] icons = new int[]{
                R.drawable.msg_autodelete,
                R.drawable.msg_mute,
                R.drawable.msg_edit,
                R.drawable.msg_delete,
        };

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setTitle(chatName);
        builder.setItems(items, icons, (d, which) -> {
            if (which == 0) {
                showPerChatTimerPicker(dialogId);
            } else if (which == 1) {
                boolean current = locks.isEffectiveHidePreview(dialogId);
                locks.setChatHidePreview(dialogId, current ? 0 : 1);
                refreshList();
            } else if (which == 2) {
                changePin(dialogId);
            } else if (which == 3) {
                confirmRemoveLock(dialogId, chatName);
            }
        });
        showDialog(builder.create());
    }

    private void showPerChatTimerPicker(long dialogId) {
        LitegramChatLocks locks = LitegramChatLocks.getInstance();

        int[] extended = new int[TIMER_VALUES.length + 1];
        extended[0] = -1;
        System.arraycopy(TIMER_VALUES, 0, extended, 1, TIMER_VALUES.length);

        String defLabel = LocaleController.getString(R.string.LitegramChatsTimerDefault)
                + " (" + autolockLabel(300) + ")";

        int perChat = locks.getChatAutolockSeconds(dialogId);

        CharSequence[] labels = new CharSequence[extended.length];
        if (perChat < 0) {
            labels[0] = withGreenCheck(defLabel);
        } else {
            labels[0] = defLabel;
        }
        for (int i = 1; i < extended.length; i++) {
            String text = autolockLabel(TIMER_VALUES[i - 1]);
            if (perChat == TIMER_VALUES[i - 1]) {
                labels[i] = withGreenCheck(text);
            } else {
                labels[i] = text;
            }
        }

        Context ctx = getParentActivity();
        if (ctx == null) return;
        BottomSheet.Builder b = new BottomSheet.Builder(ctx);
        b.setTitle(LocaleController.getString(R.string.LitegramChatsAutolock));
        b.setItems(labels, (d, which) -> {
            locks.setChatAutolockSeconds(dialogId, extended[which]);
            refreshList();
        });
        showDialog(b.create());
    }

    private void changePin(long dialogId) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        LitegramPinDialog.show(ctx, LitegramPinDialog.MODE_SET, dialogId, pin -> {
            LitegramChatLocks.getInstance().setLock(dialogId, pin);
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramChatsPinChanged),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmRemoveLock(long dialogId, String chatName) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        AlertDialog.Builder ab = new AlertDialog.Builder(ctx);
        ab.setTitle(LocaleController.getString(R.string.LitegramChatsRemoveLock));
        ab.setMessage(LocaleController.formatString(R.string.LitegramChatsRemoveLockConfirm, chatName));
        ab.setPositiveButton(LocaleController.getString(R.string.LitegramChatsRemoveLock), (d, w) -> {
            LitegramChatLocks.getInstance().removeLock(dialogId);
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramChatUnprotected),
                    Toast.LENGTH_SHORT).show();
            refreshList();
        });
        ab.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(ab.create());
    }

    // ==================== ADD MENU ====================

    private void showAddMenu() {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder b = new BottomSheet.Builder(ctx);
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.LitegramAddChat),
                LocaleController.getString(R.string.LitegramAddFolder)
        };
        int[] icons = new int[]{R.drawable.msg_discussion, R.drawable.msg_folders};
        b.setItems(items, icons, (dlg, which) -> {
            if (which == 0) {
                openChatPicker();
            } else {
                openFolderPicker();
            }
        });
        showDialog(b.create());
    }

    // ==================== MULTI-SELECT CHAT PICKER ====================

    private void openChatPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", 3);
        args.putBoolean("canSelectTopics", false);
        args.putBoolean("litegramPinPicker", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) return true;

            List<Long> newIds = new ArrayList<>();
            LitegramChatLocks lck = LitegramChatLocks.getInstance();
            for (MessagesStorage.TopicKey tk : dids) {
                long did = tk.dialogId;
                if (!lck.isLocked(did) && lck.findGroupForChat(did) < 0) {
                    newIds.add(did);
                }
            }

            if (newIds.isEmpty()) {
                Toast.makeText(getParentActivity(),
                        LocaleController.getString(R.string.LitegramChatsAlreadyLocked),
                        Toast.LENGTH_SHORT).show();
                fragment.finishFragment();
                return true;
            }

            fragment.finishFragment(false);

            if (newIds.size() > 1) {
                askGroupNameThenSetPin(newIds);
            } else {
                LitegramPinDialog.show(getParentActivity(), LitegramPinDialog.MODE_SET, newIds.get(0), pin -> {
                    LitegramChatLocks.getInstance().setLock(newIds.get(0), pin);
                    offerBiometric(pin);
                    Toast.makeText(getParentActivity(),
                            LocaleController.getString(R.string.LitegramChatProtected),
                            Toast.LENGTH_SHORT).show();
                    refreshList();
                });
            }
            return true;
        });
        presentFragment(picker);
    }

    private void askGroupNameThenSetPin(List<Long> chatIds) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.LitegramGroupName));

        FrameLayout frame = new FrameLayout(getParentActivity());
        frame.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(0));
        EditText input = new EditText(getParentActivity());
        input.setHint(LocaleController.getString(R.string.LitegramGroupNameHint));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray));
        input.setSingleLine(true);
        input.setBackground(null);
        frame.addView(input, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        input.requestFocus();
        AndroidUtilities.runOnUIThread(() -> {
            AndroidUtilities.showKeyboard(input);
        }, 300);
        b.setView(frame);

        b.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dlg, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Group";
            final String finalName = name;
            LitegramPinDialog.show(getParentActivity(), LitegramPinDialog.MODE_SET, chatIds.get(0), pin -> {
                LitegramChatLocks.getInstance().createGroup(finalName, pin, chatIds);
                offerBiometric(pin);
                Toast.makeText(getParentActivity(),
                        LocaleController.getString(R.string.LitegramChatProtected) + " (" + chatIds.size() + ")",
                        Toast.LENGTH_SHORT).show();
                refreshList();
            });
        });
        b.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        b.show();
    }

    private void offerBiometric(String pin) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        boolean available = false;
        try {
            int result = BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.BIOMETRIC_WEAK);
            available = (result == BiometricManager.BIOMETRIC_SUCCESS);
        } catch (Exception ignored) {}
        if (!available) return;
        if (LitegramChatLocks.getInstance().isBiometricEnabled()) return;

        AlertDialog.Builder ab = new AlertDialog.Builder(ctx);
        ab.setTitle(LocaleController.getString(R.string.LitegramChatsBiometric));
        ab.setMessage(LocaleController.getString(R.string.LitegramChatsBiometricOffer));
        ab.setPositiveButton(LocaleController.getString("OK", R.string.OK), (d, w) -> {
            LitegramChatLocks.getInstance().setBiometricEnabled(true);
        });
        ab.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(ab.create());
    }

    // ==================== UTILS ====================

    private View createSectionHeader(Context context, String text) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14),
                AndroidUtilities.dp(18), AndroidUtilities.dp(8));

        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextColor(COLOR_ACCENT);
        tv.setTypeface(AndroidUtilities.bold());
        header.addView(tv);
        return header;
    }

    private void setDialogInfo(BackupImageView avatar, TextView nameView, long dialogId) {
        int account = currentAccount;
        MessagesController mc = MessagesController.getInstance(account);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            if (user != null) {
                nameView.setText(buildUserName(user));
                AvatarDrawable ad = new AvatarDrawable();
                ad.setInfo(account, user);
                avatar.setForUserOrChat(user, ad);
            } else {
                nameView.setText("ID: " + dialogId);
            }
        } else {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat != null) {
                nameView.setText(chat.title);
                AvatarDrawable ad = new AvatarDrawable();
                ad.setInfo(account, chat);
                avatar.setForUserOrChat(chat, ad);
            } else {
                nameView.setText("ID: " + dialogId);
            }
        }
    }

    private String buildUserName(TLRPC.User user) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(user.first_name)) sb.append(user.first_name);
        if (!TextUtils.isEmpty(user.last_name)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(user.last_name);
        }
        if (sb.length() == 0) sb.append("User");
        return sb.toString();
    }

    private String chatName(long dialogId) {
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            return user != null ? buildUserName(user) : ("ID: " + dialogId);
        } else {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            return chat != null ? chat.title : ("ID: " + dialogId);
        }
    }
}
