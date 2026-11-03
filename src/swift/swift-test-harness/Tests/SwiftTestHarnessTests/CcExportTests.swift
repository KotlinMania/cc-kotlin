#if canImport(Testing)
import Testing
import Cc

@Suite("Cc Swift Export Suite")
struct CcExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "Cc swift module imported cleanly")
    }

    @Test("Build configuration and chaining")
    func buildConfigurationChaining() {
        let build = Build.Companion.shared.new()
        _ = build.target(target: "aarch64-unknown-linux-gnu")
        _ = build.optLevel(optLevel: 3)
        _ = build.file(p: "src/test.c")
        _ = build.include(dir: "include")
        _ = build.flag(flag: "-Wall")
        _ = build.define(varName: "FEATURE_X", valName: "1")
        _ = build.noDefaultFlags(noDefaultFlags: true)
        #expect(build.noDefaultFlags == true)
    }

    @Test("AsmFileExt enum and fromPath resolution")
    func asmFileExtResolution() {
        #expect(AsmFileExt.DotAsm.description == "DotAsm")
        #expect(AsmFileExt.DotS.description == "DotS")
        #expect(AsmFileExt.Companion.shared.fromPath(file: "entry.s") == .DotS)
        #expect(AsmFileExt.Companion.shared.fromPath(file: "entry.asm") == .DotAsm)
        #expect(AsmFileExt.Companion.shared.fromPath(file: "entry.c") == nil)
    }

    @Test("Android compiler detection helpers")
    func androidCompilerDetection() {
        #expect(NEW_STANDALONE_ANDROID_COMPILERS.contains("aarch64-linux-android21-clang"))
        #expect(NEW_STANDALONE_ANDROID_COMPILERS.contains("x86_64-linux-android21-clang"))
        #expect(androidClangCompilerUsesTargetArgInternally(clangPath: "bin/aarch64-linux-android-clang"))
        #expect(!androidClangCompilerUsesTargetArgInternally(clangPath: "usr/bin/clang"))
    }

    @Test("LLVM MinGW wrapper detection")
    func llvmMingwWrapperDetection() {
        #expect(isLlvmMingwWrapper(clangPath: "/usr/bin/x86_64-w64-mingw32-clang"))
        #expect(isLlvmMingwWrapper(clangPath: "/usr/bin/x86_64-w64-mingw32-clang++"))
        #expect(!isLlvmMingwWrapper(clangPath: "/usr/bin/gcc"))
    }
}
#elseif canImport(XCTest)
import XCTest
import Cc

final class CcExportTests: XCTestCase {
    func testSwiftModuleLoads() {
        XCTAssertTrue(true, "Cc swift module imported cleanly")
    }

    func testBuildConfigurationChaining() {
        let build = Build.Companion.shared.new()
        _ = build.target(target: "aarch64-unknown-linux-gnu")
        _ = build.optLevel(optLevel: 3)
        _ = build.file(p: "src/test.c")
        _ = build.include(dir: "include")
        _ = build.flag(flag: "-Wall")
        _ = build.define(varName: "FEATURE_X", valName: "1")
        _ = build.noDefaultFlags(noDefaultFlags: true)
        XCTAssertTrue(build.noDefaultFlags)
    }

    func testAsmFileExtResolution() {
        XCTAssertEqual(AsmFileExt.DotAsm.description, "DotAsm")
        XCTAssertEqual(AsmFileExt.DotS.description, "DotS")
        XCTAssertEqual(AsmFileExt.Companion.shared.fromPath(file: "entry.s"), .DotS)
        XCTAssertEqual(AsmFileExt.Companion.shared.fromPath(file: "entry.asm"), .DotAsm)
        XCTAssertNil(AsmFileExt.Companion.shared.fromPath(file: "entry.c"))
    }

    func testAndroidCompilerDetection() {
        XCTAssertTrue(NEW_STANDALONE_ANDROID_COMPILERS.contains("aarch64-linux-android21-clang"))
        XCTAssertTrue(NEW_STANDALONE_ANDROID_COMPILERS.contains("x86_64-linux-android21-clang"))
        XCTAssertTrue(androidClangCompilerUsesTargetArgInternally(clangPath: "bin/aarch64-linux-android-clang"))
        XCTAssertFalse(androidClangCompilerUsesTargetArgInternally(clangPath: "usr/bin/clang"))
    }

    func testLlvmMingwWrapperDetection() {
        XCTAssertTrue(isLlvmMingwWrapper(clangPath: "/usr/bin/x86_64-w64-mingw32-clang"))
        XCTAssertTrue(isLlvmMingwWrapper(clangPath: "/usr/bin/x86_64-w64-mingw32-clang++"))
        XCTAssertFalse(isLlvmMingwWrapper(clangPath: "/usr/bin/gcc"))
    }
}
#endif
