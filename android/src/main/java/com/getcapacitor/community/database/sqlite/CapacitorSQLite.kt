package com.getcapacitor.community.database.sqlite

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.getcapacitor.community.database.sqlite.SQLite.BiometricListener
import com.getcapacitor.community.database.sqlite.SQLite.Database
import com.getcapacitor.community.database.sqlite.SQLite.GlobalSQLite
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.JsonSQLite
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.UtilsEncryption
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.UtilsJson
import com.getcapacitor.community.database.sqlite.SQLite.SqliteConfig
import com.getcapacitor.community.database.sqlite.SQLite.UtilsBiometric
import com.getcapacitor.community.database.sqlite.SQLite.UtilsDownloadFromHTTP
import com.getcapacitor.community.database.sqlite.SQLite.UtilsFile
import com.getcapacitor.community.database.sqlite.SQLite.UtilsMigrate
import com.getcapacitor.community.database.sqlite.SQLite.UtilsNCDatabase
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLCipher
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLite
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSecret
import java.security.KeyStore
import java.util.Collections
import java.util.Dictionary
import java.util.Hashtable
import org.json.JSONObject

public class CapacitorSQLite
@Throws(Exception::class)
constructor(private val context: Context, config: SqliteConfig) {
    private val dbDict: Dictionary<String, Database> = Hashtable()
    private val uSqlite = UtilsSQLite()
    private val uFile = UtilsFile()
    private val uJson = UtilsJson()
    private val uMigrate = UtilsMigrate()
    private val uNCDatabase = UtilsNCDatabase()
    private val uHTTP = UtilsDownloadFromHTTP()
    private val globVar = GlobalSQLite()
    private val uCipher = UtilsSQLCipher()
    private var uSecret: UtilsSecret? = null
    private var sharedPreferences: SharedPreferences? = null
    private lateinit var masterKeyAlias: MasterKey
    private lateinit var biometricManager: BiometricManager
    private val isEncryption: Boolean = config.isEncryption
    private val biometricAuth: Boolean = config.biometricAuth
    private val biometricTitle: String = config.biometricTitle
    private val biometricSubTitle: String = config.biometricSubTitle
    private val rHandler = RetHandler()

    init {
        try {
            if (isEncryption) {
                // create or retrieve masterkey from Android keystore
                // it will be used to encrypt the passphrase for a database

                if (biometricAuth) {
                    biometricManager = BiometricManager.from(context)
                    val listener =
                        object : BiometricListener {
                            override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                                try {
                                    val ks = KeyStore.getInstance("AndroidKeyStore")
                                    ks.load(null)
                                    val aliases = ks.aliases()
                                    masterKeyAlias =
                                        if (aliases.hasMoreElements()) {
                                            MasterKey
                                                .Builder(context)
                                                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                                .setUserAuthenticationRequired(true, VALIDITY_DURATION)
                                                .build()
                                        } else {
                                            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                                        }
                                    setSharedPreferences()
                                    notifyBiometricEvent(true, null)
                                } catch (e: Exception) {
                                    val input = e.message
                                    Log.e("MY_APP_TAG", input.toString())
                                    notifyBiometricEvent(false, input)
                                }
                            }

                            override fun onFailed() {
                                val input = "Error in authenticating biometric"
                                Log.e("MY_APP_TAG", input)
                                notifyBiometricEvent(false, input)
                            }
                        }
                    val uBiom = UtilsBiometric(context, biometricManager, listener)
                    if (uBiom.checkBiometricIsAvailable()) {
                        uBiom.showBiometricDialog(biometricTitle, biometricSubTitle)
                    } else {
                        masterKeyAlias = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                        setSharedPreferences()
                    }
                } else {
                    masterKeyAlias = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                    setSharedPreferences()
                }
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    private fun notifyBiometricEvent(ret: Boolean, msg: String?) {
        val info = hashMapOf<String, Any?>("result" to ret, "message" to msg)
        Log.v(TAG, "\$\$\$\$\$ in notifyBiometricEvent $info")
        NotificationCenter.defaultCenter().postNotification("biometricResults", info)
    }

    private fun setSharedPreferences() {
        try {
            // get instance of the EncryptedSharedPreferences class
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    "sqlite_encrypted_shared_prefs",
                    masterKeyAlias,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            sharedPreferences = prefs
            uSecret = UtilsSecret(context, prefs)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * The secret store only exists once the shared preferences are set, which with biometric authentication
     * happens after the user authenticated. Before that the Java code failed with a NullPointerException
     * that was reported to JS like any other error; this keeps it an error.
     */
    private fun secretStore(): UtilsSecret = uSecret ?: throw NullPointerException("Encrypted shared preferences are not available")

    /**
     * Echo
     *
     * @param value string to echo
     * @return string to echo
     */
    public fun echo(value: String?): String? = value

    @Throws(Exception::class)
    public fun isSecretStored(): Boolean {
        if (isEncryption) {
            try {
                // getPassphrase is static: it never needed the UtilsSecret instance
                val secret = UtilsSecret.getPassphrase()
                return secret.isNotEmpty()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("No Encryption set in capacitor.config")
        }
    }

    /**
     * SetEncryptionSecret
     *
     * @param passphrase passphrase
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun setEncryptionSecret(passphrase: String) {
        if (isEncryption) {
            try {
                // close all connections
                closeAllConnections()
                // set encryption secret
                secretStore().setEncryptionSecret(passphrase)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("No Encryption set in capacitor.config")
        }
    }

    /**
     * ChangeEncryptionSecret
     *
     * @param passphrase new passphrase
     * @param oldPassphrase old passphrase
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun changeEncryptionSecret(call: PluginCall, passphrase: String, oldPassphrase: String) {
        if (isEncryption) {
            try {
                // close all connections
                closeAllConnections()
                if (biometricAuth) {
                    val listener =
                        object : BiometricListener {
                            override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                                try {
                                    // change encryption secret
                                    secretStore().changeEncryptionSecret(passphrase, oldPassphrase)
                                    rHandler.retResult(call, null, null)
                                } catch (e: Exception) {
                                    val input = e.message
                                    Log.e("MY_APP_TAG", input.toString())
                                    Toast.makeText(context, input, Toast.LENGTH_LONG).show()
                                    rHandler.retResult(call, null, e.message)
                                }
                            }

                            override fun onFailed() {
                                val input = "Error in authenticating biometric"
                                Log.e("MY_APP_TAG", input)
                                Toast.makeText(context, input, Toast.LENGTH_LONG).show()
                                rHandler.retResult(call, null, input)
                            }
                        }

                    val uBiom = UtilsBiometric(context, biometricManager, listener)
                    if (uBiom.checkBiometricIsAvailable()) {
                        uBiom.showBiometricDialog(biometricTitle, biometricSubTitle)
                    } else {
                        throw Exception("Biometric features are currently unavailable.")
                    }
                } else {
                    // change encryption secret
                    secretStore().changeEncryptionSecret(passphrase, oldPassphrase)
                }
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("No Encryption set in capacitor.config")
        }
    }

    /**
     * ClearEncryptionSecret
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun clearEncryptionSecret() {
        if (isEncryption) {
            try {
                // close all connections
                closeAllConnections()
                // set encryption secret
                secretStore().clearEncryptionSecret()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("No Encryption set in capacitor.config")
        }
    }

    /**
     * CheckEncryptionSecret
     *
     * @param passphrase secret phrase for encryption
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun checkEncryptionSecret(passphrase: String): Boolean {
        if (isEncryption) {
            try {
                // close all connections
                closeAllConnections()
                // set encryption secret
                return secretStore().checkEncryptionSecret(passphrase)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("No Encryption set in capacitor.config")
        }
    }

    @Throws(Exception::class)
    public fun getNCDatabasePath(folderPath: String, database: String): String {
        try {
            return uNCDatabase.getNCDatabasePath(context, folderPath, database)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * CreateConnection
     *
     * @param dbName database name
     * @param encrypted boolean
     * @param mode  "no-encryption", "secret", "encryption"
     * @param version database version
     * @param vUpgObject upgrade Object
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun createConnection(
        dbName: String,
        encrypted: Boolean,
        mode: String,
        version: Int,
        vUpgObject: Dictionary<Int, JSONObject>?,
        readonly: Boolean
    ) {
        val name = getDatabaseName(dbName)
        val connName = connectionName(name, readonly)
        // check if connection already exists
        if (dbDict.get(connName) != null) {
            throw Exception("Connection $name already exists")
        }
        if (encrypted && !isEncryption) {
            throw Exception("Database cannot be encrypted as 'No Encryption' set in capacitor.config")
        }
        try {
            val db = Database(context, name + "SQLite.db", encrypted, mode, version, isEncryption, vUpgObject, sharedPreferences, readonly)
            dbDict.put(connName, db)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * CreateNCConnection
     *
     * @param dbPath database path
     * @param version database version
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun createNCConnection(dbPath: String, version: Int) {
        // check if connection already exists
        val connName = "RO_$dbPath"
        if (dbDict.get(connName) != null) {
            throw Exception("Connection $dbPath already exists")
        }
        try {
            if (!uFile.isPathExists(dbPath)) {
                throw Exception("Database $dbPath does not exist")
            }
            val db = Database(context, dbPath, false, "no-encryption", version, isEncryption, Hashtable(), sharedPreferences, true)
            dbDict.put(connName, db)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * Open
     *
     * @param dbName database name
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun open(dbName: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        try {
            db.open()
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * Close
     *
     * @param dbName database name
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun close(dbName: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        if (db.isOpen) {
            if (!db.inTransaction()) {
                try {
                    db.close()
                } catch (e: Exception) {
                    throw Exception(e.message)
                }
            } else {
                throw Exception("database $name failed to close still in transaction")
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * BeginTransaction
     *
     * @param dbName Database name
     * @return JSObject changes
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun beginTransaction(dbName: String): JSObject {
        val retObj = JSObject()
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (!db.isNCDB && db.isOpen) {
            try {
                val res = db.beginTransaction()
                retObj.put("changes", res)
                return retObj
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * CommitTransaction
     *
     * @param dbName Database name
     * @return JSObject changes
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun commitTransaction(dbName: String): JSObject {
        val retObj = JSObject()
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (!db.isNCDB && db.isOpen) {
            try {
                val res = db.commitTransaction()
                retObj.put("changes", res)
                return retObj
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * Rollback Transaction
     *
     * @param dbName Database name
     * @return JSObject changes
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun rollbackTransaction(dbName: String): JSObject {
        val retObj = JSObject()
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (!db.isNCDB && db.isOpen) {
            try {
                val res = db.rollbackTransaction()
                retObj.put("changes", res)
                return retObj
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * IsTransactionActive
     *
     * @param dbName database name
     * @return Boolean
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun isTransactionActive(dbName: String): Boolean {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (!db.isNCDB && db.isOpen) {
            try {
                return db.isAvailTrans()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * GetUrl
     *
     * @param dbName database name
     * @return String
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun getUrl(dbName: String, readonly: Boolean): String {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        try {
            return db.getUrl()
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * GetVersion
     *
     * @param dbName database name
     * @return Integer
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun getVersion(dbName: String, readonly: Boolean): Int {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        try {
            return db.getVersion()
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * CloseNCConnection
     *
     * @param dbPath database path
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun closeNCConnection(dbPath: String) {
        val connName = "RO_$dbPath"
        val db = connection(dbPath, connName)
        if (db.isOpen) {
            try {
                db.close()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        }
        dbDict.remove(connName)
    }

    /**
     * CloseConnection
     *
     * @param dbName database name
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun closeConnection(dbName: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val connName = connectionName(name, readonly)
        val db = connection(name, connName)
        if (db.isOpen) {
            try {
                db.close()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        }
        dbDict.remove(connName)
    }

    @Throws(Exception::class)
    public fun getFromHTTPRequest(url: String) {
        try {
            uHTTP.download(context, url)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun checkConnectionsConsistency(dbNames: JSArray, openModes: JSArray): Boolean {
        var keys: Set<String> = HashSet(Collections.list(dbDict.keys()))
        val nameDBs = JSArray()
        for (i in 0 until dbNames.length()) {
            val name = openModes.getString(i) + "_" + dbNames.getString(i)
            nameDBs.put(name)
        }
        val conns: Set<String> = HashSet(uSqlite.stringJSArrayToArrayList(nameDBs))
        try {
            if (conns.isEmpty()) {
                closeAllConnections()
                return false
            }
            if (keys.size < conns.size) {
                // not solvable inconsistency
                closeAllConnections()
                return false
            }
            if (keys.size > conns.size) {
                for (key in keys) {
                    if (!conns.contains(key)) {
                        dbDict.remove(key)
                    }
                }
            }
            keys = HashSet(Collections.list(dbDict.keys()))
            if (keys.size == conns.size) {
                val symmetricDiff = (keys union conns) subtract (keys intersect conns)
                if (symmetricDiff.isEmpty()) {
                    return true
                } else {
                    // not solvable inconsistency
                    closeAllConnections()
                    return false
                }
            } else {
                closeAllConnections()
                return false
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * IsDatabase
     *
     * @param dbName database name
     * @return Boolean
     */
    public fun isDatabase(dbName: String): Boolean = uFile.isFileExists(context, getDatabaseName(dbName) + "SQLite.db")

    /**
     * IsDatabaseEncrypted
     *
     * @param dbName database name
     * @return Boolean
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun isDatabaseEncrypted(dbName: String): Boolean {
        val name = getDatabaseName(dbName)
        val file = context.getDatabasePath(name + "SQLite.db")
        if (uFile.isFileExists(context, name + "SQLite.db")) {
            val state = uCipher.getDatabaseState(context, file, sharedPreferences, globVar)
            if (state == UtilsSQLCipher.State.ENCRYPTED_GLOBAL_SECRET || state == UtilsSQLCipher.State.ENCRYPTED_SECRET) {
                return true
            }
            if (state == UtilsSQLCipher.State.UNENCRYPTED) {
                return false
            }
            throw Exception("Database unknown")
        } else {
            throw Exception("Database does not exist")
        }
    }

    /**
     * IsNCDatabase
     *
     * @param dbPath database path
     * @return Boolean
     */
    public fun isNCDatabase(dbPath: String): Boolean = uFile.isPathExists(dbPath)

    /**
     * IsTableExists
     *
     * @param dbName database name
     * @param tableName table name
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun isTableExists(dbName: String, tableName: String, readonly: Boolean): Boolean {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        return uJson.isTableExists(db, tableName)
    }

    /**
     * GetDatabaseList
     *
     * @return JSArray database list
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun getDatabaseList(): JSArray {
        val listFiles = uFile.getListOfFiles(context)
        val retArray = JSArray()
        for (file in listFiles) {
            if (file.contains("SQLite")) {
                retArray.put(file)
            }
        }
        if (retArray.length() > 0) {
            return retArray
        } else {
            throw Exception("No databases available ")
        }
    }

    /**
     * GetMigratableDbList
     *
     * @return JSArray database list
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun getMigratableDbList(folderPath: String): JSArray {
        // Java iterated the array without a null check
        val listFiles = uMigrate.getMigratableList(context, folderPath)!!
        val retArray = JSArray()
        for (file in listFiles) {
            if (!file.contains("SQLite")) {
                retArray.put(file)
            }
        }
        if (retArray.length() > 0) {
            return retArray
        } else {
            throw Exception("No databases available ")
        }
    }

    /**
     * AddSQLiteSuffix
     *
     * @param folderPath folder path
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun addSQLiteSuffix(folderPath: String, dbList: JSArray) {
        try {
            val mDbList = uSqlite.stringJSArrayToArrayList(dbList)
            uMigrate.addSQLiteSuffix(context, folderPath, mDbList)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * @param folderPath folder path
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun deleteOldDatabases(folderPath: String, dbList: JSArray) {
        try {
            val mDbList = uSqlite.stringJSArrayToArrayList(dbList)
            uMigrate.deleteOldDatabases(context, folderPath, mDbList)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     *
     * @param folderPath folder path
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun moveDatabasesAndAddSuffix(folderPath: String, dbList: JSArray) {
        try {
            val mDbList = uSqlite.stringJSArrayToArrayList(dbList)
            uMigrate.moveDatabasesAndAddSuffix(context, folderPath, mDbList)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * Execute
     *
     * @param dbName Database name
     * @param statements a bench of statement
     * @return JSObject changes
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun execute(dbName: String, statements: String, transaction: Boolean, readonly: Boolean): JSObject {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        if (!db.isNCDB && db.isOpen) {
            // convert string in string[]
            val sqlCmdArray = uSqlite.getStatementsArray(statements)
            try {
                return db.execute(sqlCmdArray, transaction)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * ExecuteSet
     *
     * @param dbName database name
     * @param set Set containing statements and values
     * @return JSObject changes, lastId, values when RETURNING
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun executeSet(dbName: String, set: JSArray, transaction: Boolean, readonly: Boolean, returnMode: String): JSObject {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        if (!db.isNCDB && db.isOpen) {
            try {
                return db.executeSet(set, transaction, returnMode)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * Run
     *
     * @param dbName Database name
     * @param statement SQLite statement
     * @param values SQLite values if any
     * @return JSObject changes, lastId, values when RETURNING
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun run(
        dbName: String,
        statement: String,
        values: JSArray,
        transaction: Boolean,
        readonly: Boolean,
        returnMode: String
    ): JSObject {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        if (!db.isNCDB && db.isOpen) {
            try {
                val arrValues = if (values.length() > 0) uSqlite.objectJSArrayToArrayList(values) else ArrayList()
                return db.runSQL(statement, arrValues, transaction, returnMode)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    /**
     * Query
     *
     * @param dbName Database name
     * @param statement SQLite statement
     * @param values SQLite values if any
     * @return JSArray
     * @throws Exception message
     */
    @Throws(Exception::class)
    public fun query(dbName: String, statement: String, values: JSArray, readonly: Boolean): JSArray {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        if (db.isOpen) {
            try {
                val arrValues = if (values.length() > 0) uSqlite.objectJSArrayToArrayList(values) else ArrayList()
                return db.selectSQL(statement, arrValues)
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        } else {
            throw Exception("database $name not opened")
        }
    }

    @Throws(Exception::class)
    public fun getTableList(dbName: String, readonly: Boolean): JSArray {
        // the name is used as given here, without stripping a ".db" suffix
        val db = connection(dbName, connectionName(dbName, readonly))
        if (db.isOpen) {
            return db.getTableNames()
        } else {
            throw Exception("database $dbName not opened")
        }
    }

    @Throws(Exception::class)
    public fun isDBExists(dbName: String, readonly: Boolean): Boolean {
        val name = getDatabaseName(dbName)
        connection(name, connectionName(name, readonly))
        val databaseFile = context.getDatabasePath(name + "SQLite.db")
        return databaseFile.exists()
    }

    @Throws(Exception::class)
    public fun isDBOpen(dbName: String, readonly: Boolean): Boolean {
        val name = getDatabaseName(dbName)
        return connection(name, connectionName(name, readonly)).isOpen
    }

    @Throws(Exception::class)
    public fun deleteDatabase(dbName: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        try {
            db.deleteDB(name + "SQLite.db")
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun createSyncTable(dbName: String, readonly: Boolean): JSObject {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        try {
            if (!db.isOpen) {
                throw Exception("CreateSyncTable: db not opened")
            }
            return db.createSyncTable()
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun setSyncDate(dbName: String, syncDate: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        try {
            if (!db.isOpen) {
                throw Exception("SetSyncDate: db not opened")
            }
            db.setSyncDate(syncDate)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun getSyncDate(dbName: String, readonly: Boolean): Long {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        try {
            if (!db.isOpen) {
                throw Exception("GetSyncDate: db not opened")
            }
            return db.getSyncDate()
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun addUpgradeStatement(upgrade: JSArray): Dictionary<Int, JSONObject> {
        val upgDict: Dictionary<Int, JSONObject> = Hashtable()

        for (i in 0 until upgrade.length()) {
            val upgObj: JSONObject
            try {
                upgObj = upgrade.get(i) as JSONObject
                if (!upgObj.has("toVersion") || !upgObj.has("statements")) {
                    var msg = "Must provide an upgrade statement"
                    msg += " {toVersion,statement}"
                    throw Exception(msg)
                }
            } catch (e: Exception) {
                throw Exception("Must provide an upgrade statement " + e.message)
            }
            try {
                val toVersion = upgObj.getInt("toVersion")
                upgDict.put(toVersion, upgObj)
            } catch (e: Exception) {
                throw Exception("Must provide toVersion as Integer" + e.message)
            }
        }
        return upgDict
    }

    @Throws(Exception::class)
    public fun isJsonValid(parsingData: String): Boolean {
        try {
            val jsonObject = JSObject(parsingData)
            val jsonSQL = JsonSQLite()
            return jsonSQL.isJsonSQLite(jsonObject, isEncryption)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @Throws(Exception::class)
    public fun importFromJson(parsingData: String): JSObject {
        try {
            var jsonObject = JSObject(parsingData)
            if (jsonObject.has("expData")) {
                // Decrypt the data
                jsonObject = UtilsEncryption.decryptJSONObject(context, jsonObject.getString("expData"))
            }
            val jsonSQL = JsonSQLite()
            val isValid = jsonSQL.isJsonSQLite(jsonObject, isEncryption)
            if (!isValid) {
                throw Exception("Stringify Json Object not Valid")
            }
            val dbName = getDatabaseName(jsonSQL.database) + "SQLite.db"
            // version, overwrite and encrypted were unboxed by the Java at this point
            val dbVersion: Int = jsonSQL.version!!
            val mode: String = jsonSQL.mode
            val overwrite: Boolean = jsonSQL.overwrite!!
            val encrypted: Boolean = jsonSQL.encrypted!!
            val inMode = if (encrypted) "secret" else "no-encryption"
            val db = Database(context, dbName, encrypted, inMode, dbVersion, isEncryption, Hashtable(), sharedPreferences, false)
            if (overwrite && mode == "full") {
                val isExists = uFile.isFileExists(context, dbName)
                if (isExists) {
                    uFile.deleteFile(context, dbName)
                }
            }
            db.open()
            if (!db.isOpen) {
                throw Exception(dbName + "SQLite.db not opened")
            } else {
                // check if the database as some tables
                val tableList = db.getTableNames()
                if (mode == "full" && tableList.length() > 0) {
                    val curVersion: Int = db.getVersion()
                    if (dbVersion < curVersion) {
                        var msg = "ImportFromJson: Cannot import a "
                        msg += "version lower than$curVersion"
                        throw Exception(msg)
                    }
                    if (curVersion == dbVersion) {
                        val result = JSObject()
                        result.put("changes", 0)
                        return result
                    }
                }
                val res = db.importFromJson(jsonSQL)
                db.close()
                if (res.getInteger("changes") != -1) {
                    return res
                } else {
                    throw Exception("importFromJson: import JsonObject not successful")
                }
            }
        } catch (e: Exception) {
            throw Exception("importFromJson : " + e.message)
        }
    }

    @Throws(Exception::class)
    public fun exportToJson(dbName: String, expMode: String, readonly: Boolean, encrypted: Boolean): JSObject {
        val name = getDatabaseName(dbName)
        val db = connection(name, connectionName(name, readonly))
        try {
            if (!db.isOpen) {
                throw Exception("ExportToJson: db not opened")
            }
            val ret = db.exportToJson(expMode, encrypted)
            if (ret.length() == 0) {
                throw Exception("ExportToJson: : return Object is empty " + "No data to synchronize")
            } else if (ret.length() == 1 && ret.has("expData")) {
                return ret
            } else if (ret.length() == 5 || ret.length() == 6 || ret.length() == 7) {
                return ret
            } else {
                throw Exception("ExportToJson: return Obj is not a JsonSQLite Obj")
            }
        } catch (e: Exception) {
            throw Exception("ExportToJson " + e.message)
        }
    }

    @Throws(Exception::class)
    public fun deleteExportedRows(dbName: String, readonly: Boolean) {
        val name = getDatabaseName(dbName)
        val db = connection(name, "RW_$name")
        if (readonly) {
            throw Exception("not allowed in read-only mode")
        }
        try {
            if (!db.isOpen) {
                throw Exception("deleteExportedRows: db not opened")
            }
            db.deleteExportedRows()
        } catch (e: Exception) {
            throw Exception("DeleteExportedRows " + e.message)
        }
    }

    @Throws(Exception::class)
    public fun copyFromAssets(overwrite: Boolean) {
        var msg = "copy failed : "
        try {
            uFile.copyFromAssetsToDatabase(context, overwrite)
        } catch (e: Exception) {
            msg += e.message
            throw Exception(msg)
        }
    }

    private fun getDatabaseName(dbName: String): String {
        var retName = dbName
        if (!retName.contains("/")) {
            if (retName.endsWith(".db")) {
                retName = retName.substring(0, retName.length - 3)
            }
        }
        return retName
    }

    private fun connectionName(dbName: String, readonly: Boolean): String = if (readonly) "RO_$dbName" else "RW_$dbName"

    /** The open connection registered under [connName], or the error every method reports when there is none. */
    private fun connection(dbName: String, connName: String): Database =
        dbDict.get(connName) ?: throw Exception("No available connection for database $dbName")

    private fun closeAllConnections() {
        // close all connections
        try {
            val connections = dbDict.keys()
            while (connections.hasMoreElements()) {
                val connName = connections.nextElement()
                val readonly = connName.startsWith("RO_")
                closeConnection(connName.substring(3), readonly)
            }
        } catch (e: Exception) {
            throw Exception("close all connections " + e.message)
        }
    }

    private companion object {
        private val TAG: String = CapacitorSQLite::class.java.name
        private const val VALIDITY_DURATION = 5
    }
}
