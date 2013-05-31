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
import java.util.Calendar;

/**
 * @hide
 */
public final class LocalLog {

    private final String[] mLog;
    private final String[] mLogCopy;
    private final long[] mTimestamp;
    private final long[] mTimestampCopy;
    private final int mMaxLines;
    private int mHead;
    private int mTail;
    private int mSize;
    private final Calendar mCalendar;
    private final StringBuilder mBuilder;

    public LocalLog(int maxLines) {
        mMaxLines = maxLines;
        mLog = new String[maxLines];
        mLogCopy = new String[maxLines];
        mTimestamp = new long[maxLines];
        mTimestampCopy = new long[maxLines];
        mHead = mTail = mSize = 0;
        mCalendar = Calendar.getInstance();
        mBuilder = new StringBuilder();
    }

    public void log(String msg) {
        final long t = System.currentTimeMillis();
        synchronized (this) {
            if (mMaxLines > 0) {
                mLog[mTail] = msg;
                mTimestamp[mTail] = t;
                if (mSize == mMaxLines) {
                    mHead = (mHead + 1) % mMaxLines;
                    mTail = mHead;
                } else {
                    mSize++;
                    mTail = (mTail + 1) % mMaxLines;
                }
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLogCopy) {
            final int size, head, tail;
            synchronized (this) {
                size = mSize;
                head = mHead;
                tail = mTail;
                System.arraycopy(mLog, 0, mLogCopy, 0, mMaxLines);
                System.arraycopy(mTimestamp, 0, mTimestampCopy, 0, mMaxLines);
            }

            for (int i = 0; i < size; i++) {
                final int pos = (head + i) % mMaxLines;
                mBuilder.setLength(0);
                mCalendar.setTimeInMillis(mTimestamp[pos]);
                mBuilder.append(mCalendar.get(Calendar.HOUR))
                        .append(":")
                        .append(mCalendar.get(Calendar.MINUTE))
                        .append(":")
                        .append(mCalendar.get(Calendar.SECOND))
                        .append(":")
                        .append(mCalendar.get(Calendar.MILLISECOND))
                        .append(" - ")
                        .append(mLogCopy[pos]);
                pw.println(mBuilder.toString());
            }
        }
    }
}
