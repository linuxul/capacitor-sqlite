package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

public class JsonTable {
    public var name: String = ""
    public var schema: ArrayList<JsonColumn> = ArrayList()
    public var indexes: ArrayList<JsonIndex> = ArrayList()
    public var triggers: ArrayList<JsonTrigger> = ArrayList()
    public var values: ArrayList<ArrayList<Any?>> = ArrayList()

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (name.isNotEmpty()) retArray.add("names")
        if (schema.isNotEmpty()) retArray.add("schema")
        if (indexes.isNotEmpty()) retArray.add("indexes")
        if (triggers.isNotEmpty()) retArray.add("triggers")
        if (values.isNotEmpty()) retArray.add("values")
        return retArray
    }

    public fun isTable(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        var nbColumn = 0
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyTableLevel.contains(key)) return false
            try {
                val value = jsObj.get(key)
                if (key == "name") {
                    if (value !is String) return false
                    name = value
                }
                if (key == "schema") {
                    if (value !is JSONArray && value !is ArrayList<*>) return false
                    schema = ArrayList()
                    val arr = jsObj.getJSONArray(key)
                    nbColumn = 0
                    for (i in 0 until arr.length()) {
                        val sch = JsonColumn()
                        val retSchema = sch.isSchema(arr.getJSONObject(i))
                        if (sch.column != null) nbColumn++
                        if (!retSchema) return false
                        schema.add(sch)
                    }
                }
                if (key == "indexes") {
                    if (value !is JSONArray && value !is ArrayList<*>) return false
                    indexes = ArrayList()
                    val arr = jsObj.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        val idx = JsonIndex()
                        val retIndex = idx.isIndexes(arr.getJSONObject(i))
                        if (!retIndex) return false
                        indexes.add(idx)
                    }
                }
                if (key == "triggers") {
                    if (value !is JSONArray && value !is ArrayList<*>) return false
                    triggers = ArrayList()
                    val arr = jsObj.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        val trg = JsonTrigger()
                        val retTrigger = trg.isTrigger(arr.getJSONObject(i))
                        if (!retTrigger) return false
                        triggers.add(trg)
                    }
                }
                if (key == "values") {
                    if (value !is JSONArray && value !is ArrayList<*>) return false
                    values = ArrayList()
                    val arr = jsObj.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        val row = arr.getJSONArray(i)
                        val arrRow = ArrayList<Any?>()
                        for (j in 0 until row.length()) {
                            if (nbColumn > 0 && row.length() != nbColumn) return false
                            arrRow.add(row.get(j))
                        }
                        values.add(arrRow)
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    public fun print() {
        Log.d(TAG, "name: $name")
        Log.d(TAG, "number of Schema: " + schema.size)
        for (sch in schema) {
            sch.print()
        }
        Log.d(TAG, "number of Indexes: " + indexes.size)
        for (idx in indexes) {
            idx.print()
        }
        Log.d(TAG, "number of Triggers: " + triggers.size)
        for (trg in triggers) {
            trg.print()
        }
        Log.d(TAG, "number of Values: " + values.size)
        for (row in values) {
            Log.d(TAG, "row: $row")
        }
    }

    public fun getTableAsJSObject(): JSObject {
        val retObj = JSObject()
        retObj.put("name", name)
        if (schema.isNotEmpty()) {
            val jsSchema = JSONArray()
            for (sch in schema) {
                jsSchema.put(sch.getColumnAsJSObject())
            }
            retObj.put("schema", jsSchema)
        }
        if (indexes.isNotEmpty()) {
            val jsIndexes = JSONArray()
            for (idx in indexes) {
                jsIndexes.put(idx.getIndexAsJSObject())
            }
            retObj.put("indexes", jsIndexes)
        }
        if (triggers.isNotEmpty()) {
            val jsTriggers = JSONArray()
            for (trg in triggers) {
                jsTriggers.put(trg.getTriggerAsJSObject())
            }
            retObj.put("triggers", jsTriggers)
        }
        if (values.isNotEmpty()) {
            val jsValues = JSONArray()
            for (row in values) {
                val jsRow = JSONArray()
                for (v in row) {
                    jsRow.put(v)
                }
                jsValues.put(jsRow)
            }
            retObj.put("values", jsValues)
        }

        return retObj
    }

    private companion object {
        private const val TAG = "JsonTable"

        private val keyTableLevel: List<String> = listOf("name", "schema", "indexes", "triggers", "values")
    }
}
