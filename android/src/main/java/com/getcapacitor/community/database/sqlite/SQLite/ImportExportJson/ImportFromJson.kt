package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import androidx.sqlite.db.SupportSQLiteDatabase
import com.getcapacitor.community.database.sqlite.NotificationCenter
import com.getcapacitor.community.database.sqlite.SQLite.Database
import com.getcapacitor.community.database.sqlite.SQLite.UtilsDrop
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLite
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

public class ImportFromJson {
    private val uJson = UtilsJson()
    private val uDrop = UtilsDrop()
    private val uSqlite = UtilsSQLite()

    /**
     * Notify progress import event
     */
    public fun notifyImportProgressEvent(msg: String?) {
        val info = hashMapOf<String, Any?>("progress" to "Import: $msg")
        NotificationCenter.defaultCenter().postNotification("importJsonProgress", info)
    }

    /**
     * Create the database schema for import from Json
     */
    public fun createDatabaseSchema(db: Database, jsonSQL: JsonSQLite): Int {
        val changes: Int
        // Unboxed by the Java when it was handed to setVersion
        val version: Int = jsonSQL.version!!
        // The Java dereferenced the handle without a check, so a closed database was a NullPointerException
        val sqliteDb: SupportSQLiteDatabase? = db.db

        // -> update database version
        sqliteDb!!.version = version

        if (jsonSQL.mode == "full") {
            try {
                uDrop.dropAll(db)
            } catch (e: Exception) {
                throw Exception("CreateDatabaseSchema: " + e.message)
            }
        }
        try {
            changes = createSchema(db, jsonSQL)
            notifyImportProgressEvent("Schema creation completed changes: $changes")
            return changes
        } catch (e: Exception) {
            throw Exception("CreateDatabaseSchema: " + e.message)
        }
    }

    /**
     * Create from the Json Object the database schema
     */
    private fun createSchema(mDb: Database, jsonSQL: JsonSQLite): Int {
        var changes = -1
        val db: SupportSQLiteDatabase? = mDb.db
        try {
            if (mDb.isOpen) {
                mDb.beginTransaction()
                // Create a Schema Statement
                val statements = createSchemaStatement(jsonSQL)
                if (statements.size > 0) {
                    // an open database has a handle; without one Java failed here too, inside this try
                    val initChanges = uSqlite.dbChanges(db!!)
                    for (cmd in statements) {
                        db.execSQL(cmd)
                    }
                    changes = uSqlite.dbChanges(db) - initChanges
                    if (changes >= 0) {
                        mDb.commitTransaction()
                    }
                } else {
                    if (jsonSQL.mode == "partial") {
                        changes = 0
                    }
                }
            } else {
                throw Exception("CreateSchema: Database not opened")
            }
        } catch (e: Exception) {
            throw Exception("CreateSchema: " + e.message)
        } finally {
            if (db != null && db.inTransaction()) mDb.rollbackTransaction()
        }
        return changes
    }

    /**
     * Create Schema Statements
     */
    private fun createSchemaStatement(jsonSQL: JsonSQLite): ArrayList<String> {
        val statements = ArrayList<String>()
        // Loop through Tables
        for (table in jsonSQL.tables) {
            val tableName: String? = table.name
            val mSchema = table.schema
            if (mSchema.size > 0) {
                // create table schema
                statements.addAll(createTableSchema(mSchema, tableName))
            }
            val mIndexes = table.indexes
            if (mIndexes.size > 0) {
                // create table indexes
                statements.addAll(createTableIndexes(mIndexes, tableName))
            }
            val mTriggers = table.triggers
            if (mTriggers.size > 0) {
                // create table triggers
                statements.addAll(createTableTriggers(mTriggers, tableName))
            }
        }
        return statements
    }

