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

package android.os;

import android.util.LocalLog;
import android.util.Log;
import android.util.Printer;
import android.util.PrefixPrinter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
  * Class used to run a message loop for a thread.  Threads by default do
  * not have a message loop associated with them; to create one, call
  * {@link #prepare} in the thread that is to run the loop, and then
  * {@link #loop} to have it process messages until the loop is stopped.
  * 
  * <p>Most interaction with a message loop is through the
  * {@link Handler} class.
  * 
  * <p>This is a typical example of the implementation of a Looper thread,
  * using the separation of {@link #prepare} and {@link #loop} to create an
  * initial Handler to communicate with the Looper.
  *
  * <pre>
  *  class LooperThread extends Thread {
  *      public Handler mHandler;
  *
  *      public void run() {
  *          Looper.prepare();
  *
  *          mHandler = new Handler() {
  *              public void handleMessage(Message msg) {
  *                  // process incoming messages here
  *              }
  *          };
  *
  *          Looper.loop();
  *      }
  *  }</pre>
  */
public class Looper {
    private static final String TAG = "Looper";

    // sThreadLocal.get() will return null unless you've called prepare().
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    private static Looper sMainLooper;  // guarded by Looper.class

    final MessageQueue mQueue;
    final Thread mThread;
    volatile boolean mRun;

    private Printer mLogging;
    LocalLog mLocalLog;
    private String mDispatching;
    private long mDispatchStart;
    private StringBuilder mStringBuilder;
    private static final long LATENCY_THRESHOLD = 500; // 500ms

     /** Initialize the current thread as a looper.
      * This gives you a chance to create handlers that then reference
      * this looper, before actually starting the loop. Be sure to call
      * {@link #loop()} after calling this method, and end it by calling
      * {@link #quit()}.
      */
    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    /**
     * Initialize the current thread as a looper, marking it as an
     * application's main looper. The main looper for your application
     * is created by the Android environment, so you should never need
     * to call this function yourself.  See also: {@link #prepare()}
     */
    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }

    /** Returns the application's main looper, which lives in the main thread of the application.
     */
    public static Looper getMainLooper() {
        synchronized (Looper.class) {
            return sMainLooper;
        }
    }

    /**
     * Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        final MessageQueue queue = me.mQueue;

        // Make sure the identity of this thread is that of the local process,
        // and keep track of what that identity token actually is.
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();
        LocalLog localLog = me.mLocalLog;
        if (localLog != null) me.mStringBuilder = new StringBuilder();

        for (;;) {
            Message msg = queue.next(); // might block
            if (msg == null) {
                // No message indicates that the message queue is quitting.
                return;
            }

            // This must be in a local variable, in case a UI event sets the logger
            Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }
            if (localLog != null) {
                me.mDispatching = msg.toStringLw();
                me.mDispatchStart = SystemClock.uptimeMillis();
            }

            msg.target.dispatchMessage(msg);

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }
            if (localLog != null) {
                final long elapsed = SystemClock.uptimeMillis() - me.mDispatchStart;
                final long wait = me.mDispatchStart - msg.when;
                me.mStringBuilder.setLength(0);
                if (elapsed >= LATENCY_THRESHOLD) {
                    me.mStringBuilder.append("WARNING! ");
                }
                me.mStringBuilder.append("Wait: ")
                                 .append(wait)
                                 .append("ms, Run: ")
                                 .append(elapsed)
                                 .append("ms due Message")
                                 .append(me.mDispatching);
                localLog.log(me.mStringBuilder.toString());
                me.mDispatching = null;
            }

            // Make sure that during the course of dispatching the
            // identity of the thread wasn't corrupted.
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + " what=" + msg.what);
            }
            msg.recycle();
        }
    }

    /**
     * Return the Looper object associated with the current thread.  Returns
     * null if the calling thread is not associated with a Looper.
     */
    public static Looper myLooper() {
        return sThreadLocal.get();
    }

    /**
     * Control logging of messages as they are processed by this Looper.  If
     * enabled, a log message will be written to <var>printer</var> 
     * at the beginning and ending of each message dispatch, identifying the
     * target Handler and message contents.
     * 
     * @param printer A Printer object that will receive log messages, or
     * null to disable message logging.
     */
    public void setMessageLogging(Printer printer) {
        mLogging = printer;
    }

    /** @hide */
    public void setMessageLogging(LocalLog localLog) {
        mLocalLog = localLog;
    }
    
    /**
     * Return the {@link MessageQueue} object associated with the current
     * thread.  This must be called from a thread running a Looper, or a
     * NullPointerException will be thrown.
     */
    public static MessageQueue myQueue() {
        return myLooper().mQueue;
    }

    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mRun = true;
        mThread = Thread.currentThread();
    }

    /**
     * Quits the looper.
     *
     * Causes the {@link #loop} method to terminate as soon as possible.
     */
    public void quit() {
        mQueue.quit();
    }

    /**
     * Posts a synchronization barrier to the Looper's message queue.
     *
     * Message processing occurs as usual until the message queue encounters the
     * synchronization barrier that has been posted.  When the barrier is encountered,
     * later synchronous messages in the queue are stalled (prevented from being executed)
     * until the barrier is released by calling {@link #removeSyncBarrier} and specifying
     * the token that identifies the synchronization barrier.
     *
     * This method is used to immediately postpone execution of all subsequently posted
     * synchronous messages until a condition is met that releases the barrier.
     * Asynchronous messages (see {@link Message#isAsynchronous} are exempt from the barrier
     * and continue to be processed as usual.
     *
     * This call must be always matched by a call to {@link #removeSyncBarrier} with
     * the same token to ensure that the message queue resumes normal operation.
     * Otherwise the application will probably hang!
     *
     * @return A token that uniquely identifies the barrier.  This token must be
     * passed to {@link #removeSyncBarrier} to release the barrier.
     *
     * @hide
     */
    public final int postSyncBarrier() {
        return mQueue.enqueueSyncBarrier(SystemClock.uptimeMillis());
    }


    /**
     * Removes a synchronization barrier.
     *
     * @param token The synchronization barrier token that was returned by
     * {@link #postSyncBarrier}.
     *
     * @throws IllegalStateException if the barrier was not found.
     *
     * @hide
     */
    public final void removeSyncBarrier(int token) {
        mQueue.removeSyncBarrier(token);
    }

    /**
     * Return the Thread associated with this Looper.
     */
    public Thread getThread() {
        return mThread;
    }

    /** @hide */
    public MessageQueue getQueue() {
        return mQueue;
    }

    public void dump(Printer pw, String prefix) {
        pw = PrefixPrinter.create(pw, prefix);
        pw.println(this.toString());
        pw.println("mRun=" + mRun);
        pw.println("mThread=" + mThread);
        pw.println("mQueue=" + ((mQueue != null) ? mQueue : "(null"));
        if (mLocalLog != null) {
            final long duration = SystemClock.uptimeMillis() - mDispatchStart;
            pw.println("mDispatching=" + mDispatching + ", " + duration + "ms ago.");
        }
        if (mQueue != null) {
            synchronized (mQueue) {
                long now = SystemClock.uptimeMillis();
                Message msg = mQueue.mMessages;
                int n = 0;
                while (msg != null) {
                    pw.println("  Message " + n + ": " + msg.toString(now));
                    if (msg.target == null) pw.println("  WARNING! Message " + n
                            + " is a sync barrier!!");
                    n++;
                    msg = msg.next;
                }
                pw.println("(Total messages: " + n + ")");
            }
        }
    }

    /** @hide */
    public void dumpHistory(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mLocalLog != null) {
            mLocalLog.dump(fd, pw, args);
        }
    }

    public String toString() {
        return "Looper{" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}
