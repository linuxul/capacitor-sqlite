package com.getcapacitor.community.database.sqlite.SQLite

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLStatement.addPrefixToWhereClause
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLStatement.extractForeignKeyInfo
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLStatement.flattenMultilineString
import java.util.regex.Pattern

public class UtilsDelete {
    public class ReferenceResult {
        public var tableWithRefs: String = ""
        public var retRefs: MutableList<String> = ArrayList()
    }

    public class ForeignKeyInfo(
        public var forKeys: MutableList<String> = ArrayList(),
        public var tableName: String = "",
        public var refKeys: MutableList<String> = ArrayList(),
        public var action: String = "NO ACTION"
    )

    public class UpdateResults(public val setStmt: String = "", public val updWhereStmt: String = "")

    public companion object {
        @Suppress("UNCHECKED_CAST")
        public fun findReferencesAndUpdate(
            mDB: Database,
            tableName: String,
            whereStmt: String,
            initColNames: Array<String>,
            values: ArrayList<Any?>
        ): Boolean {
            try {
                val retBool = true
                val result = getReferences(mDB, tableName)
                val references = result.retRefs
                val tableNameWithRefs = result.tableWithRefs

                if (references.size <= 0) {
                    return retBool
                }

                if (tableName == tableNameWithRefs) {
                    return retBool
                }

                for (ref in references) {
                    val foreignKeyInfo = extractForeignKeyInfo(ref)

                    val refTable = foreignKeyInfo.tableName
                    if (refTable.isEmpty() || refTable != tableName) {
                        continue
                    }

                    val withRefsNames = foreignKeyInfo.forKeys
                    val colNames = foreignKeyInfo.refKeys

                    if (colNames.size != withRefsNames.size) {
                        // An Error, not an Exception: it is not caught below and keeps its message
                        throw Error("findReferencesAndUpdate: mismatch length")
                    }

                    val action = foreignKeyInfo.action
                    if (action == "NO_ACTION") {
                        continue
                    }

                    val updTableName = tableNameWithRefs
                    val updColNames = withRefsNames

                    var results = UpdateResults()

                    if (!checkValuesMatch(withRefsNames.toTypedArray(), initColNames)) {
                        val relatedItemsResult =
                            searchForRelatedItems(
                                mDB,
                                updTableName,
                                tableName,
                                whereStmt,
                                withRefsNames.toTypedArray(),
                                colNames.toTypedArray(),
                                values
                            )

                        if ((relatedItemsResult["relatedItems"] as List<Any?>).isEmpty() &&
                            (relatedItemsResult["key"] as String).isEmpty()
                        ) {
                            continue
                        }

                        if (updTableName != tableName) {
                            results =
                                when (action) {
                                    "RESTRICT" -> upDateWhereForRestrict(relatedItemsResult)
                                    "CASCADE" -> upDateWhereForCascade(relatedItemsResult)
                                    else -> upDateWhereForDefault(withRefsNames, relatedItemsResult)
                                }
                        }
                    } else {
                        throw Error("Not implemented. Please transfer your example to the maintainer")
                    }

                    if (results.setStmt.isNotEmpty() && results.updWhereStmt.isNotEmpty()) {
                        executeUpdateForDelete(mDB, updTableName, results.updWhereStmt, results.setStmt, updColNames, values)
                    }
                }
                return retBool
            } catch (error: Exception) {
                val msg = error.message ?: error.toString()
                throw Exception(msg)
            }
        }

        @Suppress("UNCHECKED_CAST")
        public fun getReferences(mDB: Database, tableName: String): ReferenceResult {
            val sqlStmt =
                "SELECT sql FROM sqlite_master " +
                    "WHERE sql LIKE('%FOREIGN KEY%') AND sql LIKE('%REFERENCES%') AND " +
                    "sql LIKE('%" +
                    tableName +
                    "%') AND sql LIKE('%ON DELETE%');"

            try {
                val references = mDB.selectSQL(sqlStmt, ArrayList())
                val referenceResult = ReferenceResult()
                var retRefs: MutableList<String> = ArrayList()
                var tableWithRefs = ""

                if (references.length() > 0) {
                    val result = getRefs(references.getJSONObject(0).getString("sql"))
                    retRefs = result["foreignKeys"] as MutableList<String>
                    tableWithRefs = result["tableName"] as String
                }

                referenceResult.tableWithRefs = tableWithRefs
                referenceResult.retRefs = retRefs

                return referenceResult
            } catch (e: Exception) {
                val error = e.message ?: e.toString()
                val msg = "getReferences: $error"
                throw Exception(msg)
            }
        }

        public fun getRefs(sqlStatement: String): MutableMap<String, Any?> {
            val result: MutableMap<String, Any?> = HashMap()
            var tableName = ""
            val foreignKeys: MutableList<String> = ArrayList()
            val statement = flattenMultilineString(sqlStatement)

            try {
                // Regular expression pattern to match the table name
                val tableNamePattern = "CREATE\\s+TABLE\\s+(\\w+)\\s+\\("
                val tableNameRegex = Pattern.compile(tableNamePattern)
                val tableNameMatcher = tableNameRegex.matcher(statement)
                if (tableNameMatcher.find()) {
                    tableName = tableNameMatcher.group(1)
                }

                // Regular expression pattern to match the FOREIGN KEY constraints
                val foreignKeyPattern =
                    "FOREIGN\\s+KEY\\s+\\([^)]+\\)\\s+REFERENCES\\s+(\\w+)\\s*\\([^)]+\\)\\s+" +
                        "ON\\s+DELETE\\s+(CASCADE|RESTRICT|SET\\s+DEFAULT|SET\\s+NULL|NO\\s+ACTION)"
                val foreignKeyRegex = Pattern.compile(foreignKeyPattern)
                val foreignKeyMatcher = foreignKeyRegex.matcher(statement)
                while (foreignKeyMatcher.find()) {
                    val foreignKey = foreignKeyMatcher.group(0)
                    foreignKeys.add(foreignKey)
                }
            } catch (e: Exception) {
                val msg = "getRefs: Error creating regular expression: $e"
                throw Exception(msg)
            }

            result["tableName"] = tableName
            result["foreignKeys"] = foreignKeys
            return result
        }

        public fun checkValuesMatch(array1: Array<String>, array2: Array<String>): Boolean {
            for (value in array1) {
                var found = false
                for (item in array2) {
                    if (value == item) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    return false
                }
            }
            return true
        }

        public fun searchForRelatedItems(
            mDB: Database,
            updTableName: String,
            tableName: String,
            whStmt: String,
            withRefsNames: Array<String>,
            colNames: Array<String>,
            values: ArrayList<Any?>
        ): MutableMap<String, Any?> {
            val relatedItems: MutableList<Any?> = ArrayList()
            var key = ""
            val t1Names = Array(withRefsNames.size) { "t1." + withRefsNames[it] }
            // Indexed by withRefsNames like the Java loop, so a shorter colNames still fails the same way
            val t2Names = arrayOfNulls<String>(colNames.size)
            for (i in withRefsNames.indices) {
                t2Names[i] = "t2." + colNames[i]
            }

            try {
                // addPrefix to the whereClause and swap colNames with  withRefsNames
                var whereClause = addPrefixToWhereClause(whStmt, colNames, withRefsNames, "t2.")
                // look at the whereclause and change colNames with  withRefsNames
                if (whereClause.endsWith(";")) {
                    whereClause = whereClause.substring(0, whereClause.length - 1)
                }

                val resultString = StringBuilder()
                for (index in t1Names.indices) {
                    resultString.append(t1Names[index]).append(" = ").append(t2Names[index])
                    if (index < t1Names.size - 1) {
                        resultString.append(" AND ")
                    }
                }

                val sql =
                    "SELECT t1.rowid FROM " +
                        updTableName +
                        " t1 " +
                        "JOIN " +
                        tableName +
                        " t2 ON " +
                        resultString.toString() +
                        " " +
                        "WHERE " +
                        whereClause +
                        " AND t1.sql_deleted = 0;"

                val jsVals = mDB.selectSQL(sql, values)
                if (jsVals.length() > 0) {
                    val mVals = JSArrayToJavaListMap(jsVals)
                    key = mVals[0].keys.iterator().next()
                    relatedItems.addAll(mVals)
                }
                val result: MutableMap<String, Any?> = HashMap()
                result["key"] = key
                result["relatedItems"] = relatedItems
                return result
            } catch (error: Exception) {
                val msg = error.message ?: error.toString()
                throw Exception(msg)
            }
        }

        @Suppress("ktlint:standard:function-naming")
        public fun JSArrayToJavaListMap(jsArray: JSArray): MutableList<MutableMap<String, Any?>> {
            val listMap: MutableList<MutableMap<String, Any?>> = ArrayList()

            for (i in 0 until jsArray.length()) {
                val jsObject = jsArray.get(i) as JSObject // Assuming each element is an object
                val map: MutableMap<String, Any?> = HashMap()

                // Extract key-value pairs from the JSObject and put them into the Map
                val it = jsObject.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    map[key] = jsObject.get(key) // Convert JSValue to Java object
                }

                listMap.add(map) // Add the map to the list
            }

            return listMap // Return the List<Map<String, Object>>
        }

