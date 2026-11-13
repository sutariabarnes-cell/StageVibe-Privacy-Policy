package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    GET("get"),
    LIST("list"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
) {
    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoArray = JSONArray()
    currentUser?.providerData?.forEach { profile ->
        providerInfoArray.put(
            JSONObject().apply {
                put("providerId", profile.providerId)
                put("displayName", profile.displayName ?: JSONObject.NULL)
                put("email", profile.email ?: JSONObject.NULL)
                put("photoUrl", profile.photoUrl?.toString() ?: JSONObject.NULL)
            }
        )
    }

    val authInfo = JSONObject().apply {
        put("userId", currentUser?.uid ?: JSONObject.NULL)
        put("email", currentUser?.email ?: JSONObject.NULL)
        put("emailVerified", currentUser?.isEmailVerified ?: JSONObject.NULL)
        put("isAnonymous", currentUser?.isAnonymous ?: JSONObject.NULL)
        put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
        put("providerInfo", providerInfoArray)
    }

    val appCheckSnapshot = com.example.security.AppCheckTelemetry.status.value
    val appCheckInfo = JSONObject().apply {
        put("configured", appCheckSnapshot.isConfigured)
        put("provider", appCheckSnapshot.providerName)
        put("tokenAutoRefresh", appCheckSnapshot.tokenAutoRefreshEnabled)
    }

    val errInfo = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path ?: JSONObject.NULL)
        put("authInfo", authInfo)
        put("appCheckInfo", appCheckInfo)
    }

    Log.e("FirestoreError", errInfo.toString(), exception)
}
