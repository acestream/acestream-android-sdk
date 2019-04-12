/*
 * Util
 * Connect SDK
 *
 * Copyright (c) 2014 LG Electronics.
 * Created by Jeffrey Glenn on 27 Feb 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.acestream.sdk.utils;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import androidx.annotation.NonNull;

public class NetworkUtils {
    private final static String TAG = "AS/NetUtils";

    public static InetAddress getIpAddress(Context context) throws UnknownHostException {
        InetAddress addr = getWifiAddress(context);
        if(addr == null) {
            // If Wifi is disconnected then get IPv4 address of first available network interface.
            addr = getInterfaceAddress();
        }

        return addr;
    }

    public static InetAddress getWifiAddress(@NonNull Context context) throws UnknownHostException {
        WifiManager wifiMgr = (WifiManager) context.
                getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);

        if(wifiMgr == null) return null;

        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        if (ip == 0) {
            return null;
        }
        else {
            byte[] ipAddress = convertIpAddress(ip);
            return InetAddress.getByAddress(ipAddress);
        }
    }

    public static InetAddress getInterfaceAddress() {
        InetAddress addr = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String ip = inetAddress.getHostAddress();
                    boolean isIPv4 = ip.indexOf(':') < 0;

                    if (isIPv4 && !inetAddress.isLoopbackAddress()) {
                        addr = inetAddress;
                        break;
                    }
                }
            }
        }
        catch (SocketException e) {
            Log.v(TAG, "getInterfaceAddress: error: " + e.getMessage());
        }

        return addr;
    }

    public static byte[] convertIpAddress(int ip) {
        return new byte[] {
                (byte) (ip & 0xFF),
                (byte) ((ip >> 8) & 0xFF),
                (byte) ((ip >> 16) & 0xFF),
                (byte) ((ip >> 24) & 0xFF)};
    }
}
