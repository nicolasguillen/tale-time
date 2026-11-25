import XCTest

/// Walks the whole app the way a child and a grown-up would: shelf, cover, a play-through to an ending (turning the
/// device on the way), "Try another path", the parental gate and the parents area. It fails if the app crashes.
final class WalkthroughTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app.launch()
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func tap(_ label: String, timeout: TimeInterval = 5) {
        let e = element(label)
        XCTAssertTrue(e.waitForExistence(timeout: timeout), "No '\(label)' on screen")
        e.tap()
    }

    /// No story ships inside the app: a debug build downloads them from the draft channel on Firebase Storage, so
    /// publish it first (`cd tools && uv run story publish draft`).
    func testReadToAnEndingAndVisitTheParentsArea() {
        tap("The Fox and the Lantern", timeout: 20)
        if !element("Read").waitForExistence(timeout: 3) && !element("Start over").exists {
            // Not downloaded yet: download it in English (its button says "English · 0.1 MB").
            let english = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "English")).firstMatch
            XCTAssertTrue(english.waitForExistence(timeout: 10), "No language to download in: is the draft channel published?")
            english.tap()
        }
        if element("Start over").waitForExistence(timeout: 60) { tap("Start over") } else { tap("Read", timeout: 60) }

        var turned = false
        for step in 0..<40 {
            if element("The End").exists { break }
            if step == 4, !turned {
                XCUIDevice.shared.orientation = .landscapeLeft
                turned = true
            }
            if element("Next").waitForExistence(timeout: 2) {
                element("Next").tap()
            } else {
                // A fork: the cards are out. One tap on the first card picks it.
                let card = app.coordinate(withNormalizedOffset: CGVector(dx: turned ? 0.68 : 0.27, dy: turned ? 0.55 : 0.72))
                card.tap()
            }
        }
        XCTAssertTrue(element("The End").waitForExistence(timeout: 5), "Never reached an ending")
        tap("Try another path")
        XCTAssertTrue(element("Next").exists || !element("The End").exists)
        XCUIDevice.shared.orientation = .portrait
        tap("Back to the shelf")

        tap("For grown-ups")
        let question = app.staticTexts.matching(NSPredicate(format: "label CONTAINS ' times '")).firstMatch
        XCTAssertTrue(question.waitForExistence(timeout: 5))
        let words = ["three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9]
        let parts = question.label.components(separatedBy: " times ")
        let answer = String(words[parts[0]]! * words[parts[1]]!)
        for digit in answer { tap(String(digit)) }
        tap("Reset all progress", timeout: 10)
        tap("Yes, forget")
        tap("Back to the shelf")
        XCTAssertTrue(element("0/5").waitForExistence(timeout: 5))
    }
}
