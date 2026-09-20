package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.community.database.sqlite.NotificationCenter
import com.getcapacitor.community.database.sqlite.SQLite.Database
import com.getcapacitor.community.database.sqlite.SQLite.UtilsDrop
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONException

public class ExportToJson {
    private val uJson = UtilsJson()
    private val uDrop = UtilsDrop()

    /**
     * Notify progress export event
     * @param msg message to notify
     */
    public fun notifyExportProgressEvent(msg: String) {
        val message = "Export: $msg"
        val info = HashMap<String, Any?>()
        info["progress"] = message
        NotificationCenter.defaultCenter().postNotification("exportJsonProgress", info)
    }

    /**
     * GetLastExportDate method
     * get the last export date
     *
     * @param mDb Database
     * @return Long lastExportDate
     * @throws Exception message
     */
    public fun getLastExportDate(mDb: Database): Long {
        var lastExportDate: Long = -1
        val stmt = "SELECT sync_date FROM sync_table WHERE id = 2;"

        try {
            val isSyncTable = uJson.isTableExists(mDb, "sync_table")
            if (!isSyncTable) {
                throw Exception("GetSyncDate: No sync_table available")
            }
            val retQuery = mDb.selectSQL(stmt, ArrayList())
            val lQuery = retQuery.toList<JSObject>()
            if (lQuery.size == 1) {
                val syncDate = lQuery[0].getLong("sync_date")
                if (syncDate > 0) lastExportDate = syncDate
            }
            return lastExportDate
        } catch (e: Exception) {
            throw Exception("GetSyncDate: " + e.message)
        }
    }

    public fun setLastExportDate(mDb: Database, sTime: Long) {
        try {
            val isSyncTable = uJson.isTableExists(mDb, "sync_table")
            if (!isSyncTable) {
                throw Exception("SetLastExportDate: No sync_table available")
            }
            val lastExportDate = getLastExportDate(mDb)
            val stmt =
                if (lastExportDate > 0) {
                    "UPDATE sync_table SET sync_date = $sTime WHERE id = 2;"
                } else {
                    "INSERT INTO sync_table (sync_date) VALUES ($sTime);"
                }
            val retObj = mDb.prepareSQL(stmt, ArrayList(), false, "no")
            val lastId = retObj.getLong("lastId")
            if (lastId < 0) {
                throw Exception("SetLastExportDate: lastId < 0")
            }
        } catch (e: Exception) {
            throw Exception("SetLastExportDate: " + e.message)
        }
    }

    /**
     * Delete Exported Rows
     * @throws Exception message
     */
    public fun delExportedRows(mDb: Database) {
        try {
            // check if 'sync_table' exists
            val isSyncTable = uJson.isTableExists(mDb, "sync_table")
            if (!isSyncTable) {
                throw Exception("DelExportedRows: No sync_table available")
            }
            // get the last export date
            val lastExportDate = getLastExportDate(mDb)
            if (lastExportDate < 0) {
                throw Exception("DelExportedRows: No last exported date available")
            }
            // get the table' name list
            val tables = uDrop.getTablesNames(mDb)
            if (tables.isEmpty()) {
                throw Exception("DelExportedRows: No table's names returned")
            }
            // Loop through the tables
            for (table in tables) {
                // define the delete statement
                val delStmt = "DELETE FROM $table WHERE sql_deleted = 1 AND last_modified < $lastExportDate;"
                val retObj = mDb.prepareSQL(delStmt, ArrayList(), true, "no")
                val lastId = retObj.getLong("lastId")
                if (lastId < 0) {
                    throw Exception("SetLastExportDate: lastId < 0")
                }
            }
        } catch (e: Exception) {
            throw Exception("DelExportedRows: " + e.message)
        }
    }

