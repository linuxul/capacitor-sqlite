package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

public class JsonView {
    public var name: String = ""
    public var value: String = ""

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (name.isNotEmpty()) retArray.add("name")
        if (value.isNotEmpty()) retArray.add("value")
        return retArray
    }

    public fun isView(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyViewsLevel.contains(key)) return false
            try {
                val objValue = jsObj.get(key)
                if (key == "name") {
                    if (objValue !is String) return false
                    name = objValue
                }
                if (key == "value") {
                    if (objValue !is String) return false
                    value = objValue
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    public fun print() {
        Log.d(TAG, "name: $name value: $value")
    }

    public fun getViewAsJSObject(): JSObject {
        val retObj = JSObject()
        retObj.put("name", name)
        retObj.put("value", value)
        return retObj
    }

    private companion object {
        private const val TAG = "JsonView"

        private val keyViewsLevel: List<String> = listOf("name", "value")
    }
}
