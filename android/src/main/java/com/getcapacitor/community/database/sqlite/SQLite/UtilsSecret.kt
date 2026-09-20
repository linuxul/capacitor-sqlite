package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import android.content.SharedPreferences
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLCipher.State.DOES_NOT_EXIST
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLCipher.State.ENCRYPTED_GLOBAL_SECRET
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLCipher.State.ENCRYPTED_SECRET
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLCipher.State.UNKNOWN

public class UtilsSecret(private val context: Context, sharedPreferences: SharedPreferences?) {
    private val uFile = UtilsFile()
    private val globVar = GlobalSQLite()
    private val uCipher = UtilsSQLCipher()

    init {
        // The preferences are shared by every instance and by the static accessors, as they were in Java
        UtilsSecret.sharedPreferences = sharedPreferences
    }

    /**
     * SetEncryptionSecret
     * @param passphrase
     * @throws Exception
     */
    public fun setEncryptionSecret(passphrase: String?) {
        try {
            if (passphrase.isNullOrEmpty()) {
                val msg = "passphrase must not be empty"
                throw Exception(msg)
            }
            // test if Encryption secret is already set
            val savedPassPhrase = getPassphrase()
            if (savedPassPhrase.isNotEmpty()) {
                throw Exception("a passphrase has already been set ")
            }
            // Store encrypted passphrase in sharedPreferences
            setPassphrase(passphrase)

            // Get the list of databases
            val dbList = uFile.getListOfFiles(context)
            if (dbList.isNotEmpty()) {
                for (dbName in dbList) {
                    val file = context.getDatabasePath(dbName)

                    val state = uCipher.getDatabaseState(context, file, sharedPreferences, globVar)
                    // change password if encrypted with globVar.secret
                    if (state == ENCRYPTED_GLOBAL_SECRET) {
                        uCipher.changePassword(context, file, globVar.secret, passphrase)
                    } else if (state == DOES_NOT_EXIST || state == UNKNOWN) {
                        val msg = "State for: $dbName not correct"
                        throw Exception(msg)
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * ChangeEncryptionSecret
     * @param passphrase
     * @param oldPassphrase
     * @throws Exception
     */
    public fun changeEncryptionSecret(passphrase: String?, oldPassphrase: String?) {
        try {
            if (passphrase.isNullOrEmpty() || oldPassphrase.isNullOrEmpty()) {
                val msg = "Passphrase and/or oldpassphrase must not be empty"
                throw Exception(msg)
            }
            // check the oldPassphrase
            val secret = getPassphrase()
            if (secret.isEmpty()) {
                val msg = "Encryption secret has not been set"
                throw Exception(msg)
            } else if (secret != oldPassphrase) {
                val msg = "Oldpassphrase is wrong secret"
                throw Exception(msg)
            } else {
                // Get the list of databases
                val dbList = uFile.getListOfFiles(context)
                if (dbList.isNotEmpty()) {
                    for (dbName in dbList) {
                        val file = context.getDatabasePath(dbName)

                        val state = uCipher.getDatabaseState(context, file, sharedPreferences, globVar)
                        // change password if encrypted with oldPassphrase
                        if (state == ENCRYPTED_SECRET) {
                            uCipher.changePassword(context, file, oldPassphrase, passphrase)
                        } else if (state == DOES_NOT_EXIST || state == ENCRYPTED_GLOBAL_SECRET || state == UNKNOWN) {
                            val msg = "State for: $dbName not correct"
                            throw Exception(msg)
                        }
                    }
                }
                // Store the new encrypted passphrase in sharedPreferences
                setPassphrase(passphrase)
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * ClearEncryptionSecret
     * @throws Exception
     */
    public fun clearEncryptionSecret() {
        try {
            // test if Encryption secret is already set
            val savedPassPhrase = getPassphrase()
            if (savedPassPhrase.isNotEmpty()) {
                // Clear encrypted passphrase in sharedPreferences
                clearPassphrase()
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * CheckEncryptionSecret
     * @param passphrase
     * @throws Exception
     */
    public fun checkEncryptionSecret(passphrase: String?): Boolean {
        try {
            if (passphrase.isNullOrEmpty()) {
                val msg = "passphrase must not be empty"
                throw Exception(msg)
            }
            // test if Encryption secret is already set
            val savedPassPhrase = getPassphrase()
            if (savedPassPhrase.isEmpty()) {
                throw Exception("no passphrase stored  in sharedPreferences")
            }

            return savedPassPhrase == passphrase
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    public fun setPassphrase(passphrase: String?) {
        // `!!`: as in Java, a NullPointerException when the plugin runs without encryption
        sharedPreferences!!.edit().putString("secret", passphrase).apply()
    }

    public fun clearPassphrase() {
        sharedPreferences!!.edit().remove("secret").commit()
    }

    public companion object {
        private var sharedPreferences: SharedPreferences? = null

        public fun getPassphrase(): String {
            // `!!`: as in Java, a NullPointerException when no UtilsSecret was created with preferences
            return sharedPreferences!!.getString("secret", "") ?: ""
        }

        public fun isPassphrase(): Boolean = getPassphrase().isNotEmpty()
    }
}
