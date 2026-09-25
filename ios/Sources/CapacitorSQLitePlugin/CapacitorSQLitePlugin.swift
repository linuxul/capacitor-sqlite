import Foundation
import Capacitor

@objc(CapacitorSQLitePlugin)
// swiftlint:disable file_length
// swiftlint:disable type_body_length
public class CapacitorSQLitePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapacitorSQLitePlugin"
    public let jsName = "CapacitorSQLite"
    // Every method is synchronous: the bridge calls them one after the other on its queue, so a statement sees the
    // connections, transactions and upgrade statements of the calls made before it. Async methods would not keep
    // that order.
    public let pluginMethods: [CAPPluginMethod] = [
        .promise("echo", CapacitorSQLitePlugin.echo),
        .promise("createConnection", CapacitorSQLitePlugin.createConnection),
        .promise("closeConnection", CapacitorSQLitePlugin.closeConnection),
        .promise("createNCConnection", CapacitorSQLitePlugin.createNCConnection),
        .promise("closeNCConnection", CapacitorSQLitePlugin.closeNCConnection),
        .promise("getNCDatabasePath", CapacitorSQLitePlugin.getNCDatabasePath),
        .promise("open", CapacitorSQLitePlugin.open),
        .promise("close", CapacitorSQLitePlugin.close),
        .promise("getUrl", CapacitorSQLitePlugin.getUrl),
        .promise("getVersion", CapacitorSQLitePlugin.getVersion),
        .promise("execute", CapacitorSQLitePlugin.execute),
        .promise("executeSet", CapacitorSQLitePlugin.executeSet),
        .promise("run", CapacitorSQLitePlugin.run),
        .promise("query", CapacitorSQLitePlugin.query),
        .promise("isDBExists", CapacitorSQLitePlugin.isDBExists),
        .promise("isDBOpen", CapacitorSQLitePlugin.isDBOpen),
        .promise("deleteDatabase", CapacitorSQLitePlugin.deleteDatabase),
        .promise("importFromJson", CapacitorSQLitePlugin.importFromJson),
        .promise("isJsonValid", CapacitorSQLitePlugin.isJsonValid),
        .promise("exportToJson", CapacitorSQLitePlugin.exportToJson),
        .promise("deleteExportedRows", CapacitorSQLitePlugin.deleteExportedRows),
        .promise("createSyncTable", CapacitorSQLitePlugin.createSyncTable),
        .promise("setSyncDate", CapacitorSQLitePlugin.setSyncDate),
        .promise("getSyncDate", CapacitorSQLitePlugin.getSyncDate),
        .promise("addUpgradeStatement", CapacitorSQLitePlugin.addUpgradeStatement),
        .promise("copyFromAssets", CapacitorSQLitePlugin.copyFromAssets),
        .promise("isDatabase", CapacitorSQLitePlugin.isDatabase),
        .promise("isNCDatabase", CapacitorSQLitePlugin.isNCDatabase),
        .promise("isTableExists", CapacitorSQLitePlugin.isTableExists),
        .promise("getDatabaseList", CapacitorSQLitePlugin.getDatabaseList),
        .promise("getTableList", CapacitorSQLitePlugin.getTableList),
        .promise("getMigratableDbList", CapacitorSQLitePlugin.getMigratableDbList),
        .promise("addSQLiteSuffix", CapacitorSQLitePlugin.addSQLiteSuffix),
        .promise("deleteOldDatabases", CapacitorSQLitePlugin.deleteOldDatabases),
        .promise("moveDatabasesAndAddSuffix", CapacitorSQLitePlugin.moveDatabasesAndAddSuffix),
        .promise("checkConnectionsConsistency", CapacitorSQLitePlugin.checkConnectionsConsistency),
        .promise("isSecretStored", CapacitorSQLitePlugin.isSecretStored),
        .promise("setEncryptionSecret", CapacitorSQLitePlugin.setEncryptionSecret),
        .promise("changeEncryptionSecret", CapacitorSQLitePlugin.changeEncryptionSecret),
        .promise("clearEncryptionSecret", CapacitorSQLitePlugin.clearEncryptionSecret),
        .promise("getFromHTTPRequest", CapacitorSQLitePlugin.getFromHTTPRequest),
        .promise("checkEncryptionSecret", CapacitorSQLitePlugin.checkEncryptionSecret),
        .promise("isInConfigEncryption", CapacitorSQLitePlugin.isInConfigEncryption),
        .promise("isInConfigBiometricAuth", CapacitorSQLitePlugin.isInConfigBiometricAuth),
        .promise("isDatabaseEncrypted", CapacitorSQLitePlugin.isDatabaseEncrypted),
        .promise("beginTransaction", CapacitorSQLitePlugin.beginTransaction),
        .promise("commitTransaction", CapacitorSQLitePlugin.commitTransaction),
        .promise("rollbackTransaction", CapacitorSQLitePlugin.rollbackTransaction),
        .promise("isTransactionActive", CapacitorSQLitePlugin.isTransactionActive)
    ]
    private var implementation: CapacitorSQLite?
    private let modeList: [String] = ["no-encryption", "encryption", "secret",
                                      "decryption", "newsecret", "wrongsecret"]
    private let retHandler: ReturnHandler = ReturnHandler()
    private var versionUpgrades: [String: [Int: [String: Any]]] = [:]
    var importObserver: Any?
    var exportObserver: Any?
    var biometricObserver: Any?
    var config: SqliteConfig?

    override public func load() {
        let mConfig = sqliteConfig()
        self.config = mConfig
        self.addObserversToNotificationCenter()
        self.implementation = CapacitorSQLite(config: mConfig)
    }
    deinit {
        NotificationCenter.default.removeObserver(importObserver as Any)
        NotificationCenter.default.removeObserver(exportObserver as Any)
        if let biometricAuth = config?.biometricAuth {
            if biometricAuth == 1 {
                NotificationCenter.default.removeObserver(biometricObserver as Any)
            }
        }
    }

    // MARK: - Echo

    func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        if let retValue: String = implementation?.echo(value) {
            call.resolve([
                "value": retValue
            ])
        }
    }

    // MARK: - IsInConfigEncryption

    func isInConfigEncryption(_ call: CAPPluginCall) {
        var bRes: Bool = false
        if self.config?.iosIsEncryption == 1 {
            bRes = true
        }

        retHandler.rResult(call: call, ret: bRes)
    }

    // MARK: - IsInConfigBiometricAuth

    func isInConfigBiometricAuth(_ call: CAPPluginCall) {
        var bRes: Bool = false
        if self.config?.biometricAuth == 1 {
            bRes = true
        }

        retHandler.rResult(call: call, ret: bRes)
    }
    // MARK: - IsDatabaseEncrypted

    func isDatabaseEncrypted(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("isDatabaseEncrypted: Must provide a database name")
        }
        do {
            let res = try implementation?.isDatabaseEncrypted(dbName)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isDatabaseEncrypted: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "isDatabaseEncrypted: \(error)")
            return
        }

    }

    // MARK: - IsSecretStored

    func isSecretStored(_ call: CAPPluginCall) {
        do {
            let res = try implementation?.isSecretStored()
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "IsSecretStored: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "IsSecretStored: \(error)")
            return
        }

    }

    // MARK: - SetEncryptionSecret

    func setEncryptionSecret(_ call: CAPPluginCall) throws {

        guard let passphrase = call.options["passphrase"] as? String else {
            throw CAPPluginError("SetEncryptionSecret: Must provide a passphrase")
        }
        do {
            try implementation?.setEncryptionSecret(passphrase: passphrase)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "SetEncryptionSecret: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "SetEncryptionSecret: \(error)")
            return
        }
    }

    // MARK: - ChangeEncryptionSecret

    func changeEncryptionSecret(_ call: CAPPluginCall) throws {

        guard let passphrase = call.options["passphrase"] as? String else {
            throw CAPPluginError("ChangeEncryptionSecret: Must provide a passphrase")
        }
        guard let oldPassphrase = call.options["oldpassphrase"] as? String else {
            throw CAPPluginError("ChangeEncryptionSecret: Must provide the old passphrase")
        }
        do {
            try implementation?.changeEncryptionSecret(call: call, passphrase: passphrase, oldPassphrase: oldPassphrase)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "ChangeEncryptionSecret: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "ChangeEncryptionSecret: \(error)")
            return
        }
    }

    // MARK: - ClearEncryptionSecret

    func clearEncryptionSecret(_ call: CAPPluginCall) {

        do {
            try implementation?.clearEncryptionSecret()
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "ClearEncryptionSecret: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "ClearEncryptionSecret: \(error)")
            return
        }
    }

    // MARK: - CheckEncryptionSecret

    func checkEncryptionSecret(_ call: CAPPluginCall) throws {

        guard let passphrase = call.options["passphrase"] as? String else {
            throw CAPPluginError("CheckEncryptionSecret: Must provide a passphrase")
        }
        do {
            let res = try implementation?
                .checkEncryptionSecret(passphrase: passphrase)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CheckEncryptionSecret: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CheckEncryptionSecret: \(error)")
            return
        }
    }

    // MARK: - CreateConnection

    func createConnection(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("CreateConnection: Must provide a database name")
        }
        let version: Int = call.getInt("version") ?? 1
        let encrypted: Bool = call.getBool("encrypted") ?? false
        let inMode: String = call.getString("mode") ?? "no-encryption"
        if encrypted && !modeList.contains(inMode) {
            var msg: String = "CreateConnection: inMode "
            msg.append("must be in['encryption',")
            msg.append("'secret','decryption']")
            throw CAPPluginError(msg)
        }
        let readOnly: Bool = call.getBool("readonly") ?? false

        var upgDict: [Int: [String: Any]] = [:]
        if let cUpgDict = versionUpgrades[dbName] {
            upgDict = cUpgDict
        }
        do {
            try implementation?.createConnection(dbName,
                                                 encrypted: encrypted,
                                                 mode: inMode,
                                                 version: version,
                                                 vUpgDict: upgDict,
                                                 readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CreateConnection: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CreateConnection: \(error)")
            return
        }
    }

    // MARK: - Open

    func open(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("Open: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.open(dbName, readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "Open: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "Open: \(error)")
            return
        }
    }

    // MARK: - Close

    func close(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("Close: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.close(dbName, readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "Close: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "Close: \(error)")
            return
        }
    }

    // MARK: - BeginTransaction

    func beginTransaction(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("BeginTransaction: Must provide a database name")
        }
        do {
            if let ret = try implementation?.beginTransaction(dbName) {
                retHandler.rChanges(call: call, ret: ret)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "BeginTransaction: Does not return a valid execute")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "BeginTransaction: \(message)"
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: msg)
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "BeginTransaction: \(error)")
            return
        }
    }

    // MARK: - CommitTransaction

    func commitTransaction(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("CommitTransaction: Must provide a database name")
        }
        do {
            if let ret = try implementation?.commitTransaction(dbName) {
                retHandler.rChanges(call: call, ret: ret)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "CommitTransaction: Does not return a valid execute")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CommitTransaction: \(message)"
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: msg)
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "CommitTransaction: \(error)")
            return
        }
    }

    // MARK: - RollbackTransaction

    func rollbackTransaction(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("RollbackTransaction: Must provide a database name")
        }
        do {
            if let ret = try implementation?.rollbackTransaction(dbName) {
                retHandler.rChanges(call: call, ret: ret)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "RollbackTransaction: Does not return a valid execute")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "RollbackTransaction: \(message)"
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: msg)
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "RollbackTransaction: \(error)")
            return
        }
    }

    // MARK: - IsTransactionActive

    func isTransactionActive(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("IsTransactionActive: Must provide a database name")
        }
        do {
            let res = try implementation?.isTransactionActive(dbName)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "IsTransactionActive: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "IsTransactionActive: \(error)")
            return
        }
    }

    // MARK: - GetUrl

    func getUrl(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("GetUrl: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res: String = try implementation?.getUrl(dbName,
                                                         readonly: readOnly) ?? ""

            if res.count > 0 {
                retHandler.rUrl(call: call, ret: res)
                return
            } else {
                retHandler.rUrl(
                    call: call, ret: "",
                    message: "getUrl: No path returned")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rUrl(
                call: call, ret: "",
                message: "getUrl: \(message)")
            return
        } catch let error {
            retHandler.rUrl(
                call: call, ret: "",
                message: "getUrl: \(error)")
            return
        }
    }

    // MARK: - getVersion

    func getVersion(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("getVersion: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            if let version: NSNumber = try implementation?
                .getVersion(dbName, readonly: readOnly) {
                retHandler.rVersion(call: call, ret: version)
                return
            } else {
                let msg = "GetVersion: Does not return a valid version"
                retHandler.rVersion(call: call, message: msg)
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "GetVersion: \(message)"
            retHandler.rVersion(call: call, message: msg)
            return
        } catch let error {
            retHandler.rVersion(
                call: call,
                message: "GetVersion: \(error)")
            return
        }
    }

    // MARK: - Close Connection

    func closeConnection(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("CloseConnection: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.closeConnection(dbName, readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CloseConnection: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CloseConnection: \(error)")
            return
        }

    }

    // MARK: - CheckConsistency

    func checkConnectionsConsistency(_ call: CAPPluginCall) throws {
        guard let dbNames = call.options["dbNames"] as? [String] else {
            throw CAPPluginError("CheckConnectionsConsistency: Must provide a " +
                "Connection Array")
        }
        guard let openModes = call.options["openModes"] as? [String] else {
            throw CAPPluginError("CheckConnectionsConsistency: Must provide a " +
                "OpenModes Array")
        }
        do {
            let res = try implementation?
                .checkConnectionsConsistency(dbNames,
                                             openModes: openModes)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CheckConnectionsConsistency: \(error)")
            return
        }
    }

    // MARK: - IsDatabase

    func isDatabase(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("isDatabase: Must provide a database name")
        }
        do {
            let res = try implementation?.isDatabase(dbName)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isDatabase: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "isDatabase: \(error)")
            return
        }

    }

    // MARK: - IsTableExists

    func isTableExists(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("IsTableExists: Must provide a database name")
        }
        guard let tableName = call.options["table"] as? String else {
            throw CAPPluginError("IsTableExists: Must provide a table name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res = try implementation?.isTableExists(dbName,
                                                        tableName: tableName,
                                                        readonly: readOnly)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "IsTableExists: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "IsTableExists: \(error)")
            return
        }
    }

    // MARK: - GetTableList

    func getTableList(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("getDatabaseList: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res = try implementation?.getTableList(dbName,
                                                       readonly: readOnly) ?? []
            retHandler.rValues(call: call, ret: res)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rValues(
                call: call, ret: [],
                message: "getDatabaseList: \(message)")
            return
        } catch let error {
            retHandler.rValues(
                call: call, ret: [],
                message: "getDatabaseList: \(error)")
            return
        }
    }

    // MARK: - getDatabaseList

    func getDatabaseList(_ call: CAPPluginCall) {

        do {
            let res = try implementation?.getDatabaseList() ?? []
            retHandler.rValues(call: call, ret: res)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rValues(
                call: call, ret: [],
                message: "getDatabaseList: \(message)")
            return
        } catch let error {
            retHandler.rValues(
                call: call, ret: [],
                message: "getDatabaseList: \(error)")
            return
        }
    }

    // MARK: - getMigratableDbList

    func getMigratableDbList(_ call: CAPPluginCall) {
        let folderPath: String = call.getString("folderPath") ?? "default"
        do {
            let res = try implementation?
                .getMigratableDbList(folderPath) ?? []
            retHandler.rValues(call: call, ret: res)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rValues(
                call: call, ret: [],
                message: "getMigratableDbList: \(message)")
            return
        } catch let error {
            retHandler.rValues(
                call: call, ret: [],
                message: "getMigratableDbList: \(error)")
            return
        }
    }

    // MARK: - addSQLiteSuffix

    func addSQLiteSuffix(_ call: CAPPluginCall) {
        let folderPath: String = call.getString("folderPath") ?? "default"
        let dbJsList: JSArray = call.getArray("dbNameList") ?? []
        var dbList: [String] = []
        if dbJsList.count > 0 {
            for dbName in dbJsList {
                if let name = dbName as? String {
                    dbList.append(name)
                }
            }
        }
        do {
            try implementation?.addSQLiteSuffix(folderPath, dbList: dbList)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "AddSQLiteSuffix: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "AddSQLiteSuffix: \(error)")
            return
        }
    }

    // MARK: - deleteOldDatabases

    func deleteOldDatabases(_ call: CAPPluginCall) {
        let folderPath: String = call.getString("folderPath") ?? "default"
        let dbJsList: JSArray = call.getArray("dbNameList") ?? []
        var dbList: [String] = []
        if dbJsList.count > 0 {
            for dbName in dbJsList {
                if let name = dbName as? String {
                    dbList.append(name)
                }
            }
        }
        do {
            try implementation?.deleteOldDatabases(folderPath, dbList: dbList)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "DeleteOldDatabases: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "DeleteOldDatabases: \(error)")
            return
        }
    }

    // MARK: - moveDatabasesAndAddSuffix

    func moveDatabasesAndAddSuffix(_ call: CAPPluginCall) {
        let folderPath: String = call.getString("folderPath") ?? "default"
        let dbJsList: JSArray = call.getArray("dbNameList") ?? []
        var dbList: [String] = []
        if dbJsList.count > 0 {
            for dbName in dbJsList {
                if let name = dbName as? String {
                    dbList.append(name)
                }
            }
        }
        do {
            try implementation?.moveDatabasesAndAddSuffix(folderPath, dbList: dbList)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "moveDatabasesAndAddSuffix: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "moveDatabasesAndAddSuffix: \(error)")
            return
        }
    }

    // MARK: - Execute

    func execute(_ call: CAPPluginCall) throws {

        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("Execute: Must provide a database name")
        }
        let statements: String = call.getString("statements") ?? ""
        let transaction: Bool = call.getBool("transaction") ?? true
        if statements.count == 0 {
            throw CAPPluginError("Execute: Must provide raw SQL statements")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            if let res = try implementation?
                .execute(dbName, statements: statements,
                         transaction: transaction, readonly: readOnly) {
                retHandler.rChanges(call: call, ret: res)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "Execute: Does not return a valid execute")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "Execute: \(message)")
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "Execute: \(error)")
            return
        }

    }

    // MARK: - ExecuteSet
    func executeSet(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("ExecuteSet: Must provide a database name")
        }
        guard let set = call.options["set"] as? [[String: Any]] else {
            var msg: String = "ExecuteSet: "
            msg.append("Must provide a set of SQL statements")
            throw CAPPluginError(msg)
        }
        if set.count == 0 {
            var msg: String = "ExecuteSet: Must "
            msg.append("provide a non-empty set of SQL statements")
            throw CAPPluginError(msg)
        }
        for dict in set {
            let keys = dict.keys
            if !(keys.contains("statement")) ||
                !(keys.contains("values")) {
                var msg: String = "ExecuteSet: "
                msg.append("Must provide a set as Array of ")
                msg.append("{statement,values}")
                throw CAPPluginError(msg)
            }
        }
        let transaction: Bool = call.getBool("transaction") ?? true
        let readOnly: Bool = call.getBool("readonly") ?? false
        let returnMode: String = call.getString("returnMode") ?? "no"
        do {
            if let res = try implementation?.executeSet(dbName, set: set,
                                                        transaction: transaction,
                                                        readonly: readOnly,
                                                        returnMode: returnMode) {
                retHandler.rChanges(call: call, ret: res)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "ExecuteSet: Does not return a valid executeSet")
                return

            }
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "ExecuteSet: \(message)")
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "ExecuteSet: \(error)")
            return
        }

    }

    // MARK: - Run

    func run(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("Run: Must provide a database name")
        }
        guard let statement = call.options["statement"]
                as? String else {
            throw CAPPluginError("Run: Must provide a SQL statement")
        }
        guard let values = call.options["values"]
                as? [Any] else {
            throw CAPPluginError("Run: Must provide an Array of values")
        }
        let transaction: Bool = call.getBool("transaction") ?? true
        let readOnly: Bool = call.getBool("readonly") ?? false
        let returnMode: String = call.getString("returnMode") ?? "no"
        do {
            if let res = try
                implementation?.run(dbName,
                                    statement: statement,
                                    values: values,
                                    transaction: transaction,
                                    readonly: readOnly,
                                    returnMode: returnMode) {
                retHandler.rChanges(call: call, ret: res)
                return
            } else {
                retHandler.rChanges(
                    call: call, ret: ["changes": -1],
                    message: "Run: Does not return a valid run")
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "Run: \(message)")
            return
        } catch let error {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "Run: \(error)")
            return
        }

    }

    // MARK: - Query

    func query(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            var msg: String = "Query: "
            msg.append("Must provide a database name")
            throw CAPPluginError(msg)
        }

        guard let statement = call.options["statement"]
                as? String else {
            var msg: String = "Query: "
            msg.append("Must provide a query statement")
            throw CAPPluginError(msg)
        }
        guard let values = call.options["values"] as? [Any] else {
            var msg: String = "Query: "
            msg.append("Must provide an Array of value")
            throw CAPPluginError(msg)
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            if let res: [[String: Any]] = try
                implementation?.query(dbName,
                                      statement: statement,
                                      values: values,
                                      readonly: readOnly) {
                retHandler.rValues(call: call, ret: res)
                return
            } else {
                retHandler.rValues(
                    call: call, ret: [],
                    message: "Query: Does not return a valid query")
                return

            }
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rValues(
                call: call, ret: [],
                message: "Query: \(message)")
            return
        } catch let error {
            retHandler.rValues(
                call: call, ret: [],
                message: "Query: \(error)")
            return
        }

    }

    // MARK: - isDBExists

    func isDBExists(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("isDBExists: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res = try implementation?.isDBExists(dbName, readonly: readOnly)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isDBExists: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "isDBExists: \(error)")
            return
        }

    }

    // MARK: - IsDBOpen

    func isDBOpen(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("idDBOpen: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res = try implementation?.isDBOpen(dbName, readonly: readOnly)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isDBOpen: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "isDBOpen: \(error)")
            return
        }
    }

    // MARK: - DeleteDatabase

    func deleteDatabase(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("deleteDatabase: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.deleteDatabase(dbName, readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "deleteDatabase: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            let msg = "deleteDatabase: \(error)"
            retHandler.rResult(call: call, message: msg)
            return
        }
    }

    // MARK: - IsJsonValid

    func isJsonValid(_ call: CAPPluginCall) throws {
        let parsingData: String = call.getString("jsonstring") ?? ""
        if parsingData.count == 0 {
            var msg: String = "IsJsonValid: "
            msg.append("Must provide a Stringify Json Object")
            throw CAPPluginError(msg)
        }
        do {
            try implementation?.isJsonValid(parsingData)
            retHandler.rResult(call: call, ret: true)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isJsonValid: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            let msg = "isJsonValid: \(error)"
            retHandler.rResult(call: call, message: msg)
            return
        }
    }

    // MARK: - ImportFromJson

    func importFromJson(_ call: CAPPluginCall) throws {
        let parsingData: String = call.getString("jsonstring") ?? ""
        if parsingData.count == 0 {
            throw CAPPluginError("ImportFromJson: " +
                "Must provide a Stringify Json Object")
        }
        do {
            let res: [String: Int]  = try implementation?
                .importFromJson(parsingData) ?? ["changes": -1]
            retHandler.rChanges(call: call, ret: res)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: "importFromJson: \(message)")
            return
        } catch let error {
            let msg = "importFromJson: " +
                "\(error)"
            retHandler.rChanges(
                call: call, ret: ["changes": -1],
                message: msg)
            return
        }

    }

    // MARK: - ExportToJson

    func exportToJson(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("ExportToJson: Must provide a database name")
        }
        let expMode: String = call.getString("jsonexportmode") ?? ""
        if expMode.count == 0 {
            var msg: String = "ExportToJson: "
            msg.append("Must provide an export mode")
            throw CAPPluginError(msg)
        }
        if expMode != "full" && expMode != "partial" {
            var msg: String = "ExportToJson : Json export "
            msg.append("mode should be 'full' or 'partial'")
            throw CAPPluginError(msg)
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        let encrypted: Bool = call.getBool("encrypted") ?? false

        do {
            let res: [String: Any] = try implementation?
                .exportToJson(dbName, expMode: expMode,
                              readonly: readOnly,
                              encrypted: encrypted) ?? [:]
            retHandler.rJsonSQLite(call: call, ret: res)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "exportToJson: \(message)"
            retHandler.rJsonSQLite(call: call, ret: [:],
                                   message: msg)
            return
        } catch let error {
            let msg = "exportToJson: \(error)"
            retHandler.rJsonSQLite(call: call, ret: [:],
                                   message: msg)
            return
        }

    }

    // MARK: - DeleteExportedRows

    func deleteExportedRows(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("DeleteExportedRows: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.deleteExportedRows(dbName, readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "exportToJson: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            let msg = "exportToJson: \(error)"
            retHandler.rResult(call: call, message: msg)
            return
        }

    }

    // MARK: - CreateSyncTable

    func createSyncTable(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("createSyncTable: " +
                "Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res: NSNumber = try implementation?
                .createSyncTable(dbName, readonly: readOnly) ?? -1
            retHandler.rChanges(call: call,
                                ret: ["changes": Int(truncating: res)])
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "createSyncTable: \(message)"
            retHandler.rChanges(call: call, ret: ["changes": -1],
                                message: msg)
            return
        } catch let error {
            let msg = "createSyncTable: \(error)"
            retHandler.rChanges(call: call, ret: ["changes": -1],
                                message: msg)
            return
        }

    }

    // MARK: - SetSyncDate

    func setSyncDate(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("setSyncDate: Must provide a database name")
        }
        guard let syncDate = call.options["syncdate"] as? String else {
            var msg = "setSyncDate: Must provide a "
            msg.append("synchronization date")
            throw CAPPluginError(msg)
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            try implementation?.setSyncDate( dbName, syncDate: syncDate,
                                             readonly: readOnly)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "setSyncDate: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            let msg = "setSyncDate: \(error)"
            retHandler.rResult(call: call, message: msg)
            return
        }
    }

    // MARK: - GetSyncDate

    func getSyncDate(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("getSyncDate: Must provide a database name")
        }
        let readOnly: Bool = call.getBool("readonly") ?? false
        do {
            let res: NSNumber = try implementation?
                .getSyncDate( dbName, readonly: readOnly) ?? 0
            retHandler.rSyncDate(call: call,
                                 ret: Int64(truncating: res))
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "getSyncDate: \(message)"
            retHandler.rSyncDate(call: call, ret: 0, message: msg)
            return
        } catch let error {
            let msg = "getSyncDate: \(error)"
            retHandler.rSyncDate(call: call, ret: 0, message: msg)
            return
        }

    }

    // MARK: addUpgradeStatement

    func addUpgradeStatement(_ call: CAPPluginCall) throws {
        guard let dbName = call.options["database"]
                as? String else {
            throw CAPPluginError("deleteDatabase: Must provide a database name")
        }
        guard let upgrade = call.options["upgrade"] as?
                [[String: Any]] else {
            throw CAPPluginError("Must provide an upgrade statement")
        }
        do {
            if let upgVersionDict: [Int: [String: Any]] = try
                implementation?.addUpgradeStatement(dbName,
                                                    upgrade: upgrade) {
                if
                    self.versionUpgrades["\(dbName)"] != nil {
                    for (versionKey, upgObj) in upgVersionDict {
                        self.versionUpgrades["\(dbName)"]?[versionKey] = upgObj
                    }
                } else {
                    self.versionUpgrades["\(dbName)"] = upgVersionDict
                }
                retHandler.rResult(call: call)
                return
            } else {
                let msg = "addUpgradeStatement: Error in returned upgVersionDict"
                retHandler.rResult(call: call, message: msg)
                return
            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "addUpgradeStatement: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            var  msg = "addUpgradeStatement: "
            msg.append("\(error)")
            retHandler.rResult(call: call, message: msg)
            return
        }
    }

    // MARK: copyFromAssets

    func copyFromAssets(_ call: CAPPluginCall) {
        let overwrite: Bool = call.getBool("overwrite") ?? true

        do {
            try implementation?.copyFromAssets(overwrite: overwrite)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "copyFromAssets: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            let msg = "copyFromAssets: \(error)"
            retHandler.rResult(call: call, message: msg)
            return
        }
    }

    // MARK: - getNCDatabasePath

    func getNCDatabasePath(_ call: CAPPluginCall) throws {
        guard let folderPath = call.options["path"] as? String else {
            throw CAPPluginError("getNCDatabasePath: Must provide a folder path")
        }
        guard let dbName = call.options["database"] as? String else {
            throw CAPPluginError("getNCDatabasePath: Must provide a database name")
        }
        do {

            if let path: String = try implementation?
                .getNCDatabasePath(folderPath, dbName: dbName) {
                retHandler.rPath(call: call, ret: path)
                return
            } else {
                let msg = "getNCDatabasePath: Does not return a NC path"
                retHandler.rPath(call: call, ret: "", message: msg)
                return

            }
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "getNCDatabasePath: \(message)"
            retHandler.rPath(call: call, ret: "", message: msg)
            return
        } catch let error {
            retHandler.rPath(call: call, ret: "",
                             message: "getNCDatabasePath: \(error)")
            return
        }
    }

    // MARK: - CreateNCConnection

    func createNCConnection(_ call: CAPPluginCall) throws {
        guard let dbPath = call.options["databasePath"] as? String else {
            throw CAPPluginError("CreateNCConnection: Must provide a database path")
        }
        let version: Int = call.getInt("version") ?? 1
        do {
            try implementation?.createNCConnection(dbPath,
                                                   version: version)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CreateNCConnection: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CreateNCConnection: \(error)")
            return
        }
    }

    // MARK: - CloseNCConnection

    func closeNCConnection(_ call: CAPPluginCall) throws {
        guard let dbPath = call.options["databasePath"] as? String else {
            throw CAPPluginError("CloseNCConnection: Must provide a database path")
        }
        do {
            try implementation?.closeNCConnection(dbPath)
            retHandler.rResult(call: call)
            return
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "CloseNCConnection: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "CloseNCConnection: \(error)")
            return
        }
    }

    // MARK: - IsNCDatabase

    func isNCDatabase(_ call: CAPPluginCall) throws {
        guard let dbPath = call.options["databasePath"] as? String else {
            throw CAPPluginError("isNCDatabase: Must provide a database path")
        }
        do {
            let res = try implementation?.isNCDatabase(dbPath)
            var bRes: Bool = false
            if res == 1 {
                bRes = true
            }
            retHandler.rResult(call: call, ret: bRes)
        } catch CapacitorSQLiteError.failed(let message) {
            let msg = "isNCDatabase: \(message)"
            retHandler.rResult(call: call, message: msg)
            return
        } catch let error {
            retHandler.rResult(
                call: call,
                message: "isNCDatabase: \(error)")
            return
        }

    }

    // MARK: - GetFromHTTPRequest

    func getFromHTTPRequest(_ call: CAPPluginCall) throws {
        guard let url = call.options["url"] as? String else {
            throw CAPPluginError("GetFromHTTPRequest: Must provide a database url")
        }
        DispatchQueue.global(qos: .background).async(execute: {
            do {
                try self.implementation?.getFromHTTPRequest(call, url: url)
            } catch CapacitorSQLiteError.failed(let message) {

                DispatchQueue.main.async(execute: {
                    let msg = "GetFromHTTPRequest: \(message)"
                    self.retHandler.rResult(call: call, message: msg)
                    return
                })
            } catch let error {
                DispatchQueue.main.async(execute: {
                    let msg = "GetFromHTTPRequest: " +
                        "\(error.localizedDescription)"
                    self.retHandler.rResult(call: call, message: msg)
                    return
                })
            }
        })

    }

    // MARK: - Add Observers

    @objc func addObserversToNotificationCenter() {
        // add Observers
        importObserver = NotificationCenter.default.addObserver(
            forName: .importJsonProgress, object: nil, queue: nil,
            using: importJsonProgress)
        exportObserver = NotificationCenter.default.addObserver(
            forName: .exportJsonProgress, object: nil, queue: nil,
            using: exportJsonProgress)
        if let biometricAuth = config?.biometricAuth {
            if biometricAuth == 1 {
                biometricObserver = NotificationCenter.default.addObserver(
                    forName: .biometricEvent, object: nil, queue: nil,
                    using: biometricEvent)
            }
        }
    }

    // MARK: - Handle Notifications

    @objc func importJsonProgress(notification: Notification) {
        guard let info = notification.userInfo as? [String: Any] else { return }
        DispatchQueue.main.async {
            self.notifyListeners("sqliteImportProgressEvent", data: info, retainUntilConsumed: true)
            return
        }
    }
    @objc func exportJsonProgress(notification: Notification) {
        guard let info = notification.userInfo as? [String: Any] else { return }
        DispatchQueue.main.async {
            self.notifyListeners("sqliteExportProgressEvent", data: info, retainUntilConsumed: true)
            return
        }
    }
    @objc func biometricEvent(notification: Notification) {
        guard let info = notification.userInfo as? [String: Any] else { return }
        DispatchQueue.main.async {
            self.notifyListeners("sqliteBiometricEvent", data: info, retainUntilConsumed: true)
            return
        }
    }
    private func sqliteConfig() -> SqliteConfig {
        var config = SqliteConfig()
        config.iosIsEncryption = 1
        config.biometricAuth = 0
        config.iosKeychainPrefix = ""
        let configPlugin = getConfig()
        if let keychainPrefix = configPlugin.getString("iosKeychainPrefix") {
            config.iosKeychainPrefix = keychainPrefix
        }
        if let iosDatabaseLocation = configPlugin.getString("iosDatabaseLocation") {
            config.iosDatabaseLocation = iosDatabaseLocation
        }
        let isEncryption = configPlugin.getBoolean("iosIsEncryption", false)
        if !isEncryption {
            config.iosIsEncryption = 0
        }
        if config.iosIsEncryption == 1 {
            let iosBiometric = configPlugin.getObject("iosBiometric")
            if let bioAuth = iosBiometric?["biometricAuth"] as? Bool {
                if bioAuth {
                    config.biometricAuth = 1
                    if let bioTitle = iosBiometric?["biometricTitle"] as? String {
                        config.biometricTitle = bioTitle.count > 0
                            ? bioTitle
                            : "Biometric login for capacitor sqlite"
                    }
                }
            }
        }
        return config
    }

}
// swiftlint:enable type_body_length
// swiftlint:enable file_length
