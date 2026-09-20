package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONException
import org.json.JSONObject

public class JsonTrigger {
    public var name: String? = null
    public var timeevent: String? = null
    public var condition: String? = null
    public var logic: String? = null

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (!name.isNullOrEmpty()) retArray.add("name")
        if (!timeevent.isNullOrEmpty()) retArray.add("timeevent")
        if (!condition.isNullOrEmpty()) retArray.add("condition")
        if (!logic.isNullOrEmpty()) retArray.add("logic")
        return retArray
    }

    public fun isTrigger(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyTriggerLevel.contains(key)) return false
            try {
                val v = jsObj.get(key)
                if (key == "name") {
                    if (v !is String) return false
                    name = v
                }
                if (key == "timeevent") {
                    if (v !is String) return false
                    timeevent = v
                }
                if (key == "condition") {
                    if (v !is String) return false
                    condition = v
                }
                if (key == "logic") {
                    if (v !is String) return false
                    logic = v
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    public fun print() {
        var row = "name: $name timeevent: $timeevent"
        if (condition != null) row += " condition: $condition"
        Log.d(TAG, "$row logic: $logic")
    }

    public fun getTriggerAsJSObject(): JSObject {
        val retObj = JSObject()
        retObj.put("name", name)
        retObj.put("timeevent", timeevent)
        if (condition != null) retObj.put("condition", condition)
        retObj.put("logic", logic)
        return retObj
    }

    private companion object {
        private const val TAG = "JsonTrigger"

        private val keyTriggerLevel: List<String> = listOf("name", "timeevent", "condition", "logic")
    }
}
