/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.os.cms;

/**
 * {@hide}
 * The CurrentManager class contains strings and constants used for values
 * in the CMS relatd intents and methods exposed to internal applications.
 */
public class CurrentManager {
    public static final String ACTION_CMS_MODE_CHANGED = "android.os.cms_mode_changed";
    public static final String EXTRA_MODE = "mode";

    // Values for "mode" field in ACTION_CMS_MODE_CHANGED intent
    public static final int MODE_AUTO = 0;
    public static final int MODE_CUSTOM = 1;
    public static final int MODE_CALL = 2;
    public static final int MODE_NO = 3;

    public static final String ACTION_CMS_DEV_STATE_CHANGED = "android.os.cms_dev_state_changed";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_STATE = "state";
    public static final int STATE_NORMAL = 0;
    public static final int STATE_CRITICAL = 3;
    public static final int STATE_DEFAULT = -1;
}
