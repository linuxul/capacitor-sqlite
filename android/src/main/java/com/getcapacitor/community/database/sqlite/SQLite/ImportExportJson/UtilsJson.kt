package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.community.database.sqlite.SQLite.Database
import com.getcapacitor.community.database.sqlite.SQLite.UtilsDrop
import java.sql.Blob
import java.util.Locale
import org.json.JSONObject

public class UtilsJson {
    private val uJCol = JsonColumn()
    private val uJIdx = JsonIndex()
    private val uJTrg = JsonTrigger()
    private val uJView = JsonView()
    private val uDrop = UtilsDrop()

    /**
     * Check existence of last_modified column
     */
    public fun isLastModified(db: Database): Boolean {
        if (!db.isOpen) {
            throw Exception("isLastModified: Database not opened")
        }
        var ret = false
        try {
            val tables = uDrop.getTablesNames(db)
            for (tableName in tables) {
                val namesTypes = getTableColumnNamesTypes(db, tableName)
                val colNames =
                    if (namesTypes.has("names")) {
                        getColumnNames(namesTypes.get("names"))
                    } else {
                        throw Exception("isLastModified: Table $tableName no names")
                    }

                if (colNames.size > 0 && colNames.contains("last_modified")) {
                    ret = true
                    break
                }
            }
            return ret
        } catch (e: Exception) {
            throw Exception("isLastModified: " + e.message)
        }
    }

    /**
     * Get Column name's list
     */
    @Suppress("UNCHECKED_CAST")
    public fun getColumnNames(obj: Any?): ArrayList<String> = if (obj is ArrayList<*>) obj as ArrayList<String> else ArrayList()

    /**
     * Check existence of sql_deleted column
     */
    public fun isSqlDeleted(db: Database): Boolean {
        if (!db.isOpen) {
            throw Exception("isSqlDeleted: Database not opened")
        }
        var ret = false
        try {
            val tables = uDrop.getTablesNames(db)
            for (tableName in tables) {
                val namesTypes = getTableColumnNamesTypes(db, tableName)
                val colNames =
                    if (namesTypes.has("names")) {
                        getColumnNames(namesTypes.get("names"))
                    } else {
                        throw Exception("isSqlDeleted: Table $tableName no names")
                    }
                if (colNames.contains("sql_deleted")) {
                    ret = true
                    break
                }
            }
            return ret
        } catch (e: Exception) {
            throw Exception("isSqlDeleted: " + e.message)
        }
    }

    /**
     * Check if a table exists
     */
    public fun isTableExists(db: Database, tableName: String?): Boolean {
        val query = "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName';"
        try {
            val resQuery = db.selectSQL(query, ArrayList())
            return resQuery.length() > 0
        } catch (e: Exception) {
            throw Exception("isTableExists: " + e.message)
        }
    }

    /**
     * Check if a view exists
     */
    public fun isViewExists(db: Database, viewName: String?): Boolean {
        val query = "SELECT name FROM sqlite_master WHERE type='view' AND name='$viewName';"
        try {
            val resQuery = db.selectSQL(query, ArrayList())
            return resQuery.length() > 0
        } catch (e: Exception) {
            throw Exception("isViewExists: " + e.message)
        }
    }

    /**
     * Check if the Id already exsists
     */
    public fun isIdExists(mDb: Database, tableName: String?, firstColumnName: String?, key: Any?): Boolean {
        val sbQuery =
            StringBuilder("SELECT ")
                .append(firstColumnName)
                .append(" FROM ")
                .append(tableName)
                .append(" WHERE ")
                .append(firstColumnName)

        // fix #160 by peakcool
        if (key is String) {
            sbQuery.append(" = '").append(key).append("';")
        } else {
            sbQuery.append(" = ").append(key).append(";")
        }
        val query = sbQuery.toString()
        try {
            val resQuery = mDb.selectSQL(query, ArrayList())
            return resQuery.length() == 1
        } catch (e: Exception) {
            throw Exception("isIdExists: " + e.message)
        }
    }

    /**
     * Create a String from a given Array of Strings with
     * a given separator
     */
    public fun convertToString(arr: ArrayList<String>, sep: Char): String {
        val builder = StringBuilder()
        for (str in arr) {
            builder.append(str)
            builder.append(sep)
        }
        // Remove last delimiter with setLength (throws on an empty list, as it always did).
        builder.setLength(builder.length - 1)
        return builder.toString()
    }

