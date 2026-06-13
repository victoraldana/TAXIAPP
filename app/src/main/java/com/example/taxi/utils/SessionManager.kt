package com.example.taxi.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.taxi.model.AuthModels
import com.google.gson.Gson

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("taxi_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSession(user: AuthModels.UserData, tokens: AuthModels.TokenData) {
        val editor = prefs.edit()
        editor.putString("USER_DATA", gson.toJson(user))
        editor.putString("ACCESS_TOKEN", tokens.accessToken)
        editor.putString("REFRESH_TOKEN", tokens.refreshToken)
        editor.apply()
    }

    fun getUser(): AuthModels.UserData? {
        val userStr = prefs.getString("USER_DATA", null) ?: return null
        return try {
            gson.fromJson(userStr, AuthModels.UserData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString("ACCESS_TOKEN", null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
