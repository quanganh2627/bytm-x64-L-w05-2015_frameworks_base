/*
 * Copyright (c) 2012-2013, Intel Corporation. All rights reserved.
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
 *
 */

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.os.UEventObserver;
import android.util.Slog;
import android.view.InputDevice;

import com.android.server.input.InputManagerService;
import static com.android.server.input.InputManagerService.SW_SILENT;

/**
 * <p>HardwareMuteSwitch checks for the status of Hardware Mute switch after
 *    boot completed and notifies in case the HW Mute switch is ON.
 *
 */
final class HardwareMuteSwitch {
    private final InputManagerService mInputManager;

    public HardwareMuteSwitch(Context context, InputManagerService inputManager) {
        mInputManager = inputManager;
        context.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        bootCompleted();
                    }
                },
                new IntentFilter(Intent.ACTION_BOOT_COMPLETED), null, null);
    }

    private void bootCompleted() {
        if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY,
                    SW_SILENT) == 1) {
            mInputManager.getWindowManagerCallbacks().
                    notifySilentSwitchChanged(true);
        }
    }
}
