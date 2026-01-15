/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2025 The LineageOS Project
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
package com.android.internal.infinity.hardware;

import android.content.Context;
import android.hidl.base.V1_0.IBase;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.android.internal.infinity.app.LineageContextConstants;
import com.android.internal.infinity.hardware.HIDLHelper;

import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages access to LineageOS hardware extensions
 *
 *  <p>
 *  This manager requires the HARDWARE_ABSTRACTION_ACCESS permission.
 *  <p>
 *  To get the instance of this class, utilize LineageHardwareManager#getInstance(Context context)
 */
public final class LineageHardwareManager {
    private static final String TAG = "LineageHardwareManager";

    // The VisibleForTesting annotation is to ensure Proguard doesn't remove these
    // fields, as they might be used via reflection. When the @Keep annotation in
    // the support library is properly handled in the platform, we should change this.
    
    /**
     * High Touch Polling Rate
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_POLLING_RATE = 0x8;

    /**
     * High touch sensitivity for touch panels
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10;

    /**
     * Hardware navigation key disablement
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_DISABLE = 0x20;

    /**
     * Touchscreen hovering
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCH_HOVERING = 0x800;

    /**
     * Touchscreen gesture
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCHSCREEN_GESTURES = 0x80000;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_HIGH_TOUCH_POLLING_RATE,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_TOUCH_HOVERING
    );

    private static ILineageHardwareService sService;
    private static LineageHardwareManager sLineageHardwareManagerInstance;

    private Context mContext;

    // AIDL hals
    private HashMap<Integer, IBinder> mAIDLMap = new HashMap<Integer, IBinder>();

    // HIDL hals
    private HashMap<Integer, IBase> mHIDLMap = new HashMap<Integer, IBase>();

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private LineageHardwareManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();

        if (!checkService()) {
            Log.wtf(TAG, "Unable to get LineageHardwareService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link com.android.internal.infinity.hardware.LineageHardwareManager}
     * @param context
     * @return {@link LineageHardwareManager}
     */
    public static LineageHardwareManager getInstance(Context context) {
        if (sLineageHardwareManagerInstance == null) {
            sLineageHardwareManagerInstance = new LineageHardwareManager(context);
        }
        return sLineageHardwareManagerInstance;
    }