        public fun upDateWhereForRestrict(results: Map<String, Any?>): UpdateResults {
            try {
                // !! keeps the NullPointerException Java threw for a missing "relatedItems" entry
                if ((results["relatedItems"] as List<*>?)!!.isNotEmpty()) {
                    val msg = "Restrict mode related items exist, please delete them first"
                    throw Exception(msg)
                }
                return UpdateResults()
            } catch (error: Exception) {
                val msg = error.message ?: ""
                throw Exception(msg)
            }
        }

        public fun upDateWhereForCascade(results: Map<String, Any?>): UpdateResults {
            var setStmt = ""
            val uWhereStmt: String

            try {
                val (key, cols) = relatedKeyValues(results)

                setStmt += "sql_deleted = 1"

                // Create the where statement
                uWhereStmt = buildInWhereStmt(key, cols)
            } catch (error: Exception) {
                val msg = error.message ?: ""
                throw Exception(msg)
            }
            return UpdateResults(setStmt, uWhereStmt)
        }

        public fun upDateWhereForDefault(withRefsNames: List<String>, results: Map<String, Any?>): UpdateResults {
            var setStmt = ""
            val uWhereStmt: String

            try {
                val (key, cols) = relatedKeyValues(results)

                // Create the set statement
                for (name in withRefsNames) {
                    setStmt += "$name = NULL, "
                }
                setStmt += "sql_deleted = 0"

                // Create the where statement
                uWhereStmt = buildInWhereStmt(key, cols)
            } catch (error: Exception) {
                val msg = error.message ?: ""
                throw Exception(msg)
            }

            return UpdateResults(setStmt, uWhereStmt)
        }

