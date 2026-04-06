package org.telegram.ui;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

public class IntroActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int PAGES_COUNT = 3;

    private static final int[] SLIDE_IMAGES = {
            R.drawable.litegram_slide_1,
            R.drawable.litegram_slide_2,
            R.drawable.litegram_slide_3
    };

    private final int currentAccount = UserConfig.selectedAccount;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView switchLanguageTextView;
    private GradientDrawable startMessagingButtonBackground;
    private TextView startMessagingButton;

    private int lastPage = 0;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private int currentViewPagerPage;

    private LocaleController.LocaleInfo localeInfo;

    private boolean destroyed;

    private boolean isOnLogout;

    @Override
    public boolean onFragmentCreate() {
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", System.currentTimeMillis()).apply();
        return true;
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setAddToContainer(false);

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(Color.BLACK);

        viewPager = new ViewPager(context);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        rootLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);
            }

            @Override
            public void onPageSelected(int i) {
                currentViewPagerPage = i;
                updateButtonText();
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (lastPage != viewPager.getCurrentItem()) {
                        lastPage = viewPager.getCurrentItem();
                    }
                }
            }
        });

        startMessagingButtonBackground = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF5B2D8E, 0xFF9E84B6});
        startMessagingButton = new TextView(context) {
            private final CellFlickerDrawable cellFlickerDrawable = new CellFlickerDrawable();
            {
                cellFlickerDrawable.drawFrame = false;
                cellFlickerDrawable.repeatProgress = 2f;
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                startMessagingButtonBackground.setBounds(0, 0, w, h);
                startMessagingButtonBackground.setCornerRadius(Math.min(w, h) / 2f);
                cellFlickerDrawable.setParentWidth(w);
            }

            @Override
            public void draw(@NonNull android.graphics.Canvas canvas) {
                startMessagingButtonBackground.draw(canvas);
                super.draw(canvas);
            }

            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, getMeasuredHeight() / 2f, null);
                invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (size > dp(260)) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(dp(320), MeasureSpec.EXACTLY), heightMeasureSpec);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        ScaleStateListAnimator.apply(startMessagingButton, .02f, 1.2f);
        startMessagingButton.setText(LocaleController.getString(R.string.Next));
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTypeface(AndroidUtilities.bold());
        startMessagingButton.setTextColor(Color.WHITE);
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setPadding(dp(34), 0, dp(34), 0);

        rootLayout.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 16, 0, 16, 120));
        startMessagingButton.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            if (currentViewPagerPage < PAGES_COUNT - 1) {
                viewPager.setCurrentItem(currentViewPagerPage + 1, true);
            } else {
                startPressed = true;
                presentFragment(new LoginActivity().setIntroView(rootLayout, startMessagingButton), true);
                destroyed = true;
            }
        });

        bottomPages = new BottomPagesView(context, viewPager, PAGES_COUNT);
        rootLayout.addView(bottomPages, LayoutHelper.createFrame(66, 20, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 76));

        switchLanguageTextView = new TextView(context);
        switchLanguageTextView.setGravity(Gravity.CENTER);
        switchLanguageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        switchLanguageTextView.setTextColor(0xFF9E84B6);
        rootLayout.addView(switchLanguageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 40));
        switchLanguageTextView.setOnClickListener(v -> {
            if (startPressed || localeInfo == null) {
                return;
            }
            startPressed = true;

            AlertDialog loaderDialog = new AlertDialog(v.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            loaderDialog.setCanCancel(false);
            loaderDialog.showDelayed(1000);

            NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.reloadInterface) {
                        loaderDialog.dismiss();
                        NotificationCenter.getGlobalInstance().removeObserver(this, id);
                        AndroidUtilities.runOnUIThread(() -> {
                            presentFragment(new LoginActivity().setIntroView(rootLayout, startMessagingButton), true);
                            destroyed = true;
                        }, 100);
                    }
                }
            }, NotificationCenter.reloadInterface);
            LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
        });

        fragmentView = rootLayout;

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.configLoaded);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        checkContinueText();
        justCreated = true;

        return fragmentView;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (justCreated) {
            if (LocaleController.isRTL) {
                viewPager.setCurrentItem(PAGES_COUNT - 1);
                lastPage = PAGES_COUNT - 1;
            } else {
                viewPager.setCurrentItem(0);
                lastPage = 0;
            }
            justCreated = false;
        }
        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return false;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.configLoaded);
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", 0).apply();
    }

    private void checkContinueText() {
        LocaleController.LocaleInfo englishInfo = null;
        LocaleController.LocaleInfo systemInfo = null;
        LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        String systemLang = MessagesController.getInstance(currentAccount).suggestedLangCode;
        if (systemLang == null || systemLang.equals("en") && LocaleController.getInstance().getSystemDefaultLocale().getLanguage() != null && !LocaleController.getInstance().getSystemDefaultLocale().getLanguage().equals("en")) {
            systemLang = LocaleController.getInstance().getSystemDefaultLocale().getLanguage();
            if (systemLang == null) {
                systemLang = "en";
            }
        }

        String arg = systemLang.contains("-") ? systemLang.split("-")[0] : systemLang;
        String alias = LocaleController.getLocaleAlias(arg);
        for (int a = 0; a < LocaleController.getInstance().languages.size(); a++) {
            LocaleController.LocaleInfo info = LocaleController.getInstance().languages.get(a);
            if (info.shortName.equals("en")) {
                englishInfo = info;
            }
            if (info.shortName.replace("_", "-").equals(systemLang) || info.shortName.equals(arg) || info.shortName.equals(alias)) {
                systemInfo = info;
            }
            if (englishInfo != null && systemInfo != null) {
                break;
            }
        }
        if (englishInfo == null || systemInfo == null || englishInfo == systemInfo) {
            return;
        }
        TLRPC.TL_langpack_getStrings req = new TLRPC.TL_langpack_getStrings();
        req.lang_code = systemInfo.getLangCode();
        req.keys.add("ContinueOnThisLanguage");
        final LocaleController.LocaleInfo finalSystemInfo = systemInfo;
        final String finalSystemLang = systemLang;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                Vector res = (Vector) response;
                if (!res.objects.isEmpty()) {
                    TLRPC.TL_langPackString string = (TLRPC.TL_langPackString) res.objects.get(0);
                    String stringValue = string.value;
                    String currentContinue = LocaleController.getString(R.string.ContinueOnThisLanguage);
                    if (!stringValue.equals(currentContinue) && !destroyed) {
                        localeInfo = finalSystemInfo;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (!destroyed) {
                                switchLanguageTextView.setText(stringValue);
                                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                preferences.edit().putString("language_showed2", finalSystemLang.toLowerCase()).apply();
                            }
                        });
                    }
                }
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack || id == NotificationCenter.configLoaded) {
            checkContinueText();
        }
    }

    private void updateButtonText() {
        if (startMessagingButton == null) return;
        if (currentViewPagerPage >= PAGES_COUNT - 1) {
            startMessagingButton.setText(LocaleController.getString(R.string.StartMessaging));
        } else {
            startMessagingButton.setText(LocaleController.getString(R.string.Next));
        }
    }

    public IntroActivity setOnLogout() {
        isOnLogout = true;
        return this;
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (isOnLogout) {
            AnimatorSet set = new AnimatorSet().setDuration(50);
            set.playTogether(ValueAnimator.ofFloat());
            return set;
        }
        return null;
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return PAGES_COUNT;
        }

        @NonNull
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ImageView imageView = new ImageView(container.getContext());
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            imageView.setPadding(0, 0, 0, dp(160));
            imageView.setImageResource(SLIDE_IMAGES[position]);
            container.addView(imageView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.setCurrentPage(position);
            currentViewPagerPage = position;
        }

        @Override
        public boolean isViewFromObject(View view, @NonNull Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    @Override
    public ArrayList<org.telegram.ui.ActionBar.ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }
}
