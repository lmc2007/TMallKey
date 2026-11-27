package com.fktm.app.credential

import com.fktm.app.protocol.VocProtocol
import org.json.JSONArray
import org.json.JSONObject

internal object LockCredentialJson {
    fun encode(locks: List<LockCredential>): String = toArray(locks).toString()

    fun toArray(locks: List<LockCredential>): JSONArray = JSONArray().apply {
        locks.forEach { lock ->
            put(JSONObject().apply {
                put("name", lock.name)
                put("address", lock.address)
                put("activationKey", lock.activationKey)
                put("serviceUuid", lock.serviceUuid)
                put("deviceName", lock.deviceName)
                put("type", lock.type)
            })
        }
    }

    fun decode(json: String): List<LockCredential> = decode(JSONArray(json))

    fun decode(array: JSONArray): List<LockCredential> =
        List(array.length()) { index ->
            val lock = array.getJSONObject(index)
            LockCredential(
                name = lock.optString("name", "宿舍门"),
                address = lock.getString("address"),
                activationKey = lock.optString("activationKey", ""),
                serviceUuid = lock.optString("serviceUuid", VocProtocol.SERVICE_UUID),
                deviceName = lock.optString("deviceName", VocProtocol.VOC_LOCK_TAG),
                type = lock.optString("type", LockCredential.TYPE_VOC),
            )
        }
}