    /**
     * Convert ArrayList to JSArray
     */
    public fun convertToJSArray(row: ArrayList<Any?>): JSArray {
        val jsArray = JSArray()
        for (item in row) {
            jsArray.put(item)
        }
        return jsArray
    }

    /**
     * Create the ? string for a given values length
     */
    public fun createQuestionMarkString(length: Int): String {
        val strB = StringBuilder()
        for (i in 0 until length) {
            strB.append("?,")
        }
        strB.deleteCharAt(strB.length - 1)
        return strB.toString()
    }

    /**
     * Create the Name string from a given Names array
     */
    public fun setNameForUpdate(names: ArrayList<String>): String {
        val strB = StringBuilder()
        for (name in names) {
            strB.append("($name) = ? ,")
        }
        strB.deleteCharAt(strB.length - 1)
        return strB.toString()
    }

    /**
     * Check the values type from fields type
     */
    public fun checkColumnTypes(types: ArrayList<String>, values: ArrayList<Any?>): Boolean {
        var isType = true
        for (i in values.indices) {
            isType = this.isType(types[i], values[i])
            if (!isType) break
        }
        return isType
    }

    /**
     * Check if the the value type is the same than the field type
     */
    private fun isType(type: String, value: Any?): Boolean {
        val valStr = value.toString().uppercase(Locale.getDefault())
        return when {
            valStr == "NULL" -> true
            valStr.contains("BASE64") -> true
            value == null -> true
            type == "NULL" && value is JSONObject -> true
            type == "TEXT" && value is String -> true
            type == "INTEGER" && (value is Int || value is Long) -> true
            type == "REAL" && (value is Double || value is Int) -> true
            type == "BLOB" && value is Blob -> true
            else -> false
        }
    }

    /**
     * Get Field's type and name for a given table
     */
    public fun getTableColumnNamesTypes(mDb: Database, tableName: String?): JSObject {
        val ret = JSObject()
        val names = ArrayList<String>()
        val types = ArrayList<String>()
        val query = "PRAGMA table_info('$tableName');"
        try {
            val resQuery = mDb.selectSQL(query, ArrayList())
            val lQuery = resQuery.toList<JSObject>()
            if (lQuery.size > 0) {
                for (obj in lQuery) {
                    // table_info always reports a name and a type
                    names.add(obj.getString("name") ?: "")
                    types.add(obj.getString("type") ?: "")
                }
                ret.put("names", names)
                ret.put("types", types)
            }
            return ret
        } catch (e: Exception) {
            throw Exception("GetTableColumnNamesTypes: " + e.message)
        }
    }

