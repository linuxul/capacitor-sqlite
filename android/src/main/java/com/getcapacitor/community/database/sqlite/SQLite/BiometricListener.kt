package com.getcapacitor.community.database.sqlite.SQLite

import androidx.biometric.BiometricPrompt

public interface BiometricListener {
    public fun onSuccess(result: BiometricPrompt.AuthenticationResult)

    public fun onFailed()
}