    /**
     * Create Export Json Object from Database (Schema, Data)
     * @param db Database
     * @param sqlObj Json SQLite Object
     * @return JsonSQLite
     */
    public fun createExportObject(db: Database, sqlObj: JsonSQLite): JsonSQLite {
        val retObj = JsonSQLite()
        val views = ArrayList<JsonView>()
        try {
            // Get Views
            var stmtV = "SELECT name,sql FROM sqlite_master WHERE "
            stmtV += "type = 'view' AND name NOT LIKE 'sqlite_%';"
            val resViews = db.selectSQL(stmtV, ArrayList())
            if (resViews.length() > 0) {
                for (i in 0 until resViews.length()) {
                    val oView = resViews.getJSONObject(i)
                    val v = JsonView()
                    var value = oView.get("sql") as String
                    value = value.substring(value.indexOf("AS ") + 3)
                    v.name = oView.get("name") as String
                    v.value = value
                    views.add(v)
                }
            }
            // Get Tables
            var stmt = "SELECT name,sql FROM sqlite_master WHERE "
            stmt += "type = 'table' AND name NOT LIKE 'sqlite_%' AND "
            stmt += "name NOT LIKE 'android_%' AND "
            stmt += "name NOT LIKE 'sync_table';"
            val resTables = db.selectSQL(stmt, ArrayList())
            if (resTables.length() == 0) {
                throw Exception("CreateExportObject: table's names failed")
            }
            val isSyncTable = uJson.isTableExists(db, "sync_table")
            if (!isSyncTable && sqlObj.mode == "partial") {
                throw Exception("No sync_table available")
            }

            val tables =
                when (sqlObj.mode) {
                    "partial" -> getTablesPartial(db, resTables)
                    "full" -> getTablesFull(db, resTables)
                    else -> throw Exception("CreateExportObject: expMode " + sqlObj.mode + " not defined")
                }
            if (tables.isNotEmpty()) {
                retObj.database = sqlObj.database
                retObj.version = sqlObj.version
                retObj.encrypted = sqlObj.encrypted
                retObj.mode = sqlObj.mode
                retObj.tables = tables
                if (views.isNotEmpty()) {
                    retObj.views = views
                }
            }
            return retObj
        } catch (e: Exception) {
            throw Exception("CreateExportObject: " + e.message)
        }
    }

    /**
     * get Tables when Mode is Full
     * @param mDb Database
     * @param resTables JSArray
     * @return ArrayList of JsonTable
     * @throws Exception message
     */
    private fun getTablesFull(mDb: Database, resTables: JSArray): ArrayList<JsonTable> {
        val tables = ArrayList<JsonTable>()
        try {
            // Loop through tables
            val lTables = resTables.toList<JSObject>()
            for (i in lTables.indices) {
                val tableName =
                    if (lTables[i].has("name")) {
                        lTables[i].getString("name")
                    } else {
                        throw Exception("GetTablesFull: no name")
                    } ?: throw Exception("GetTablesFull: no name")

                val sqlStmt =
                    if (lTables[i].has("sql")) {
                        lTables[i].getString("sql")
                    } else {
                        throw Exception("GetTablesFull: no sql")
                    }
                val table = JsonTable()
                // create Table's Schema
                // a null sql threw a NullPointerException here in Java as well
                val schema = getSchema(sqlStmt!!)
                if (schema.isEmpty()) {
                    throw Exception("GetTablesFull: no Schema returned")
                }
                // check schema validity
                uJson.checkSchemaValidity(schema)

                // create Table's indexes if any
                val indexes = getIndexes(mDb, tableName)
                if (indexes.isNotEmpty()) {
                    // check indexes validity
                    uJson.checkIndexesValidity(indexes)
                }
                // create Table's triggers if any
                val triggers = getTriggers(mDb, tableName)
                if (triggers.isNotEmpty()) {
                    // check triggers validity
                    uJson.checkTriggersValidity(triggers)
                }

                // create Table's Data
                val query = "SELECT * FROM $tableName;"
                val values = uJson.getValues(mDb, query, tableName)

                table.name = tableName
                table.schema = schema
                if (indexes.isNotEmpty()) {
                    table.indexes = indexes
                }
                if (triggers.isNotEmpty()) {
                    table.triggers = triggers
                }
                var msg = "Full: Table $tableName schema export completed"
                msg += " " + (i + 1) + "/" + lTables.size + " ..."
                notifyExportProgressEvent(msg)
                if (values.isNotEmpty()) {
                    table.values = values
                }
                if (table.getKeys().size <= 1) {
                    throw Exception("GetTablesFull: table $tableName is not a jsonTable")
                }
                tables.add(table)
                msg = "Full: Table $tableName data export completed"
                msg += " " + (i + 1) + "/" + lTables.size + " ..."
                notifyExportProgressEvent(msg)
            }
            notifyExportProgressEvent("Full: Table's export completed")
            return tables
        } catch (e: Exception) {
            notifyExportProgressEvent("Full: Table's export failed")
            throw Exception("GetTablesFull: " + e.message)
        }
    }

