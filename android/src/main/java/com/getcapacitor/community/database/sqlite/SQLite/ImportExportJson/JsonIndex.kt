package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.util.Log
import com.getcapacitor.JSObject
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

public class JsonIndex {
    public var name: String = ""
    public var value: String = ""

    // isIndexes stores the mode as it was given, whereas the setter only accepts "UNIQUE" and uppercases it
    private var modeValue: String = ""

    public var mode: String
        get() = modeValue
        set(newMode) {
            if (newMode.equals("UNIQUE", ignoreCase = true)) {
                modeValue = newMode.uppercase(Locale.getDefault())
            }
        }

    public fun getKeys(): ArrayList<String> {
        val retArray = ArrayList<String>()
        if (name.isNotEmpty()) retArray.add("name")
        if (value.isNotEmpty()) retArray.add("value")
        if (modeValue.isNotEmpty() && modeValue.equals("UNIQUE", ignoreCase = true)) retArray.add("mode")
        return retArray
    }

    public fun isIndexes(jsObj: JSONObject?): Boolean {
        if (jsObj == null || jsObj.length() == 0) return false
        val keys = jsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!keyIndexesLevel.contains(key)) return false
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
                if (key == "mode") {
                    if (objValue !is String || !objValue.equals("UNIQUE", ignoreCase = true)) return false
                    modeValue = objValue
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    public fun print() {
        var toPrint = "name: $name value: $value"
        if (modeValue.isNotEmpty()) toPrint += " mode: $modeValue"
        Log.d(TAG, toPrint)
    }

    public fun getIndexAsJSObject(): JSObject {
        val retObj = JSObject()
        retObj.put("name", name)
        retObj.put("value", value)
        if (modeValue.isNotEmpty()) retObj.put("mode", modeValue)
        return retObj
    }

    private companion object {
        private const val TAG = "JsonIndex"

        private val keyIndexesLevel: List<String> = listOf("name", "value", "mode")
    }
}
