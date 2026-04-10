package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

public class BottomPagesView extends View {

    private static final int DOT_SIZE_DP = 8;
    private static final int DOT_SPACING_DP = 14;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private int scrollPosition;
    private int currentPage;
    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private RectF rect = new RectF();
    private float animatedProgress;
    private ViewPager viewPager;
    private int pagesCount;

    private int colorKey = -1;
    private int selectedColorKey = -1;

    public BottomPagesView(Context context, ViewPager pager, int count) {
        super(context);
        viewPager = pager;
        pagesCount = count;
        setClickable(true);
    }

    public void setPageOffset(int position, float offset) {
        progress = offset;
        scrollPosition = position;
        invalidate();
    }

    public void setCurrentPage(int page) {
        currentPage = page;
        invalidate();
    }

    public void setColor(int key, int selectedKey) {
        colorKey = key;
        selectedColorKey = selectedKey;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && viewPager != null) {
            float totalWidth = (pagesCount - 1) * AndroidUtilities.dp(DOT_SPACING_DP) + AndroidUtilities.dp(DOT_SIZE_DP);
            float startX = (getMeasuredWidth() - totalWidth) / 2f;
            float touchX = event.getX();
            for (int a = 0; a < pagesCount; a++) {
                float dotX = startX + a * AndroidUtilities.dp(DOT_SPACING_DP);
                if (touchX >= dotX - AndroidUtilities.dp(4) && touchX <= dotX + AndroidUtilities.dp(DOT_SIZE_DP + 4)) {
                    viewPager.setCurrentItem(a, true);
                    break;
                }
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float totalWidth = (pagesCount - 1) * AndroidUtilities.dp(DOT_SPACING_DP) + AndroidUtilities.dp(DOT_SIZE_DP);
        float startX = (getMeasuredWidth() - totalWidth) / 2f;
        float centerY = getMeasuredHeight() / 2f;
        float radius = AndroidUtilities.dp(DOT_SIZE_DP) / 2f;

        if (colorKey >= 0) {
            paint.setColor((Theme.getColor(colorKey) & 0x00ffffff) | 0xb4000000);
        } else {
            paint.setColor(Theme.getCurrentTheme().isDark() ? 0xff555555 : 0xffbbbbbb);
        }
        currentPage = viewPager.getCurrentItem();
        for (int a = 0; a < pagesCount; a++) {
            if (a == currentPage) {
                continue;
            }
            float cx = startX + a * AndroidUtilities.dp(DOT_SPACING_DP) + radius;
            canvas.drawCircle(cx, centerY, radius, paint);
        }
        if (selectedColorKey >= 0) {
            paint.setColor(Theme.getColor(selectedColorKey));
        } else {
            paint.setColor(0xFF9E84B6);
        }
        float cx = startX + currentPage * AndroidUtilities.dp(DOT_SPACING_DP);
        if (progress != 0) {
            if (scrollPosition >= currentPage) {
                rect.set(cx, centerY - radius, cx + AndroidUtilities.dp(DOT_SIZE_DP) + AndroidUtilities.dp(DOT_SPACING_DP) * progress, centerY + radius);
            } else {
                rect.set(cx - AndroidUtilities.dp(DOT_SPACING_DP) * (1.0f - progress), centerY - radius, cx + AndroidUtilities.dp(DOT_SIZE_DP), centerY + radius);
            }
        } else {
            rect.set(cx, centerY - radius, cx + AndroidUtilities.dp(DOT_SIZE_DP), centerY + radius);
        }
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
