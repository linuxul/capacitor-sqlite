package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONException

public class JsonSQLite {
    public var database: String = ""
    public var version: Int? = 1
    public var overwrite: Boolean? = false
    public var encrypted: Boolean? = null
    public var mode: String = ""
    public var tables: ArrayList<JsonTable> = ArrayList()
    public var views: ArrayList<JsonView> = ArrayList()

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (database.isNotEmpty()) retArray.add("database")
        if (version != null) retArray.add("version")
        if (overwrite != null) retArray.add("overwrite")
        if (encrypted != null) retArray.add("encrypted")
        if (mode.isNotEmpty()) retArray.add("mode")
        if (tables.isNotEmpty()) retArray.add("tables")
        if (views.isNotEmpty()) retArray.add("views")
        return retArray
    }

    public fun isJsonSQLite(jsObj: JSObject?, isEncryption: Boolean): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyFirstLevel.contains(key)) return false
            try {
                val value = jsObj.get(key)

                when (key) {
                    "database" -> {
                        if (value !is String) return false
                        database = value
                    }

                    "version" -> {
                        if (value !is Int) return false
                        version = value
                    }

                    "overwrite" -> {
                        if (value !is Boolean) return false
                        overwrite = jsObj.getBool(key)
                    }

                    "encrypted" -> {
                        if (value !is Boolean) return false
                        encrypted = jsObj.getBool(key)
                        if (encrypted == true && !isEncryption) {
                            return false
                        }
                    }

                    "mode" -> {
                        if (value !is String) return false
                        mode = value
                    }

                    "tables" -> {
                        if (value !is JSONArray) {
                            Log.d(TAG, "value: not instance of JSONArray 1")
                            return false
                        }
                        val arrJS = jsObj.getJSONArray(key)
                        tables = ArrayList()

                        for (i in 0 until arrJS.length()) {
                            val table = JsonTable()
                            val retTable = table.isTable(arrJS.getJSONObject(i))

                            if (!retTable) return false
                            tables.add(table)
                        }
                    }

                    "views" -> {
                        if (value !is JSONArray) {
                            Log.d(TAG, "value: not instance of JSONArray")
                            return false
                        }
                        val arrJS = jsObj.getJSONArray(key)
                        views = ArrayList()

                        for (i in 0 until arrJS.length()) {
                            val view = JsonView()
                            val retView = view.isView(arrJS.getJSONObject(i))
                            if (!retView) return false
                            views.add(view)
                        }
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
        Log.d(TAG, "database: $database")
        Log.d(TAG, "version: $version")
        Log.d(TAG, "overwrite: $overwrite")
        Log.d(TAG, "encrypted: $encrypted")
        Log.d(TAG, "mode: $mode")
        Log.d(TAG, "number of Tables: " + tables.size)
        for (table in tables) {
            table.print()
        }
        if (views.isNotEmpty()) {
            Log.d(TAG, "number of Views: " + views.size)
            for (view in views) {
                view.print()
            }
        }
    }

    public fun getTablesAsJSObject(): JSArray {
        val jsTables = JSArray()
        for (table in tables) {
            jsTables.put(table.getTableAsJSObject())
        }
        return jsTables
    }

    public fun getViewsAsJSObject(): JSArray {
        val jsViews = JSArray()
        for (view in views) {
            jsViews.put(view.getViewAsJSObject())
        }
        return jsViews
    }

    private companion object {
        private const val TAG = "JsonSQLite"

        private val keyFirstLevel: List<String> = listOf("database", "version", "overwrite", "encrypted", "mode", "tables", "views")
    }
}
