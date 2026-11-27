import XCTest

/// Drives the reader's page turn on a device, to measure it there (docs/page-turn.md §10–§11): record it with
/// Instruments ("Animation Hitches" or "Time Profiler") while it runs. Each test opens the fox story, starts it over
/// and marks "Corner up" in its log once the first page's corner has folded up: the moment to attach Instruments,
/// which takes a few seconds, so the test waits 5 s before it starts (an older device keeps only about 13 s of an
/// "Animation Hitches" recording). The app is launched with `-skipReading`
/// (platform/AgentFlags), so every page shows its words at once and its corner folds up about a second after it lands.
/// The draft channel must be published (a debug build downloads from it).
final class PageTurnPerformanceTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app.launchArguments = ["-skipReading"]
        app.launch()
    }

    /// The first element labeled with any of [labels] (the app may speak English or Spanish).
    private func any(_ labels: [String]) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", labels)).firstMatch
    }

    private var corner: XCUIElement { any(["Next", "Siguiente"]) }

    /// Opens the fox story from its first page and waits for its corner, then for Instruments.
    private func openTheFoxStory() {
        let story = any(["The Fox and the Lantern", "El zorro y el farolito"])
        // Not downloaded yet: it's in the store.
        if !story.waitForExistence(timeout: 10) { any(["Store", "Tienda"]).tap() }
        XCTAssertTrue(story.waitForExistence(timeout: 10), "No fox story on the shelf:\n\(app.debugDescription)")
        story.tap()
        let download = any(["Download", "Descargar"])
        let read = any(["Read", "Leer"])
        let startOver = any(["Start over", "Empezar de nuevo"])
        if download.waitForExistence(timeout: 3) { download.tap() }
        XCTAssertTrue(read.waitForExistence(timeout: 60) || startOver.exists, "The story didn't open")
        if startOver.exists { startOver.tap() } else { read.tap() }
        XCTAssertTrue(corner.waitForExistence(timeout: 30), "The first page's corner never folded up")
        XCTContext.runActivity(named: "Corner up") { _ in }
        sleep(5)
    }

    /// Lets the corner breathe, drags it a few times letting go before it turns, then turns the page with a tap.
    func testDragTheFoldedCorner() {
        openTheFoxStory()
        let frame = corner.frame
        let width = app.windows.firstMatch.frame.width
        let origin = app.coordinate(withNormalizedOffset: .zero)
        sleep(5)
        for _ in 0..<6 {
            // From the middle of the flap, up and to the left, not far enough to turn: it settles back. The corner
            // stays in the right 45% of the screen (on a phone too), slowly, well under a flick (0.35 dp/ms).
            let from = origin.withOffset(CGVector(dx: frame.maxX - 60, dy: frame.maxY - 70))
            let to = from.withOffset(CGVector(dx: -width * 0.12, dy: -100))
            from.press(forDuration: 0.1, thenDragTo: to, withVelocity: XCUIGestureVelocity(150), thenHoldForDuration: 0.4)
            // No query in between: each one costs the app a frame or two.
            sleep(1)
        }
        XCTAssertTrue(corner.exists, "The page turned instead of settling back")
        corner.tap()
        sleep(2)
    }

    /// Turns page after page: a tap on the corner, a flick, a slow drag across the page, in turn; at a fork, the first
    /// card (the book flips by itself). It stops at an ending or after 12 page changes.
    func testTurnPages() {
        openTheFoxStory()
        let card = app.descendants(matching: .any).matching(identifier: "choice-0").firstMatch
        let theEnd = any(["The End", "Fin"])
        let window = app.windows.firstMatch.frame
        let origin = app.coordinate(withNormalizedOffset: .zero)
        var changes = 0
        var waited = 0
        while changes < 12 && waited < 20 {
            if corner.exists {
                let frame = corner.frame
                let from = origin.withOffset(CGVector(dx: frame.maxX - 50, dy: frame.maxY - 50))
                switch changes % 3 {
                case 0:
                    corner.tap()
                case 1:
                    // A flick to the left.
                    let to = from.withOffset(CGVector(dx: -window.width * 0.3, dy: -60))
                    from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .fast, thenHoldForDuration: 0)
                default:
                    // A slow drag across the page, let go far to the left.
                    let to = from.withOffset(CGVector(dx: -window.width * 0.8, dy: -window.height * 0.15))
                    from.press(forDuration: 0.1, thenDragTo: to, withVelocity: XCUIGestureVelocity(600), thenHoldForDuration: 0)
                }
                changes += 1
                waited = 0
                // The turn, the new page landing and its corner folding up: no query in between, each one costs the
                // app a frame or two.
                sleep(3)
            } else if card.exists {
                card.tap()
                changes += 1
                waited = 0
                sleep(5)
            } else if theEnd.exists {
                break
            } else {
                sleep(1)
                waited += 1
            }
        }
        XCTAssertGreaterThan(changes, 3, "Hardly any page turned")
    }
}
