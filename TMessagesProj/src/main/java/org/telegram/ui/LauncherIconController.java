package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public static int getNotificationColor() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return icon.notificationColor;
            }
        }
        return LauncherIcon.DEFAULT.notificationColor;
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_litegram_default_bg, R.mipmap.icon_litegram_default, R.string.AppIconDefault, false, 0xFF7B5EA7),
        DEFAULT_X("DefaultXIcon", R.drawable.icon_litegram_x_bg, R.mipmap.icon_litegram_x, R.string.AppIconDefaultX, false, 0xFF2A2438),
        BLACK("BlackIcon", R.drawable.icon_litegram_black_bg, R.mipmap.icon_litegram_black, R.string.AppIconBlack, false, 0xFF000000),
        WHITE("WhiteIcon", R.drawable.icon_litegram_white_bg, R.mipmap.icon_litegram_white, R.string.AppIconWhite, false, 0xFF5B2D8E);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final int notificationColor;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false, 0xFF5B2D8E);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this(key, background, foreground, title, premium, 0xFF5B2D8E);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, int notificationColor) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.notificationColor = notificationColor;
        }
    }
}
