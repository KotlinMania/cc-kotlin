#if canImport(Testing)
import Testing
import Cc

@Suite("Cc Swift Export Suite")
struct CcExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "Cc swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Cc

final class CcExportTests: XCTestCase {
    func testSwiftModuleLoads() {
        XCTAssertTrue(true, "Cc swift module imported cleanly")
    }
}
#endif
