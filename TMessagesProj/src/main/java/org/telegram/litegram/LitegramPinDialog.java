package org.telegram.litegram;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class LitegramPinDialog extends Dialog {

    public static final int MODE_SET = 0;
    public static final int MODE_VERIFY = 1;

    private static final int PIN_LENGTH = 4;
    private static final int COLOR_ACCENT = 0xFFAB7CFF;
    private static final int COLOR_ACCENT_DIM = 0xFF7B5EA7;

    private final int mode;
    private final long dialogId;
    private OnPinResult callback;
    private OnPinVerify callback2;
    private final StringBuilder currentPin = new StringBuilder();
    private String firstPin;
    private boolean confirmPhase;

    private DotsView dotsView;
    private TextView titleView;
    private View lockIcon;
    private LinearLayout rootLayout;
    private FrameLayout wrapper;

    public interface OnPinResult {
        void onPin(String pin);
    }

    public interface OnPinVerify {
        void onPin(String pin, LitegramPinDialog dialog);
    }

    public LitegramPinDialog(Context context, int mode, long dialogId, OnPinResult callback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.mode = mode;
        this.dialogId = dialogId;
        this.callback = callback;
    }

    public static void show(Context context, int mode, long dialogId, OnPinResult callback) {
        new LitegramPinDialog(context, mode, dialogId, callback).show();
    }

    public static void showVerify(Context context, long dialogId, OnPinVerify callback) {
        LitegramPinDialog dialog = new LitegramPinDialog(context, MODE_VERIFY, dialogId, null);
        dialog.callback2 = callback;
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                w.setStatusBarColor(Color.TRANSPARENT);
                w.setNavigationBarColor(0xFF0D0818);
            }
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            w.setWindowAnimations(0);
        }

        View bgView = new View(getContext()) {
            Paint bgPaint = new Paint();
            Paint orbPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint orbPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint orbPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean inited = false;

            @Override
            protected void onDraw(Canvas canvas) {
                int width = getWidth();
                int height = getHeight();
                if (!inited || width == 0) {
                    bgPaint.setShader(new LinearGradient(0, 0, width * 0.3f, height,
                            new int[]{0xFF0D0818, 0xFF1A0E2E, 0xFF12082A, 0xFF0D0818},
                            new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                    orbPaint1.setShader(new RadialGradient(width * 0.2f, height * 0.15f,
                            width * 0.45f, 0x30A855F7, 0x00000000, Shader.TileMode.CLAMP));
                    orbPaint2.setShader(new RadialGradient(width * 0.85f, height * 0.6f,
                            width * 0.4f, 0x207C3AED, 0x00000000, Shader.TileMode.CLAMP));
                    orbPaint3.setShader(new RadialGradient(width * 0.5f, height * 0.85f,
                            width * 0.35f, 0x18EC4899, 0x00000000, Shader.TileMode.CLAMP));
                    inited = true;
                }
                canvas.drawRect(0, 0, width, height, bgPaint);
                canvas.drawRect(0, 0, width, height, orbPaint1);
                canvas.drawRect(0, 0, width, height, orbPaint2);
                canvas.drawRect(0, 0, width, height, orbPaint3);
            }
        };

        wrapper = new FrameLayout(getContext());
        wrapper.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER);
        int statusBarH = AndroidUtilities.statusBarHeight;
        rootLayout.setPadding(0, statusBarH + AndroidUtilities.dp(28), 0, AndroidUtilities.dp(24));
        wrapper.addView(rootLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lockIcon = new View(getContext()) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = AndroidUtilities.dp(30);

                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx, cy, r,
                        new int[]{0x44AB7CFF, 0x22AB7CFF, 0x00000000},
                        new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, r * 1.4f, p);
                p.setShader(null);

                p.setColor(0x33FFFFFF);
                canvas.drawCircle(cx, cy, r, p);
                p.setColor(0x22FFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(1));
                canvas.drawCircle(cx, cy, r, p);

                p.setColor(Color.WHITE);
                p.setStrokeWidth(AndroidUtilities.dp(2.2f));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                float lockW = r * 0.35f;
                float lockH = r * 0.3f;
                float lockTop = cy + r * 0.02f;
                RectF body = new RectF(cx - lockW, lockTop, cx + lockW, lockTop + lockH);
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(body, AndroidUtilities.dp(2), AndroidUtilities.dp(2), p);
                p.setStyle(Paint.Style.STROKE);
                float arcR = lockW * 0.65f;
                float arcBottom = lockTop + AndroidUtilities.dp(1);
                canvas.drawArc(cx - arcR, arcBottom - arcR * 2.2f, cx + arcR, arcBottom, 180, 180, false, p);
            }
        };
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(72), AndroidUtilities.dp(72));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconLp.bottomMargin = AndroidUtilities.dp(16);
        rootLayout.addView(lockIcon, iconLp);

        titleView = new TextView(getContext());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        updateTitle();
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.gravity = Gravity.CENTER_HORIZONTAL;
        tLp.bottomMargin = AndroidUtilities.dp(28);
        rootLayout.addView(titleView, tLp);

        dotsView = new DotsView(getContext());
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(24));
        dLp.gravity = Gravity.CENTER_HORIZONTAL;
        dLp.bottomMargin = AndroidUtilities.dp(36);
        rootLayout.addView(dotsView, dLp);

        GridLayout numPad = createNumPad();
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nLp.gravity = Gravity.CENTER_HORIZONTAL;
        rootLayout.addView(numPad, nLp);

        if (mode == MODE_VERIFY) {
            TextView hintBtn = new TextView(getContext());
            hintBtn.setText(LocaleController.getString(R.string.LitegramPinHintShow));
            hintBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hintBtn.setTextColor(0x88FFFFFF);
            hintBtn.setGravity(Gravity.CENTER);
            hintBtn.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                    AndroidUtilities.dp(20), AndroidUtilities.dp(8));
            hintBtn.setOnClickListener(v -> {
                String h = getHintForDialog();
                String msg = (h != null && !h.isEmpty()) ? h
                        : LocaleController.getString(R.string.LitegramPinHintEmpty);
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
            });
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hLp.gravity = Gravity.CENTER_HORIZONTAL;
            hLp.topMargin = AndroidUtilities.dp(4);
            rootLayout.addView(hintBtn, hLp);
        }

        ImageView backBtn = new ImageView(getContext());
        backBtn.setImageResource(R.drawable.ic_ab_back);
        backBtn.setColorFilter(Color.WHITE);
        backBtn.setScaleType(ImageView.ScaleType.CENTER);
        backBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        backBtn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF), null, null));
        backBtn.setOnClickListener(v -> dismiss());
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                AndroidUtilities.dp(48), AndroidUtilities.dp(48),
                Gravity.TOP | Gravity.START);
        cLp.topMargin = statusBarH;
        cLp.leftMargin = AndroidUtilities.dp(4);
        wrapper.addView(backBtn, cLp);

        setContentView(wrapper);

        // Slide-up entrance animation
        rootLayout.setAlpha(0f);
        rootLayout.setTranslationY(AndroidUtilities.dp(100));
        rootLayout.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .setStartDelay(60)
                .start();

        backBtn.setAlpha(0f);
        backBtn.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(200)
                .start();

        if (mode == MODE_VERIFY && isBiometricAvailable()) {
            AndroidUtilities.runOnUIThread(this::tryBiometric, 500);
        }
    }

    private void updateTitle() {
        if (mode == MODE_SET) {
            titleView.setText(LocaleController.getString(confirmPhase
                    ? R.string.LitegramPinConfirm : R.string.LitegramPinSet));
        } else {
            titleView.setText(LocaleController.getString(R.string.LitegramPinEnter));
        }
    }

    private GridLayout createNumPad() {
        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(3);
        grid.setRowCount(4);

        boolean showBiometric = mode == MODE_VERIFY && isBiometricAvailable();
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9",
                showBiometric ? "FP" : "", "0", "\u232B"};

        int btnSize = AndroidUtilities.dp(76);
        int btnMargin = AndroidUtilities.dp(6);

        for (String label : labels) {
            GridLayout.LayoutParams bp = new GridLayout.LayoutParams();
            bp.width = btnSize;
            bp.height = btnSize;
            bp.setMargins(btnMargin, btnMargin, btnMargin, btnMargin);

            if (label.isEmpty()) {
                View spacer = new View(getContext());
                grid.addView(spacer, bp);
                continue;
            }

            if (label.equals("FP")) {
                ImageView fpBtn = new ImageView(getContext());
                fpBtn.setImageResource(R.drawable.fingerprint);
                fpBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                fpBtn.setColorFilter(Color.WHITE);
                fpBtn.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20),
                        AndroidUtilities.dp(20), AndroidUtilities.dp(20));
                fpBtn.setBackground(createGlassButtonBg(false));
                fpBtn.setOnClickListener(v -> tryBiometric());
                grid.addView(fpBtn, bp);
                continue;
            }

            if (label.equals("\u232B")) {
                TextView btn = new TextView(getContext());
                btn.setText(label);
                btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
                btn.setTextColor(0xCCFFFFFF);
                btn.setGravity(Gravity.CENTER);
                RippleDrawable ripple = new RippleDrawable(
                        ColorStateList.valueOf(0x22FFFFFF), null, null);
                btn.setBackground(ripple);
                btn.setOnClickListener(v -> onBackspace());
                grid.addView(btn, bp);
                continue;
            }

            TextView btn = new TextView(getContext());
            btn.setText(label);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            btn.setTextColor(Color.WHITE);
            btn.setGravity(Gravity.CENTER);
            btn.setTypeface(AndroidUtilities.bold());
            btn.setBackground(createGlassButtonBg(true));
            btn.setOnClickListener(v -> onDigit(label));
            grid.addView(btn, bp);
        }
        return grid;
    }

    private RippleDrawable createGlassButtonBg(boolean isDigit) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(isDigit ? 0x1AFFFFFF : 0x14FFFFFF);
        bg.setStroke(AndroidUtilities.dp(0.5f), 0x22FFFFFF);

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);

        return new RippleDrawable(
                ColorStateList.valueOf(0x28FFFFFF), bg, mask);
    }

    // --- Biometric ---

    private boolean isBiometricAvailable() {
        try {
            int canAuth = BiometricManager.from(getContext()).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    private void tryBiometric() {
        Context ctx = getContext();
        if (!(ctx instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) ctx;

        try {
            int canAuth = BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return;
        } catch (Exception e) {
            return;
        }

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(LocaleController.getString(R.string.LitegramPinBiometricTitle))
                .setDescription(LocaleController.getString(R.string.LitegramPinBiometricDesc))
                .setNegativeButtonText(LocaleController.getString("Cancel", R.string.Cancel))
                .build();

        BiometricPrompt prompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(ctx),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        onBiometricSuccess();
                    }
                });
        prompt.authenticate(info);
    }

    private void onBiometricSuccess() {
        if (callback2 != null) {
            callback2.onPin("__biometric__", LitegramPinDialog.this);
        } else {
            playUnlockAnimation();
        }
    }

    // --- Input ---

    private void onDigit(String digit) {
        if (currentPin.length() >= PIN_LENGTH) return;
        currentPin.append(digit);
        dotsView.setFilledCount(currentPin.length());
        dotsView.animateDot(currentPin.length() - 1);

        if (currentPin.length() == PIN_LENGTH) {
            AndroidUtilities.runOnUIThread(this::onPinComplete, 200);
        }
    }

    private void onBackspace() {
        if (currentPin.length() > 0) {
            currentPin.deleteCharAt(currentPin.length() - 1);
            dotsView.setFilledCount(currentPin.length());
        }
    }

    private void onPinComplete() {
        String pin = currentPin.toString();

        if (mode == MODE_SET) {
            if (!confirmPhase) {
                firstPin = pin;
                confirmPhase = true;
                resetInput();
                updateTitle();
            } else {
                if (pin.equals(firstPin)) {
                    showHintInput(pin);
                } else {
                    shakeAndReset(LocaleController.getString(R.string.LitegramPinMismatch));
                    confirmPhase = false;
                    firstPin = null;
                    updateTitle();
                }
            }
        } else {
            if (callback2 != null) {
                callback2.onPin(pin, this);
            } else if (callback != null) {
                callback.onPin(pin);
                dismiss();
            }
        }
    }

    private void showHintInput(String confirmedPin) {
        Context ctx = getContext();

        rootLayout.animate()
                .alpha(0f)
                .translationY(AndroidUtilities.dp(-30))
                .setDuration(200)
                .withEndAction(() -> {
                    rootLayout.removeAllViews();
                    buildHintLayout(ctx, confirmedPin);
                    rootLayout.setTranslationY(AndroidUtilities.dp(40));
                    rootLayout.setAlpha(0f);
                    rootLayout.animate()
                            .alpha(1f)
                            .translationY(0)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator(1.5f))
                            .start();
                })
                .start();
    }

    private void buildHintLayout(Context ctx, String confirmedPin) {
        LinearLayout hintLayout = new LinearLayout(ctx);
        hintLayout.setOrientation(LinearLayout.VERTICAL);
        hintLayout.setGravity(Gravity.CENTER);
        hintLayout.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(24),
                AndroidUtilities.dp(32), AndroidUtilities.dp(24));

        TextView hintTitle = new TextView(ctx);
        hintTitle.setText(LocaleController.getString(R.string.LitegramPinHintTitle));
        hintTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        hintTitle.setTextColor(Color.WHITE);
        hintTitle.setTypeface(AndroidUtilities.bold());
        hintTitle.setGravity(Gravity.CENTER);
        hintLayout.addView(hintTitle);

        TextView hintSub = new TextView(ctx);
        hintSub.setText(LocaleController.getString(R.string.LitegramPinHintPlaceholder));
        hintSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintSub.setTextColor(0x88FFFFFF);
        hintSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = AndroidUtilities.dp(6);
        hintLayout.addView(hintSub, subLp);

        EditText hintInput = new EditText(ctx);
        hintInput.setHint(LocaleController.getString(R.string.LitegramPinHintPlaceholder));
        hintInput.setHintTextColor(0x44FFFFFF);
        hintInput.setTextColor(Color.WHITE);
        hintInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hintInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hintInput.setSingleLine(true);
        hintInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(AndroidUtilities.dp(14));
        inputBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        inputBg.setColor(0x15FFFFFF);
        hintInput.setBackground(inputBg);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = AndroidUtilities.dp(20);
        hintLayout.addView(hintInput, inputLp);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = AndroidUtilities.dp(24);
        hintLayout.addView(btnRow, brLp);

        TextView skipBtn = createGlassBtn(ctx,
                LocaleController.getString(R.string.LitegramPinHintSkip), false);
        skipBtn.setOnClickListener(v -> {
            if (callback != null) callback.onPin(confirmedPin);
            dismiss();
        });
        btnRow.addView(skipBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View space = new View(ctx);
        btnRow.addView(space, new LinearLayout.LayoutParams(AndroidUtilities.dp(12), 0));

        TextView saveBtn = createGlassBtn(ctx,
                LocaleController.getString(R.string.LitegramPinHintSave), true);
        saveBtn.setOnClickListener(v -> {
            String hintText = hintInput.getText().toString().trim();
            if (!hintText.isEmpty()) {
                setHintForDialog(hintText);
            }
            if (callback != null) callback.onPin(confirmedPin);
            dismiss();
        });
        btnRow.addView(saveBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rootLayout.addView(hintLayout);
    }

    private boolean isFolderDialog() {
        return dialogId >= LitegramChatLocks.folderDialogId(0);
    }

    private int getFolderId() {
        return (int) (dialogId - LitegramChatLocks.folderDialogId(0));
    }

    private String getHintForDialog() {
        if (isFolderDialog()) {
            return LitegramChatLocks.getInstance().getFolderHint(getFolderId());
        }
        return LitegramChatLocks.getInstance().getHint(dialogId);
    }

    private void setHintForDialog(String text) {
        if (isFolderDialog()) {
            LitegramChatLocks.getInstance().setFolderHint(getFolderId(), text);
        } else {
            LitegramChatLocks.getInstance().setHint(dialogId, text);
        }
    }

    private TextView createGlassBtn(Context ctx, String text, boolean accent) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        if (accent) {
            bg.setColor(COLOR_ACCENT_DIM);
            bg.setStroke(AndroidUtilities.dp(0.5f), 0x44AB7CFF);
        } else {
            bg.setColor(0x1AFFFFFF);
            bg.setStroke(AndroidUtilities.dp(0.5f), 0x22FFFFFF);
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(14));
        mask.setColor(Color.WHITE);
        btn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF), bg, mask));
        return btn;
    }

    // --- Animations ---

    public void showWrongPin() {
        shakeAndReset(LocaleController.getString(R.string.LitegramPinWrong));
    }

    private void shakeAndReset(String message) {
        if (titleView != null) {
            titleView.setText(message);
            titleView.setTextColor(0xFFFF5252);
        }
        if (dotsView != null) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(400);
            animator.setInterpolator(new CycleInterpolator(3));
            animator.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                dotsView.setTranslationX(v * AndroidUtilities.dp(16));
            });
            animator.start();
        }
        AndroidUtilities.runOnUIThread(() -> {
            resetInput();
            if (titleView != null) {
                titleView.setTextColor(Color.WHITE);
                updateTitle();
            }
        }, 600);
    }

    public void playUnlockAnimation() {
        if (lockIcon == null) {
            dismiss();
            return;
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(lockIcon, View.SCALE_X, 1f, 1.4f, 0f),
                ObjectAnimator.ofFloat(lockIcon, View.SCALE_Y, 1f, 1.4f, 0f),
                ObjectAnimator.ofFloat(lockIcon, View.ALPHA, 1f, 1f, 0f)
        );
        set.setDuration(500);
        set.setInterpolator(new OvershootInterpolator(1.2f));

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(rootLayout, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(300);
        fadeOut.setStartDelay(200);

        AnimatorSet combined = new AnimatorSet();
        combined.playTogether(set, fadeOut);
        combined.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                dismiss();
            }
        });
        combined.start();
    }

    private void resetInput() {
        currentPin.setLength(0);
        if (dotsView != null) dotsView.setFilledCount(0);
    }

    // --- DotsView with glow + pop ---

    private static class DotsView extends View {
        private int filledCount;
        private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] dotScales = new float[PIN_LENGTH];

        DotsView(Context context) {
            super(context);
            emptyPaint.setColor(0x33FFFFFF);
            emptyPaint.setStyle(Paint.Style.FILL);
            filledPaint.setColor(Color.WHITE);
            filledPaint.setStyle(Paint.Style.FILL);
            glowPaint.setStyle(Paint.Style.FILL);
            borderPaint.setColor(0x44FFFFFF);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(0.5f));
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            for (int i = 0; i < PIN_LENGTH; i++) dotScales[i] = 1f;
        }

        void setFilledCount(int count) {
            filledCount = count;
            invalidate();
        }

        void animateDot(int index) {
            if (index < 0 || index >= PIN_LENGTH) return;
            ValueAnimator anim = ValueAnimator.ofFloat(0.4f, 1.25f, 1f);
            anim.setDuration(280);
            anim.setInterpolator(new OvershootInterpolator(2.5f));
            anim.addUpdateListener(a -> {
                dotScales[index] = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int dotR = AndroidUtilities.dp(8);
            int gap = AndroidUtilities.dp(22);
            int w = PIN_LENGTH * dotR * 2 + (PIN_LENGTH - 1) * gap;
            setMeasuredDimension(w, dotR * 2 + AndroidUtilities.dp(8));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int dotR = AndroidUtilities.dp(8);
            int gap = AndroidUtilities.dp(22);
            int totalW = PIN_LENGTH * dotR * 2 + (PIN_LENGTH - 1) * gap;
            float startX = (getWidth() - totalW) / 2f + dotR;
            float cy = getHeight() / 2f;

            for (int i = 0; i < PIN_LENGTH; i++) {
                float cx = startX + i * (dotR * 2 + gap);
                boolean filled = i < filledCount;
                float scale = filled ? dotScales[i] : 1f;
                float r = dotR * scale;

                if (filled) {
                    glowPaint.setColor(0x40AB7CFF);
                    glowPaint.setShadowLayer(AndroidUtilities.dp(8), 0, 0, 0x40AB7CFF);
                    canvas.drawCircle(cx, cy, r + AndroidUtilities.dp(2), glowPaint);
                    glowPaint.clearShadowLayer();
                }

                canvas.drawCircle(cx, cy, r, filled ? filledPaint : emptyPaint);
                if (!filled) {
                    canvas.drawCircle(cx, cy, r, borderPaint);
                }
            }
        }
    }
}
