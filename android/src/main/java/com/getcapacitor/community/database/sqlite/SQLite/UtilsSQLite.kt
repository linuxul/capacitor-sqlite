package com.getcapacitor.community.database.sqlite.SQLite

import androidx.sqlite.db.SupportSQLiteDatabase
import com.getcapacitor.JSArray
import net.zetetic.database.sqlcipher.SQLiteCursor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

public class UtilsSQLite {
    public fun dbChanges(db: SupportSQLiteDatabase): Int {
        var ret = -1
        val cursor = db.query("SELECT total_changes()") as SQLiteCursor
        if (cursor.moveToFirst()) {
            ret = cursor.getString(0).toInt()
        }
        cursor.close()
        return ret
    }

    public fun dbLastId(db: SupportSQLiteDatabase): Long {
        var ret = -1L
        val cursor = db.query("SELECT last_insert_rowid()") as SQLiteCursor
        if (cursor.moveToFirst()) {
            ret = cursor.getString(0).toLong()
        }
        cursor.close()
        return ret
    }

    public fun getStatementsArray(statements: String): Array<String> {
        val stmts = statements.replace("end;", "END;")
        // split for each statement
        // deal with trigger if any
        var sqlCmdArray = dealWithTriggers(javaSplit(stmts, ";\n"))
        // split for a single statement on multilines
        for (i in sqlCmdArray.indices) {
            val builder = StringBuilder()
            for (s in javaSplit(sqlCmdArray[i], "\n")) {
                var line = s.trim { it <= ' ' }
                val idx = line.indexOf("--")
                if (idx > -1) {
                    line = line.substring(0, idx)
                }
                if (line.isNotEmpty()) {
                    if (builder.isNotEmpty()) {
                        builder.append(" ")
                    }
                    builder.append(line)
                }
            }
            sqlCmdArray[i] = builder.toString()
        }
        if (sqlCmdArray[sqlCmdArray.size - 1].trim { it <= ' ' }.isEmpty()) {
            sqlCmdArray = sqlCmdArray.copyOf(sqlCmdArray.size - 1).requireNoNulls()
        }
        return sqlCmdArray
    }

    /** `String.split(String)` of Java for a literal delimiter: trailing empty strings are dropped. */
    private fun javaSplit(input: String, delimiter: String): List<String> {
        if (!input.contains(delimiter)) {
            return listOf(input)
        }
        return input.split(delimiter).dropLastWhile { it.isEmpty() }
    }

    private fun dealWithTriggers(sqlCmdArray: List<String>): Array<String> = concatRemoveEnd(trimArray(sqlCmdArray)).toTypedArray()

    private fun concatRemoveEnd(listArray: List<String>): List<String> {
        val lArray = ArrayList(listArray)
        if (lArray.contains("END")) {
            val idx = lArray.indexOf("END")
            lArray[idx - 1] = lArray[idx - 1] + "; END"
            lArray.removeAt(idx)
            return concatRemoveEnd(lArray)
        } else {
            return lArray
        }
    }

    private fun trimArray(listArray: List<String>): List<String> = listArray.map { s -> s.trim { it <= ' ' } }

    @Throws(JSONException::class)
    public fun objectJSArrayToArrayList(jsArray: JSArray): ArrayList<Any?> {
        val list = ArrayList<Any?>()
        for (i in 0 until jsArray.length()) {
            if (jsArray.isNull(i)) {
                list.add(null)
            } else {
                val obj = jsArray.get(i)
                // Exactly JSONObject, not a subclass, as in the Java version
                if (obj.javaClass == JSONObject::class.java) {
                    if ((obj as JSONObject).getString("type") == "Buffer") {
                        val bArr = JSONArrayToByteArray(obj.getJSONArray("data"))
                        list.add(bArr)
                    } else {
                        throw JSONException("Object not implemented")
                    }
                } else {
                    list.add(obj)
                }
            }
        }
        return list
    }

    @Throws(JSONException::class)
    public fun stringJSArrayToArrayList(jsArray: JSArray): ArrayList<String> {
        var list = ArrayList<String>()
        for (i in 0 until jsArray.length()) {
            if (jsArray.get(i) is String) {
                list.add(jsArray.getString(i))
            } else {
                list = ArrayList()
                break
            }
        }
        return list
    }

    @Suppress("ktlint:standard:function-naming")
    @Throws(JSONException::class)
    public fun JSONArrayToByteArray(arr: JSONArray): ByteArray {
        val bArr = ByteArray(arr.length())
        for (i in 0 until arr.length()) {
            // A value that is not an Int is a ClassCastException, as in the Java version
            bArr[i] = ((arr.get(i) as Int) and 0xFF).toByte()
        }
        return bArr
    }

    public fun parse(mVar: Any?): Boolean = mVar is JSONArray

    @Suppress("ktlint:standard:function-naming")
    public fun ByteToInt(bVal: Byte): Int {
        var out = bVal.toInt()
        // Get Unsigned Int
        if (out < 0) {
            out += 256
        }
        return out
    }

    @Suppress("ktlint:standard:function-naming")
    public fun ByteArrayToJSArray(bArr: ByteArray): JSArray {
        val arr = JSArray()

        for (b in bArr) {
            arr.put(ByteToInt(b))
        }
        return arr
    }
}
