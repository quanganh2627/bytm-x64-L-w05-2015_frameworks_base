
package com.android.internal.app;

import android.os.Bundle;




/**
 * The interface is designed for Access request related events,
 * registerAccessReqCallback(IAccessReqCallback cb) in AppOpsService.
 */

interface IAccessReqCallback {
    /**
     * The callback will be triggered when monitored API is invoked.
     * 
     * @param pkgName
     * @Bundle package name
     * @return Returns an int to judge success or not
     */
    int onAccessReqCb(String pkgName, int op, int mode, int sel);
    
}

