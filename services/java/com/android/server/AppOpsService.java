/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.Xml;

import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IAppOpsCallback;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;
//jw begin

import com.android.internal.app.IAccessReqCallback;
import android.os.Bundle;
import android.os.Process;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManager;
import android.content.ComponentName;

public class AppOpsService extends IAppOpsService.Stub {
    static final String TAG = "AppOps";
    static final boolean DEBUG = false;

    // Write at most every 30 minutes.
    static final long WRITE_DELAY = DEBUG ? 1000 : 30*60*1000;

    Context mContext;
    final AtomicFile mFile;
    final Handler mHandler;
	
    IAccessReqCallback mAccReqCb;  //jw

    boolean mWriteScheduled;
    final Runnable mWriteRunner = new Runnable() {
        public void run() {
            synchronized (AppOpsService.this) {
                mWriteScheduled = false;
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override protected Void doInBackground(Void... params) {
                        writeState();
                        return null;
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            }
        }
    };

    final SparseArray<HashMap<String, Ops>> mUidOps
            = new SparseArray<HashMap<String, Ops>>();
			
	final SparseArray<ArrayList<LocalAccessStatus>> mUidMostRecentOpStatus
            = new SparseArray<ArrayList<LocalAccessStatus>>();
			
	private Object mUserAccessLock = new Object();

    public final static class Ops extends SparseArray<Op> {
        public final String packageName;
        public final int uid;

        public Ops(String _packageName, int _uid) {
            packageName = _packageName;
            uid = _uid;
        }
    }

    public final static class Op {
        public final int uid;
        public final String packageName;
        public final int op;
        public int mode;
        public int duration;
        public long time;
        public long rejectTime;
        public int nesting;

        public Op(int _uid, String _packageName, int _op) {
            uid = _uid;
            packageName = _packageName;
            op = _op;
            mode = AppOpsManager.opToDefaultMode(op);
        }
    }

    final SparseArray<ArrayList<Callback>> mOpModeWatchers
            = new SparseArray<ArrayList<Callback>>();
    final ArrayMap<String, ArrayList<Callback>> mPackageModeWatchers
            = new ArrayMap<String, ArrayList<Callback>>();
    final ArrayMap<IBinder, Callback> mModeWatchers
            = new ArrayMap<IBinder, Callback>();

    public final class Callback implements DeathRecipient {
        final IAppOpsCallback mCallback;

        public Callback(IAppOpsCallback callback) {
            mCallback = callback;
            try {
                mCallback.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
            }
        }

        public void unlinkToDeath() {
            mCallback.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            stopWatchingMode(mCallback);
        }
    }

    final ArrayMap<IBinder, ClientState> mClients = new ArrayMap<IBinder, ClientState>();

    public final class ClientState extends Binder implements DeathRecipient {
        final IBinder mAppToken;
        final int mPid;
        final ArrayList<Op> mStartedOps;

        public ClientState(IBinder appToken) {
            mAppToken = appToken;
            mPid = Binder.getCallingPid();
            if (appToken instanceof Binder) {
                // For local clients, there is no reason to track them.
                mStartedOps = null;
            } else {
                mStartedOps = new ArrayList<Op>();
                try {
                    mAppToken.linkToDeath(this, 0);
                } catch (RemoteException e) {
                }
            }
        }

        @Override
        public String toString() {
            return "ClientState{" +
                    "mAppToken=" + mAppToken +
                    ", " + (mStartedOps != null ? ("pid=" + mPid) : "local") +
                    '}';
        }

        @Override
        public void binderDied() {
            synchronized (AppOpsService.this) {
                for (int i=mStartedOps.size()-1; i>=0; i--) {
                    finishOperationLocked(mStartedOps.get(i));
                }
                mClients.remove(mAppToken);
            }
        }
    }

    public AppOpsService(File storagePath) {
        mAccReqCb = null;  //jw
        mFile = new AtomicFile(storagePath);
        mHandler = new Handler();
        readState();
    }

    public void publish(Context context) {
        mContext = context;
        ServiceManager.addService(Context.APP_OPS_SERVICE, asBinder());
    }

    public void systemReady() {
        synchronized (this) {
            boolean changed = false;
            for (int i=0; i<mUidOps.size(); i++) {
                HashMap<String, Ops> pkgs = mUidOps.valueAt(i);
                Iterator<Ops> it = pkgs.values().iterator();
                while (it.hasNext()) {
                    Ops ops = it.next();
                    int curUid;
                    try {
                        curUid = mContext.getPackageManager().getPackageUid(ops.packageName,
                                UserHandle.getUserId(ops.uid));
                    } catch (NameNotFoundException e) {
                        curUid = -1;
                    }
                    if (curUid != ops.uid) {
                        Slog.i(TAG, "Pruning old package " + ops.packageName
                                + "/" + ops.uid + ": new uid=" + curUid);
                        it.remove();
                        changed = true;
                    }
                }
                if (pkgs.size() <= 0) {
                    mUidOps.removeAt(i);
                }
            }
            if (changed) {
                scheduleWriteLocked();
            }
        }
    }

    public void packageRemoved(int uid, String packageName) {
        synchronized (this) {
            HashMap<String, Ops> pkgs = mUidOps.get(uid);
            if (pkgs != null) {
                if (pkgs.remove(packageName) != null) {
                    if (pkgs.size() <= 0) {
                        mUidOps.remove(uid);
                    }
                    scheduleWriteLocked();
                }
            }
        }
    }

    public void uidRemoved(int uid) {
        synchronized (this) {
            if (mUidOps.indexOfKey(uid) >= 0) {
                mUidOps.remove(uid);
                scheduleWriteLocked();
            }
        }
    }

    public void shutdown() {
        Slog.w(TAG, "Writing app ops before shutdown...");
        boolean doWrite = false;
        synchronized (this) {
            if (mWriteScheduled) {
                mWriteScheduled = false;
                doWrite = true;
            }
        }
        if (doWrite) {
            writeState();
        }
    }

    private ArrayList<AppOpsManager.OpEntry> collectOps(Ops pkgOps, int[] ops) {
        ArrayList<AppOpsManager.OpEntry> resOps = null;
        if (ops == null) {
            resOps = new ArrayList<AppOpsManager.OpEntry>();
            for (int j=0; j<pkgOps.size(); j++) {
                Op curOp = pkgOps.valueAt(j);
                resOps.add(new AppOpsManager.OpEntry(curOp.op, curOp.mode, curOp.time,
                        curOp.rejectTime, curOp.duration));
            }
        } else {
            for (int j=0; j<ops.length; j++) {
                Op curOp = pkgOps.get(ops[j]);
                if (curOp != null) {
                    if (resOps == null) {
                        resOps = new ArrayList<AppOpsManager.OpEntry>();
                    }
                    resOps.add(new AppOpsManager.OpEntry(curOp.op, curOp.mode, curOp.time,
                            curOp.rejectTime, curOp.duration));
                }
            }
        }
        return resOps;
    }

    @Override
    public List<AppOpsManager.PackageOps> getPackagesForOps(int[] ops) {
        mContext.enforcePermission(android.Manifest.permission.GET_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        ArrayList<AppOpsManager.PackageOps> res = null;
        synchronized (this) {
            for (int i=0; i<mUidOps.size(); i++) {
                HashMap<String, Ops> packages = mUidOps.valueAt(i);
                for (Ops pkgOps : packages.values()) {
                    ArrayList<AppOpsManager.OpEntry> resOps = collectOps(pkgOps, ops);
                    if (resOps != null) {
                        if (res == null) {
                            res = new ArrayList<AppOpsManager.PackageOps>();
                        }
                        AppOpsManager.PackageOps resPackage = new AppOpsManager.PackageOps(
                                pkgOps.packageName, pkgOps.uid, resOps);
                        res.add(resPackage);
                    }
                }
            }
        }
        return res;
    }

    @Override
    public List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName,
            int[] ops) {
        mContext.enforcePermission(android.Manifest.permission.GET_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        synchronized (this) {
            Ops pkgOps = getOpsLocked(uid, packageName, false);
            if (pkgOps == null) {
                return null;
            }
            ArrayList<AppOpsManager.OpEntry> resOps = collectOps(pkgOps, ops);
            if (resOps == null) {
                return null;
            }
            ArrayList<AppOpsManager.PackageOps> res = new ArrayList<AppOpsManager.PackageOps>();
            AppOpsManager.PackageOps resPackage = new AppOpsManager.PackageOps(
                    pkgOps.packageName, pkgOps.uid, resOps);
            res.add(resPackage);
            return res;
        }
    }

    private void pruneOp(Op op, int uid, String packageName) {
        if (op.time == 0 && op.rejectTime == 0) {
            Ops ops = getOpsLocked(uid, packageName, false);
            if (ops != null) {
                ops.remove(op.op);
                if (ops.size() <= 0) {
                    HashMap<String, Ops> pkgOps = mUidOps.get(uid);
                    if (pkgOps != null) {
                        pkgOps.remove(ops.packageName);
                        if (pkgOps.size() <= 0) {
                            mUidOps.remove(uid);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setMode(int code, int uid, String packageName, int mode) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        ArrayList<Callback> repCbs = null;
        code = AppOpsManager.opToSwitch(code);
        synchronized (this) {
            Op op = getOpLocked(code, uid, packageName, true);
            if (op != null) {
                if (op.mode != mode) {
                    op.mode = mode;
                    ArrayList<Callback> cbs = mOpModeWatchers.get(code);
                    if (cbs != null) {
                        if (repCbs == null) {
                            repCbs = new ArrayList<Callback>();
                        }
                        repCbs.addAll(cbs);
                    }
                    cbs = mPackageModeWatchers.get(packageName);
                    if (cbs != null) {
                        if (repCbs == null) {
                            repCbs = new ArrayList<Callback>();
                        }
                        repCbs.addAll(cbs);
                    }
                    if (mode == AppOpsManager.opToDefaultMode(op.op)) {
                        // If going into the default mode, prune this op
                        // if there is nothing else interesting in it.
						if(checkSystemApp(packageName, uid) != 3)  //system app
                            pruneOp(op, uid, packageName);
                    }
                    scheduleWriteNowLocked();
                }
            }
        }
        if (repCbs != null) {
            for (int i=0; i<repCbs.size(); i++) {
                try {
                    repCbs.get(i).mCallback.opChanged(code, packageName);
                } catch (RemoteException e) {
                }
            }
        }
    }

    private static HashMap<Callback, ArrayList<Pair<String, Integer>>> addCallbacks(
            HashMap<Callback, ArrayList<Pair<String, Integer>>> callbacks,
            String packageName, int op, ArrayList<Callback> cbs) {
        if (cbs == null) {
            return callbacks;
        }
        if (callbacks == null) {
            callbacks = new HashMap<Callback, ArrayList<Pair<String, Integer>>>();
        }
        for (int i=0; i<cbs.size(); i++) {
            Callback cb = cbs.get(i);
            ArrayList<Pair<String, Integer>> reports = callbacks.get(cb);
            if (reports == null) {
                reports = new ArrayList<Pair<String, Integer>>();
                callbacks.put(cb, reports);
            }
            reports.add(new Pair<String, Integer>(packageName, op));
        }
        return callbacks;
    }

    @Override
    public void resetAllModes() {
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        HashMap<Callback, ArrayList<Pair<String, Integer>>> callbacks = null;
        synchronized (this) {
            boolean changed = false;
            for (int i=mUidOps.size()-1; i>=0; i--) {
                HashMap<String, Ops> packages = mUidOps.valueAt(i);
                Iterator<Map.Entry<String, Ops>> it = packages.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Ops> ent = it.next();
                    String packageName = ent.getKey();
                    Ops pkgOps = ent.getValue();
                    for (int j=pkgOps.size()-1; j>=0; j--) {
                        Op curOp = pkgOps.valueAt(j);
                        if (AppOpsManager.opAllowsReset(curOp.op)
                                && curOp.mode != AppOpsManager.opToDefaultMode(curOp.op)) {
                            curOp.mode = AppOpsManager.opToDefaultMode(curOp.op);
                            changed = true;
                            callbacks = addCallbacks(callbacks, packageName, curOp.op,
                                    mOpModeWatchers.get(curOp.op));
                            callbacks = addCallbacks(callbacks, packageName, curOp.op,
                                    mPackageModeWatchers.get(packageName));
                            if (curOp.time == 0 && curOp.rejectTime == 0) {
                                pkgOps.removeAt(j);
                            }
                        }
                    }
                    if (pkgOps.size() == 0) {
                        it.remove();
                    }
                }
                if (packages.size() == 0) {
                    mUidOps.removeAt(i);
                }
            }
            if (changed) {
                scheduleWriteNowLocked();
            }
        }
        if (callbacks != null) {
            for (Map.Entry<Callback, ArrayList<Pair<String, Integer>>> ent : callbacks.entrySet()) {
                Callback cb = ent.getKey();
                ArrayList<Pair<String, Integer>> reports = ent.getValue();
                for (int i=0; i<reports.size(); i++) {
                    Pair<String, Integer> rep = reports.get(i);
                    try {
                        cb.mCallback.opChanged(rep.second, rep.first);
                    } catch (RemoteException e) {
                    }
                }
            }
        }
    }

    @Override
    public void startWatchingMode(int op, String packageName, IAppOpsCallback callback) {
        synchronized (this) {
            op = AppOpsManager.opToSwitch(op);
            Callback cb = mModeWatchers.get(callback.asBinder());
            if (cb == null) {
                cb = new Callback(callback);
                mModeWatchers.put(callback.asBinder(), cb);
            }
            if (op != AppOpsManager.OP_NONE) {
                ArrayList<Callback> cbs = mOpModeWatchers.get(op);
                if (cbs == null) {
                    cbs = new ArrayList<Callback>();
                    mOpModeWatchers.put(op, cbs);
                }
                cbs.add(cb);
            }
            if (packageName != null) {
                ArrayList<Callback> cbs = mPackageModeWatchers.get(packageName);
                if (cbs == null) {
                    cbs = new ArrayList<Callback>();
                    mPackageModeWatchers.put(packageName, cbs);
                }
                cbs.add(cb);
            }
        }
    }

    @Override
    public void stopWatchingMode(IAppOpsCallback callback) {
        synchronized (this) {
            Callback cb = mModeWatchers.remove(callback.asBinder());
            if (cb != null) {
                cb.unlinkToDeath();
                for (int i=mOpModeWatchers.size()-1; i>=0; i--) {
                    ArrayList<Callback> cbs = mOpModeWatchers.valueAt(i);
                    cbs.remove(cb);
                    if (cbs.size() <= 0) {
                        mOpModeWatchers.removeAt(i);
                    }
                }
                for (int i=mPackageModeWatchers.size()-1; i>=0; i--) {
                    ArrayList<Callback> cbs = mPackageModeWatchers.valueAt(i);
                    cbs.remove(cb);
                    if (cbs.size() <= 0) {
                        mPackageModeWatchers.removeAt(i);
                    }
                }
            }
        }
    }

    @Override
    public IBinder getToken(IBinder clientToken) {
        synchronized (this) {
            ClientState cs = mClients.get(clientToken);
            if (cs == null) {
                cs = new ClientState(clientToken);
                mClients.put(clientToken, cs);
            }
            return cs;
        }
    }

    @Override
    public int checkOperation(int code, int uid, String packageName) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            Op op = getOpLocked(AppOpsManager.opToSwitch(code), uid, packageName, false);
            if (op == null) {
                return AppOpsManager.opToDefaultMode(code);
            }
            return op.mode;
        }
    }

    @Override
    public int checkPackage(int uid, String packageName) {
        synchronized (this) {
            if (getOpsLocked(uid, packageName, true) != null) {
                return AppOpsManager.MODE_ALLOWED;
            } else {
                return AppOpsManager.MODE_ERRORED;
            }
        }
    }

    @Override
    public int noteOperation(int code, int uid, String packageName) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            Ops ops = getOpsLocked(uid, packageName, true);
            if (ops == null) {
                if (DEBUG) Log.d(TAG, "noteOperation: no op for code " + code + " uid " + uid
                        + " package " + packageName);
                return AppOpsManager.MODE_ERRORED;
            }
            Op op = getOpLocked(ops, code, true);
            if (op.duration == -1) {
                Slog.w(TAG, "Noting op not finished: uid " + uid + " pkg " + packageName
                        + " code " + code + " time=" + op.time + " duration=" + op.duration);
            }
            op.duration = 0;
            final int switchCode = AppOpsManager.opToSwitch(code);
            final Op switchOp = switchCode != code ? getOpLocked(ops, switchCode, true) : op;
            if (switchOp.mode != AppOpsManager.MODE_ALLOWED && switchOp.mode != AppOpsManager.MODE_CHECK) {
                if (DEBUG) Log.d(TAG, "noteOperation: reject #" + op.mode + " for code "
                        + switchCode + " (" + code + ") uid " + uid + " package " + packageName);
                op.rejectTime = System.currentTimeMillis();
                return switchOp.mode;
            }
            if (DEBUG) Log.d(TAG, "noteOperation: allowing code " + code + " uid " + uid
                    + " package " + packageName);
            op.time = System.currentTimeMillis();
            op.rejectTime = 0;
            return AppOpsManager.MODE_ALLOWED;
        }
    }

    @Override
    public int startOperation(IBinder token, int code, int uid, String packageName) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        ClientState client = (ClientState)token;
        synchronized (this) {
            Ops ops = getOpsLocked(uid, packageName, true);
            if (ops == null) {
                if (DEBUG) Log.d(TAG, "startOperation: no op for code " + code + " uid " + uid
                        + " package " + packageName);
                return AppOpsManager.MODE_ERRORED;
            }
            Op op = getOpLocked(ops, code, true);
            final int switchCode = AppOpsManager.opToSwitch(code);
            final Op switchOp = switchCode != code ? getOpLocked(ops, switchCode, true) : op;
            if (switchOp.mode != AppOpsManager.MODE_ALLOWED) {
                if (DEBUG) Log.d(TAG, "startOperation: reject #" + op.mode + " for code "
                        + switchCode + " (" + code + ") uid " + uid + " package " + packageName);
                op.rejectTime = System.currentTimeMillis();
                return switchOp.mode;
            }
            if (DEBUG) Log.d(TAG, "startOperation: allowing code " + code + " uid " + uid
                    + " package " + packageName);
            if (op.nesting == 0) {
                op.time = System.currentTimeMillis();
                op.rejectTime = 0;
                op.duration = -1;
            }
            op.nesting++;
            if (client.mStartedOps != null) {
                client.mStartedOps.add(op);
            }
            return AppOpsManager.MODE_ALLOWED;
        }
    }

    @Override
    public void finishOperation(IBinder token, int code, int uid, String packageName) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        ClientState client = (ClientState)token;
        synchronized (this) {
            Op op = getOpLocked(code, uid, packageName, true);
            if (op == null) {
                return;
            }
            if (client.mStartedOps != null) {
                if (!client.mStartedOps.remove(op)) {
                    throw new IllegalStateException("Operation not started: uid" + op.uid
                            + " pkg=" + op.packageName + " op=" + op.op);
                }
            }
            finishOperationLocked(op);
        }
    }

