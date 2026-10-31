package com.alibaba.one.sdk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.util.List;
import org.json.JSONObject;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class a {
    public static String a(Throwable th) {
        if (th == null) {
            return "";
        }
        try {
            return th.getClass().getName() + ": " + th.getMessage();
        } catch (Exception unused) {
            return "";
        }
    }

    public static byte[] a(Context context) {
        long j;
        PackageInfo packageInfo;
        long j2 = 0;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (Exception unused) {
        }
        if (packageInfo != null) {
            j = packageInfo.firstInstallTime;
            try {
                j2 = packageInfo.lastUpdateTime;
            } catch (Exception unused2) {
            }
            return (System.currentTimeMillis() + "," + j + "," + j2).getBytes();
        }
        j = 0;
        return (System.currentTimeMillis() + "," + j + "," + j2).getBytes();
    }

    public static String b(Context context) {
        JSONObject jSONObject = new JSONObject();
        if (context == null) {
            return jSONObject.toString();
        }
        if (Build.VERSION.SDK_INT >= 23 && context != null && context.checkSelfPermission("android.permission.ACCESS_WIFI_STATE") != 0) {
            return jSONObject.toString();
        }
        List<WifiConfiguration> configuredNetworks = ((WifiManager) context.getSystemService("wifi")).getConfiguredNetworks();
        if (configuredNetworks != null) {
            jSONObject.put("count", configuredNetworks.size());
            for (int i = 0; i < configuredNetworks.size(); i++) {
                WifiConfiguration wifiConfiguration = configuredNetworks.get(i);
                if (wifiConfiguration != null) {
                    JSONObject jSONObject2 = new JSONObject();
                    jSONObject2.put("ssid", wifiConfiguration.SSID);
                    jSONObject2.put("bssid", wifiConfiguration.BSSID);
                    if (Build.VERSION.SDK_INT >= 23) {
                        jSONObject2.put("provider_friendly_name", wifiConfiguration.providerFriendlyName);
                    }
                    if (Build.VERSION.SDK_INT >= 21) {
                        jSONObject2.put("fqdn", wifiConfiguration.FQDN);
                    }
                    jSONObject2.put("network_id", wifiConfiguration.networkId);
                    jSONObject2.put("priority", wifiConfiguration.priority);
                    jSONObject2.put("hidden_ssid", wifiConfiguration.hiddenSSID);
                    jSONObject.put(String.valueOf(i), jSONObject2);
                }
            }
        }
        return jSONObject.toString();
    }
}
