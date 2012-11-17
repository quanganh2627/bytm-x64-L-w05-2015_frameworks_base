/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.util;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @hide
 */
public final class LocalLog {

    private LinkedList<String> mLog;
    private int mMaxLines;
    private final static Date sDate = new Date();
    private final static SimpleDateFormat sFormatter =
            new SimpleDateFormat("HH:mm:ss.SSS");

    public LocalLog(int maxLines) {
        mLog = new LinkedList<String>();
        mMaxLines = maxLines;
    }

    public synchronized void log(String msg) {
        if (mMaxLines > 0) {
            sDate.setTime(System.currentTimeMillis());
            mLog.add(sFormatter.format(sDate) + " - " + msg);
            while (mLog.size() > mMaxLines) mLog.remove();
        }
    }

    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        Iterator<String> itr = mLog.listIterator(0);
        while (itr.hasNext()) {
            pw.println(itr.next());
        }
    }
}