    /** @hide */
    public static ILineageHardwareService getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_HARDWARE_SERVICE);
        if (b != null) {
            sService = ILineageHardwareService.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    /**
     * Determine if a Lineage Hardware feature is supported on this device
     *
     * @param feature The Lineage Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
    return isSupportedAIDL(feature) || isSupportedHIDL(feature) || isSupportedLegacy(feature);
    }

    private boolean isSupportedAIDL(int feature) {
        if (!mAIDLMap.containsKey(feature)) {
            mAIDLMap.put(feature, getAIDLService(feature));
        }
        return mAIDLMap.get(feature) != null;
    }

    private boolean isSupportedHIDL(int feature) {
        if (!mHIDLMap.containsKey(feature)) {
            mHIDLMap.put(feature, getHIDLService(feature));
        }
        return mHIDLMap.get(feature) != null;
    }

    private boolean isSupportedLegacy(int feature) {
        try {
            if (checkService()) {
                return feature == (sService.getSupportedFeatures() & feature);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private IBinder getAIDLService(int feature) {
        switch (feature) {
            case FEATURE_HIGH_TOUCH_POLLING_RATE:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.touch.IHighTouchPollingRate.DESCRIPTOR + "/default");
            case FEATURE_HIGH_TOUCH_SENSITIVITY:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.touch.IGloveMode.DESCRIPTOR + "/default");
            case FEATURE_KEY_DISABLE:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.touch.IKeyDisabler.DESCRIPTOR + "/default");
            case FEATURE_TOUCH_HOVERING:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.touch.IStylusMode.DESCRIPTOR + "/default");
            case FEATURE_TOUCHSCREEN_GESTURES:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.touch.ITouchscreenGesture.DESCRIPTOR + "/default");
        }
        return null;
    }

    private IBase getHIDLService(int feature) {
        try {
            switch (feature) {
                case FEATURE_HIGH_TOUCH_POLLING_RATE:
                    return vendor.lineage.touch.V1_0.IHighTouchPollingRate.getService(true);
                case FEATURE_HIGH_TOUCH_SENSITIVITY:
                    return vendor.lineage.touch.V1_0.IGloveMode.getService(true);
                case FEATURE_KEY_DISABLE:
                    return vendor.lineage.touch.V1_0.IKeyDisabler.getService(true);
                case FEATURE_TOUCH_HOVERING:
                    return vendor.lineage.touch.V1_0.IStylusMode.getService(true);
                case FEATURE_TOUCHSCREEN_GESTURES:
                    return vendor.lineage.touch.V1_0.ITouchscreenGesture.getService(true);
            }
        } catch (NoSuchElementException | RemoteException e) {
        }
        return null;
    }

    /**
     * String version for preference constraints
     *
     * @hide
     */
    public boolean isSupported(String feature) {
        if (!feature.startsWith("FEATURE_")) {
            return false;
        }
        try {
            Field f = getClass().getField(feature);
            if (f != null) {
                return isSupported((int) f.get(null));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.d(TAG, e.getMessage(), e);
        }

        return false;
    }
    /**
     * Determine if the given feature is enabled or disabled.
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to query
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean get(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (isSupportedAIDL(feature)) {
                IBinder b = mAIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        vendor.lineage.touch.IHighTouchPollingRate highTouchPollingRate =
                                vendor.lineage.touch.IHighTouchPollingRate.Stub.asInterface(b);
                        return highTouchPollingRate.getEnabled();
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        vendor.lineage.touch.IGloveMode gloveMode =
                                vendor.lineage.touch.IGloveMode.Stub.asInterface(b);
                        return gloveMode.getEnabled();
                    case FEATURE_KEY_DISABLE:
                        vendor.lineage.touch.IKeyDisabler keyDisabler =
                                vendor.lineage.touch.IKeyDisabler.Stub.asInterface(b);
                        return keyDisabler.getEnabled();
                    case FEATURE_TOUCH_HOVERING:
                        vendor.lineage.touch.IStylusMode stylusMode =
                                vendor.lineage.touch.IStylusMode.Stub.asInterface(b);
                        return stylusMode.getEnabled();
                }
            } else if (isSupportedHIDL(feature)) {
                IBase obj = mHIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        vendor.lineage.touch.V1_0.IHighTouchPollingRate highTouchPollingRate =
                                (vendor.lineage.touch.V1_0.IHighTouchPollingRate) obj;
                        return highTouchPollingRate.isEnabled();
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        vendor.lineage.touch.V1_0.IGloveMode gloveMode =
                                (vendor.lineage.touch.V1_0.IGloveMode) obj;
                        return gloveMode.isEnabled();
                    case FEATURE_KEY_DISABLE:
                        vendor.lineage.touch.V1_0.IKeyDisabler keyDisabler =
                                (vendor.lineage.touch.V1_0.IKeyDisabler) obj;
                        return keyDisabler.isEnabled();
                    case FEATURE_TOUCH_HOVERING:
                        vendor.lineage.touch.V1_0.IStylusMode stylusMode =
                                (vendor.lineage.touch.V1_0.IStylusMode) obj;
                        return stylusMode.isEnabled();
                }
            } else if (checkService()) {
                return sService.get(feature);
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * Enable or disable the given feature
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to set
     * @param enable true to enable, false to disale
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean set(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (isSupportedAIDL(feature)) {
                IBinder b = mAIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        vendor.lineage.touch.IHighTouchPollingRate highTouchPollingRate =
                                vendor.lineage.touch.IHighTouchPollingRate.Stub.asInterface(b);
                        highTouchPollingRate.setEnabled(enable);
                        break;
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        vendor.lineage.touch.IGloveMode gloveMode =
                                vendor.lineage.touch.IGloveMode.Stub.asInterface(b);
                        gloveMode.setEnabled(enable);
                        break;
                    case FEATURE_KEY_DISABLE:
                        vendor.lineage.touch.IKeyDisabler keyDisabler =
                                vendor.lineage.touch.IKeyDisabler.Stub.asInterface(b);
                        keyDisabler.setEnabled(enable);
                        break;
                    case FEATURE_TOUCH_HOVERING:
                        vendor.lineage.touch.IStylusMode stylusMode =
                                vendor.lineage.touch.IStylusMode.Stub.asInterface(b);
                        stylusMode.setEnabled(enable);
                        break;
                }
                return enable;
            }
            if (isSupportedHIDL(feature)) {
                IBase obj = mHIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        vendor.lineage.touch.V1_0.IHighTouchPollingRate highTouchPollingRate =
                                (vendor.lineage.touch.V1_0.IHighTouchPollingRate) obj;
                        return highTouchPollingRate.setEnabled(enable);
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        vendor.lineage.touch.V1_0.IGloveMode gloveMode =
                                (vendor.lineage.touch.V1_0.IGloveMode) obj;
                        return gloveMode.setEnabled(enable);
                    case FEATURE_KEY_DISABLE:
                        vendor.lineage.touch.V1_0.IKeyDisabler keyDisabler =
                                (vendor.lineage.touch.V1_0.IKeyDisabler) obj;
                        return keyDisabler.setEnabled(enable);
                    case FEATURE_TOUCH_HOVERING:
                        vendor.lineage.touch.V1_0.IStylusMode stylusMode =
                                (vendor.lineage.touch.V1_0.IStylusMode) obj;
                        return stylusMode.setEnabled(enable);
                }
            } else if (checkService()) {
                return sService.set(feature, enable);
            }
        } catch (Exception e) {
        }
        return false;
    }


    /**
     * @return a list of available touchscreen gestures on the devices
     */
   public TouchscreenGesture[] getTouchscreenGestures() {
        try {
            if (isSupportedAIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                vendor.lineage.touch.ITouchscreenGesture touchscreenGesture =
                        vendor.lineage.touch.ITouchscreenGesture.Stub.asInterface(
                                mAIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES));
                return AIDLHelper.fromAIDLGestures(touchscreenGesture.getSupportedGestures());
            }
            if (isSupportedHIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                vendor.lineage.touch.V1_0.ITouchscreenGesture touchscreenGesture =
                        (vendor.lineage.touch.V1_0.ITouchscreenGesture)
                                mHIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES);
                return HIDLHelper.fromHIDLGestures(touchscreenGesture.getSupportedGestures());
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * @return true if setting the activation status was successful
     */
    public boolean setTouchscreenGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        try {
            if (isSupportedAIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                vendor.lineage.touch.ITouchscreenGesture touchscreenGesture =
                        vendor.lineage.touch.ITouchscreenGesture.Stub.asInterface(
                                mAIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES));
                touchscreenGesture.setGestureEnabled(AIDLHelper.toAIDLGesture(gesture), state);
                return true;
            }
            if (isSupportedHIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                vendor.lineage.touch.V1_0.ITouchscreenGesture touchscreenGesture =
                        (vendor.lineage.touch.V1_0.ITouchscreenGesture)
                                mHIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES);
                return touchscreenGesture.setGestureEnabled(
                        HIDLHelper.toHIDLGesture(gesture), state);
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        sService = getService();
        if (sService == null) {
            Log.w(TAG, "not connected to LineageHardwareManagerService");
            return false;
        }
        return true;
    }

}
