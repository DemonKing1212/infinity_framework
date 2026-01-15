/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeDisplayAware;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.LocationController;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.inject.Inject;

/**
 * Quick Settings tile for: Light/Dark/Black theme modes.
 */
public class UiModeNightTile extends QSTileImpl<QSTile.BooleanState> implements
        ConfigurationController.ConfigurationListener,
        BatteryController.BatteryStateChangeCallback {

    public static final String TILE_SPEC = "dark";
    private static final String BERRY_BLACK_THEME_KEY = "berry_black_theme";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");

    private final UiModeManager mUiModeManager;
    private final BatteryController mBatteryController;
    private final LocationController mLocationController;
    
    private static final int MODE_LIGHT = 0;
    private static final int MODE_DARK = 1;
    private static final int MODE_BLACK = 2;
    
    private int mCurrentMode = MODE_LIGHT;

    @Inject
    public UiModeNightTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            @ShadeDisplayAware ConfigurationController configurationController,
            BatteryController batteryController,
            LocationController locationController
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mBatteryController = batteryController;
        mUiModeManager = host.getUserContext().getSystemService(UiModeManager.class);
        mLocationController = locationController;
        configurationController.observe(getLifecycle(), this);
        batteryController.observe(getLifecycle(), this);
        
        updateCurrentMode();
    }

    @Override
    public void onUiModeChanged() {
        updateCurrentMode();
        refreshState();
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        if (isPowerSave) {
            if (mCurrentMode == MODE_LIGHT) {
                mCurrentMode = MODE_DARK;
                mUiModeManager.setNightModeActivated(true);
            }
        }
        refreshState();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            return;
        }

        mCurrentMode = (mCurrentMode + 1) % 3;
        applyCurrentMode();
        refreshState();
    }

    private void applyCurrentMode() {
        switch (mCurrentMode) {
            case MODE_LIGHT:
                setBlackThemeEnabled(false);
                mUiModeManager.setNightModeActivated(false);
                break;
            case MODE_DARK:
                setBlackThemeEnabled(false);
                mUiModeManager.setNightModeActivated(true);
                break;
            case MODE_BLACK:
                setBlackThemeEnabled(true);
                mUiModeManager.setNightModeActivated(true);
                break;
        }
    }

    private void updateCurrentMode() {
        boolean isBlackTheme = isBlackThemeEnabled();
        boolean isNightMode = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (isBlackTheme) {
            mCurrentMode = MODE_BLACK;
        } else if (isNightMode) {
            mCurrentMode = MODE_DARK;
        } else {
            mCurrentMode = MODE_LIGHT;
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean powerSave = mBatteryController.isPowerSave();
        updateCurrentMode();

        switch (mCurrentMode) {
            case MODE_LIGHT:
                state.label = mContext.getString(R.string.quick_settings_ui_mode_light_label);
                state.icon = ResourceIcon.get(R.drawable.qs_light_mode_icon);
                state.state = Tile.STATE_INACTIVE;
                break;
            case MODE_DARK:
                state.label = mContext.getString(R.string.quick_settings_ui_mode_dark_label);
                state.icon = ResourceIcon.get(R.drawable.qs_dark_mode_icon);
                state.state = Tile.STATE_ACTIVE;
                break;
            case MODE_BLACK:
                state.label = mContext.getString(R.string.quick_settings_ui_mode_black_label);
                state.icon = ResourceIcon.get(R.drawable.qs_black_mode_icon);
                state.state = Tile.STATE_ACTIVE;
                break;
        }

        if (powerSave) {
            state.secondaryLabel = mContext.getString(
                    R.string.quick_settings_dark_mode_secondary_label_battery_saver);
            state.state = Tile.STATE_UNAVAILABLE;
        } else {
            state.secondaryLabel = getSecondaryLabelForMode(mCurrentMode != MODE_LIGHT);
        }

        state.contentDescription = TextUtils.isEmpty(state.secondaryLabel)
                ? state.label
                : TextUtils.concat(state.label, ", ", state.secondaryLabel);
    }

    private CharSequence getSecondaryLabelForMode(boolean isDarkModeActive) {
        int uiMode = mUiModeManager.getNightMode();
        
        if (uiMode == UiModeManager.MODE_NIGHT_AUTO && mLocationController.isLocationEnabled()) {
            return mContext.getResources().getString(isDarkModeActive
                    ? R.string.quick_settings_dark_mode_secondary_label_until_sunrise
                    : R.string.quick_settings_dark_mode_secondary_label_on_at_sunset);
        } else if (uiMode == UiModeManager.MODE_NIGHT_CUSTOM) {
            int nightModeCustomType = mUiModeManager.getNightModeCustomType();
            if (nightModeCustomType == UiModeManager.MODE_NIGHT_CUSTOM_TYPE_SCHEDULE) {
                final boolean use24HourFormat = android.text.format.DateFormat.is24HourFormat(
                        mContext);
                final LocalTime time = isDarkModeActive 
                        ? mUiModeManager.getCustomNightModeEnd()
                        : mUiModeManager.getCustomNightModeStart();
                return mContext.getResources().getString(isDarkModeActive
                                ? R.string.quick_settings_dark_mode_secondary_label_until
                                : R.string.quick_settings_dark_mode_secondary_label_on_at,
                        use24HourFormat ? time.toString() : formatter.format(time));
            } else if (nightModeCustomType == UiModeManager.MODE_NIGHT_CUSTOM_TYPE_BEDTIME) {
                return mContext.getResources().getString(isDarkModeActive
                        ? R.string.quick_settings_dark_mode_secondary_label_until_bedtime_ends
                        : R.string.quick_settings_dark_mode_secondary_label_on_at_bedtime);
            }
        }
        return null;
    }

    private boolean isBlackThemeEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                BERRY_BLACK_THEME_KEY, 0) == 1;
    }

    private void setBlackThemeEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                BERRY_BLACK_THEME_KEY, enabled ? 1 : 0);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.QS_UI_MODE_NIGHT;
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DARK_THEME_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }
}
