# fkTM —— 协议层不做混淆，避免字节级逻辑被优化改写（安全靠凭证加密，不靠混淆）
-keep class com.fktm.app.protocol.** { *; }
-keep class com.fktm.app.ble.** { *; }
-keep class com.fktm.app.credential.** { *; }
