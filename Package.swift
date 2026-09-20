// swift-tools-version: 5.9
import Foundation
import PackageDescription

// Apps override this dependency with the @capacitor/ios they installed. To build this package on its own
// against a local runtime, point CAPACITOR_IOS_PATH at it.
let capacitor: Package.Dependency
if let path = ProcessInfo.processInfo.environment["CAPACITOR_IOS_PATH"] {
    capacitor = .package(name: "capacitor-swift-pm", path: path)
} else {
    capacitor = .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
}

let package = Package(
    name: "CapacitorCommunitySqlite",
    platforms: [.iOS(.v17)],
    products: [
        .library(
            name: "CapacitorCommunitySqlite",
            targets: ["CapacitorSQLitePlugin"])
    ],
    dependencies: [
        capacitor,
        .package(url: "https://github.com/sqlcipher/SQLCipher.swift.git", from: "4.14.0"),
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.0")
    ],
    targets: [
        .target(
            name: "CapacitorSQLitePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "SQLCipher", package: "SQLCipher.swift"),
                .product(name: "ZIPFoundation", package: "ZIPFoundation")
            ],
            path: "ios/Sources/CapacitorSQLitePlugin"),
        .testTarget(
            name: "CapacitorSQLitePluginTests",
            dependencies: ["CapacitorSQLitePlugin"],
            path: "ios/Tests/CapacitorSQLitePluginTests")
    ]
)
