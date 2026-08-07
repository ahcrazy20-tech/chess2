import SwiftUI

struct ContentView: View {
    @StateObject private var engine = EngineViewModel()
    @StateObject private var stealth = StealthManager()
    @StateObject private var router = AppRouter()

    var body: some View {
        TabView(selection: $router.selectedTab) {

            PlayView(engine: engine, stealth: stealth, router: router)
                .tabItem { Label("Play", systemImage: "gamecontroller.fill") }
                .tag(0)

            ReviewView(router: router)
                .tabItem { Label("Review", systemImage: "magnifyingglass") }
                .tag(1)

            OpeningsView(router: router)
                .tabItem { Label("Openings", systemImage: "book.fill") }
                .tag(2)

            PuzzlesView(router: router)
                .tabItem { Label("Puzzles", systemImage: "puzzlepiece.fill") }
                .tag(3)

            StatsView(router: router)
                .tabItem { Label("Stats", systemImage: "chart.bar.fill") }
                .tag(4)

            SettingsView(stealth: stealth)
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(5)
        }
        .tint(.green)
        .task {
            await StockfishEngine.shared.start()
        }
    }
}