    /**
     * Get JSObject keys
     */
    public fun getJSObjectKeys(jsonObject: JSObject): ArrayList<String> {
        // one level JSObject keys
        val retArray = ArrayList<String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            retArray.add(keys.next())
        }
        return retArray
    }

    /**
     * Check Row validity
     */
    @Suppress("UNUSED_PARAMETER")
    public fun checkRowValidity(
        mDb: Database?,
        tColNames: ArrayList<String>,
        tColTypes: ArrayList<String>,
        row: ArrayList<Any?>,
        j: Int,
        tableName: String?
    ) {
        if (tColNames.size != row.size || row.size == 0 || tColNames.size == 0) {
            throw Exception("checkRowValidity: Table$tableName values row $j not correct length")
        }
        // Check the column's type before proceeding
        val retTypes = checkColumnTypes(tColTypes, row)
        if (!retTypes) {
            throw Exception("checkRowValidity: Table$tableName values row $j not correct types")
        }
    }

    /**
     * Check Schema Validity
     */
    public fun checkSchemaValidity(schema: ArrayList<JsonColumn>) {
        for (i in schema.indices) {
            val jsSch = JSONObject()
            val keys = schema[i].getKeys()
            if (keys.contains("column")) {
                jsSch.put("column", schema[i].column)
            }
            if (keys.contains("value")) {
                jsSch.put("value", schema[i].value)
            }
            if (keys.contains("foreignkey")) {
                jsSch.put("foreignkey", schema[i].foreignkey)
            }
            if (keys.contains("constraint")) {
                jsSch.put("constraint", schema[i].constraint)
            }
            val isValid = uJCol.isSchema(jsSch)
            if (!isValid) {
                throw Exception("checkSchemaValidity: schema[$i] not valid")
            }
        }
    }

    /**
     * Check Indexes Validity
     */
    public fun checkIndexesValidity(indexes: ArrayList<JsonIndex>) {
        for (i in indexes.indices) {
            val jsIdx = JSONObject()
            val keys = indexes[i].getKeys()
            if (keys.contains("value")) {
                jsIdx.put("value", indexes[i].value)
            }
            if (keys.contains("name")) {
                jsIdx.put("name", indexes[i].name)
            }
            if (keys.contains("mode")) {
                val mode: String? = indexes[i].mode
                if (mode == "UNIQUE") {
                    jsIdx.put("mode", mode)
                }
            }
            val isValid = uJIdx.isIndexes(jsIdx)
            if (!isValid) {
                throw Exception("checkIndexesValidity: indexes[$i] not valid")
            }
        }
    }

    /**
     * Check Triggers Validity
     */
    public fun checkTriggersValidity(triggers: ArrayList<JsonTrigger>) {
        for (i in triggers.indices) {
            val jsTrg = JSONObject()
            val keys = triggers[i].getKeys()
            if (keys.contains("name")) {
                jsTrg.put("name", triggers[i].name)
            }
            if (keys.contains("timeevent")) {
                jsTrg.put("timeevent", triggers[i].timeevent)
            }
            if (keys.contains("condition")) {
                jsTrg.put("condition", triggers[i].condition)
            }
            if (keys.contains("logic")) {
                jsTrg.put("logic", triggers[i].logic)
            }
            val isValid = uJTrg.isTrigger(jsTrg)
            if (!isValid) {
                throw Exception("checkTriggersValidity: triggers[$i] not valid")
            }
        }
    }

    /**
     * Check Views Validity
     */
    public fun checkViewsValidity(views: ArrayList<JsonView>) {
        for (i in views.indices) {
            val jsView = JSONObject()
            val keys = views[i].getKeys()
            if (keys.contains("value")) {
                jsView.put("value", views[i].value)
            }
            if (keys.contains("name")) {
                jsView.put("name", views[i].name)
            }
            val isValid = uJView.isView(jsView)
            if (!isValid) {
                throw Exception("checkViewsValidity: views[$i] not valid")
            }
        }
    }

    /**
     * Get Tables Values
     *
     * Never throws: the Java returned from a finally block, which discarded any exception and
     * handed back the rows collected so far. Callers rely on getting a (possibly empty) list.
     */
    public fun getValues(mDb: Database, query: String, tableName: String?): ArrayList<ArrayList<Any?>> {
        val values = ArrayList<ArrayList<Any?>>()
        try {
            val tableNamesTypes = getTableColumnNamesTypes(mDb, tableName)
            val rowNames =
                if (tableNamesTypes.has("names")) {
                    getColumnNames(tableNamesTypes.get("names"))
                } else {
                    throw Exception("GetValues: Table $tableName no names")
                }
            val rowTypes =
                if (tableNamesTypes.has("types")) {
                    getColumnNames(tableNamesTypes.get("types"))
                } else {
                    throw Exception("GetValues: Table $tableName no types")
                }
            val retValues = mDb.selectSQL(query, ArrayList())
            val lValues = retValues.toList<JSObject>()
            for (j in lValues.indices) {
                values.add(createRowValues(lValues, j, rowNames, rowTypes))
            }
        } catch (_: Exception) {
            // see the function comment
        }
        return values
    }

    /**
     * Get Table Row Values
     */
    @Suppress("UNUSED_PARAMETER")
    public fun createRowValues(
        values: List<JSObject>,
        pos: Int,
        rowNames: ArrayList<String>,
        rowTypes: ArrayList<String>
    ): ArrayList<Any?> {
        val row = ArrayList<Any?>()
        for (k in rowNames.indices) {
            val nName = rowNames[k]

            if (values[pos].has(nName)) {
                val obj = values[pos].get(nName)
                if (obj.toString() == "null") {
                    row.add(JSONObject.NULL)
                } else if (obj is Long) {
                    row.add(values[pos].getLong(nName))
                } else if (obj is String) {
                    row.add(values[pos].getString(nName))
                } else if (obj is Double) {
                    row.add(values[pos].getDouble(nName))
                }
            } else {
                val msg = "value is not (string, nsnull,int64,double"
                throw Exception("CreateRowValues: $msg")
            }
        }
        return row
    }
}
