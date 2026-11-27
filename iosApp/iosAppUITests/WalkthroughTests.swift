import XCTest

/// Walks the whole app the way a child and a grown-up would: shelf, cover (its download and its language cog), a
/// play-through to an ending (turning the device on the way), "Try another path", the parental gate and the parents
/// area, in Spanish, the app's language until a grown-up picks one (domain/Language). It fails if the app crashes.
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
        tap("El zorro y el farolito", timeout: 20)
        if !element("Leer").waitForExistence(timeout: 3) && !element("Empezar de nuevo").exists {
            // Not downloaded yet: one download brings every language.
            XCTAssertTrue(element("Descargar").waitForExistence(timeout: 10), "Nothing to download: is the draft channel published?")
            element("Descargar").tap()
        }
        _ = element("Leer").waitForExistence(timeout: 60) || element("Empezar de nuevo").waitForExistence(timeout: 1)
        // The cog lists every language it's published in.
        tap("Idioma del cuento")
        let english = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "English")).firstMatch
        XCTAssertTrue(english.waitForExistence(timeout: 5), "No English in the cog's list")
        tap("Cerrar")
        if element("Empezar de nuevo").exists { tap("Empezar de nuevo") } else { tap("Leer") }

        var turned = false
        for step in 0..<40 {
            if element("Fin").exists { break }
            if step == 4, !turned {
                XCUIDevice.shared.orientation = .landscapeLeft
                turned = true
            }
            // A page's corner folds up ("Siguiente") once it's read; a fork's cards and "Fin" rise by themselves.
            let next = element("Siguiente")
            let card = app.descendants(matching: .any).matching(identifier: "choice-0").firstMatch
            var waited = 0
            while !next.exists && !card.exists && !element("Fin").exists && waited < 90 {
                sleep(1)
                waited += 1
            }
            if element("Fin").exists { break }
            if next.exists {
                // A tap on the folded corner turns the page.
                next.tap()
            } else {
                // A fork: one tap on the first card picks it, and the book flips by itself to its page.
                card.tap()
                waited = 0
                while card.exists && waited < 10 {
                    sleep(1)
                    waited += 1
                }
            }
        }
        XCTAssertTrue(element("Fin").waitForExistence(timeout: 5), "Never reached an ending")
        tap("Probar otro camino")
        XCTAssertTrue(element("Siguiente").exists || !element("Fin").exists)
        XCUIDevice.shared.orientation = .portrait
        tap("Volver a los cuentos")

        tap("Para adultos")
        let question = app.staticTexts.matching(NSPredicate(format: "label CONTAINS ' por '")).firstMatch
        XCTAssertTrue(question.waitForExistence(timeout: 5))
        let words = ["tres": 3, "cuatro": 4, "cinco": 5, "seis": 6, "siete": 7, "ocho": 8, "nueve": 9]
        let parts = question.label.components(separatedBy: " por ")
        let answer = String(words[parts[0]]! * words[parts[1]]!)
        for digit in answer { tap(String(digit)) }
        tap("Borrar todo el progreso", timeout: 10)
        tap("Sí, olvidar")
        tap("Volver a los cuentos")
        XCTAssertTrue(element("Mis libros").waitForExistence(timeout: 5))
    }
}
