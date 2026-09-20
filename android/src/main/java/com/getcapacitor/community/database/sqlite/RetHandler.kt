package com.getcapacitor.community.database.sqlite

import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall

public class RetHandler {
    /**
     * RetResult Method
     * Create and return the capSQLiteResult object
     * @param call
     * @param res
     * @param message
     */
    public fun retResult(call: PluginCall, res: Boolean?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        if (res != null) {
            val ret = JSObject()
            ret.put("result", res)
            call.resolve(ret)
        } else {
            call.resolve()
        }
    }

    /**
     * RetVersion Method
     * Create and return the capVersionResult object
     * @param call
     * @param res
     * @param message
     */
    public fun retVersion(call: PluginCall, res: Int?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        if (res != null) {
            val ret = JSObject()
            ret.put("version", res)
            call.resolve(ret)
        } else {
            call.resolve()
        }
    }

    /**
     * RetChanges Method
     * Create and return the capSQLiteChanges object
     * @param call
     * @param res
     * @param message
     */
    public fun retChanges(call: PluginCall, res: JSObject?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        val ret = JSObject()
        ret.put("changes", res)
        call.resolve(ret)
    }

    /**
     * RetValues Method
     * Create and return the capSQLiteValues object
     * @param call
     * @param res
     * @param message
     */
    public fun retValues(call: PluginCall, res: JSArray?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        val ret = JSObject()
        ret.put("values", res)
        call.resolve(ret)
    }

    /**
     * RetSyncDate Method
     * Create and return the capSQLiteSyncDate object
     * @param call
     * @param res
     * @param message
     */
    public fun retSyncDate(call: PluginCall, res: Long?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        val ret = JSObject()
        ret.put("syncDate", res)
        call.resolve(ret)
    }

    /**
     * RetJSObject Method
     * Create and return the capSQLiteJson object
     * @param call
     * @param res
     * @param message
     */
    public fun retJSObject(call: PluginCall, res: JSObject?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        val ret = JSObject()
        ret.put("export", res)
        call.resolve(ret)
    }

    /**
     * RetPath Method
     * Create and return the capNCDatabasePathResult object
     * @param call
     * @param res
     * @param message
     */
    public fun retPath(call: PluginCall, res: String?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        if (res != null) {
            val ret = JSObject()
            ret.put("path", res)
            call.resolve(ret)
        } else {
            call.resolve()
        }
    }

    /**
     * RetUrl Method
     * Create and return the capSQLiteUrl object
     * @param call
     * @param res
     * @param message
     */
    public fun retUrl(call: PluginCall, res: String?, message: String?) {
        if (message != null) {
            Log.v(TAG, "*** ERROR $message")
            call.reject(message)
            return
        }
        if (res != null) {
            val ret = JSObject()
            ret.put("url", res)
            call.resolve(ret)
        } else {
            call.resolve()
        }
    }

    private companion object {
        private val TAG = RetHandler::class.java.name
    }
}