        /** The "key" entry of a searchForRelatedItems result and the non-null values the related items hold for it. */
        @Suppress("UNCHECKED_CAST")
        private fun relatedKeyValues(results: Map<String, Any?>): Pair<String?, List<Any>> {
            val key = results["key"] as String?
            val cols: MutableList<Any> = ArrayList()
            // !! keeps the NullPointerException Java threw for a missing "relatedItems" entry
            val relatedItems = (results["relatedItems"] as List<Map<String, Any?>>?)!!

            for (relItem in relatedItems) {
                val mVal = key?.let { relItem[it] }
                if (mVal != null) {
                    cols.add(mVal)
                }
            }
            return Pair(key, cols)
        }

        private fun buildInWhereStmt(key: String?, cols: List<Any>): String {
            val uWhereStmtBuilder = StringBuilder("WHERE $key IN (")
            for (col in cols) {
                uWhereStmtBuilder.append(col).append(",")
            }
            if (uWhereStmtBuilder.toString().endsWith(",")) {
                uWhereStmtBuilder.deleteCharAt(uWhereStmtBuilder.length - 1)
            }
            uWhereStmtBuilder.append(");")
            return uWhereStmtBuilder.toString()
        }

        public fun executeUpdateForDelete(
            mDB: Database,
            tableName: String,
            whereStmt: String,
            setStmt: String,
            colNames: List<String>,
            values: ArrayList<Any?>
        ) {
            try {
                // Update sql_deleted for this references
                val stmt = "UPDATE $tableName SET $setStmt $whereStmt"
                val selValues = getSelectedValues(values, whereStmt, colNames)

                val retObj = mDB.prepareSQL(stmt, selValues, false, "no")
                val lastId = retObj.getLong("lastId")
                if (lastId == -1L) {
                    val msg = "UPDATE sql_deleted failed for table: $tableName"
                    throw Exception(msg)
                }
            } catch (error: Exception) {
                val msg = error.message ?: ""
                throw Exception(msg)
            }
        }

        public fun getSelectedValues(values: ArrayList<Any?>, whereStmt: String, colNames: List<String>): ArrayList<Any?> {
            val selValues = ArrayList<Any?>() // Initialize the selected values ArrayList

            if (values.size > 0) {
                // Java's String.split: a regex separator, trailing empty strings dropped
                val arrVal = Pattern.compile("\\?").split(whereStmt)
                if (arrVal[arrVal.size - 1] == ";") {
                    arrVal[arrVal.size - 1] = ""
                }

                for (jdx in arrVal.indices) {
                    for (updVal in colNames) {
                        val indices = UtilsSQLStatement.indicesOf(arrVal[jdx], updVal, 0)
                        if (indices.isNotEmpty()) {
                            selValues.add(values[jdx])
                        }
                    }
                }
            }

            return selValues
        }
    }
}
