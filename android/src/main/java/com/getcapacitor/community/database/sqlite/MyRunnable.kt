package com.getcapacitor.community.database.sqlite

public open class MyRunnable : Runnable {
    public var info: Map<String, Any?>? = null

    override fun run() {}

    public open fun run(info: Map<String, Any?>?) {}
}
