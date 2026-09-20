package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

public class UtilsBiometric(
    private val context: Context,
    private var biometricManager: BiometricManager?,
    private val listener: BiometricListener
) {
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    /**
     * This method checks if the device can support biometric authentication APIs
     */
    public fun checkBiometricIsAvailable(): Boolean {
        val input: String
        var ret = false
        val manager = BiometricManager.from(this.context)
        biometricManager = manager
        when (
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                input = "App can authenticate using biometrics."
                Log.d("MY_APP_TAG", input)
                Toast.makeText(context, input, Toast.LENGTH_LONG).show()
                ret = true
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                input = "No biometric features available on this device."
                Log.e("MY_APP_TAG", input)
                Toast.makeText(context, input, Toast.LENGTH_LONG).show()
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                input = "App can authenticate using biometrics."
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.")
                Toast.makeText(context, input, Toast.LENGTH_LONG).show()
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                input = "The user hasn't associated any biometric credentials with their account.."
                Log.e("MY_APP_TAG", input)
                Toast.makeText(context, input, Toast.LENGTH_LONG).show()
            }
        }
        return ret
    }

    public fun showBiometricDialog(biometricTitle: String, biometricSubTitle: String?) {
        // Initialize everything needed for authentication
        setupBiometricPrompt(biometricTitle, biometricSubTitle)
        try {
            biometricPrompt.authenticate(promptInfo)
            return
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception(e)
        }
    }

    /**
     * This method setups the biometric authentication dialog
     */
    private fun setupBiometricPrompt(biometricTitle: String, biometricSubTitle: String?) {
        val executor = ContextCompat.getMainExecutor(context)
        biometricPrompt =
            BiometricPrompt(
                // A context that is not a FragmentActivity is a ClassCastException, as in the Java version
                context as FragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                        listener.onFailed()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        listener.onSuccess(result)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                        listener.onFailed()
                    }
                }
            )

        // Create prompt dialog
        promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(biometricTitle)
                .setSubtitle(biometricSubTitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
    }
}
