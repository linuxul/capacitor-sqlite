package com.getcapacitor.community.database.sqlite

import android.os.Process
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.community.database.sqlite.SQLite.SqliteConfig
import java.util.Collections
import java.util.Dictionary
import java.util.Hashtable
import org.json.JSONObject

// Option values are handed to the implementation with `!!` inside each try block: a value that is present
// but not of the expected type made the Java implementation throw a NullPointerException there, which is
// rejected with the method's prefix just like any other failure.
@CapacitorPlugin(name = "CapacitorSQLite")
public class CapacitorSQLitePlugin : Plugin() {
    // Named sqliteConfig because Plugin.config (the plugin's capacitor.config section) is a final property
    private var sqliteConfig: SqliteConfig? = null
    private var implementation: CapacitorSQLite? = null
    private val versionUpgrades: Dictionary<String, Dictionary<Int, JSONObject>> = Hashtable()
    private val rHandler = RetHandler()
    private var passphrase: String? = null
    private var oldpassphrase: String? = null
    private var loadMessage = ""
    private val modeList = arrayListOf("no-encryption", "encryption", "secret", "decryption", "wrongsecret")

    /**
     * Load Method
     * Load the context
     */
    override fun load() {
        try {
            val config = getSqliteConfig()
            sqliteConfig = config
            addObserversToNotificationCenter()
            implementation = CapacitorSQLite(context, config)
        } catch (e: Exception) {
            implementation = null
            loadMessage = "CapacitorSQLitePlugin: ${e.message}"
            Log.e(TAG, loadMessage)
        }
    }

