package com.getcapacitor.community.database.sqlite

public class NotificationCenter private constructor() {
    private val registredObjects = HashMap<String, ArrayList<MyRunnable>>()

    @Synchronized
    public fun addMethodForNotification(notificationName: String, r: MyRunnable) {
        registredObjects.getOrPut(notificationName) { ArrayList() }.add(r)
    }

    @Synchronized
    public fun removeMethodForNotification(notificationName: String, r: MyRunnable) {
        registredObjects[notificationName]?.remove(r)
    }

    @Synchronized
    public fun removeAllNotifications() {
        val entries = registredObjects.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            removeMethodForNotification(entry.key, entry.value[0])
            entries.remove()
        }
    }

    @Synchronized
    public fun postNotification(notificationName: String, info: Map<String, Any?>?) {
        val list = registredObjects[notificationName] ?: return
        for (r in ArrayList(list)) {
            r.info = info
            r.run()
        }
    }

    public companion object {
        // static reference for singleton
        private var instance: NotificationCenter? = null

        // returning the reference
        @Synchronized
        public fun defaultCenter(): NotificationCenter = instance ?: NotificationCenter().also { instance = it }
    }
}