    /**
     * Modify ',' by '§' for Embedded Parentheses
     * @param sqlStmt SQLite statement
     * @return String
     */
    private fun modEmbeddedParentheses(sqlStmt: String): String {
        val oPars = getIndices(sqlStmt, "(")
        val cPars = getIndices(sqlStmt, ")")
        if (oPars.size != cPars.size) {
            throw Exception("ModEmbeddedParentheses: Not same number of opening and closing parentheses")
        }
        if (oPars.isEmpty()) return sqlStmt
        val resStmt = StringBuilder(sqlStmt.substring(0, oPars[0] - 1))
        var i = 0
        while (i < oPars.size) {
            val str: String
            if (i < oPars.size - 1) {
                if (oPars[i + 1] < cPars[i]) {
                    str = sqlStmt.substring(oPars[i] - 1, cPars[i + 1])
                    i++
                } else {
                    str = sqlStmt.substring(oPars[i] - 1, cPars[i])
                }
            } else {
                str = sqlStmt.substring(oPars[i] - 1, cPars[i])
            }
            resStmt.append(str.replace(",", "§"))
            if (i < oPars.size - 1) {
                resStmt.append(sqlStmt.substring(cPars[i], oPars[i + 1] - 1))
            }
            i++
        }
        resStmt.append(sqlStmt.substring(cPars[cPars.size - 1]))
        return resStmt.toString()
    }

    private fun getIndices(textString: String, search: String): List<Int> {
        val indexes = ArrayList<Int>()

        var index = 0
        while (index != -1) {
            index = textString.indexOf(search, index)
            if (index != -1) {
                indexes.add(index)
                index++
            }
        }
        return indexes
    }

    /**
     * Get Schema
     * @param sqlStmt SQLite statement
     * @return ArrayList<JsonColumn>
     */
    private fun getSchema(sqlStmt: String): ArrayList<JsonColumn> {
        val msg = "GetSchema: "
        val schema = ArrayList<JsonColumn>()
        // get the sqlStmt between the parenthesis sqlStmt
        var stmt = sqlStmt.substring(sqlStmt.indexOf("(") + 1, sqlStmt.lastIndexOf(")"))
        // check if there is other parenthesis and replace the ',' by '§'
        try {
            stmt = modEmbeddedParentheses(stmt)
            val sch = javaSplit(stmt, ",")
            // for each element of the array split the
            // first word as key
            for (s in sch) {
                val sc = s.replace("\n", "").trim { it <= ' ' }
                val row = javaSplit(sc, "\\s+", 2)
                val jsonRow = JsonColumn()
                val uppercasedValue = row[0].uppercase(Locale.getDefault())
                val oPar: Int
                val cPar: Int
                when (uppercasedValue) {
                    "FOREIGN" -> {
                        oPar = sc.indexOf("(")
                        cPar = sc.indexOf(")")
                        val fk = sc.substring(oPar + 1, cPar)
                        row[0] = fk.replace("§", ",")
                        row[1] = sc.substring(cPar + 2)
                        jsonRow.foreignkey = row[0]
                    }

                    "PRIMARY", "UNIQUE" -> {
                        val prefix = if (uppercasedValue == "PRIMARY") "CPK_" else "CUN_"
                        oPar = sc.indexOf("(")
                        cPar = sc.indexOf(")")
                        val pk = sc.substring(oPar + 1, cPar)
                        row[0] = prefix + pk.replace("§", "_")
                        row[0] = row[0].replace("_ ", "_")
                        row[1] = sc.substring(0, cPar + 1)
                        jsonRow.constraint = row[0]
                    }

                    "CONSTRAINT" -> {
                        val tRow = javaSplit(row[1].trim { it <= ' ' }, " ", 2)
                        row[0] = tRow[0]
                        jsonRow.constraint = row[0]
                        row[1] = tRow[1]
                    }

                    else -> jsonRow.column = row[0]
                }
                jsonRow.value = row[1].replace("§", ",")
                schema.add(jsonRow)
            }
            return schema
        } catch (e: JSONException) {
            throw Exception(msg + e.message)
        }
    }

