package com.alibaba.one.sdk;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class j {

    /* renamed from: a, reason: collision with root package name */
    public static SensorManager f16a;
    public static Context b;

    /* JADX WARN: Removed duplicated region for block: B:5:0x0052 A[ORIG_RETURN, RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:7:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String a() {
        /*
            android.content.Context r0 = com.alibaba.one.sdk.j.b
            if (r0 == 0) goto L4f
            java.lang.String r1 = "sensor"
            java.lang.Object r0 = r0.getSystemService(r1)     // Catch: java.lang.Exception -> L4f
            android.hardware.SensorManager r0 = (android.hardware.SensorManager) r0     // Catch: java.lang.Exception -> L4f
            if (r0 == 0) goto L4f
            r1 = -1
            java.util.List r0 = r0.getSensorList(r1)     // Catch: java.lang.Exception -> L4f
            if (r0 == 0) goto L4f
            int r1 = r0.size()     // Catch: java.lang.Exception -> L4f
            if (r1 <= 0) goto L4f
            java.lang.StringBuilder r1 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L4f
            r1.<init>()     // Catch: java.lang.Exception -> L4f
            java.util.Iterator r0 = r0.iterator()     // Catch: java.lang.Exception -> L4f
        L24:
            boolean r2 = r0.hasNext()     // Catch: java.lang.Exception -> L4f
            if (r2 == 0) goto L46
            java.lang.Object r2 = r0.next()     // Catch: java.lang.Exception -> L4f
            android.hardware.Sensor r2 = (android.hardware.Sensor) r2     // Catch: java.lang.Exception -> L4f
            java.lang.String r3 = r2.getName()     // Catch: java.lang.Exception -> L4f
            r1.append(r3)     // Catch: java.lang.Exception -> L4f
            int r3 = r2.getVersion()     // Catch: java.lang.Exception -> L4f
            r1.append(r3)     // Catch: java.lang.Exception -> L4f
            java.lang.String r2 = r2.getVendor()     // Catch: java.lang.Exception -> L4f
            r1.append(r2)     // Catch: java.lang.Exception -> L4f
            goto L24
        L46:
            java.lang.String r0 = r1.toString()     // Catch: java.lang.Exception -> L4f
            java.lang.String r0 = com.alibaba.one.sdk.h.a(r0)     // Catch: java.lang.Exception -> L4f
            goto L50
        L4f:
            r0 = 0
        L50:
            if (r0 != 0) goto L54
            java.lang.String r0 = ""
        L54:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.sdk.j.a():java.lang.String");
    }

    public static String a(int i) {
        Sensor defaultSensor;
        try {
            SensorManager sensorManager = f16a;
            if (sensorManager == null || (defaultSensor = sensorManager.getDefaultSensor(i)) == null) {
                return null;
            }
            return defaultSensor.getName();
        } catch (Exception unused) {
            return null;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:5:0x00b8 A[ORIG_RETURN, RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:7:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String b() {
        /*
            android.content.Context r0 = com.alibaba.one.sdk.j.b
            if (r0 == 0) goto Lb5
            java.lang.String r1 = "sensor"
            java.lang.Object r0 = r0.getSystemService(r1)     // Catch: java.lang.Exception -> Lb5
            android.hardware.SensorManager r0 = (android.hardware.SensorManager) r0     // Catch: java.lang.Exception -> Lb5
            if (r0 == 0) goto Lb5
            r1 = 7
            int[] r2 = new int[r1]     // Catch: java.lang.Exception -> Lb5
            r3 = 0
            r4 = 1
            r2[r3] = r4     // Catch: java.lang.Exception -> Lb5
            r5 = 13
            r2[r4] = r5     // Catch: java.lang.Exception -> Lb5
            r6 = 2
            r2[r6] = r6     // Catch: java.lang.Exception -> Lb5
            r7 = 3
            r8 = 4
            r2[r7] = r8     // Catch: java.lang.Exception -> Lb5
            r7 = 5
            r2[r8] = r7     // Catch: java.lang.Exception -> Lb5
            r9 = 8
            r2[r7] = r9     // Catch: java.lang.Exception -> Lb5
            r10 = 6
            r2[r10] = r10     // Catch: java.lang.Exception -> Lb5
            java.util.HashMap r11 = new java.util.HashMap     // Catch: java.lang.Exception -> Lb5
            r11.<init>()     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r4)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r12 = "SENSOR_TYPE_ACCELEROMETER"
            r11.put(r4, r12)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_AMBIENT_TEMPERATURE"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r6)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_MAGNETIC_FIELD"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r8)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_GYROSCOPE"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r7)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_LIGHT"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r9)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_PROXIMITY"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r4 = java.lang.Integer.valueOf(r10)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = "SENSOR_TYPE_PRESSURE"
            r11.put(r4, r5)     // Catch: java.lang.Exception -> Lb5
            org.json.JSONObject r4 = new org.json.JSONObject     // Catch: java.lang.Exception -> Lb5
            r4.<init>()     // Catch: java.lang.Exception -> Lb5
        L73:
            if (r3 >= r1) goto Lb0
            r5 = r2[r3]     // Catch: java.lang.Exception -> Lb5
            org.json.JSONObject r6 = new org.json.JSONObject     // Catch: java.lang.Exception -> Lb5
            r6.<init>()     // Catch: java.lang.Exception -> Lb5
            android.hardware.Sensor r7 = r0.getDefaultSensor(r5)     // Catch: java.lang.Exception -> Lb5
            if (r7 == 0) goto Lad
            java.lang.String r8 = "power"
            float r9 = r7.getPower()     // Catch: java.lang.Exception -> Lb5
            double r9 = (double) r9     // Catch: java.lang.Exception -> Lb5
            r6.put(r8, r9)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r8 = "resoltution"
            float r9 = r7.getResolution()     // Catch: java.lang.Exception -> Lb5
            double r9 = (double) r9     // Catch: java.lang.Exception -> Lb5
            r6.put(r8, r9)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r8 = "maximumRange"
            float r7 = r7.getMaximumRange()     // Catch: java.lang.Exception -> Lb5
            double r9 = (double) r7     // Catch: java.lang.Exception -> Lb5
            r6.put(r8, r9)     // Catch: java.lang.Exception -> Lb5
            java.lang.Integer r5 = java.lang.Integer.valueOf(r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.Object r5 = r11.get(r5)     // Catch: java.lang.Exception -> Lb5
            java.lang.String r5 = (java.lang.String) r5     // Catch: java.lang.Exception -> Lb5
            r4.put(r5, r6)     // Catch: java.lang.Exception -> Lb5
        Lad:
            int r3 = r3 + 1
            goto L73
        Lb0:
            java.lang.String r0 = r4.toString()     // Catch: java.lang.Exception -> Lb5
            goto Lb6
        Lb5:
            r0 = 0
        Lb6:
            if (r0 != 0) goto Lba
            java.lang.String r0 = ""
        Lba:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.sdk.j.b():java.lang.String");
    }

    public static String b(int i) {
        Sensor defaultSensor;
        try {
            SensorManager sensorManager = f16a;
            if (sensorManager == null || (defaultSensor = sensorManager.getDefaultSensor(i)) == null) {
                return null;
            }
            return defaultSensor.getVendor();
        } catch (Exception unused) {
            return null;
        }
    }

    public static boolean c(int i) {
        try {
            SensorManager sensorManager = f16a;
            if (sensorManager != null) {
                return sensorManager.getDefaultSensor(i) != null;
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }
}
