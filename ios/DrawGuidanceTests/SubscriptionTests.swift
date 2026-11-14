import XCTest
@testable import DrawGuidance

/// App Store aboneliğinin sunucuyla ortak parçaları.
final class SubscriptionTests: XCTestCase {
    /// Sunucudaki AppleStates.appAccountToken ile aynı test vektörü.
    func testAppAccountTokenMatchesServer() {
        XCTAssertEqual(AppAccountToken.forUid("test-uid-123"), UUID(uuidString: "a8c418aa-055f-8fdb-9801-967606c84f12"))
    }

    func testServerSubscriptionDecodesAndGrants() throws {
        let json = """
        {"planId":"premium","status":"ACTIVE","active":true,"productId":"drawguidance_premium",
         "expiryTime":"2099-01-01T00:00:00.123Z","autoRenewing":true,"features":["ads.none","comic.effects"],
         "store":"GOOGLE_PLAY","appAccountToken":"x","unknown":1}
        """
        let s = try JSONDecoder().decode(ServerSubscription.self, from: Data(json.utf8))
        XCTAssertTrue(s.grants(FeatureKeys.ADS_NONE))
        XCTAssertFalse(s.grants(FeatureKeys.MODEL3D_EXPORT))
        XCTAssertEqual(s.store, ServerSubscription.STORE_PLAY)
        // Önbellek gidiş-dönüşü
        let back = try JSONDecoder().decode(ServerSubscription.self, from: JSONEncoder().encode(s))
        XCTAssertTrue(back.grants(FeatureKeys.ADS_NONE))

        let expired = try JSONDecoder().decode(ServerSubscription.self, from: Data(
            #"{"active":true,"features":["ads.none"],"expiryTime":"2001-01-01T00:00:00Z"}"#.utf8))
        XCTAssertFalse(expired.grants(FeatureKeys.ADS_NONE))
        XCTAssertFalse(ServerSubscription().grants(FeatureKeys.ADS_NONE))
    }
}
