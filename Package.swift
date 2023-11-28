// swift-tools-version:5.8
import PackageDescription

let package = Package(
    name: "FitJournalKMP",
    platforms: [
        .iOS(.v14)
    ],
    products: [
        .library(
            name: "FitJournalKMP",
            targets: ["FitJournalKMP"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "FitJournalKMP",
            path: "./FitJournalKMP.xcframework"
        ),
    ]
)
