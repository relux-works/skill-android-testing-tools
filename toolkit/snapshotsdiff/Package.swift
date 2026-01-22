// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "SnapshotsDiff",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(
            name: "snapshotsdiff",
            targets: ["SnapshotsDiff"]
        )
    ],
    targets: [
        .executableTarget(
            name: "SnapshotsDiff",
            dependencies: [],
            linkerSettings: [
                .linkedFramework("AppKit"),
                .linkedFramework("CoreGraphics")
            ]
        )
    ]
)
