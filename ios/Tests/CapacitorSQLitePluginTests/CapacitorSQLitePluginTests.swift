import XCTest
import Capacitor
@testable import CapacitorSQLitePlugin

class CapacitorSQLiteTests: XCTestCase {

    func testEcho() {
        // This is an example of a functional test case for a plugin.
        // Use XCTAssert and related functions to verify your tests produce the correct results.

        let implementation = CapacitorSQLite(config: SqliteConfig(iosIsEncryption: 0))
        let value = "Hello, World!"
        let result = implementation.echo(value)

        XCTAssertEqual(value, result)
    }

    func testMethodsWithMissingOptionsAreRejected() {
        let plugin = CapacitorSQLitePlugin()
        let cases: [RejectedCall] = [
            RejectedCall("createConnection", plugin.createConnection, [:], "CreateConnection: Must provide a database name"),
            RejectedCall("open", plugin.open, [:], "Open: Must provide a database name"),
            RejectedCall("close", plugin.close, [:], "Close: Must provide a database name"),
            RejectedCall("beginTransaction", plugin.beginTransaction, [:], "BeginTransaction: Must provide a database name"),
            RejectedCall("execute", plugin.execute, ["database": "db"], "Execute: Must provide raw SQL statements"),
            RejectedCall("executeSet", plugin.executeSet, ["database": "db", "set": [["statement": "SELECT 1"]]],
                "ExecuteSet: Must provide a set as Array of {statement,values}"),
            RejectedCall("run", plugin.run, ["database": "db"], "Run: Must provide a SQL statement"),
            RejectedCall("query", plugin.query, ["database": "db", "statement": "SELECT 1"], "Query: Must provide an Array of value"),
            RejectedCall("exportToJson", plugin.exportToJson, ["database": "db", "jsonexportmode": "some"],
                "ExportToJson : Json export mode should be 'full' or 'partial'"),
            RejectedCall("getTableList", plugin.getTableList, [:], "getDatabaseList: Must provide a database name"),
            RejectedCall("getNCDatabasePath", plugin.getNCDatabasePath, [:], "getNCDatabasePath: Must provide a folder path"),
            RejectedCall("checkConnectionsConsistency", plugin.checkConnectionsConsistency, ["dbNames": ["db"]],
                "CheckConnectionsConsistency: Must provide a OpenModes Array"),
            RejectedCall("getFromHTTPRequest", plugin.getFromHTTPRequest, [:], "GetFromHTTPRequest: Must provide a database url")
        ]
        for rejected in cases {
            let (name, message) = (rejected.name, rejected.message)
            XCTAssertThrowsError(try rejected.method(unansweredCall(name, rejected.options)), name) { error in
                // The bridge rejects the call with this message and no code, as the method did before.
                XCTAssertEqual((error as? CAPPluginError)?.message, message, name)
                XCTAssertNil((error as? CAPPluginError)?.code, name)
            }
        }
    }

    func testStatementsSeeTheCallsMadeBeforeThem() throws {
        let plugin = CapacitorSQLitePlugin()
        plugin.load()
        let database = "capacitorSQLitePluginTests"
        let connection: JSObject = ["database": database]

        try settle(plugin.createConnection, "createConnection", connection)
        try settle(plugin.open, "open", connection)
        let changes = try settle(plugin.execute, "execute", [
            "database": database,
            "statements": "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT); " +
                "DELETE FROM users; INSERT INTO users (name) VALUES ('Alice');"
        ])
        XCTAssertNotNil(changes?["changes"])
        let rows = try settle(plugin.query, "query", ["database": database, "statement": "SELECT name FROM users", "values": []])
        let values = try XCTUnwrap(rows?["values"] as? [[String: Any]])
        XCTAssertTrue(values.contains { $0["name"] as? String == "Alice" }, "\(values)")

        try settle(plugin.close, "close", connection)
        try settle(plugin.deleteDatabase, "deleteDatabase", connection)
        let exists = try settle(plugin.isDBExists, "isDBExists", connection)
        XCTAssertEqual(exists?["result"] as? Bool, false)
        try settle(plugin.closeConnection, "closeConnection", connection)
    }

    private struct RejectedCall {
        let name: String
        let method: (CAPPluginCall) throws -> Void
        let options: JSObject
        let message: String

        init(_ name: String, _ method: @escaping (CAPPluginCall) throws -> Void, _ options: JSObject, _ message: String) {
            self.name = name
            self.method = method
            self.options = options
            self.message = message
        }
    }

    /// Calls a synchronous method, which settles its call before returning, and returns what it resolved with.
    @discardableResult
    private func settle(_ method: (CAPPluginCall) throws -> Void, _ name: String,
                        _ options: JSObject) throws -> PluginCallResultData? {
        var resolved = false
        var data: PluginCallResultData?
        try method(CAPPluginCall(callbackId: "test", methodName: name, options: options, success: { result, _ in
            resolved = true
            data = result.data
        }, error: { error in
            XCTFail("\(name) rejected: \(error.message)")
        }))
        XCTAssertTrue(resolved, "\(name) must resolve before returning")
        return data
    }

    private func unansweredCall(_ method: String, _ options: JSObject) -> CAPPluginCall {
        return CAPPluginCall(callbackId: "test", methodName: method, options: options, success: { _, _ in
            XCTFail("\(method) must not resolve")
        }, error: { _ in
            XCTFail("\(method) answers by throwing")
        })
    }
}
