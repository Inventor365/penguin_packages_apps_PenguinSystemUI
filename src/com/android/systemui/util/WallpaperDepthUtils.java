/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.MathUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.media.MediaViewController;
import com.android.systemui.penguin.DepthSubject;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.ScrimState;
import com.android.systemui.tuner.TunerService;

import dagger.Lazy;

import java.io.File;

public class WallpaperDepthUtils implements DepthSubject {

    private static final String TAG = "DepthWallpaper";

    private static final String WALLPAPER_DEPTH_KEY = "system:depth_wallpaper_subject_image_uri";
    private static final String WALLPAPER_DEPTH_ENABLED_KEY = "system:depth_wallpaper_enabled";
    private static final String WALLPAPER_DEPTH_OPACITY_KEY = "system:depth_wallpaper_opacity";
    private static final String WALLPAPER_DEPTH_OFFSET_X_KEY = "system:depth_wallpaper_offset_x";
    private static final String WALLPAPER_DEPTH_OFFSET_Y_KEY = "system:depth_wallpaper_offset_y";
    private static final String WALLPAPER_DEPTH_BOTTOM_FADE_KEY = "system:depth_wallpaper_bottom_fade";
    private static final String WALLPAPER_DEPTH_FADE_CURVE_KEY = "system:depth_wallpaper_fade_curve";
    private static final String WALLPAPER_DEPTH_BOTTOM_INSET_KEY = "system:depth_wallpaper_bottom_inset";
    private static final String WALLPAPER_DEPTH_FLOATING_KEY = "depth_wallpaper_floating";

    private static final int DEFAULT_FADE_CURVE_PERCENT = 50;
    private static final int DEFAULT_BOTTOM_FADE_DP = 100;
    private static final int DEFAULT_BOTTOM_INSET_DP = 180;

    private static WallpaperDepthUtils instance;
    private FrameLayout mLockScreenSubject;

    private final Context mContext;
    private final Lazy<ScrimController> mScrimControllerLazy;
    private final TunerService mTunerService;

    private boolean mDWallpaperEnabled;
    private int mDWallOpacity = 255;
    private String mWallpaperSubjectPath;
    private boolean mDozing;
    private boolean mBouncerShowing;
    private boolean mGlanceableHubShowing;
    private boolean mWallpaperLoaded = false;
    private String mPreviousWallpaperPath;
    private Bitmap mWallpaperBitmap;
    private int mOffsetX;
    private int mOffsetY;
    private boolean mUnlocking;
    private boolean mOnLockscreenScene = true;
    private boolean mFloating;
    private FloatingDepthMotion mFloatingMotion;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mLoadRunnable = this::startLoad;
    private int mLoadGeneration;
    /** What the subject on screen was decoded from; see {@link LoadWallpaperTask#keyFor}. */
    private String mLoadedKey;

    private int mBottomFadeDp = DEFAULT_BOTTOM_FADE_DP;
    private int mFadeCurvePercent = DEFAULT_FADE_CURVE_PERCENT;
    private int mBottomInsetDp = DEFAULT_BOTTOM_INSET_DP;