    /**
     * Echo Method
     * test the plugin
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun echo(call: PluginCall) {
        val value = call.getString("value")
        val impl = implementation
        if (impl != null) {
            try {
                val ret = JSObject()
                ret.put("value", impl.echo(value))
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject(e.message)
            }
        } else {
            call.reject(loadMessage)
        }
    }

    /**
     * IsSecretStored
     * Check if a secret has been stored
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isSecretStored(call: PluginCall) {
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isSecretStored()
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "IsSecretStored: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * SetEncryptionSecret
     * set a passphrase secret for a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun setEncryptionSecret(call: PluginCall) {
        if (!call.data.has("passphrase")) {
            val msg = "SetEncryptionSecret: Must provide a passphrase"
            rHandler.retResult(call, null, msg)
            return
        }
        val passphrase = call.getString("passphrase")
        val impl = implementation
        if (impl != null) {
            try {
                impl.setEncryptionSecret(passphrase!!)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "SetEncryptionSecret: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * ChangeEncryptionSecret
     * change a passphrase secret for a database
     * with a new passphrase
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun changeEncryptionSecret(call: PluginCall) {
        if (!call.data.has("passphrase")) {
            val msg = "SetEncryptionSecret: Must provide a passphrase"
            rHandler.retResult(call, null, msg)
            return
        }
        passphrase = call.getString("passphrase")

        if (!call.data.has("oldpassphrase")) {
            val msg = "SetEncryptionSecret: Must provide a oldpassphrase"
            rHandler.retResult(call, null, msg)
            return
        }
        oldpassphrase = call.getString("oldpassphrase")
        val impl = implementation
        if (impl != null) {
            activity.runOnUiThread {
                try {
                    impl.changeEncryptionSecret(call, passphrase!!, oldpassphrase!!)
                    rHandler.retResult(call, null, null)
                } catch (e: Exception) {
                    val msg = "ChangeEncryptionSecret: ${e.message}"
                    rHandler.retResult(call, null, msg)
                }
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * ClearEncryptionSecret
     * clear the passphrase secret for a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun clearEncryptionSecret(call: PluginCall) {
        val impl = implementation
        if (impl != null) {
            try {
                impl.clearEncryptionSecret()
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "ClearEncryptionSecret: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * checkEncryptionSecret
     * check a passphrase secret against the stored passphrase
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun checkEncryptionSecret(call: PluginCall) {
        if (!call.data.has("passphrase")) {
            val msg = "checkEncryptionSecret: Must provide a passphrase"
            rHandler.retResult(call, null, msg)
            return
        }
        val passphrase = call.getString("passphrase")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.checkEncryptionSecret(passphrase!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "CheckEncryptionSecret: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    @PluginMethod
    public fun getNCDatabasePath(call: PluginCall) {
        if (!call.data.has("path")) {
            val msg = "getNCDatabasePath: Must provide a folder path"
            rHandler.retPath(call, null, msg)
            return
        }
        val folderPath = call.getString("path")
        if (!call.data.has("database")) {
            val msg = "getNCDatabasePath: Must provide a database name"
            rHandler.retPath(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val impl = implementation
        if (impl != null) {
            try {
                val databasePath = impl.getNCDatabasePath(folderPath!!, dbName!!)
                rHandler.retPath(call, databasePath, null)
            } catch (e: Exception) {
                val msg = "getNCDatabasePath: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CreateNCConnection Method
     * Create a non-conformed connection to a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun createNCConnection(call: PluginCall) {
        if (!call.data.has("databasePath")) {
            val msg = "CreateNCConnection: Must provide a database path"
            rHandler.retResult(call, null, msg)
            return
        }
        val impl = implementation
        if (impl != null) {
            try {
                val dbPath = call.getString("databasePath")
                val dbVersion = call.getInt("version", 1) ?: 1
                impl.createNCConnection(dbPath!!, dbVersion)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "CreateNCConnection: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CreateConnection Method
     * Create a connection to a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun createConnection(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "CreateConnection: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val dbVersion = call.getInt("version", 1) ?: 1

        val inMode: String?
        val encrypted = call.getBoolean("encrypted", false) ?: false
        if (encrypted) {
            inMode = call.getString("mode", "no-encryption")
            if (!modeList.contains(inMode)) {
                var msg = "CreateConnection: inMode must "
                msg += "be in ['encryption','secret', 'decryption'] "
                rHandler.retResult(call, null, msg)
                return
            }
        } else {
            inMode = "no-encryption"
        }
        val readOnly = call.getBoolean("readonly", false) ?: false
        val upgDict = dbName?.let { versionUpgrades.get(it) }
        val impl = implementation
        if (impl != null) {
            try {
                impl.createConnection(dbName!!, encrypted, inMode!!, dbVersion, upgDict, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "CreateConnection: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * Open Method
     * Open a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun open(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "Open: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.open(dbName!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "Open: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * Close Method
     * Close a Database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun close(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "Close: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.close(dbName!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "Close: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * BeginTransaction Method
     * Begin a Database Transaction
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun beginTransaction(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "BeginTransaction: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")

        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.beginTransaction(dbName!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "BeginTransaction: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * CommitTransaction Method
     * Commit a Database Transaction
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun commitTransaction(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "CommitTransaction: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")

        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.commitTransaction(dbName!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "CommitTransaction: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * RollbackTransaction Method
     * Rollbact a Database Transaction
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun rollbackTransaction(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "RollbackTransaction: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")

        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.rollbackTransaction(dbName!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "RollbackTransaction: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * IsTransactionActive Method
     * Check if a Database Transaction is Active
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isTransactionActive(call: PluginCall) {
        if (!call.data.has("database")) {
            rHandler.retResult(call, null, "Must provide a database name")
            return
        }
        val dbName = call.getString("database")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isTransactionActive(dbName!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "IsTransactionActive: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * GetUrl Method
     * Get a database Url
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun getUrl(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "GetUrl: Must provide a database name"
            rHandler.retUrl(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.getUrl(dbName!!, readOnly)
                rHandler.retUrl(call, res, null)
            } catch (e: Exception) {
                val msg = "GetUrl: ${e.message}"
                rHandler.retUrl(call, null, msg)
            }
        } else {
            rHandler.retUrl(call, null, loadMessage)
        }
    }

    /**
     * GetVersion Method
     * Get a database Version
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun getVersion(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "GetVersion: Must provide a database name"
            rHandler.retVersion(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.getVersion(dbName!!, readOnly)
                rHandler.retVersion(call, res, null)
            } catch (e: Exception) {
                val msg = "GetVersion: ${e.message}"
                rHandler.retVersion(call, null, msg)
            }
        } else {
            rHandler.retVersion(call, null, loadMessage)
        }
    }

    /**
     * CloseNCConnection Method
     * Close a non-conformed database connection
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun closeNCConnection(call: PluginCall) {
        if (!call.data.has("databasePath")) {
            val msg = "CloseNCConnection: Must provide a database path"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbPath = call.getString("databasePath")
        val impl = implementation
        if (impl != null) {
            try {
                impl.closeNCConnection(dbPath!!)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "CloseNCConnection: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CloseConnection Method
     * Close the connection to a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun closeConnection(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "CloseConnection: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.closeConnection(dbName!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "CloseConnection: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CheckConnectionsConsistency Method
     * Check the connections consistency JS <=> Native
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun checkConnectionsConsistency(call: PluginCall) {
        if (!call.data.has("dbNames")) {
            val msg = "CheckConnectionsConsistency: Must provide a " + "connection Array"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbNames = call.getArray("dbNames")
        if (!call.data.has("openModes")) {
            val msg = "CheckConnectionsConsistency: Must provide a " + "openModes Array"
            rHandler.retResult(call, null, msg)
            return
        }
        val openModes = call.getArray("openModes")
        if (dbNames == null || openModes == null) {
            val msg = "CheckConnectionsConsistency: No dbNames or openModes given"
            rHandler.retResult(call, null, msg)
            return
        }
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.checkConnectionsConsistency(dbNames, openModes)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "CheckConnectionsConsistency: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * IsDatabase Method
     * Check if the database file exists
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isDatabase(call: PluginCall) {
        if (!call.data.has("database")) {
            rHandler.retResult(call, null, "Must provide a database name")
            return
        }
        val dbName = call.getString("database")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isDatabase(dbName!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isDatabase: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * IsDatabaseEncrypted Method
     * Check if the database is encrypted
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isDatabaseEncrypted(call: PluginCall) {
        if (!call.data.has("database")) {
            rHandler.retResult(call, null, "Must provide a database name")
            return
        }
        val dbName = call.getString("database")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isDatabaseEncrypted(dbName!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isDatabaseEncrypted: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * isInConfigEncryption
     * Check if encryption is definrd in capacitor.config
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isInConfigEncryption(call: PluginCall) {
        // sqliteConfig is only null when load() failed; the Java original threw a NullPointerException here too
        val res = sqliteConfig!!.isEncryption
        rHandler.retResult(call, res, null)
    }

    /**
     * isInConfigBiometricAuth
     * Check if biometric auth is definrd in capacitor.config
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isInConfigBiometricAuth(call: PluginCall) {
        // sqliteConfig is only null when load() failed; the Java original threw a NullPointerException here too
        val res = sqliteConfig!!.biometricAuth
        rHandler.retResult(call, res, null)
    }

    /**
     * IsNCDatabase Method
     * Check if the database file exists
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isNCDatabase(call: PluginCall) {
        if (!call.data.has("databasePath")) {
            rHandler.retResult(call, null, "Must provide a database path")
            return
        }
        val dbPath = call.getString("databasePath")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isNCDatabase(dbPath!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isNCDatabase: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * IsTableExists Method
     * Check if a table exists in a database
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isTableExists(call: PluginCall) {
        if (!call.data.has("database")) {
            rHandler.retResult(call, null, "Must provide a database name")
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("table")) {
            rHandler.retResult(call, null, "Must provide a table name")
            return
        }
        val tableName = call.getString("table")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isTableExists(dbName!!, tableName!!, readOnly)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isTableExists: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * GetDatabaseList Method
     * Return the list of databases
     */
    @PluginMethod
    public fun getDatabaseList(call: PluginCall) {
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.getDatabaseList()
                rHandler.retValues(call, res, null)
            } catch (e: Exception) {
                val msg = "getDatabaseList: ${e.message}"
                rHandler.retValues(call, JSArray(), msg)
            }
        } else {
            rHandler.retValues(call, JSArray(), loadMessage)
        }
    }

    /**
     * GetMigratableDbList Method
     * Return the list of migratable databases
     */
    @PluginMethod
    public fun getMigratableDbList(call: PluginCall) {
        val folderPath =
            if (!call.data.has("folderPath")) {
                "default"
            } else {
                call.getString("folderPath")
            }
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.getMigratableDbList(folderPath!!)
                rHandler.retValues(call, res, null)
            } catch (e: Exception) {
                val msg = "getMigratableDbList: ${e.message}"
                rHandler.retValues(call, JSArray(), msg)
            }
        } else {
            rHandler.retValues(call, JSArray(), loadMessage)
        }
    }

    /**
     * AddSQLiteSuffix Method
     * Add SQLITE suffix to a list of databases
     */
    @PluginMethod
    public fun addSQLiteSuffix(call: PluginCall) {
        val folderPath =
            if (!call.data.has("folderPath")) {
                "default"
            } else {
                call.getString("folderPath")
            }
        val dbList =
            if (!call.data.has("dbNameList")) {
                null
            } else {
                call.getArray("dbNameList")
            }
        if (dbList == null) {
            val msg = "AddSQLiteSuffix: dbNameList not given or empty"
            rHandler.retResult(call, null, msg)
            return
        }
        val impl = implementation
        if (impl != null) {
            try {
                impl.addSQLiteSuffix(folderPath!!, dbList)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "addSQLiteSuffix: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * DeleteOldDatabases Method
     * Delete Old Cordova plugin databases
     */
    @PluginMethod
    public fun deleteOldDatabases(call: PluginCall) {
        val folderPath =
            if (!call.data.has("folderPath")) {
                "default"
            } else {
                call.getString("folderPath")
            }
        val dbList =
            if (!call.data.has("dbNameList")) {
                null
            } else {
                call.getArray("dbNameList")
            }
        if (dbList == null) {
            val msg = "deleteOldDatabases: dbNameList not given or empty"
            rHandler.retResult(call, null, msg)
            return
        }
        val impl = implementation
        if (impl != null) {
            try {
                impl.deleteOldDatabases(folderPath!!, dbList)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "deleteOldDatabases: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * DeleteOldDatabases Method
     * Delete Old Cordova plugin databases
     */
    @PluginMethod
    public fun moveDatabasesAndAddSuffix(call: PluginCall) {
        val folderPath =
            if (!call.data.has("folderPath")) {
                "default"
            } else {
                call.getString("folderPath")
            }
        val dbList =
            if (!call.data.has("dbNameList")) {
                null
            } else {
                call.getArray("dbNameList")
            }
        if (dbList == null) {
            val msg = "moveDatabasesAndAddSuffix: dbNameList not given or empty"
            rHandler.retResult(call, null, msg)
            return
        }
        val impl = implementation
        if (impl != null) {
            try {
                impl.moveDatabasesAndAddSuffix(folderPath!!, dbList)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "moveDatabasesAndAddSuffix: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * Execute Method
     * Execute SQL statements provided in a String
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun execute(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "Execute: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("statements")) {
            val msg = "Execute: Must provide raw SQL statements"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val statements = call.getString("statements")
        val transaction = call.getBoolean("transaction", true) ?: true
        val readOnly = call.getBoolean("readonly", false) ?: false

        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.execute(dbName!!, statements!!, transaction, readOnly)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "Execute: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * ExecuteSet Method
     * Execute a Set of raw sql statement
     *
     * @param call PluginCall
     * @throws Exception message
     */
    @PluginMethod
    public fun executeSet(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "ExecuteSet: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("set")) {
            val msg = "ExecuteSet: Must provide a set of SQL statements"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val set = call.getArray("set")
        if (set == null) {
            val msg = "ExecuteSet: Must provide a set of SQL statements"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        if (set.length() == 0) {
            val msg = "ExecuteSet: Must provide a non-empty set of SQL statements"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        for (i in 0 until set.length()) {
            // names() is null for an empty object; the Java original threw a NullPointerException here too
            val keys = set.getJSONObject(i).names()!!
            for (j in 0 until keys.length()) {
                val key = keys.getString(j)
                if (key != "statement" && key != "values") {
                    var msg = "ExecuteSet: Must provide a set as Array of {statement,"
                    msg += "values}"
                    rHandler.retChanges(call, retRes, msg)
                    return
                }
            }
        }
        val transaction = call.getBoolean("transaction", true) ?: true
        val readOnly = call.getBoolean("readonly", false) ?: false
        val returnMode = call.getString("returnMode", "no")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.executeSet(dbName!!, set, transaction, readOnly, returnMode!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "ExecuteSet: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * Run method
     * Execute a raw sql statement
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun run(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "Run: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("statement")) {
            val msg = "Run: Must provide a SQL statement"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val statement = call.getString("statement")
        if (!call.data.has("values")) {
            val msg = "Run: Must provide an Array of values"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val values = call.getArray("values")
        if (values == null) {
            val msg = "Run: Must provide an Array of values"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val transaction = call.getBoolean("transaction", true) ?: true
        val readOnly = call.getBoolean("readonly", false) ?: false
        val returnMode = call.getString("returnMode", "no")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.run(dbName!!, statement!!, values, transaction, readOnly, returnMode!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "Run: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * Query Method
     * Execute an sql query
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun query(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "Query: Must provide a database name"
            rHandler.retValues(call, JSArray(), msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("statement")) {
            val msg = "Query: Must provide a SQL statement"
            rHandler.retValues(call, JSArray(), msg)
            return
        }
        val statement = call.getString("statement")
        if (!call.data.has("values")) {
            val msg = "Query: Must provide an Array of Strings"
            rHandler.retValues(call, JSArray(), msg)
            return
        }
        val values = call.getArray("values")
        if (values == null) {
            val msg = "Query: Must provide an Array of values"
            rHandler.retValues(call, JSArray(), msg)
            return
        }
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.query(dbName!!, statement!!, values, readOnly)
                rHandler.retValues(call, res, null)
            } catch (e: Exception) {
                val msg = "Query: ${e.message}"
                rHandler.retValues(call, JSArray(), msg)
            }
        } else {
            rHandler.retValues(call, JSArray(), loadMessage)
        }
    }

    @PluginMethod
    public fun getTableList(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "getTableList: Must provide a database name"
            rHandler.retValues(call, JSArray(), msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.getTableList(dbName!!, readOnly)
                rHandler.retValues(call, res, null)
            } catch (e: Exception) {
                val msg = "GetTableList: ${e.message}"
                rHandler.retValues(call, JSArray(), msg)
            }
        } else {
            rHandler.retValues(call, JSArray(), loadMessage)
        }
    }

    /**
     * IsDBExists Method
     * check if the database exists on the database folder
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isDBExists(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "isDBExists: Must provide a database name"
            rHandler.retResult(call, false, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isDBExists(dbName!!, readOnly)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isDBExists: ${e.message}"
                rHandler.retResult(call, false, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * IsDBOpen Method
     * check if the database is opened
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isDBOpen(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "isDBOpen: Must provide a database name"
            rHandler.retResult(call, false, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isDBOpen(dbName!!, readOnly)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "isDBOpen: ${e.message}"
                rHandler.retResult(call, false, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * DeleteDatabase Method
     * delete a database from the database folder
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun deleteDatabase(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "deleteDatabase: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.deleteDatabase(dbName!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "deleteDatabase: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CreateSyncTable Method
     * Create the synchronization table
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun createSyncTable(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("database")) {
            val msg = "CreateSyncTable: Must provide a database name"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.createSyncTable(dbName!!, readOnly)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "CreateSyncTable: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * SetSyncDate Method
     * set the synchronization date
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun setSyncDate(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "SetSyncDate: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")

        if (!call.data.has("syncdate")) {
            val msg = "SetSyncDate : Must provide a sync date"
            rHandler.retResult(call, null, msg)
            return
        }
        val syncDate = call.getString("syncdate")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.setSyncDate(dbName!!, syncDate!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "SetSyncDate: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * GetSyncDate Method
     * Get the synchronization date
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun getSyncDate(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "GetSyncDate : Must provide a database name"
            rHandler.retSyncDate(call, 0L, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                val syncDate: Long = impl.getSyncDate(dbName!!, readOnly)
                rHandler.retSyncDate(call, syncDate, null)
            } catch (e: Exception) {
                val msg = "GetSyncDate: ${e.message}"
                rHandler.retSyncDate(call, 0L, msg)
            }
        } else {
            rHandler.retSyncDate(call, 0L, loadMessage)
        }
    }

    /**
     * AddUpgradeStatement Method
     * Define an upgrade object when updating to a new version
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun addUpgradeStatement(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "AddUpgradeStatement: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("upgrade")) {
            val msg = "AddUpgradeStatement: Must provide an array with upgrade statement"
            rHandler.retResult(call, null, msg)
            return
        }
        val upgrade = call.getArray("upgrade")
        if (upgrade == null) {
            val msg = "AddUpgradeStatement: Must provide an array with upgrade statement"
            rHandler.retResult(call, null, msg)
            return
        }

        val impl = implementation
        if (impl != null) {
            try {
                val upgDict = impl.addUpgradeStatement(upgrade)

                // A null name made the Hashtable lookup of the Java original throw, which ends in the catch below
                val name = dbName ?: throw NullPointerException()
                val existing = versionUpgrades.get(name)
                if (existing != null) {
                    val keys = Collections.list(upgDict.keys())
                    for (versionKey in keys) {
                        val upgObj = upgDict.get(versionKey)

                        existing.put(versionKey, upgObj)
                    }
                } else {
                    versionUpgrades.put(name, upgDict)
                }

                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "AddUpgradeStatement: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * IsJsonValid
     * Check the validity of a given Json object
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun isJsonValid(call: PluginCall) {
        if (!call.data.has("jsonstring")) {
            val msg = "IsJsonValid: Must provide a Stringify Json Object"
            rHandler.retResult(call, false, msg)
            return
        }
        val parsingData = call.getString("jsonstring")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.isJsonValid(parsingData!!)
                rHandler.retResult(call, res, null)
            } catch (e: Exception) {
                val msg = "IsJsonValid: ${e.message}"
                rHandler.retResult(call, false, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * ImportFromJson Method
     * Import from a given Json object
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun importFromJson(call: PluginCall) {
        val retRes = JSObject()
        retRes.put("changes", -1)
        if (!call.data.has("jsonstring")) {
            val msg = "ImportFromJson: Must provide a Stringify Json Object"
            rHandler.retChanges(call, retRes, msg)
            return
        }
        val parsingData = call.getString("jsonstring")
        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.importFromJson(parsingData!!)
                rHandler.retChanges(call, res, null)
            } catch (e: Exception) {
                val msg = "ImportFromJson: ${e.message}"
                rHandler.retChanges(call, retRes, msg)
            }
        } else {
            rHandler.retChanges(call, retRes, loadMessage)
        }
    }

    /**
     * ExportToJson Method
     * Export the database to Json Object
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun exportToJson(call: PluginCall) {
        val retObj = JSObject()
        if (!call.data.has("database")) {
            val msg = "ExportToJson: Must provide a database name"
            rHandler.retJSObject(call, retObj, msg)
            return
        }
        val dbName = call.getString("database")
        if (!call.data.has("jsonexportmode")) {
            val msg = "ExportToJson: Must provide an export mode"
            rHandler.retJSObject(call, retObj, msg)
            return
        }
        val expMode = call.getString("jsonexportmode")

        if (expMode != "full" && expMode != "partial") {
            val msg = "ExportToJson: Json export mode should be 'full' or 'partial'"
            rHandler.retJSObject(call, retObj, msg)
            return
        }
        val readOnly = call.getBoolean("readonly", false) ?: false
        val encrypted = call.getBoolean("encrypted", false) ?: false

        val impl = implementation
        if (impl != null) {
            try {
                val res = impl.exportToJson(dbName!!, expMode, readOnly, encrypted)
                rHandler.retJSObject(call, res, null)
            } catch (e: Exception) {
                val msg = "ExportToJson: ${e.message}"
                rHandler.retJSObject(call, retObj, msg)
            }
        } else {
            rHandler.retJSObject(call, retObj, loadMessage)
        }
    }

    @PluginMethod
    public fun deleteExportedRows(call: PluginCall) {
        if (!call.data.has("database")) {
            val msg = "DeleteExportedRows: Must provide a database name"
            rHandler.retResult(call, null, msg)
            return
        }
        val dbName = call.getString("database")
        val readOnly = call.getBoolean("readonly", false) ?: false
        val impl = implementation
        if (impl != null) {
            try {
                impl.deleteExportedRows(dbName!!, readOnly)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "DeleteExportedRows: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * CopyFromAssets
     * copy all databases from public/assets/databases to application folder
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun copyFromAssets(call: PluginCall) {
        val overwrite = if (call.data.has("overwrite")) call.getBoolean("overwrite") else true

        val impl = implementation
        if (impl != null) {
            try {
                impl.copyFromAssets(overwrite!!)
                rHandler.retResult(call, null, null)
            } catch (e: Exception) {
                val msg = "CopyFromAssets: ${e.message}"
                rHandler.retResult(call, null, msg)
            }
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    /**
     * GetFromHTTPRequest
     * get a database or a zipped database from HTTP Request
     *
     * @param call PluginCall
     */
    @PluginMethod
    public fun getFromHTTPRequest(call: PluginCall) {
        if (!call.data.has("url")) {
            val msg = "GetFromHTTPRequest: Must provide a database url"
            rHandler.retResult(call, null, msg)
            return
        }
        val url = call.getString("url")
        val impl = implementation
        if (impl != null) {
            val setHTTPRunnable =
                Runnable {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    try {
                        impl.getFromHTTPRequest(url!!)
                        activity.runOnUiThread { rHandler.retResult(call, null, null) }
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            val msg = "GetFromHTTPRequest: ${e.message}"
                            rHandler.retResult(call, null, msg)
                        }
                    }
                }
            val myHttpThread = Thread(setHTTPRunnable)
            myHttpThread.start()
            // The call does not return before the download is over, as in the Java original
            @Suppress("ControlFlowWithEmptyBody")
            while (myHttpThread.isAlive) {
            }
            println("Thread Exiting!")
        } else {
            rHandler.retResult(call, null, loadMessage)
        }
    }

    private fun addObserversToNotificationCenter() {
        NotificationCenter.defaultCenter().addMethodForNotification(
            "importJsonProgress",
            object : MyRunnable() {
                override fun run() {
                    val data = JSObject()
                    data.put("progress", info?.get("progress"))
                    notifyListeners("sqliteImportProgressEvent", data)
                }
            }
        )
        NotificationCenter.defaultCenter().addMethodForNotification(
            "exportJsonProgress",
            object : MyRunnable() {
                override fun run() {
                    val data = JSObject()
                    data.put("progress", info?.get("progress"))
                    notifyListeners("sqliteExportProgressEvent", data)
                }
            }
        )
        NotificationCenter.defaultCenter().addMethodForNotification(
            "biometricResults",
            object : MyRunnable() {
                override fun run() {
                    val data = JSObject()
                    data.put("result", info?.get("result"))
                    data.put("message", info?.get("message"))
                    notifyListeners("sqliteBiometricEvent", data)
                }
            }
        )
    }

    private fun getSqliteConfig(): SqliteConfig {
        val sqliteConfig = SqliteConfig()
        val pConfig = config.configJSON
        val isEncryption = if (pConfig.has("androidIsEncryption")) pConfig.getBoolean("androidIsEncryption") else sqliteConfig.isEncryption
        sqliteConfig.isEncryption = isEncryption
        val androidBiometric = if (pConfig.has("androidBiometric")) pConfig.getJSONObject("androidBiometric") else null
        if (androidBiometric != null) {
            val biometricAuth =
                if (androidBiometric.has("biometricAuth") && isEncryption) {
                    androidBiometric.getBoolean("biometricAuth")
                } else {
                    sqliteConfig.biometricAuth
                }
            sqliteConfig.biometricAuth = biometricAuth
            val biometricTitle =
                if (androidBiometric.has("biometricTitle")) {
                    androidBiometric.getString("biometricTitle")
                } else {
                    sqliteConfig.biometricTitle
                }
            sqliteConfig.biometricTitle = biometricTitle
            val biometricSubTitle =
                if (androidBiometric.has("biometricSubTitle")) {
                    androidBiometric.getString("biometricSubTitle")
                } else {
                    sqliteConfig.biometricSubTitle
                }
            sqliteConfig.biometricSubTitle = biometricSubTitle
        }
        return sqliteConfig
    }

    private companion object {
        private val TAG: String = CapacitorSQLitePlugin::class.java.name
    }
}
