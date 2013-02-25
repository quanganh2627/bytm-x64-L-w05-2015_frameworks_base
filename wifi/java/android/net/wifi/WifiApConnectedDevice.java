/*
 * Copyright (C) 2008 The Android Open Source Project
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

/* Intel - Wifi_Hotspot */
package android.net.wifi;

import android.os.Parcelable;
import android.os.Parcel;

/**
 * A class storing mac address, device name and ip address from hostpad daemon
 * for the connected devices (stations).
 * @hide
 */
public class WifiApConnectedDevice implements Parcelable {

     private int time_stamp;
     private String mac_addr;
     private String device_name;
     private String ip_addr;

     /** @hide */
     public WifiApConnectedDevice() {
         time_stamp = 0;
         mac_addr = null;
         ip_addr = null;
         device_name = null;
     }

     /** Implement the Parcelable interface **/
     public int describeContents() {
         return 0;
     }

     /** @hide */
     public WifiApConnectedDevice(int time, String mac, String ip, String device) {
         time_stamp = time;
         mac_addr = mac;
         ip_addr = ip;
         device_name = device;
     }

     /** @hide */
     public int getTimeStamp() {
         return time_stamp;
     }

     /** @hide */
     public String getMacAddr() {
         return mac_addr;
     }

     /** @hide */
     public String getDeviceName() {
         return device_name;
     }

     /** @hide */
     public String getIpAddr() {
         return ip_addr;
     }

     /** Implement the Parcelable interface */
     public void writeToParcel(Parcel dest, int flags) {
         dest.writeInt(time_stamp);
         dest.writeString(mac_addr);
         dest.writeString(ip_addr);
         dest.writeString(device_name);
     }

     /** Implement the Parcelable interface */
     public static final Creator<WifiApConnectedDevice> CREATOR =
         new Creator<WifiApConnectedDevice>() {
             public WifiApConnectedDevice createFromParcel(Parcel in) {
                 WifiApConnectedDevice config = new WifiApConnectedDevice();
                 config.time_stamp = in.readInt();
                 config.mac_addr = in.readString();
                 config.ip_addr = in.readString();
                 config.device_name = in.readString();
                 return config;
             }

             public WifiApConnectedDevice[] newArray(int size) {
                 return new WifiApConnectedDevice[size];
             }
     };
}