    private WallpaperDepthUtils(Context context, Lazy<ScrimController> scrimControllerLazy) {
        mContext = context.getApplicationContext();
        mScrimControllerLazy = scrimControllerLazy;
        mTunerService = Dependency.get(TunerService.class);
        mFloatingMotion = new FloatingDepthMotion(mContext);
        mTunerService.addTunable(mTunable, WALLPAPER_DEPTH_KEY, 
            WALLPAPER_DEPTH_ENABLED_KEY, WALLPAPER_DEPTH_OPACITY_KEY, 
            WALLPAPER_DEPTH_OFFSET_X_KEY, WALLPAPER_DEPTH_OFFSET_Y_KEY,
            WALLPAPER_DEPTH_BOTTOM_FADE_KEY, WALLPAPER_DEPTH_FADE_CURVE_KEY,
            WALLPAPER_DEPTH_BOTTOM_INSET_KEY, WALLPAPER_DEPTH_FLOATING_KEY);
        mLockScreenSubject = new FrameLayout(mContext) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                WallpaperDepthUtils.this.onDetachedFromWindow();
            }
        };
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        mLockScreenSubject.setLayoutParams(lp);
        // Make view non-interactive so touches pass through to notifications
        mLockScreenSubject.setClickable(false);
        mLockScreenSubject.setFocusable(false);
        mLockScreenSubject.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public static WallpaperDepthUtils getInstance(Context context, Lazy<ScrimController> scrimControllerLazy) {
        if (instance == null) {
            instance = new WallpaperDepthUtils(context, scrimControllerLazy);
            DepthSubject.Companion.setInstance(instance);
        }
        return instance;
    }
    public static WallpaperDepthUtils get() {
        return instance;
    }

    private static boolean sExpandedMediaArtVisible;

    @Override
    public void setExpandedMediaArtVisible(boolean visible) {
        if (sExpandedMediaArtVisible == visible) return;
        sExpandedMediaArtVisible = visible;
        updateDepthWallpaperVisibility();
    }

    public void setLockscreenScene(boolean onLockscreen) {
        if (mOnLockscreenScene == onLockscreen) return;
        mOnLockscreenScene = onLockscreen;
        if (onLockscreen) {
            updateDepthWallpaperVisibility();
        } else {
            hideDepthWallpaperImmediate();
        }
    }

    public void onUnlockStarted() {
        mUnlocking = true;
        hideDepthWallpaperImmediate();
    }

    public void onUnlockCancelled() {
        mUnlocking = false;
        updateDepthWallpaperVisibility();
    }

    public void onUnlockCompleted() {
        mUnlocking = false;
    }
    
    public void onDozingChanged(boolean dozing) {
        mDozing = dozing;
        if (mDozing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaper(true);
            updateDepthWallpaperVisibility();
            if (mLockScreenSubject != null) {
                mLockScreenSubject.postDelayed(this::updateDepthWallpaperVisibility, 200);
                mLockScreenSubject.postDelayed(this::updateDepthWallpaperVisibility, 500);
            }
        }
    }

    public void onBouncerShowingChanged(boolean showing) {
        if (mBouncerShowing == showing) {
            return;
        }
        mBouncerShowing = showing;
        if (mBouncerShowing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    public void onGlanceableHubShowingChanged(boolean showing) {
        if (mGlanceableHubShowing == showing) {
            return;
        }
        mGlanceableHubShowing = showing;
        if (mGlanceableHubShowing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case WALLPAPER_DEPTH_ENABLED_KEY:
                    mDWallpaperEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallpaper(true);
                    updateDepthWallpaperVisibility();
                    break;
                case WALLPAPER_DEPTH_KEY:
                    mPreviousWallpaperPath = mWallpaperSubjectPath;
                    mWallpaperSubjectPath = newValue;
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OPACITY_KEY:
                    int opacity = TunerService.parseInteger(newValue, 100);
                    mDWallOpacity = Math.round(opacity * 2.55f);
                    applyOpacity();
                    break;
                case WALLPAPER_DEPTH_OFFSET_X_KEY:
                    mOffsetX = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_Y_KEY:
                    mOffsetY = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_BOTTOM_FADE_KEY:
                    mBottomFadeDp = TunerService.parseInteger(newValue, DEFAULT_BOTTOM_FADE_DP);
                    applyBottomFade();
                    break;
                case WALLPAPER_DEPTH_FADE_CURVE_KEY:
                    mFadeCurvePercent = TunerService.parseInteger(newValue, DEFAULT_FADE_CURVE_PERCENT);
                    applyFadeCurve();
                    break;
                case WALLPAPER_DEPTH_FLOATING_KEY:
                    mFloating = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallpaperVisibility();
                    break;
                case WALLPAPER_DEPTH_BOTTOM_INSET_KEY:
                    mBottomInsetDp = TunerService.parseInteger(newValue, DEFAULT_BOTTOM_INSET_DP);
                    applyBottomInset();
                    break;
                default:
                    break;
            }
        }
    };
    
    public void setSubjectAlpha(float subjectAlpha) {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setAlpha(subjectAlpha));
    }

    private void applyOpacity() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background != null) {
            background.setAlpha(mDWallOpacity);
        }
    }

    private void applyBottomFade() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setFadeHeightPx(dpToPx(mBottomFadeDp));
        }
    }

    private void applyBottomInset() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setBottomInsetPx(dpToPx(mBottomInsetDp));
        }
    }

    private void applyFadeCurve() {
        if (mLockScreenSubject == null) return;
        Drawable background = mLockScreenSubject.getBackground();
        if (background instanceof FadeBottomDrawable) {
            ((FadeBottomDrawable) background).setFadeCurveExponent(computeFadeCurveExponent());
        }
    }

    private float computeFadeCurveExponent() {
        float percent = MathUtils.constrain(mFadeCurvePercent, 0, 100) / 100f;
        return 0.2f + percent * 0.8f;
    }

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        return Math.round(dp * displayMetrics.density);
    }
    
    public void updateDepthWallpaper() {
        updateDepthWallpaper(false);
    }

    @Override
    public View getView() {
        return mLockScreenSubject;
    }

    public FrameLayout getDepthWallpaperView() {
        return mLockScreenSubject;
    }

    private boolean isDWallpaperEnabled() {
        return mDWallpaperEnabled && mWallpaperSubjectPath != null
                && !mWallpaperSubjectPath.isEmpty();
    }

    private boolean isAlbumArtVisible() {
        try {
            MediaViewController controller = MediaViewController.get(mContext);
            return controller != null && controller.albumArtVisible();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canShowDepthWallpaper() {
        ScrimState currentState = null;
        try {
            if (mScrimControllerLazy != null && mScrimControllerLazy.get() != null) {
                currentState = mScrimControllerLazy.get().getState();
            }
        } catch (Throwable t) {
            // Ignore legacy assertion error if SceneContainer is active
        }
        // Only show on KEYGUARD state when bouncer is NOT showing
        return mLockScreenSubject != null
                && isDWallpaperEnabled()
                && !mDozing
                && !mBouncerShowing
                && !mGlanceableHubShowing
                && !mUnlocking
                && mOnLockscreenScene
                && (currentState == null || currentState == ScrimState.KEYGUARD)
                && mContext.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE
                && !isAlbumArtVisible()
                && !sExpandedMediaArtVisible;
    }

    public void updateDepthWallpaperVisibility() {
        if (mLockScreenSubject == null) return;
        if (!isDWallpaperEnabled()) {
            mLockScreenSubject.post(() -> {
                mLockScreenSubject.setVisibility(View.GONE);
                mLockScreenSubject.setTranslationZ(-100f);
                updateFloating(false);
            });
            return;
        }
        mLockScreenSubject.post(() -> {
            final boolean canShow = canShowDepthWallpaper();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "visible=" + canShow + " dozing=" + mDozing + " bouncer=" + mBouncerShowing
                        + " hub=" + mGlanceableHubShowing + " unlocking=" + mUnlocking
                        + " lockscreen=" + mOnLockscreenScene + " art=" + isAlbumArtVisible()
                        + " expandedArt=" + sExpandedMediaArtVisible);
            }
            final int targetVisibility = canShow ? View.VISIBLE : View.GONE;
            if (canShow) {
                mLockScreenSubject.animate().cancel();
            }
            mLockScreenSubject.setVisibility(targetVisibility);
            if (canShow) {
                mLockScreenSubject.setAlpha(1f);
                mLockScreenSubject.setTranslationZ(0f);
                mLockScreenSubject.invalidate();
            } else {
                mLockScreenSubject.setTranslationZ(-100f);
            }
            updateFloating(canShow);
        });
    }
    
    public void hideDepthWallpaper() {
        if (mLockScreenSubject == null) return;
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "hide", new Throwable());
        mLockScreenSubject.post(() -> {
            updateFloating(false);
            mLockScreenSubject.setVisibility(View.GONE);
            mLockScreenSubject.setTranslationZ(-100f);
        });
    }

    public void hideDepthWallpaperImmediate() {
        if (mLockScreenSubject == null) return;
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "hideImmediate", new Throwable());
        mLockScreenSubject.post(() -> {
            updateFloating(false);
            mLockScreenSubject.animate().cancel();
            mLockScreenSubject.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        mLockScreenSubject.setAlpha(1f);
                        if (canShowDepthWallpaper()) return;
                        mLockScreenSubject.setVisibility(View.GONE);
                        mLockScreenSubject.setTranslationZ(-100f);
                    })
                    .start();
        });
    }

    private void updateFloating(boolean subjectShowing) {
        if (subjectShowing && mFloating
                && mLockScreenSubject.getParent() instanceof ViewGroup root) {
            mFloatingMotion.start(root);
        } else {
            mFloatingMotion.stop();
        }
    }

    public Bitmap getResizedBitmap(Bitmap wallpaperBitmap, float xOffsetDp, float yOffsetDp) {
        return getResizedBitmap(wallpaperBitmap, xOffsetDp, yOffsetDp, portraitBounds());
    }

    /**
     * Scales {@code wallpaperBitmap} to cover {@code target} and crops it to exactly that size, shifted by the
     * user's offsets. The subject is drawn stretched to the screen, so a crop that ran past the
     * image's edge and came out narrower or shorter than the screen pulled it out of line with the
     * wallpaper; the shift now stops at the edge instead.
     */
    private Bitmap getResizedBitmap(Bitmap wallpaperBitmap, float xOffsetDp, float yOffsetDp,
            Rect target) {
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int width = target.width();
        int height = target.height();
        float scale = Math.max(width / (float) wallpaperBitmap.getWidth(),
                height / (float) wallpaperBitmap.getHeight());
        int scaledWidth = Math.max(Math.round(wallpaperBitmap.getWidth() * scale), width);
        int scaledHeight = Math.max(Math.round(wallpaperBitmap.getHeight() * scale), height);
        Bitmap scaled = Bitmap.createScaledBitmap(wallpaperBitmap, scaledWidth, scaledHeight, true);
        int x = MathUtils.constrain((scaledWidth - width) / 2
                - Math.round(xOffsetDp * displayMetrics.density), 0, scaledWidth - width);
        int y = MathUtils.constrain((scaledHeight - height) / 2
                - Math.round(yOffsetDp * displayMetrics.density), 0, scaledHeight - height);
        Bitmap cropped = Bitmap.createBitmap(scaled, x, y, width, height);
        if (scaled != cropped && scaled != wallpaperBitmap) {
            scaled.recycle();
        }
        return cropped;
    }

    /**
     * The screen in portrait, which is the only way the subject is shown. Measuring the current
     * window instead decoded a sideways subject when the settings changed in landscape.
     */
    private Rect portraitBounds() {
        Rect bounds = mContext.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics()
                .getBounds();
        return new Rect(0, 0, Math.min(bounds.width(), bounds.height()),
                Math.max(bounds.width(), bounds.height()));
    }

    public void updateDepthWallpaper(boolean forced) {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        boolean pathChanged = (mPreviousWallpaperPath != null && !mPreviousWallpaperPath.equals(mWallpaperSubjectPath));
        boolean backgroundMissing = (mLockScreenSubject.getBackground() == null);
        if (!mWallpaperLoaded || pathChanged || backgroundMissing || forced) {
            Log.d("WallpaperDepthUtils", "updateDepthWallpaper: " + (mWallpaperLoaded || forced ? "update required" : "first load"));
            scheduleLoad();
            mWallpaperLoaded = true;
            mPreviousWallpaperPath = mWallpaperSubjectPath;
        }
        updateDepthWallpaperVisibility();
    }

    /**
     * Every tunable asks for a reload, and registering delivers all of them at once, so post the
     * load and let a burst of requests decode the subject once.
     */
    private void scheduleLoad() {
        mMainHandler.removeCallbacks(mLoadRunnable);
        mMainHandler.post(mLoadRunnable);
    }

    private void startLoad() {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        String loaded = mLockScreenSubject.getBackground() != null ? mLoadedKey : null;
        new LoadWallpaperTask(++mLoadGeneration, mWallpaperSubjectPath, mOffsetX, mOffsetY,
                portraitBounds(), loaded).execute();
    }

    private class LoadWallpaperTask extends AsyncTask<Void, Void, Drawable> {
        private final int mGeneration;
        private final String mPath;
        private final int mTaskOffsetX;
        private final int mTaskOffsetY;
        private final Rect mTarget;
        private final String mShownKey;
        private String mKey;
        private Bitmap mBitmap;
        private boolean mUnchanged;

        LoadWallpaperTask(int generation, String path, int offsetX, int offsetY, Rect target,
                String shownKey) {
            mGeneration = generation;
            mPath = path;
            mTaskOffsetX = offsetX;
            mTaskOffsetY = offsetY;
            mTarget = target;
            mShownKey = shownKey;
        }

        /** Changes whenever the subject file, the offsets or the screen it is cut for do. */
        private String keyFor(File file) {
            return mPath + '|' + file.lastModified() + '|' + file.length() + '|' + mTaskOffsetX
                    + '|' + mTaskOffsetY + '|' + mTarget.width() + 'x' + mTarget.height();
        }

        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                Log.d("LoadWallpaperTask", "Wallpaper path: " + mPath);
                mKey = keyFor(new File(mPath));
                // Waking from doze asks for a reload in case the file was replaced in place;
                // the subject on screen is still right unless something it was made from changed.
                if (mKey.equals(mShownKey)) {
                    mUnchanged = true;
                    return null;
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(mPath, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    Log.d("LoadWallpaperTask", "Failed to decode bitmap from file");
                    return null;
                }
                // Decode no larger than needed to cover the screen; a camera-sized subject
                // decoded whole is tens of megabytes in SystemUI.
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (bounds.outWidth / (options.inSampleSize * 2) >= mTarget.width()
                        && bounds.outHeight / (options.inSampleSize * 2) >= mTarget.height()) {
                    options.inSampleSize *= 2;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(mPath, options);
                if (bitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode bitmap from file");
                    return null;
                }
                Bitmap resizedBitmap = getResizedBitmap(bitmap, mTaskOffsetX, mTaskOffsetY,
                        mTarget);
                if (resizedBitmap != bitmap) {
                    bitmap.recycle();
                }
                mBitmap = resizedBitmap;
                FadeBottomDrawable drawable =
                        new FadeBottomDrawable(resizedBitmap, dpToPx(mBottomFadeDp));
                drawable.setFadeCurveExponent(computeFadeCurveExponent());
                drawable.setBottomInsetPx(dpToPx(mBottomInsetDp));
                drawable.setAlpha(255);
                return drawable;
            } catch (OutOfMemoryError e) {
                Log.e("LoadWallpaperTask", "Out of memory error", e);
                return null;
            } catch (Exception e) {
                Log.e("LoadWallpaperTask", "Error loading wallpaper", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (mGeneration != mLoadGeneration) {
                // A newer request is queued behind this one and will set the subject.
                return;
            }
            if (mUnchanged) {
                updateDepthWallpaperVisibility();
                return;
            }
            if (drawable == null) {
                Log.d("LoadWallpaperTask", "decodeFile returned nothing, skipping application of subject as background");
                mWallpaperLoaded = false;
                mLoadedKey = null;
                updateDepthWallpaperVisibility();
                return;
            }
            mWallpaperBitmap = mBitmap;
            mLoadedKey = mKey;
            mLockScreenSubject.setBackground(drawable);
            mLockScreenSubject.getBackground().setAlpha(mDWallOpacity);
            Log.d("LoadWallpaperTask", "Subject Loaded!");
            updateDepthWallpaperVisibility();
        }
    }

    /**
     * The scene container takes the subject out and puts it back whenever it rebinds its window.
     * This object lives as long as SystemUI does, so it keeps listening to its settings; dropping
     * the tunable here left every later change to the depth wallpaper ignored. Only the tilt
     * sensor has to stop with the view.
     */
    public void onDetachedFromWindow() {
        mFloatingMotion.stop();
    }
}