    /**
     * Create table schema from Json object
     */
    private fun createTableSchema(mSchema: ArrayList<JsonColumn>, tableName: String?): ArrayList<String> {
        val statements = ArrayList<String>()
        val stmt = StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (")
        var isLastModified = false
        var isSqlDeleted = false
        for (j in mSchema.indices) {
            val separator = if (j == mSchema.size - 1) "" else ","
            val column: String? = mSchema[j].column
            val foreignkey: String? = mSchema[j].foreignkey
            val constraint: String? = mSchema[j].constraint
            val value: String? = mSchema[j].value
            if (column != null) {
                stmt.append(column).append(" ").append(value).append(separator)
                if (column == "last_modified") {
                    isLastModified = true
                }
                if (column == "sql_deleted") {
                    isSqlDeleted = true
                }
            } else if (foreignkey != null) {
                stmt.append("FOREIGN KEY (").append(foreignkey).append(") ").append(value).append(separator)
            } else if (constraint != null) {
                stmt.append("CONSTRAINT ").append(constraint).append(" ").append(value).append(separator)
            }
        }
        stmt.append(");")
        statements.add(stmt.toString())
        if (isLastModified && isSqlDeleted) {
            // create trigger last_modified associated with the table
            val stmtTrigger =
                StringBuilder("CREATE TRIGGER IF NOT EXISTS ")
                    .append(tableName)
                    .append("_trigger_last_modified")
                    .append(" AFTER UPDATE ON ")
                    .append(tableName)
                    .append(" FOR EACH ROW ")
                    .append("WHEN NEW.last_modified <= OLD.last_modified BEGIN ")
                    .append("UPDATE ")
                    .append(tableName)
                    .append(" SET last_modified = (strftime('%s','now')) ")
                    .append("WHERE id=NEW.id; ")
                    .append("END;")
                    .toString()
            statements.add(stmtTrigger)
        }
        return statements
    }

    /**
     * Create table indexes from Json object
     */
    private fun createTableIndexes(mIndexes: ArrayList<JsonIndex>, tableName: String?): ArrayList<String> {
        val statements = ArrayList<String>()
        for (index in mIndexes) {
            val mMode: String? = index.mode
            // a null mode was a NullPointerException in Java
            val mUnique = if (mMode!!.isNotEmpty()) "$mMode " else ""
            val name: String? = index.name
            val value: String? = index.value
            val stmt =
                StringBuilder("CREATE ")
                    .append(mUnique)
                    .append("INDEX IF NOT EXISTS ")
                    .append(name)
                    .append(" ON ")
                    .append(tableName)
                    .append(" (")
                    .append(value)
                    .append(");")
                    .toString()
            statements.add(stmt)
        }
        return statements
    }

    /**
     * Create table triggers from Json object
     */
    private fun createTableTriggers(mTriggers: ArrayList<JsonTrigger>, tableName: String?): ArrayList<String> {
        val statements = ArrayList<String>()
        for (trigger in mTriggers) {
            val rawTimeEvent: String? = trigger.timeevent
            // a trigger without timeevent was a NullPointerException in Java
            var timeEvent: String = rawTimeEvent!!
            if (timeEvent.uppercase(Locale.getDefault()).endsWith(" ON")) {
                timeEvent = timeEvent.substring(0, timeEvent.length - 3)
            }

            val name: String? = trigger.name
            val condition: String? = trigger.condition
            val logic: String? = trigger.logic
            val sBuilder =
                StringBuilder("CREATE TRIGGER IF NOT EXISTS ")
                    .append(name)
                    .append(" ")
                    .append(timeEvent)
                    .append(" ON ")
                    .append(tableName)
                    .append(" ")
            if (condition != null) {
                sBuilder.append(condition).append(" ")
            }
            sBuilder.append(logic)
            statements.add(sBuilder.toString())
        }
        return statements
    }

    /**
     * Create the database tables data for import from Json
     */
    public fun createDatabaseData(mDb: Database, jsonSQL: JsonSQLite): Int {
        var isValues = false
        var changes = -1
        val db: SupportSQLiteDatabase? = mDb.db
        try {
            if (mDb.isOpen) {
                // an open database has a handle; without one Java failed here too, inside this try
                val initChanges = uSqlite.dbChanges(db!!)
                mDb.beginTransaction()
                val tables = jsonSQL.tables
                for (i in tables.indices) {
                    if (tables[i].values.size > 0) {
                        isValues = true
                        try {
                            val tableName: String? = tables[i].name
                            createTableData(mDb, jsonSQL.mode, tables[i].values, tableName)
                            val msg = "Table $tableName data creation completed ${i + 1}/${tables.size} ..."
                            notifyImportProgressEvent(msg)
                        } catch (e: Exception) {
                            throw Exception("CreateDatabaseData: " + e.message)
                        }
                    }
                }
                if (!isValues) {
                    changes = 0
                } else {
                    changes = uSqlite.dbChanges(db) - initChanges
                    if (changes >= 0) {
                        mDb.commitTransaction()
                        notifyImportProgressEvent("Tables data creation completed changes: $changes")
                    }
                }
            } else {
                throw Exception("CreateDatabaseData: Database not opened")
            }
        } catch (e: Exception) {
            throw Exception("CreateDatabaseData: " + e.message)
        } finally {
            if (db != null && db.inTransaction()) mDb.rollbackTransaction()
        }
        return changes
    }

    /**
     * Create table data from the Json Object
     */
    private fun createTableData(mDb: Database, mode: String?, values: ArrayList<ArrayList<Any?>>, tableName: String?) {
        // Check if table exists
        val isTable = uJson.isTableExists(mDb, tableName)
        if (!isTable) {
            throw Exception("createTableData: Table " + tableName + "does not exist")
        }
        // Get the Column's Name and Types
        try {
            val tableNamesTypes = uJson.getTableColumnNamesTypes(mDb, tableName)
            if (tableNamesTypes.length() == 0) {
                throw Exception("CreateTableData: no column names & types returned")
            }
            val tColNames =
                if (tableNamesTypes.has("names")) {
                    uJson.getColumnNames(tableNamesTypes.get("names"))
                } else {
                    throw Exception("GetValues: Table $tableName no names")
                }
            val tColTypes =
                if (tableNamesTypes.has("types")) {
                    uJson.getColumnNames(tableNamesTypes.get("types"))
                } else {
                    throw Exception("GetValues: Table $tableName no types")
                }
            if (isBlob(tColTypes)) {
                // Old process flow
                oldProcessFow(mDb, values, tableName, tColNames, tColTypes, mode)
            } else {
                // New process flow
                newProcessFlow(mDb, values, tableName, tColNames)
            }
        } catch (e: Exception) {
            throw Exception("CreateTableData: " + e.message)
        }
    }

    /**
     * Use the old process flow for INSERT, UPDATE and DELETE
     * One by one row values
     */
    private fun oldProcessFow(
        mDb: Database,
        values: ArrayList<ArrayList<Any?>>,
        tableName: String?,
        tColNames: ArrayList<String>,
        tColTypes: ArrayList<String>,
        mode: String?
    ) {
        try {
            // Loop on table's value
            for (j in values.indices) {
                // Check the row number of columns
                var row = createRowValues(values[j])
                //
                // Create INSERT or UPDATE Statements
                val stmt = createRowStatement(mDb, tColNames, row, j, tableName, mode)
                val isRun = checkUpdate(mDb, stmt, row, tableName, tColNames)
                if (isRun) {
                    // load the values
                    if (stmt.substring(0, 6).uppercase(Locale.getDefault()) == "DELETE") {
                        row = ArrayList()
                    }
                    val retObj = mDb.prepareSQL(stmt, row, true, "no")
                    val lastId = retObj.getLong("lastId")
                    if (lastId < 0) {
                        throw Exception("CreateTableData: lastId < 0")
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception("oldProcessFlow: " + e.message)
        }
    }

    private fun createRowValues(row: ArrayList<Any?>): ArrayList<Any?> {
        try {
            // Iterate over the ArrayList and check for JSONArray objects
            for (i in row.indices) {
                val obj = row[i]
                if (obj is JSONArray) {
                    // Replace the JSONArray object with the corresponding byte[] in the ArrayList
                    row[i] = jsonArrayToByteArray(obj)
                }
            }
            return row
        } catch (e: Exception) {
            throw Exception("createRowValues: " + e.message)
        }
    }

    private fun jsonArrayToByteArray(jsonArray: JSONArray): ByteArray {
        val byteArray = ByteArray(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            byteArray[i] = jsonArray.getInt(i).toByte()
        }
        return byteArray
    }

    /**
     * Use the new process flow for INSERT, UPDATE and DELETE
     */
    private fun newProcessFlow(mDb: Database, values: ArrayList<ArrayList<Any?>>, tableName: String?, tColNames: ArrayList<String>) {
        try {
            val retObjStrs = generateInsertAndDeletedStrings(tColNames, values)
            // Create the statement for INSERT
            val namesString = uJson.convertToString(tColNames, ',')
            if (retObjStrs.has("insert")) {
                val stmtInsert =
                    StringBuilder("INSERT OR REPLACE INTO ")
                        .append(tableName)
                        .append("(")
                        .append(namesString)
                        .append(") ")
                        .append(retObjStrs.get("insert"))
                        .append(";")
                        .toString()
                val retObj = mDb.prepareSQL(stmtInsert, ArrayList(), true, "no")
                val lastId = retObj.getLong("lastId")
                if (lastId < 0) {
                    throw Exception("CreateTableData: INSERT lastId < 0")
                }
            }
            if (retObjStrs.has("delete")) {
                val stmtDelete =
                    StringBuilder("DELETE FROM ")
                        .append(tableName)
                        .append(" WHERE ")
                        .append(tColNames[0])
                        .append(" ")
                        .append(retObjStrs.get("delete"))
                        .append(";")
                        .toString()
                val retObj = mDb.prepareSQL(stmtDelete, ArrayList(), true, "no")
                val lastId = retObj.getLong("lastId")
                if (lastId < 0) {
                    throw Exception("newProcessFlow: INSERT lastId < 0")
                }
            }
        } catch (e: Exception) {
            throw Exception("newProcessFlow: " + e.message)
        }
    }

    /**
     * Check if there is a BLOB types
     */
    private fun isBlob(tColTypes: ArrayList<String>): Boolean = tColTypes.any { it.equals("BLOB", ignoreCase = true) }

    /**
     * Create the Row Statement to load the data
     */
    private fun createRowStatement(
        mDb: Database,
        tColNames: ArrayList<String>,
        row: ArrayList<Any?>,
        j: Int,
        tableName: String?,
        mode: String?
    ): String {
        val msg = "CreateRowStatement: Table$tableName values row"
        if (tColNames.size != row.size || row.size == 0 || tColNames.size == 0) {
            throw Exception("$msg$j not correct length")
        }

        val retIsIdExists = uJson.isIdExists(mDb, tableName, tColNames[0], row[0])
        var stmt = ""
        // Create INSERT or UPDATE Statements
        if (mode == "full" || (mode == "partial" && !retIsIdExists)) {
            // Insert
            val namesString = uJson.convertToString(tColNames, ',')
            val questionMarkString = uJson.createQuestionMarkString(tColNames.size)
            if (questionMarkString.isEmpty()) {
                throw Exception(msg + j + "questionMarkString is empty")
            }
            stmt =
                StringBuilder("INSERT INTO ")
                    .append(tableName)
                    .append("(")
                    .append(namesString)
                    .append(")")
                    .append(" VALUES (")
                    .append(questionMarkString)
                    .append(");")
                    .toString()
        } else {
            var isUpdate = true
            val idxDelete = tColNames.indexOf("sql_deleted")
            if (idxDelete >= 0) {
                if (row[idxDelete] == 1) {
                    // Delete
                    isUpdate = false
                    // The Java tested the type of the key column's name, which is always a String,
                    // so the key value has always been quoted.
                    stmt =
                        StringBuilder("DELETE FROM ")
                            .append(tableName)
                            .append(" WHERE ")
                            .append(tColNames[0])
                            .append(" = ")
                            .append("'")
                            .append(row[0])
                            .append("';")
                            .toString()
                }
            }
            if (isUpdate) {
                // Update
                val setString = uJson.setNameForUpdate(tColNames)
                if (setString.isEmpty()) {
                    throw Exception(msg + j + "setString is empty")
                }
                // Quoted for the same reason as in the DELETE above
                stmt =
                    StringBuilder("UPDATE ")
                        .append(tableName)
                        .append(" SET ")
                        .append(setString)
                        .append(" WHERE ")
                        .append(tColNames[0])
                        .append(" = ")
                        .append("'")
                        .append(row[0])
                        .append("';")
                        .toString()
            }
        }
        return stmt
    }

    /**
     * Check when UPDATE if the values are updated
     */
    private fun checkUpdate(
        mDb: Database,
        stmt: String,
        values: ArrayList<Any?>,
        tableName: String?,
        tColNames: ArrayList<String>
    ): Boolean {
        var isRun = true
        if (stmt.substring(0, 6) == "UPDATE") {
            val sbQuery = StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ").append(tColNames[0])

            if (values[0] is String) {
                sbQuery.append(" = '").append(values[0]).append("';")
            } else {
                sbQuery.append(" = ").append(values[0]).append(";")
            }
            val query = sbQuery.toString()

            try {
                val resValues = uJson.getValues(mDb, query, tableName)
                if (resValues.size > 0) {
                    isRun = checkValues(values, resValues[0])
                } else {
                    throw Exception("CheckUpdate: CheckUpdate statement returns nothing")
                }
            } catch (e: Exception) {
                throw Exception("CheckUpdate: " + e.message)
            }
        }
        return isRun
    }

    /**
     * Check Values
     */
    private fun checkValues(values: ArrayList<Any?>, nValues: ArrayList<Any?>): Boolean {
        if (values.size > 0 && nValues.size > 0 && values.size == nValues.size) {
            for (i in values.indices) {
                val value = values[i]
                val nValue = nValues[i]
                if (nValue is String) {
                    if (value != nValue) {
                        return true
                    }
                } else if (nValue is Long && value is Int) {
                    if (value.toLong() != nValue) {
                        return true
                    }
                } else if (nValue is Double && value is Int) {
                    if (value.toDouble() != nValue) {
                        return true
                    }
                } else {
                    // The Java compared these by reference (`!=` on Object), and so does this
                    if (value !== nValue) {
                        return true
                    }
                }
            }
            return false
        } else {
            throw Exception("CheckValues: Both arrays not the same length")
        }
    }

    /**
     * GenerateInsertAndDeletedStrings
     */
    private fun generateInsertAndDeletedStrings(tColNames: ArrayList<String>, values: ArrayList<ArrayList<Any?>>): JSONObject {
        val retObj = JSONObject()
        val insertValues = StringBuilder()
        val deletedIds = StringBuilder()

        for (rowIndex in values) {
            val colIndex = tColNames.indexOf("sql_deleted")

            // Check if the column "sql_deleted" is 0 (a value that is not an Int fails the cast, as in Java)
            if (colIndex == -1 || rowIndex[colIndex] as Int == 0) {
                val formattedRow =
                    rowIndex.joinToString(", ") { item ->
                        if (item is String) {
                            "'" + item.replace("'", "''") + "'"
                        } else {
                            item.toString()
                        }
                    }
                insertValues.append("(").append(formattedRow).append("), ")
            } else if (rowIndex[colIndex] as Int == 1) {
                if (rowIndex[0] is String) {
                    deletedIds.append("'").append(rowIndex[0]).append("', ")
                } else {
                    deletedIds.append(rowIndex[0]).append(", ")
                }
            }
        }

        // Remove the trailing comma and space from insertValues and deletedIds
        if (insertValues.isNotEmpty()) {
            insertValues.setLength(insertValues.length - 2) // Remove trailing comma and space
            insertValues.insert(0, "VALUES ")
        }
        if (deletedIds.isNotEmpty()) {
            deletedIds.setLength(deletedIds.length - 2) // Remove trailing comma and space
            deletedIds.insert(0, "IN (")
            deletedIds.append(")")
        }
        if (insertValues.isNotEmpty()) {
            retObj.put("insert", insertValues.toString())
        }
        if (deletedIds.isNotEmpty()) {
            retObj.put("delete", deletedIds.toString())
        }
        return retObj
    }

    /**
     * Create from the Json Object the database views
     */
    public fun createViews(mDb: Database, views: ArrayList<JsonView>): Int {
        var changes = 0
        val db: SupportSQLiteDatabase? = mDb.db
        try {
            if (mDb.isOpen && views.size > 0) {
                mDb.beginTransaction()
                // Create Views
                // an open database has a handle; without one Java failed here too, inside this try
                val initChanges = uSqlite.dbChanges(db!!)
                for (view in views) {
                    val name: String? = view.name
                    val value: String? = view.value
                    // a null name or value was a NullPointerException in Java, caught below like this one
                    if (name!!.isNotEmpty() && value!!.isNotEmpty()) {
                        db.execSQL("CREATE VIEW IF NOT EXISTS $name AS $value ;")
                    } else {
                        throw Exception("CreateViews: no name and value")
                    }
                }
                changes = uSqlite.dbChanges(db) - initChanges
                if (changes >= 0) {
                    mDb.commitTransaction()
                }
            } else {
                throw Exception("CreateViews: Database not opened")
            }
        } catch (e: Exception) {
            throw Exception("CreateViews: " + e.message)
        } finally {
            if (db != null && db.inTransaction()) mDb.rollbackTransaction()
        }
        return changes
    }
}