    /**
     * Get Indexes
     * @param mDb Database
     * @param tableName table name
     * @return ArrayList<JsonIndex>
     * @throws Exception message
     */
    private fun getIndexes(mDb: Database, tableName: String): ArrayList<JsonIndex> {
        val msg = "GetIndexes: "
        val indexes = ArrayList<JsonIndex>()
        var stmt = "SELECT name,tbl_name,sql FROM "
        stmt += "sqlite_master WHERE "
        stmt += "type = 'index' AND tbl_name = '$tableName"
        stmt += "' AND sql NOTNULL;"
        try {
            val retIndexes = mDb.selectSQL(stmt, ArrayList())
            val lIndexes = retIndexes.toList<JSObject>()
            for (j in lIndexes.indices) {
                val jsonRow = JsonIndex()
                if (lIndexes[j].getString("tbl_name") == tableName) {
                    // a null name threw a NullPointerException in Java as well, later on in JsonIndex.getKeys
                    jsonRow.name = lIndexes[j].getString("name")!!
                    val sql = lIndexes[j].getString("sql")
                    if (sql != null && sql.contains("UNIQUE")) {
                        jsonRow.mode = "UNIQUE"
                    }
                    if (sql != null) {
                        val oPar = sql.lastIndexOf("(")
                        val cPar = sql.lastIndexOf(")")
                        jsonRow.value = sql.substring(oPar + 1, cPar)
                        indexes.add(jsonRow)
                    } else {
                        throw Exception(msg + "sql statement is null")
                    }
                } else {
                    throw Exception(msg + "table name doesn't match")
                }
            }
            return indexes
        } catch (e: JSONException) {
            throw Exception(msg + e.message)
        }
    }

    /**
     * Get Triggers
     * @param mDb Database
     * @param tableName table name
     * @return ArrayList<JsonTrigger>
     * @throws Exception message
     */
    private fun getTriggers(mDb: Database, tableName: String): ArrayList<JsonTrigger> {
        val msg = "Error: getTriggers "
        val triggers = ArrayList<JsonTrigger>()
        var stmt = "SELECT name,tbl_name,sql FROM "
        stmt += "sqlite_master WHERE "
        stmt += "type = 'trigger' AND tbl_name = '$tableName"
        stmt += "' AND sql NOTNULL;"
        try {
            val retTriggers = mDb.selectSQL(stmt, ArrayList())
            val lTriggers = retTriggers.toList<JSObject>()
            for (j in lTriggers.indices) {
                val jsonRow = JsonTrigger()
                if (lTriggers[j].getString("tbl_name") == tableName) {
                    val name = lTriggers[j].getString("name")
                    val sql = lTriggers[j].getString("sql")
                    if (sql == null || name == null) {
                        throw Exception(msg + "sql statement or name is null")
                    }
                    // the names are used as regular expressions, as Java's String.split does
                    var sqlArr = javaSplit(sql, name)
                    if (sqlArr.size != 2) {
                        throw Exception(msg + "sql split name does not return 2 values")
                    }
                    if (!sqlArr[1].contains(tableName)) {
                        throw Exception(msg + "sql split does not contains " + tableName)
                    }
                    var timeEvent = javaSplit(sqlArr[1], tableName)[0].trim { it <= ' ' }
                    sqlArr = javaSplit(sqlArr[1], "$timeEvent $tableName")
                    if (sqlArr.size != 2) {
                        throw Exception(msg + "sql split tableName does not return 2 values")
                    }
                    var condition = ""
                    val logic: String
                    if (!sqlArr[1].trim { it <= ' ' }.substring(0, 5).equals("BEGIN", ignoreCase = true)) {
                        sqlArr = javaSplit(sqlArr[1].trim { it <= ' ' }, "BEGIN")
                        if (sqlArr.size != 2) {
                            throw Exception(msg + "sql split BEGIN does not return 2 values")
                        }
                        condition = sqlArr[0].trim { it <= ' ' }
                        logic = "BEGIN" + sqlArr[1]
                    } else {
                        logic = sqlArr[1].trim { it <= ' ' }
                    }
                    if (timeEvent.uppercase(Locale.getDefault()).endsWith(" ON")) {
                        timeEvent = timeEvent.substring(0, timeEvent.length - 3)
                    }
                    jsonRow.name = name
                    jsonRow.timeevent = timeEvent
                    jsonRow.logic = logic
                    if (condition.isNotEmpty()) jsonRow.condition = condition
                    triggers.add(jsonRow)
                } else {
                    throw Exception(msg + "table name doesn't match")
                }
            }
            return triggers
        } catch (e: JSONException) {
            throw Exception(msg + e.message)
        }
    }

