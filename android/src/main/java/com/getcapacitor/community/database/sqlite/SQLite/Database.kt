package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor.FIELD_TYPE_BLOB
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.DatabaseUtils
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.ExportToJson
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.ImportFromJson
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.JsonSQLite
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.UtilsEncryption
import com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson.UtilsJson
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Dictionary
import java.util.Locale
import java.util.Objects
import java.util.regex.Matcher
import java.util.regex.Pattern
import net.zetetic.database.sqlcipher.SQLiteCursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

public class Database(
    private val context: Context,
    private val dbName: String,
    private val encrypted: Boolean,
    private val mode: String,
    private val version: Int,
    private val isEncryption: Boolean,
    private val vUpgObject: Dictionary<Int, JSONObject>?,
    sharedPreferences: SharedPreferences?,
    private val readOnly: Boolean
) {
    /**
     * Database status
     */
    public var isOpen: Boolean = false
        private set

    /**
     * Non-conformed database status
     */
    public var isNCDB: Boolean = false
        private set

    /**
     * The underlying database, null until the database has been opened
     */
    public var db: SupportSQLiteDatabase? = null
        private set

    private val file: File
    private val uSqlite = UtilsSQLite()
    private val uCipher = UtilsSQLCipher()
    private val uFile = UtilsFile()
    private val uJson = UtilsJson()
    private val uUpg = UtilsUpgrade()
    private val uDrop = UtilsDrop()

    // Only its constructor matters here: it hands the SharedPreferences to the static passphrase accessors
    private val uSecret: UtilsSecret? = if (isEncryption) UtilsSecret(context, sharedPreferences) else null
    private val fromJson = ImportFromJson()
    private val toJson = ExportToJson()

    init {
        if (dbName.contains("/") && dbName.endsWith("SQLite.db")) {
            isNCDB = true
            file = File(dbName)
        } else {
            file = context.getDatabasePath(dbName)
        }
        initializeSQLCipher()
        val parentFile = Objects.requireNonNull(file.parentFile)
        if (!parentFile.exists()) {
            val dirCreated = parentFile.mkdirs()
            if (!dirCreated) {
                println("Failed to create parent directories.")
            }
        }
        Log.v(TAG, "&&& file path " + file.absolutePath)
    }

    /**
     * InitializeSQLCipher Method
     * Initialize the SQLCipher Libraries
     */
    private fun initializeSQLCipher() {
        System.loadLibrary("sqlcipher")
    }

    /**
     * IsAvailTrans method
     *
     * @return database transaction is active
     */
    public fun isAvailTrans(): Boolean {
        // The Java threw a NullPointerException here too when the database had never been opened
        return db!!.inTransaction()
    }

    /**
     * BeginTransaction method
     *
     * @return begin a database transaction
     */
    public fun beginTransaction(): Int {
        // The Java threw a NullPointerException here too when the database had never been opened
        val db = db!!
        if (db.isOpen) {
            try {
                if (isAvailTrans()) {
                    throw Exception("Already in transaction")
                }
                db.beginTransaction()
                return 0
            } catch (e: Exception) {
                val msg = "Failed in beginTransaction" + e.message
                Log.v(TAG, msg)
                throw Exception(msg)
            }
        } else {
            throw Exception("Database not opened")
        }
    }

    /**
     * CommitTransaction method
     *
     * @return commit a database transaction
     */
    public fun commitTransaction(): Int {
        // The Java threw a NullPointerException here too when the database had never been opened
        val db = db!!
        if (db.isOpen) {
            try {
                if (!isAvailTrans()) {
                    throw Exception("No transaction active")
                }
                db.setTransactionSuccessful()
                return 0
            } catch (e: Exception) {
                val msg = "Failed in commitTransaction" + e.message
                Log.v(TAG, msg)
                throw Exception(msg)
            } finally {
                db.endTransaction()
            }
        } else {
            throw Exception("Database not opened")
        }
    }

    /**
     * Rollback Transaction method
     *
     * @return rollback a database transaction
     */
    public fun rollbackTransaction(): Int {
        // The Java threw a NullPointerException here too when the database had never been opened
        val db = db!!
        if (db.isOpen) {
            try {
                if (isAvailTrans()) {
                    db.endTransaction()
                }
                return 0
            } catch (e: Exception) {
                val msg = "Failed in rollbackTransaction" + e.message
                Log.v(TAG, msg)
                throw Exception(msg)
            }
        } else {
            throw Exception("Database not opened")
        }
    }

    /**
     * GetUrl method
     *
     * @return database url
     */
    public fun getUrl(): String {
        val url = "file://"
        return url + file.absolutePath
    }

    /**
     * Open method
     */
    public fun open() {
        val curVersion: Int

        var password = ""
        if (encrypted && (mode == "secret" || mode == "encryption" || mode == "decryption")) {
            if (!UtilsSecret.isPassphrase()) {
                throw Exception("No Passphrase stored")
            }
            password = UtilsSecret.getPassphrase()
        }
        if (mode == "encryption") {
            if (isEncryption) {
                try {
                    uCipher.encrypt(context, file, password.toByteArray(StandardCharsets.UTF_8))
                } catch (e: Exception) {
                    val msg = "Failed in encryption " + e.message
                    Log.v(TAG, msg)
                    throw Exception(msg)
                }
            } else {
                throw Exception("No Encryption set in capacitor.config")
            }
        }
        if (mode == "decryption") {
            if (isEncryption) {
                try {
                    uCipher.decrypt(context, file, password.toByteArray(Charset.defaultCharset()))
                    password = ""
                } catch (e: Exception) {
                    val msg = "Failed in decryption " + e.message
                    Log.v(TAG, msg)
                    throw Exception(msg)
                }
            } else {
                throw Exception("No Encryption set in capacitor.config")
            }
        }
        try {
            val db: SupportSQLiteDatabase? =
                if (!isNCDB && !readOnly) {
                    SQLiteDatabase.openOrCreateDatabase(file, password, null, null)
                } else {
                    SQLiteDatabase.openDatabase(file.toString(), password, null, SQLiteDatabase.OPEN_READONLY, null)
                }
            this.db = db
            if (db != null) {
                if (db.isOpen) {
                    // set the Foreign Key Pragma ON
                    try {
                        db.setForeignKeyConstraintsEnabled(true)
                    } catch (e: IllegalStateException) {
                        val msg = "Failed in setForeignKeyConstraintsEnabled " + e.message
                        Log.v(TAG, msg)
                        close()
                        this.db = null
                        throw Exception(msg)
                    }
                    if (isNCDB || readOnly) {
                        isOpen = true
                        return
                    }
                    try {
                        curVersion = db.version // default 0
                    } catch (e: IllegalStateException) {
                        val msg = "Failed in get/setVersion " + e.message
                        Log.v(TAG, msg)
                        close()
                        this.db = null
                        throw Exception(msg)
                    }
                    if (version > curVersion && vUpgObject != null && vUpgObject.size() > 0) {
                        try {
                            uFile.copyFile(context, dbName, "backup-$dbName")

                            uUpg.onUpgrade(this, vUpgObject, curVersion, version)

                            val ret: Boolean = uFile.deleteBackupDB(context, dbName)
                            if (!ret) {
                                val msg = "Failed in deleteBackupDB backup-\" + _dbName"
                                Log.v(TAG, msg)
                                close()
                                this.db = null
                                throw Exception(msg)
                            }
                        } catch (e: Exception) {
                            // restore DB
                            val ret: Boolean = uFile.restoreDatabase(context, dbName)
                            var msg = e.message
                            if (!ret) msg += "Failed in restoreDatabase $dbName"
                            Log.v(TAG, msg.toString())
                            close()
                            this.db = null
                            throw Exception(msg)
                        }
                    }
                    isOpen = true
                    return
                } else {
                    isOpen = false
                    this.db = null
                    throw Exception("Database not opened")
                }
            } else {
                isOpen = false
                this.db = null
                throw Exception("No database returned")
            }
        } catch (e: Exception) {
            val msg = "Error in creating the database" + e.message
            isOpen = false
            this.db = null
            throw Exception(msg)
        }
    }

    /**
     * Close Method
     */
    public fun close() {
        // The Java threw a NullPointerException here too when the database had never been opened
        val db = db!!
        if (db.isOpen) {
            try {
                db.close()
                isOpen = false
                return
            } catch (e: Exception) {
                val msg = "Failed in database close" + e.message
                Log.v(TAG, msg)
                throw Exception(msg)
            }
        } else {
            throw Exception("Database not opened")
        }
    }

    public fun getVersion(): Int {
        // The Java threw a NullPointerException here too when the database had never been opened
        val db = db!!
        if (db.isOpen) {
            try {
                return db.version
            } catch (e: Exception) {
                val msg = "Failed in database getVersion" + e.message
                Log.v(TAG, msg)
                throw Exception(msg)
            }
        } else {
            throw Exception("Database not opened")
        }
    }

    /**
     * IsDBExists Method
     *
     * @return the existence of the database on folder
     */
    public fun isDBExists(): Boolean = file.exists()

    /**
     * Execute Method
     * Execute an Array of SQL Statements
     *
     * @param statements Array of Strings
     * @param transaction wrap the statements in a transaction
     * @return
     */
    public fun execute(statements: Array<String>, transaction: Boolean = true): JSObject {
        val retObj = JSObject()
        val db = db
        try {
            if (db != null && db.isOpen) {
                val initChanges = uSqlite.dbChanges(db)
                if (transaction) beginTransaction()
                for (statement in statements) {
                    var cmd = statement
                    if (!cmd.endsWith(";")) cmd += ";"
                    var nCmd = cmd
                    val trimmed = nCmd.trim { it <= ' ' }
                    val trimCmd = trimmed.substring(0, minOf(trimmed.length, 11)).uppercase(Locale.getDefault())
                    if (trimCmd == "DELETE FROM" &&
                        nCmd.lowercase(Locale.getDefault()).contains("WHERE".lowercase(Locale.getDefault()))
                    ) {
                        val whereStmt = nCmd.trim { it <= ' ' }
                        nCmd = deleteSQL(this, whereStmt, ArrayList())
                    }
                    db.execSQL(nCmd)
                }
                val changes = uSqlite.dbChanges(db) - initChanges
                if (changes != -1) {
                    if (transaction) commitTransaction()
                    retObj.put("changes", changes)
                }
                return retObj
            } else {
                throw Exception("Database not opened")
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        } finally {
            if (db != null && transaction && db.inTransaction()) rollbackTransaction()
        }
    }

    /**
     * ExecuteSet Method
     * Execute a Set of SQL Statements
     *
     * @param set JSArray of statements
     * @return
     */
    public fun executeSet(set: JSArray, transaction: Boolean, returnMode: String): JSObject {
        val retObj = JSObject()
        var lastId: Long = -1
        var response = JSObject()
        val db = db
        try {
            if (db != null && db.isOpen) {
                val initChanges = uSqlite.dbChanges(db)
                if (transaction) beginTransaction()
                for (i in 0 until set.length()) {
                    val row = set.getJSONObject(i)
                    val statement = row.getString("statement")
                    val valuesJson = row.getJSONArray("values")
                    // optimize executeSet
                    val isArray: Boolean = if (valuesJson.length() > 0) uSqlite.parse(valuesJson.get(0)) else false
                    val respSet =
                        if (isArray) {
                            multipleRowsStatement(statement, valuesJson, returnMode)
                        } else {
                            oneRowStatement(statement, valuesJson, returnMode)
                        }
                    lastId = respSet.getLong("lastId")
                    if (lastId == -1L) break
                    response = addToResponse(response, respSet)
                }
                if (lastId == -1L) {
                    throw Exception("lastId equals -1")
                } else {
                    if (transaction) commitTransaction()
                    val changes = uSqlite.dbChanges(db) - initChanges
                    retObj.put("changes", changes)
                    retObj.put("lastId", lastId)
                    retObj.put("values", response.getJSONArray("values"))
                    return retObj
                }
            } else {
                throw Exception("Database not opened")
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        } finally {
            if (db != null && transaction && db.inTransaction()) rollbackTransaction()
        }
    }

    public fun multipleRowsStatement(statement: String, valuesJson: JSONArray, returnMode: String): JSObject {
        val sqlBuilder = StringBuilder()
        try {
            for (j in 0 until valuesJson.length()) {
                val innerArray = valuesJson.getJSONArray(j)
                val innerSqlBuilder = StringBuilder()
                for (k in 0 until innerArray.length()) {
                    val innerElement = innerArray.get(k)
                    val elementValue =
                        if (innerElement is String) {
                            DatabaseUtils.sqlEscapeString(innerElement)
                        } else {
                            innerElement.toString()
                        }
                    innerSqlBuilder.append(elementValue)

                    if (k < innerArray.length() - 1) {
                        innerSqlBuilder.append(",")
                    }
                }

                sqlBuilder.append("(").append(innerSqlBuilder.toString()).append(")")

                if (j < valuesJson.length() - 1) {
                    sqlBuilder.append(",")
                }
            }
            val finalSql = replacePlaceholders(statement, sqlBuilder.toString())

            return prepareSQL(finalSql, ArrayList(), false, returnMode)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    public fun replacePlaceholders(stmt: String, sqlBuilder: String): String {
        // Extract question mark placeholders from the input statement
        // Return the original statement if no placeholders are found
        extractQuestionMarkValues(stmt) ?: return stmt

        // Regex to match the VALUES clause with varying number of placeholders
        val regex = "(?i)VALUES\\s*\\((\\s*\\?\\s*(?:,\\s*\\?\\s*)*)\\)"

        // Create a pattern and matcher
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(stmt)

        // Check if the pattern matches and perform the replacement
        if (matcher.find()) {
            // Perform the replacement without causing an IndexOutOfBoundsException
            return matcher.replaceAll("VALUES " + Matcher.quoteReplacement(sqlBuilder))
        } else {
            throw IllegalArgumentException("The statement does not contain a valid VALUES clause with placeholders.")
        }
    }

    public fun extractQuestionMarkValues(input: String): String? {
        val pattern = Pattern.compile("(?i)VALUES \\((\\?(?:,\\s*\\?\\s*)*)\\)")
        val matcher = pattern.matcher(input)

        return if (matcher.find()) {
            // Group 1 always takes part in a match of this pattern
            val extractedSubstring = matcher.group(1) ?: ""
            "(" + extractedSubstring.replace(Regex("\\s*,\\s*"), ",") + ")"
        } else {
            null
        }
    }

    public fun oneRowStatement(statement: String, valuesJson: JSONArray, returnMode: String): JSObject {
        val values = ArrayList<Any?>()
        for (j in 0 until valuesJson.length()) {
            values.add(valuesJson.get(j))
        }
        try {
            return prepareSQL(statement, values, false, returnMode)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    public fun addToResponse(response: JSObject, respSet: JSObject): JSObject {
        val lastId = respSet.getLong("lastId")
        var respVals = respSet.getJSONArray("values")
        if (response.keys().hasNext()) {
            val retVals = respSet.getJSONArray("values")
            respVals = response.getJSONArray("values")
            mergeJSONArrays(respVals, retVals)
        }
        response.put("lastId", lastId)
        response.put("values", respVals)
        return response
    }

    /**
     * InTransaction Method
     * Check if a transaction is still running
     *
     * @return
     */
    public fun inTransaction(): Boolean {
        // The Java threw a NullPointerException here too when the database had never been opened
        return db!!.inTransaction()
    }

    /**
     * RunSQL Method
     *
     * @param statement a raw SQL statement
     * @param values    Array of Strings to bind to the statement
     * @return
     */
    public fun runSQL(statement: String, values: ArrayList<Any?>?, transaction: Boolean, returnMode: String): JSObject {
        val retObj = JSObject()
        val db = db
        try {
            if (db != null && db.isOpen && statement.isNotEmpty()) {
                val initChanges = uSqlite.dbChanges(db)
                if (transaction) beginTransaction()
                val response = prepareSQL(statement, values, false, returnMode)
                val lastId = response.getLong("lastId")
                if (lastId != -1L && transaction) commitTransaction()
                val changes = uSqlite.dbChanges(db) - initChanges
                retObj.put("changes", changes)
                retObj.put("lastId", lastId)
                retObj.put("values", response.getJSONArray("values"))
                return retObj
            } else {
                throw Exception("Database not opened")
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        } finally {
            if (db != null && transaction && db.inTransaction()) rollbackTransaction()
        }
    }

    /**
     * PrepareSQL Method
     *
     * @param statement SQL statement
     * @param values SQL Values if any
     * @param fromJson is the statement from importFromJson
     * @param returnMode return mode to handle RETURNING
     * @return JSObject
     * @throws Exception message
     */
    public fun prepareSQL(statement: String, values: ArrayList<Any?>?, fromJson: Boolean, returnMode: String): JSObject {
        val stmtType = statement.trim { it <= ' ' }.split(Regex("\\s+"))[0].uppercase(Locale.getDefault())
        var stmt: SupportSQLiteStatement? = null
        var sqlStmt = statement
        var retMode = returnMode
        var retValues = JSArray()
        val retObject = JSObject()
        var colNames = ""
        if (retMode != "no") {
            retMode = "wA$retMode"
        }
        if (retMode == "no" || retMode.substring(0, minOf(retMode.length, 2)) == "wA") {
            // get the statement and the returning column names
            try {
                val stmtObj = getStmtAndRetColNames(sqlStmt, retMode)
                sqlStmt = stmtObj.getString("stmt", sqlStmt) ?: throw Exception("sqlStmt is null")
                colNames = stmtObj.getString("names", "") ?: ""
            } catch (e: JSONException) {
                throw Exception(e.message)
            }
        }
        try {
            if (!fromJson && stmtType == "DELETE") {
                sqlStmt = deleteSQL(this, sqlStmt, values)
            }
            // The Java threw a NullPointerException here too when the database had never been opened
            val db = db!!
            stmt = db.compileStatement(sqlStmt)
            if (values != null && values.size > 0) {
                val valObj = arrayOfNulls<Any>(values.size)
                for (i in values.indices) {
                    valObj[i] = if (values[i] == null || JSONObject.NULL === values[i]) null else values[i]
                }
                SimpleSQLiteQuery.bind(stmt, valObj)
            }
            val initLastId: Long = uSqlite.dbLastId(db)
            if (stmtType == "INSERT") {
                stmt.executeInsert()
            } else {
                if (retMode.startsWith("wA") && colNames.isNotEmpty() && stmtType == "DELETE") {
                    retValues = getUpdDelReturnedValues(this, sqlStmt, colNames)
                }
                stmt.executeUpdateDelete()
            }
            val lastId: Long = uSqlite.dbLastId(db)
            if (retMode.startsWith("wA") && colNames.isNotEmpty()) {
                if (stmtType == "INSERT") {
                    val tableName = UtilsSQLStatement.extractTableName(sqlStmt)
                    if (tableName != null) {
                        retValues = getInsertReturnedValues(this, colNames, tableName, initLastId, lastId, retMode)
                    }
                } else if (stmtType == "UPDATE") {
                    retValues = getUpdDelReturnedValues(this, sqlStmt, colNames)
                }
            }
            retObject.put("lastId", lastId)
            retObject.put("values", retValues)
            return retObject
        } catch (e: Exception) {
            throw Exception(e.message)
        } finally {
            stmt?.close()
        }
    }

    private fun getStmtAndRetColNames(sqlStmt: String, retMode: String): JSObject {
        val retObj = JSObject()
        val retIsReturning = isReturning(sqlStmt)
        val isReturning = retIsReturning.getBoolean("isReturning")
        val stmt = retIsReturning.getString("stmt")
        val suffix = retIsReturning.getString("names")
        retObj.put("stmt", stmt)
        retObj.put("names", "")

        if (isReturning && retMode.startsWith("wA")) {
            val lowercaseSuffix = suffix?.lowercase(Locale.getDefault()) ?: ""
            val returningIndex = lowercaseSuffix.indexOf("returning")
            if (returningIndex != -1 && suffix != null) {
                val substring = suffix.substring(returningIndex + "returning".length)
                val names = substring.trim { it <= ' ' }
                retObj.put("names", getNames(names))
            }
        }
        return retObj
    }

    private fun getNames(input: String): String {
        val indexSemicolon = input.indexOf(";")
        val indexDoubleDash = input.indexOf("--")
        val indexCommentStart = input.indexOf("/*")

        // Find the minimum index among them
        var minIndex = input.length
        if (indexSemicolon != -1) {
            minIndex = minOf(minIndex, indexSemicolon)
        }
        if (indexDoubleDash != -1) {
            minIndex = minOf(minIndex, indexDoubleDash)
        }
        if (indexCommentStart != -1) {
            minIndex = minOf(minIndex, indexCommentStart)
        }
        return input.substring(0, minIndex).trim { it <= ' ' }
    }

    private fun isReturning(sqlStmt: String): JSObject {
        val retObj = JSObject()

        var stmt = sqlStmt.trim { it <= ' ' }
        if (stmt.endsWith(";")) {
            // Remove the suffix
            stmt = stmt.substring(0, stmt.length - 1).trim { it <= ' ' }
        }
        retObj.put("isReturning", false)
        retObj.put("stmt", sqlStmt)
        retObj.put("names", "")
        val stmtType = sqlStmt.trim { it <= ' ' }.split(Regex("\\s+"))[0].uppercase(Locale.getDefault())

        when (stmtType) {
            "INSERT" -> {
                val valuesIndex = stmt.uppercase(Locale.getDefault()).indexOf("VALUES")
                if (valuesIndex != -1) {
                    var closingParenthesisIndex = -1

                    for (i in stmt.length - 1 downTo valuesIndex) {
                        if (stmt[i] == ')') {
                            closingParenthesisIndex = i
                            break
                        }
                    }
                    if (closingParenthesisIndex != -1) {
                        val stmtString = stmt.substring(0, closingParenthesisIndex + 1).trim { it <= ' ' } + ";"
                        var resultString = stmt.substring(closingParenthesisIndex + 1).trim { it <= ' ' }
                        if (resultString.isNotEmpty() && !resultString.endsWith(";")) {
                            resultString += ";"
                        }
                        if (resultString.lowercase(Locale.getDefault()).contains("returning")) {
                            retObj.put("isReturning", true)
                            retObj.put("stmt", stmtString)
                            retObj.put("names", resultString)
                        }
                    }
                }
                return retObj
            }

            "DELETE", "UPDATE" -> {
                // Same tokens as Java's String.split: trailing empty strings are dropped
                val words = stmt.split(Regex("\\s+")).dropLastWhile { it.isEmpty() }
                val wordsBeforeReturning = ArrayList<String>()
                val returningString = ArrayList<String>()

                var isReturningOutsideMessage = false
                for (word in words) {
                    if (word.lowercase(Locale.getDefault()) == "returning") {
                        isReturningOutsideMessage = true
                        // Include "RETURNING" and the words after it in returningString
                        returningString.add(word)
                        returningString.addAll(wordsAfter(word, words))
                        break
                    }
                    wordsBeforeReturning.add(word)
                }

                if (isReturningOutsideMessage) {
                    val joinedWords = wordsBeforeReturning.joinToString(" ") + ";"
                    var joinedReturningString = returningString.joinToString(" ")
                    if (joinedReturningString.isNotEmpty() && !joinedReturningString.endsWith(";")) {
                        joinedReturningString += ";"
                    }
                    retObj.put("isReturning", true)
                    retObj.put("stmt", joinedWords)
                    retObj.put("names", joinedReturningString)
                }
                return retObj
            }

            else -> return retObj
        }
    }

    private fun wordsAfter(word: String, words: List<String>): List<String> {
        val index = words.indexOf(word)
        if (index == -1) {
            return ArrayList()
        }
        return ArrayList(words.subList(index + 1, words.size))
    }

    private fun getInsertReturnedValues(
        mDB: Database,
        colNames: String,
        tableName: String,
        iLastId: Long,
        lastId: Long,
        rMode: String
    ): JSArray {
        if (iLastId < 0 || colNames.isEmpty()) return JSArray()
        val sLastId = iLastId + 1
        val sbQuery = StringBuilder("SELECT ").append(colNames).append(" FROM ")

        sbQuery.append(tableName).append(" WHERE ").append("rowid ")
        if (rMode == "wAone") {
            sbQuery.append("= ").append(sLastId)
        }
        if (rMode == "wAall") {
            sbQuery.append("BETWEEN ").append(sLastId).append(" AND ").append(lastId)
        }
        sbQuery.append(";")
        return mDB.selectSQL(sbQuery.toString(), ArrayList())
    }

    private fun getUpdDelReturnedValues(mDB: Database, stmt: String, colNames: String): JSArray {
        var retVals = JSArray()
        val tableName = UtilsSQLStatement.extractTableName(stmt)
        val whereClause = UtilsSQLStatement.extractWhereClause(stmt)
        if (whereClause != null && tableName != null) {
            val sbQuery = StringBuilder("SELECT ").append(colNames).append(" FROM ")
            sbQuery.append(tableName).append(" WHERE ").append(whereClause).append(";")
            retVals = mDB.selectSQL(sbQuery.toString(), ArrayList())
        }
        return retVals
    }

    /**
     * DeleteSQL method
     *
     * @param mDB
     * @param statement
     * @param values
     * @return
     * @throws Exception
     */
    public fun deleteSQL(mDB: Database, statement: String, values: ArrayList<Any?>?): String {
        var sqlStmt = statement
        val msg = "DeleteSQL"

        try {
            val isLast = uJson.isLastModified(mDB)
            val isDel = uJson.isSqlDeleted(mDB)

            if (!isLast || !isDel) {
                return sqlStmt
            }

            // Replace DELETE by UPDATE
            // set sql_deleted to 1
            val whereClause =
                UtilsSQLStatement.extractWhereClause(sqlStmt) ?: throw Exception("deleteSQL: cannot find a WHERE clause")

            val tableName =
                UtilsSQLStatement.extractTableName(sqlStmt) ?: throw Exception("deleteSQL: cannot find a WHERE clause")

            val colNames = UtilsSQLStatement.extractColumnNames(whereClause).toTypedArray()

            if (colNames.isEmpty()) {
                throw Exception("deleteSQL: Did not find column names in the WHERE Statement")
            }
            val setStmt = "sql_deleted = 1"

            // Find REFERENCES if any and update the sql_deleted column
            // A null list made the Java fail with a NullPointerException further down this call
            val hasToUpdate = UtilsDelete.findReferencesAndUpdate(mDB, tableName, whereClause, colNames, values!!)

            if (hasToUpdate) {
                val whereStmt = if (whereClause.endsWith(";")) whereClause.substring(0, whereClause.length - 1) else whereClause

                sqlStmt = "UPDATE $tableName SET $setStmt WHERE $whereStmt AND sql_deleted = 0;"
            } else {
                sqlStmt = ""
            }

            return sqlStmt
        } catch (err: Exception) {
            val errmsg = err.message ?: err.toString()
            throw Exception("$msg $errmsg")
        }
    }

    /**
     * SelectSQL Method
     * Query a raw sql statement with or without binding values
     *
     * @param statement
     * @param values
     * @return
     */
    public fun selectSQL(statement: String, values: ArrayList<Any?>): JSArray {
        val retArray = JSArray()
        var c: SQLiteCursor? = null
        val db = db ?: return retArray
        try {
            c = db.query(statement, values.toTypedArray()) as SQLiteCursor
            while (c.moveToNext()) {
                val row = JSObject()
                for (i in 0 until c.columnCount) {
                    val colName = c.getColumnName(i)
                    val index = c.getColumnIndex(colName)
                    when (c.getType(i)) {
                        FIELD_TYPE_STRING -> row.put(colName, c.getString(index))
                        FIELD_TYPE_INTEGER -> row.put(colName, c.getLong(index))
                        FIELD_TYPE_FLOAT -> row.put(colName, c.getDouble(index))
                        FIELD_TYPE_BLOB -> row.put(colName, uSqlite.ByteArrayToJSArray(c.getBlob(index)))
                        FIELD_TYPE_NULL -> row.put(colName, JSONObject.NULL)
                        else -> {}
                    }
                }
                retArray.put(row)
            }
            return retArray
        } catch (e: Exception) {
            throw Exception("in selectSQL cursor " + e.message)
        } finally {
            c?.close()
        }
    }

    /**
     * GetTableNames Method
     * Returned a JSArray of table's name
     *
     * @return
     * @throws Exception
     */
    public fun getTableNames(): JSArray {
        val retArray = JSArray()
        try {
            val tableList = uDrop.getTablesNames(this)
            for (tableName in tableList) {
                retArray.put(tableName)
            }
            return retArray
        } catch (e: Exception) {
            throw Exception("in getTableNames " + e.message)
        }
    }

    /**
     * DeleteDB Method
     * Delete the database file
     *
     * @param dbName
     * @return
     */
    public fun deleteDB(dbName: String) {
        try {
            // open the database
            if (file.exists() && !isOpen) {
                open()
            }
            // close the db
            if (isOpen) {
                close()
            }
            // delete the database
            if (file.exists()) {
                val ret: Boolean = uFile.deleteDatabase(context, dbName)
                if (ret) {
                    isOpen = false
                } else {
                    throw Exception("Failed in deleteDB ")
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed in deleteDB " + e.message)
        }
    }

    /**
     * CreateSyncTable Method
     * create the synchronization table
     *
     * @return
     */
    public fun createSyncTable(): JSObject {
        // check if the table has already been created
        val isExists = uJson.isTableExists(this, "sync_table")
        if (!isExists) {
            val isLastModified = uJson.isLastModified(this)
            val isSqlDeleted = uJson.isSqlDeleted(this)
            if (isLastModified && isSqlDeleted) {
                val date = Date()
                val syncTime = date.time / 1000L
                val statements =
                    arrayOf(
                        "CREATE TABLE IF NOT EXISTS sync_table (" + "id INTEGER PRIMARY KEY NOT NULL," + "sync_date INTEGER);",
                        "INSERT INTO sync_table (sync_date) VALUES ('$syncTime');"
                    )
                try {
                    return execute(statements)
                } catch (e: Exception) {
                    throw Exception(e.message)
                }
            } else {
                throw Exception("No last_modified/sql_deleted columns in tables")
            }
        } else {
            val retObj = JSObject()
            retObj.put("changes", 0)
            return retObj
        }
    }

    /**
     * GetSyncDate method
     * get the synchronization date
     *
     * @return
     * @throws Exception
     */
    public fun getSyncDate(): Long {
        try {
            val isSyncTable = uJson.isTableExists(this, "sync_table")
            if (!isSyncTable) {
                throw Exception("No sync_table available")
            }
            return toJson.getSyncDate(this)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * SetSyncDate Method
     * Set the synchronization date
     *
     * @param syncDate
     * @return
     */
    public fun setSyncDate(syncDate: String) {
        try {
            val isSyncTable = uJson.isTableExists(this, "sync_table")
            if (!isSyncTable) {
                throw Exception("No sync_table available")
            }
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            val date = formatter.parse(syncDate.replace(Regex("Z$"), "+0000"))
            val syncTime = date.time / 1000L
            val statements = arrayOf("UPDATE sync_table SET sync_date = $syncTime WHERE id = 1;")
            val retObj = execute(statements)
            if (retObj.getInteger("changes") != -1) {
                return
            } else {
                throw Exception("changes < 0")
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * Import from Json object
     *
     * @param jsonSQL
     * @return
     */
    public fun importFromJson(jsonSQL: JsonSQLite): JSObject {
        val retObj = JSObject()
        var changes = 0
        try {
            // The Java threw a NullPointerException here too when the database had never been opened
            val db = db!!
            // set Foreign Keys OFF
            db.setForeignKeyConstraintsEnabled(false)

            if (jsonSQL.tables.size > 0) {
                // create the database schema
                changes = fromJson.createDatabaseSchema(this, jsonSQL)
                if (changes != -1) {
                    changes += fromJson.createDatabaseData(this, jsonSQL)
                }
            }
            if (jsonSQL.views.size > 0) {
                changes += fromJson.createViews(this, jsonSQL.views)
            }
            // set Foreign Keys ON
            db.setForeignKeyConstraintsEnabled(true)

            retObj.put("changes", changes)
            return retObj
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    /**
     * Export to JSON Object
     *
     * @param mode
     * @return
     */
    public fun exportToJson(mode: String, isEncrypted: Boolean): JSObject {
        val inJson = JsonSQLite()
        var retObj = JSObject()
        inJson.database = dbName.substring(0, dbName.length - 9)
        inJson.version = version
        inJson.encrypted = encrypted
        inJson.mode = mode
        try {
            val isSyncTable = uJson.isTableExists(this, "sync_table")
            if (isSyncTable) {
                // set the last export date
                val date = Date()
                val syncTime = date.time / 1000L
                toJson.setLastExportDate(this, syncTime)
            } else {
                if (inJson.mode == "partial") {
                    throw Exception("No sync_table available")
                }
            }
            // launch the export process
            val retJson = toJson.createExportObject(this, inJson)
            val keys = retJson.getKeys()
            if (keys.contains("tables")) {
                if (retJson.tables.size > 0) {
                    retObj.put("database", retJson.database)
                    retObj.put("version", retJson.version)
                    retObj.put("encrypted", retJson.encrypted)
                    retObj.put("mode", retJson.mode)
                    retObj.put("tables", retJson.getTablesAsJSObject())
                    if (keys.contains("views") && retJson.views.size > 0) {
                        retObj.put("views", retJson.getViewsAsJSObject())
                    }
                }
            }
            if (encrypted && isEncryption && isEncrypted) {
                retObj.put("encrypted", true)
                retObj.put("overwrite", true)
                val base64Str = UtilsEncryption.encryptJSONObject(context, retObj)
                retObj = JSObject()
                retObj.put("expData", base64Str)
            }

            return retObj
        } catch (e: Exception) {
            Log.e(TAG, "Error: exportToJson " + e.message)
            throw Exception(e.message)
        }
    }

    /**
     * Delete exported rows
     *
     * @throws Exception
     */
    public fun deleteExportedRows() {
        try {
            toJson.delExportedRows(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error: exportToJson " + e.message)
            throw Exception(e.message)
        }
    }

    public companion object {
        private val TAG = Database::class.java.name

        public fun mergeJSONArrays(target: JSONArray, source: JSONArray) {
            for (i in 0 until source.length()) {
                target.put(source.get(i))
            }
        }
    }
}