    void finishOperationLocked(Op op) {
        if (op.nesting <= 1) {
            if (op.nesting == 1) {
                op.duration = (int)(System.currentTimeMillis() - op.time);
                op.time += op.duration;
            } else {
                Slog.w(TAG, "Finishing op nesting under-run: uid " + op.uid + " pkg "
                        + op.packageName + " code " + op.op + " time=" + op.time
                        + " duration=" + op.duration + " nesting=" + op.nesting);
            }
            op.nesting = 0;
        } else {
            op.nesting--;
        }
    }

    private void verifyIncomingUid(int uid) {
        if (uid == Binder.getCallingUid()) {
            return;
        }
        if (Binder.getCallingPid() == Process.myPid()) {
            return;
        }
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
    }

    private void verifyIncomingOp(int op) {
        if (op >= 0 && op < AppOpsManager._NUM_OP) {
            return;
        }
        throw new IllegalArgumentException("Bad operation #" + op);
    }

    private Ops getOpsLocked(int uid, String packageName, boolean edit) {
        HashMap<String, Ops> pkgOps = mUidOps.get(uid);
        if (pkgOps == null) {
            if (!edit) {
                return null;
            }
            pkgOps = new HashMap<String, Ops>();
            mUidOps.put(uid, pkgOps);
        }
        if (uid == 0) {
            packageName = "root";
        } else if (uid == Process.SHELL_UID) {
            packageName = "com.android.shell";
        }
        Ops ops = pkgOps.get(packageName);
        if (ops == null) {
            if (!edit) {
                return null;
            }
            // This is the first time we have seen this package name under this uid,
            // so let's make sure it is valid.
            if (uid != 0) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    int pkgUid = -1;
                    try {
                        pkgUid = mContext.getPackageManager().getPackageUid(packageName,
                                UserHandle.getUserId(uid));
                    } catch (NameNotFoundException e) {
                        if ("media".equals(packageName)) {
                            pkgUid = Process.MEDIA_UID;
                        }
                    }
                    if (pkgUid != uid) {
                        // Oops!  The package name is not valid for the uid they are calling
                        // under.  Abort.
                        Slog.w(TAG, "Bad call: specified package " + packageName
                                + " under uid " + uid + " but it is really " + pkgUid);
                        return null;
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            ops = new Ops(packageName, uid);
            pkgOps.put(packageName, ops);
        }
        return ops;
    }

    private void scheduleWriteLocked() {
        if (!mWriteScheduled) {
            mWriteScheduled = true;
            mHandler.postDelayed(mWriteRunner, WRITE_DELAY);
        }
    }

    private void scheduleWriteNowLocked() {
        if (!mWriteScheduled) {
            mWriteScheduled = true;
        }
        mHandler.removeCallbacks(mWriteRunner);
        mHandler.post(mWriteRunner);
    }

    private Op getOpLocked(int code, int uid, String packageName, boolean edit) {
        Ops ops = getOpsLocked(uid, packageName, edit);
        if (ops == null) {
            return null;
        }
        return getOpLocked(ops, code, edit);
    }

    private Op getOpLocked(Ops ops, int code, boolean edit) {
        Op op = ops.get(code);
        if (op == null) {
            if (!edit) {
                return null;
            }
            op = new Op(ops.uid, ops.packageName, code);
			if(checkSystemApp(ops.packageName, ops.uid) == 3) { //user app
				Slog.i(TAG, "set pkg " + ops.packageName + " op : " + code +" to MODE_CHECK");
				op.mode = AppOpsManager.MODE_CHECK;
			}			
            ops.put(code, op);
        }
        if (edit) {
            scheduleWriteLocked();
        }
        return op;
    }

    void readState() {
        synchronized (mFile) {
            synchronized (this) {
                FileInputStream stream;
                try {
                    stream = mFile.openRead();
                } catch (FileNotFoundException e) {
                    Slog.i(TAG, "No existing app ops " + mFile.getBaseFile() + "; starting empty");
                    return;
                }
                boolean success = false;
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, null);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG
                            && type != XmlPullParser.END_DOCUMENT) {
                        ;
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new IllegalStateException("no start tag found");
                    }

                    int outerDepth = parser.getDepth();
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }

                        String tagName = parser.getName();
                        if (tagName.equals("pkg")) {
                            readPackage(parser);
                        } else {
                            Slog.w(TAG, "Unknown element under <app-ops>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    success = true;
                } catch (IllegalStateException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NullPointerException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NumberFormatException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (XmlPullParserException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IndexOutOfBoundsException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } finally {
                    if (!success) {
                        mUidOps.clear();
                    }
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    void readPackage(XmlPullParser parser) throws NumberFormatException,
            XmlPullParserException, IOException {
        String pkgName = parser.getAttributeValue(null, "n");
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("uid")) {
                readUid(parser, pkgName);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void readUid(XmlPullParser parser, String pkgName) throws NumberFormatException,
            XmlPullParserException, IOException {
        int uid = Integer.parseInt(parser.getAttributeValue(null, "n"));
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("op")) {
                Op op = new Op(uid, pkgName, Integer.parseInt(parser.getAttributeValue(null, "n")));
                String mode = parser.getAttributeValue(null, "m");
                if (mode != null) {
                    op.mode = Integer.parseInt(mode);
                }
                String time = parser.getAttributeValue(null, "t");
                if (time != null) {
                    op.time = Long.parseLong(time);
                }
                time = parser.getAttributeValue(null, "r");
                if (time != null) {
                    op.rejectTime = Long.parseLong(time);
                }
                String dur = parser.getAttributeValue(null, "d");
                if (dur != null) {
                    op.duration = Integer.parseInt(dur);
                }
                HashMap<String, Ops> pkgOps = mUidOps.get(uid);
                if (pkgOps == null) {
                    pkgOps = new HashMap<String, Ops>();
                    mUidOps.put(uid, pkgOps);
                }
                Ops ops = pkgOps.get(pkgName);
                if (ops == null) {
                    ops = new Ops(pkgName, uid);
                    pkgOps.put(pkgName, ops);
                }
                ops.put(op.op, op);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void writeState() {
        synchronized (mFile) {
            List<AppOpsManager.PackageOps> allOps = getPackagesForOps(null);

            FileOutputStream stream;
            try {
                stream = mFile.startWrite();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state: " + e);
                return;
            }

            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, "utf-8");
                out.startDocument(null, true);
                out.startTag(null, "app-ops");

                if (allOps != null) {
                    String lastPkg = null;
                    for (int i=0; i<allOps.size(); i++) {
                        AppOpsManager.PackageOps pkg = allOps.get(i);
                        if (!pkg.getPackageName().equals(lastPkg)) {
                            if (lastPkg != null) {
                                out.endTag(null, "pkg");
                            }
                            lastPkg = pkg.getPackageName();
                            out.startTag(null, "pkg");
                            out.attribute(null, "n", lastPkg);
                        }
                        out.startTag(null, "uid");
                        out.attribute(null, "n", Integer.toString(pkg.getUid()));
                        List<AppOpsManager.OpEntry> ops = pkg.getOps();
                        for (int j=0; j<ops.size(); j++) {
                            AppOpsManager.OpEntry op = ops.get(j);
                            out.startTag(null, "op");
                            out.attribute(null, "n", Integer.toString(op.getOp()));
                            if (op.getMode() != AppOpsManager.opToDefaultMode(op.getOp())) {
                                out.attribute(null, "m", Integer.toString(op.getMode()));
                            }
                            long time = op.getTime();
                            if (time != 0) {
                                out.attribute(null, "t", Long.toString(time));
                            }
                            time = op.getRejectTime();
                            if (time != 0) {
                                out.attribute(null, "r", Long.toString(time));
                            }
                            int dur = op.getDuration();
                            if (dur != 0) {
                                out.attribute(null, "d", Integer.toString(dur));
                            }
                            out.endTag(null, "op");
                        }
                        out.endTag(null, "uid");
                    }
                    if (lastPkg != null) {
                        out.endTag(null, "pkg");
                    }
                }

                out.endTag(null, "app-ops");
                out.endDocument();
                mFile.finishWrite(stream);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state, restoring backup.", e);
                mFile.failWrite(stream);
            }
        }
    }
	
	@Override
	public void registerAccessReqCallback(IAccessReqCallback accReqCb) {
		Slog.d(TAG, "registerPermissionListener PID : " + Process.myPid());  
		if(accReqCb == null) {
			Slog.w(TAG, "in accReqCb is null");	
			return;
		}
		//Slog.d(TAG, "register callback in service");
		mAccReqCb = accReqCb;
	}
	
	@Override
	public void unRegisterAccessReqCallback() {
		Slog.d(TAG, "unRegisterPermissionListener PID : " + Process.myPid());  
		if(mAccReqCb != null) {
			Slog.w(TAG, "mAaccReqCb set to null");
			mAccReqCb = null;	
			return;
		}
		Slog.d(TAG, "callback is null");
		
	}
	
	//private LocalAccessStatus syncObject = new LocalAccessStatus();
	
	@Override
	public int checkOperationWithData(int op, int uid, Bundle data) {  //jw
		int mode = AppOpsManager.MODE_ALLOWED;
        
        AppOpsManager.OpEntry entry = null; 
		// Get calling app package name via UID from Binder call
        PackageManager pm = mContext.getPackageManager();
        String[] pkgNames = pm.getPackagesForUid(uid);

		if(mAccReqCb == null) {
			Slog.w(TAG, "no callback exisit");
			return PackageManager.PERMISSION_GRANTED;
		}
			
        if (pkgNames == null || pkgNames.length == 0) {
            // Refuse to send SMS if we can't get the calling package name.
            Slog.e(TAG, "Can't get calling app package name: refusing it");
            return PackageManager.PERMISSION_DENIED;
        }
		
		String pkgName = pkgNames[0];

        if(2 == checkSystemApp(pkgName, uid)) {  //2 system app
            Slog.d(TAG, "jw filter out the system app : " + pkgName);
			if(pkgName.equals("com.android.camera2")) {
			
				String cpkgName = getRecentTask(mContext);
				
				if(!cpkgName.equals("com.android.camera2")) {
					try {            
						//PackageManager pm = getPackageManager();       
						ApplicationInfo ai = pm.getApplicationInfo(cpkgName, PackageManager.GET_ACTIVITIES);            
						Slog.d(TAG, "jw !!" + ai.uid + " start camera by IMG_CAPTURE");  
						uid = ai.uid;
						pkgNames = pm.getPackagesForUid(uid);
						pkgName = pkgNames[0];
					} catch (NameNotFoundException e) {            
						e.printStackTrace();        
					}

				} else {
					return PackageManager.PERMISSION_GRANTED;
				}

			} else {
				return PackageManager.PERMISSION_GRANTED;
			}
            
        }
			
		synchronized (mUserAccessLock) {
			Op opItem = null;
			HashMap<String, Ops> pkgOps = null;
			boolean exist_in_mUidOps = false;
			//Slog.i(TAG, "---------------------start  TID : " + Process.myTid() + "-----------------");
			synchronized(this) {
				pkgOps = mUidOps.get(uid);				
			}
			if (pkgOps == null) {
					Slog.d(TAG, "pkgName : " + pkgName + " op : " + op + " is not recorded");
			} else {
				Ops ops = pkgOps.get(pkgName);
				if (ops == null) {
					Slog.w(TAG, "pkgName : " + pkgName + " ops is null");
				} else {					
					opItem = ops.get(AppOpsManager.opToSwitch(op));
				}
			}
	
			if(opItem != null) {
				//Slog.d(TAG, "jw pkgName : " + pkgName + " op : " + op + " mode : " + opItem.mode);
	            exist_in_mUidOps = true;
				if(AppOpsManager.MODE_CHECK == opItem.mode) {
					//Slog.d(TAG, "jw found pkg : " + pkgName + " op : " + op + " is need to check");
				} else if(AppOpsManager.MODE_IGNORED == opItem.mode || AppOpsManager.MODE_ERRORED == opItem.mode) {
					//Slog.d(TAG, "jw found pkg : " + pkgName + " op : " + op + " is not allowed");
					return PackageManager.PERMISSION_DENIED;
				} else
					return PackageManager.PERMISSION_GRANTED;
			} else {
				//Slog.d(TAG, "jw not found the op : " + op + " in the pkg : " + pkgName);
				exist_in_mUidOps = false;
			}
	
			Slog.i(TAG, "package : " + pkgName + " with op : " + op + " is need to check");
			
			int secureLevel = 0x40;
			boolean exist = false;
			ArrayList<LocalAccessStatus> las_p = mUidMostRecentOpStatus.get(uid);
			if(las_p == null) {
				Slog.i(TAG, "uid : " + uid + " request op : " + op + " need to check");
				las_p = new ArrayList<LocalAccessStatus>();
				las_p.add(new LocalAccessStatus(uid, op, AppOpsManager.MODE_CHECK));
				mUidMostRecentOpStatus.put(uid, las_p);
			} else {
				int curMode = AppOpsManager.MODE_CHECK;
				int oldMode = AppOpsManager.MODE_CHECK;
				Iterator<LocalAccessStatus> it = las_p.iterator();				
				while(it.hasNext()) {
					LocalAccessStatus las = it.next();
					if(las.getOp() == op) {
						exist = true;
						
						if(!exist_in_mUidOps)  //need more test....
							exist_in_mUidOps = true;
					
						curMode = las.getCurMode();
						oldMode = las.getOldMode();
						secureLevel = las.getLevel();
						
						Slog.d(TAG, "jw pkgName " + pkgName + " op : " + op + " curMode : " +curMode + " oldMode : " + oldMode + " level : " + secureLevel);

						switch(secureLevel) {
						
						case 0x80: //high
						case 0x40: //mid
							if(curMode == AppOpsManager.MODE_CHECK) {
								int span = 15;
								if(secureLevel == 0x80) {
									span = 15;  //15 minutes for high level
								} else if(secureLevel == 0x40){
									span = 60; //60 minutes for mid level
								} 
									
								if(las.getSpan(System.currentTimeMillis()) > span) {
									las.setStartTime(System.currentTimeMillis()); //set new start time
								} else {
									if(oldMode != AppOpsManager.MODE_CHECK) {
										if(oldMode == AppOpsManager.MODE_ALLOWED) {
											return PackageManager.PERMISSION_GRANTED;
										} else {
											return PackageManager.PERMISSION_DENIED;
										}
									}
									
								}
							} 
							break;
						case 0x20: //low
							if(oldMode == AppOpsManager.MODE_ALLOWED) {
								return PackageManager.PERMISSION_GRANTED;
							} else {
								return PackageManager.PERMISSION_DENIED;
							}
							
						}	
						
						if(curMode != AppOpsManager.MODE_CHECK) {
							if(curMode == AppOpsManager.MODE_ALLOWED) {
								return PackageManager.PERMISSION_GRANTED;
							} else {
								return PackageManager.PERMISSION_DENIED;
							}
						}
					}
				}
				
				if(!exist) {
					las_p.add(new LocalAccessStatus(uid, op, AppOpsManager.MODE_CHECK));
				}
			}

			try {
				mode = mAccReqCb.onAccessReqCb(pkgName, op, AppOpsManager.MODE_CHECK, secureLevel);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			Slog.d(TAG, "onAccessReqCb pkgName : " + pkgName + " op : " + op + " return mode : " + mode);
			
			ArrayList<LocalAccessStatus> las_q = mUidMostRecentOpStatus.get(uid);
			Iterator<LocalAccessStatus> ir = las_q.iterator();
			int persist = 0x10 & mode;
			while(ir.hasNext()) {
					LocalAccessStatus las = ir.next();
					if(las.getOp() == op) {
						//Slog.i(TAG, "set pkgName : " + pkgName + " op : " + op + " to mode : " + mode);
						int level = 0xE0 & mode;
						
						las.setLevel(level);
						
						switch(level) {
						
						case 0x80: //high
						case 0x40:
							if(las.getCurMode() == AppOpsManager.MODE_CHECK) {
								if(persist > 0) {
									las.setCurMode(mode & 0x03);
									las.setOldMode(mode & 0x03);
								} else {
									las.setOldMode(mode & 0x03);
								}
							} 
							break;
						case 0x20: //low
							if(las.getCurMode() == AppOpsManager.MODE_CHECK) {
								las.setCurMode(mode & 0x03);
								las.setOldMode(mode & 0x03);
							} else {
								las.setOldMode(mode & 0x03);
							}
							break;
						}	

						break;
					}
			}
			
			
			Slog.i(TAG, "persist = " + persist + " exist_in_mUidOps = " + exist_in_mUidOps);
			if(persist > 0) {  //need to remember for long
				if(!exist_in_mUidOps)	
					exist_in_mUidOps = true;
				setMode(op, uid, pkgName, (mode & 0x03)); //record the user selection in file
			} 
			
			if(!exist_in_mUidOps) {
				setMode(op, uid, pkgName, AppOpsManager.MODE_CHECK); //record the user selection in file
			} 
				
			mode = mode & 0x03;
					
			if(mode == AppOpsManager.MODE_ALLOWED) {
				return PackageManager.PERMISSION_GRANTED;
			} else {
				return PackageManager.PERMISSION_DENIED;
			}
		}
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ApOps service from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        synchronized (this) {
            pw.println("Current AppOps Service state:");
            final long now = System.currentTimeMillis();
            boolean needSep = false;
            if (mOpModeWatchers.size() > 0) {
                needSep = true;
                pw.println("  Op mode watchers:");
                for (int i=0; i<mOpModeWatchers.size(); i++) {
                    pw.print("    Op "); pw.print(AppOpsManager.opToName(mOpModeWatchers.keyAt(i)));
                    pw.println(":");
                    ArrayList<Callback> callbacks = mOpModeWatchers.valueAt(i);
                    for (int j=0; j<callbacks.size(); j++) {
                        pw.print("      #"); pw.print(j); pw.print(": ");
                        pw.println(callbacks.get(j));
                    }
                }
            }
            if (mPackageModeWatchers.size() > 0) {
                needSep = true;
                pw.println("  Package mode watchers:");
                for (int i=0; i<mPackageModeWatchers.size(); i++) {
                    pw.print("    Pkg "); pw.print(mPackageModeWatchers.keyAt(i));
                    pw.println(":");
                    ArrayList<Callback> callbacks = mPackageModeWatchers.valueAt(i);
                    for (int j=0; j<callbacks.size(); j++) {
                        pw.print("      #"); pw.print(j); pw.print(": ");
                        pw.println(callbacks.get(j));
                    }
                }
            }
            if (mModeWatchers.size() > 0) {
                needSep = true;
                pw.println("  All mode watchers:");
                for (int i=0; i<mModeWatchers.size(); i++) {
                    pw.print("    "); pw.print(mModeWatchers.keyAt(i));
                    pw.print(" -> "); pw.println(mModeWatchers.valueAt(i));
                }
            }
            if (mClients.size() > 0) {
                needSep = true;
                pw.println("  Clients:");
                for (int i=0; i<mClients.size(); i++) {
                    pw.print("    "); pw.print(mClients.keyAt(i)); pw.println(":");
                    ClientState cs = mClients.valueAt(i);
                    pw.print("      "); pw.println(cs);
                    if (cs.mStartedOps != null && cs.mStartedOps.size() > 0) {
                        pw.println("      Started ops:");
                        for (int j=0; j<cs.mStartedOps.size(); j++) {
                            Op op = cs.mStartedOps.get(j);
                            pw.print("        "); pw.print("uid="); pw.print(op.uid);
                            pw.print(" pkg="); pw.print(op.packageName);
                            pw.print(" op="); pw.println(AppOpsManager.opToName(op.op));
                        }
                    }
                }
            }
            if (needSep) {
                pw.println();
            }
            for (int i=0; i<mUidOps.size(); i++) {
                pw.print("  Uid "); UserHandle.formatUid(pw, mUidOps.keyAt(i)); pw.println(":");
                HashMap<String, Ops> pkgOps = mUidOps.valueAt(i);
                for (Ops ops : pkgOps.values()) {
                    pw.print("    Package "); pw.print(ops.packageName); pw.println(":");
                    for (int j=0; j<ops.size(); j++) {
                        Op op = ops.valueAt(j);
                        pw.print("      "); pw.print(AppOpsManager.opToName(op.op));
                        pw.print(": mode="); pw.print(op.mode);
                        if (op.time != 0) {
                            pw.print("; time="); TimeUtils.formatDuration(now-op.time, pw);
                            pw.print(" ago");
                        }
                        if (op.rejectTime != 0) {
                            pw.print("; rejectTime="); TimeUtils.formatDuration(now-op.rejectTime, pw);
                            pw.print(" ago");
                        }
                        if (op.duration == -1) {
                            pw.println(" (running)");
                        } else {
                            pw.print("; duration=");
                                    TimeUtils.formatDuration(op.duration, pw);
                                    pw.println();
                        }
                    }
                }
            }
        }
    }
	
	
	private String getRecentTask(Context context) {
		ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);	
		final List<ActivityManager.RecentTaskInfo> recentTasks =  
			am.getRecentTasks(Integer.MAX_VALUE, ActivityManager.RECENT_IGNORE_UNAVAILABLE);  
		
		Slog.i(TAG, "jw " + recentTasks.get(0).baseIntent.toString());
		
		
		String pkgName = recentTasks.get(0).baseIntent.toString(); //(recentTasks.get(0).origActivity).getPackageName();
		
		String[] tmpStr = pkgName.split("cmp=");
		
		String[] realPkgName = tmpStr[1].split("/");
			
		return realPkgName[0];
		
	}
	

    private boolean isSystemApp(PackageInfo pInfo) {
        return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
        
    private boolean isSystemUpdateApp(PackageInfo pInfo) {
        return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
    }
            
    private  boolean isUserApp(PackageInfo pInfo) {
        return (!isSystemApp(pInfo) && !isSystemUpdateApp(pInfo));
    }

    public int checkSystemApp(String pkgName, int uid) {
        try {

                if ("media".equals(pkgName) || (uid == 0) || (Process.SHELL_UID == uid))
                    return 2;

                PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(pkgName, 0);
                                    
                if(isSystemApp(pInfo) || isSystemUpdateApp(pInfo)) {
                             return 2; //SYSTEM_APP;
                } else {
                             return 3; //USER_APP;
                }
         } catch (NameNotFoundException e) {
                e.printStackTrace();
         }
         return 0; //UNKNOWN_APP;

    }
	
	class LocalAccessStatus {
		int uid_;
		int op_;
		int mode_;
		int old_mode_;
		int level_; //0x80 high, 0x40 mid, 0x20 low
		long start_time_;
		int span_;		
		
		public LocalAccessStatus() {
		}
		
		public LocalAccessStatus(int uid, int op, int mode) {
			uid_ = uid;
			op_ = op;
			mode_ = mode;
			old_mode_ = AppOpsManager.MODE_CHECK;
			start_time_ = System.currentTimeMillis();;
			level_ = 0x40;  //default level
			span_ = 0;
		}
		
		public int getOp() {
			return op_;
		}
		
		public int getCurMode() {
			return mode_;
		}
		
		public void setOp(int op) {
			op_ = op;
		}
		
		public void setCurMode(int mode) {
			mode_ = mode;
		}
		
		public void setOldMode(int mode) {
			old_mode_ = mode;
		}
		
		public int getOldMode() {
			return old_mode_;
		}
		
		public int getSpan(long time) {
			span_ = (int)(time - start_time_)/(1000*60);	
			return span_;
		}
			
		public int getSpan() {
			return span_;
		}
		
		public int getLevel(int uid, int op) {
			return level_;
		}
		
		public void setLevel(int newLevel) {
			level_ = newLevel;
		}
		
		public long getStartTime() {
			return start_time_;
		}
		
		public void setStartTime(long time) {
			start_time_ = time;
		}
		
		public int getLevel() {
			return level_;
		}
		
		public void setUid(int uid){
			uid_ = uid;
		}
		
		public int getUid() {
			return uid_;
		}
	}
	
	

}