    /**
     * Get Tables when Mode is Partial
     * @param mDb Database
     * @param resTables  tables
     * @return ArrayList<JsonTable>
     * @throws Exception message
     */
    private fun getTablesPartial(mDb: Database, resTables: JSArray): ArrayList<JsonTable> {
        val tables = ArrayList<JsonTable>()

        try {
            // Get the syncDate and the Modified Tables
            val partialModeData = getPartialModeData(mDb, resTables)
            val syncDate =
                if (partialModeData.has("syncDate")) {
                    partialModeData.getLong("syncDate")
                } else {
                    throw Exception("GetTablesPartial: no syncDate")
                }
            val modTables =
                if (partialModeData.has("modTables")) {
                    partialModeData.getJSObject("modTables")
                } else {
                    throw Exception("GetTablesPartial: no modTables")
                } ?: throw Exception("GetTablesPartial: no modTables")
            val modTablesKeys = uJson.getJSObjectKeys(modTables)

            // Loop trough tables
            val lTables = resTables.toList<JSObject>()
            for (i in lTables.indices) {
                val tableName =
                    if (lTables[i].has("name")) {
                        lTables[i].getString("name")
                    } else {
                        throw Exception("GetTablesPartial: no name")
                    } ?: throw Exception("GetTablesPartial: no name")
                val sqlStmt =
                    if (lTables[i].has("sql")) {
                        lTables[i].getString("sql")
                    } else {
                        throw Exception("GetTablesPartial: no sql")
                    }
                if (modTablesKeys.isEmpty() || !modTablesKeys.contains(tableName) || modTables.getString(tableName) == "No") {
                    continue
                }
                val table = JsonTable()
                table.name = tableName
                var schema = ArrayList<JsonColumn>()
                var indexes = ArrayList<JsonIndex>()
                var triggers = ArrayList<JsonTrigger>()
                if (modTables.getString(tableName) == "Create") {
                    // create Table's Schema
                    // a null sql threw a NullPointerException here in Java as well
                    schema = getSchema(sqlStmt!!)
                    if (schema.isNotEmpty()) {
                        // check schema validity
                        uJson.checkSchemaValidity(schema)
                    }

                    // create Table's indexes if any
                    indexes = getIndexes(mDb, tableName)

                    if (indexes.isNotEmpty()) {
                        // check indexes validity
                        uJson.checkIndexesValidity(indexes)
                    }
                    // create Table's triggers if any
                    triggers = getTriggers(mDb, tableName)

                    if (triggers.isNotEmpty()) {
                        // check triggers validity
                        uJson.checkTriggersValidity(triggers)
                    }
                }
                // create Table's Data
                val query =
                    if (modTables.getString(tableName) == "Create") {
                        "SELECT * FROM $tableName;"
                    } else {
                        "SELECT * FROM $tableName WHERE last_modified >= $syncDate;"
                    }
                val values = uJson.getValues(mDb, query, tableName)

                // check the table object validity
                table.name = tableName
                if (schema.isNotEmpty()) {
                    table.schema = schema
                }
                if (indexes.isNotEmpty()) {
                    table.indexes = indexes
                }
                if (triggers.isNotEmpty()) {
                    table.triggers = triggers
                }
                var msg = "Partial: Table $tableName schema export completed"
                msg += " " + (i + 1) + "/" + lTables.size + " ..."
                notifyExportProgressEvent(msg)

                if (values.isNotEmpty()) {
                    table.values = values
                }
                if (table.getKeys().size <= 1) {
                    throw Exception("GetTablesPartial: table $tableName is not a jsonTable")
                }
                tables.add(table)
                msg = "Partial: Table $tableName data export completed"
                msg += " " + (i + 1) + "/" + lTables.size + " ..."
                notifyExportProgressEvent(msg)
            }
            notifyExportProgressEvent("Partial: Table's export completed")
            return tables
        } catch (e: Exception) {
            notifyExportProgressEvent("Partial: Table's export failed")
            throw Exception("GetTablesPartial: " + e.message)
        }
    }

