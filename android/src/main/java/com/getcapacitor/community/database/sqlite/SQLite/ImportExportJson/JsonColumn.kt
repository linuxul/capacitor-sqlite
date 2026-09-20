package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

public class JsonColumn {
    public var column: String? = null
    public var value: String? = null
    public var foreignkey: String? = null
    public var constraint: String? = null

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (!column.isNullOrEmpty()) retArray.add("column")
        if (!value.isNullOrEmpty()) retArray.add("value")
        if (!foreignkey.isNullOrEmpty()) retArray.add("foreignkey")
        if (!constraint.isNullOrEmpty()) retArray.add("constraint")
        return retArray
    }

    public fun isSchema(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keySchemaLevel.contains(key)) return false
            try {
                val v = jsObj.get(key)
                if (key == "column") {
                    if (v !is String) return false
                    column = v
                }
                if (key == "value") {
                    if (v !is String) return false
                    value = v
                }
                if (key == "foreignkey") {
                    if (v !is String) return false
                    foreignkey = v
                }
                if (key == "constraint") {
                    if (v !is String) return false
                    constraint = v
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    public fun print() {
        var row = ""
        if (column != null) row = "column: $column"
        if (foreignkey != null) row += " foreignkey: $foreignkey"
        if (constraint != null) row += " constraint: $constraint"
        Log.d(TAG, "$row value: $value")
    }

    public fun getColumnAsJSObject(): JSObject {
        val retObj = JSObject()
        if (column != null) retObj.put("column", column)
        retObj.put("value", value)
        if (foreignkey != null) retObj.put("foreignkey", foreignkey)
        if (constraint != null) retObj.put("constraint", constraint)
        return retObj
    }

    private companion object {
        private const val TAG = "JsonColumn"

        private val keySchemaLevel: List<String> = listOf("column", "value", "foreignkey", "constraint")
    }
}