    /**
     * Get Tables Data when Mode is Partial
     * @param mDb Database
     * @param resTables tables
     * @return JSObject
     * @throws Exception message
     */
    private fun getPartialModeData(mDb: Database, resTables: JSArray): JSObject {
        val retData = JSObject()

        try {
            // get the sync date if expMode = "partial"
            val syncDate = getSyncDate(mDb)
            if (syncDate == -1L) {
                throw Exception("GetPartialModeData: did not find a sync_date")
            }
            // get the tables which have been updated
            // since last synchronization
            val modTables = getTablesModified(mDb, resTables, syncDate)
            retData.put("syncDate", syncDate)
            retData.put("modTables", modTables)
            return retData
        } catch (e: Exception) {
            throw Exception("GetPartialModeData: " + e.message)
        }
    }

    /**
     * Get Synchronization Date
     * @param mDb Database
     * @return Long synchronization date
     * @throws Exception message
     */
    public fun getSyncDate(mDb: Database): Long {
        var ret: Long = -1
        val stmt = "SELECT sync_date FROM sync_table WHERE id = 1;"
        try {
            val isSyncTable = uJson.isTableExists(mDb, "sync_table")
            if (!isSyncTable) {
                throw Exception("No sync_table available")
            }
            val retQuery = mDb.selectSQL(stmt, ArrayList())
            val lQuery = retQuery.toList<JSObject>()
            if (lQuery.size == 1) {
                val syncDate = lQuery[0].getLong("sync_date")
                if (syncDate > 0) ret = syncDate
            }
            return ret
        } catch (e: Exception) {
            throw Exception("GetSyncDate: " + e.message)
        }
    }

    /**
     * Get the tables which have been modified since last sync
     * @param mDb Database
     * @param resTables tables
     * @param syncDate synchronization date
     * @return JSObject
     * @throws Exception message
     */
    private fun getTablesModified(mDb: Database, resTables: JSArray, syncDate: Long): JSObject {
        val retObj = JSObject()
        try {
            val lTables = resTables.toList<JSObject>()
            for (i in lTables.indices) {
                val tableName =
                    if (lTables[i].has("name")) {
                        lTables[i].getString("name")
                    } else {
                        throw Exception("GetTablesModified: no name")
                    }
                var stmt = "SELECT count(*) AS count FROM $tableName;"
                var retQuery = mDb.selectSQL(stmt, ArrayList())
                var lQuery = retQuery.toList<JSObject>()
                if (lQuery.size != 1) break
                val totalCount = lQuery[0].getLong("count")
                // get total count of modified since last sync
                stmt = "SELECT count(*) AS count FROM $tableName WHERE last_modified >= $syncDate;"
                retQuery = mDb.selectSQL(stmt, ArrayList())
                lQuery = retQuery.toList()
                if (lQuery.size != 1) break
                val totalModCnt = lQuery[0].getLong("count")
                val mode =
                    if (totalModCnt == 0L) {
                        "No"
                    } else if (totalCount == totalModCnt) {
                        "Create"
                    } else {
                        "Modified"
                    }
                // JSObject ignored a null key in Java
                if (tableName != null) retObj.put(tableName, mode)
            }
            return retObj
        } catch (e: Exception) {
            throw Exception("GetTablesModified: " + e.message)
        }
    }

    private companion object {
        /** `String.split(regex)` of Java: the separator is a regular expression and trailing empty strings are dropped. */
        private fun javaSplit(input: String, regex: String, limit: Int = 0): Array<String> = Pattern.compile(regex).split(input, limit)
    }
}
