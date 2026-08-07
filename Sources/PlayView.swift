import SwiftUI

struct PlayView: View {
    @ObservedObject var engine: EngineViewModel
    @ObservedObject var stealth: StealthManager
    @ObservedObject var router: AppRouter
    @State private var showDisclaimer = true

    var body: some View {
        VStack(spacing: 0) {
            navBar
            ZStack(alignment: .bottom) {
                ChessWebView(engine: engine, router: router)
                HelperOverlay(engine: engine, stealth: stealth)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
            }
        }
        .overlay(alignment: .center) {
            if showDisclaimer {
                DisclaimerCard {
                    withAnimation { showDisclaimer = false }
                }
                .padding(24)
            }
        }
    }

    private var navBar: some View {
        HStack(spacing: 8) {
            navButton(icon: "house.fill", label: "Home") {
                router.openInPlay("https://www.chess.com/")
            }
            navButton(icon: "globe", label: "Online") {
                router.openInPlay("https://www.chess.com/play/online/new")
            }
            navButton(icon: "cpu", label: "Bots") {
                router.openInPlay("https://www.chess.com/play/computer")
            }
            navButton(icon: "puzzlepiece.fill", label: "Puzzles") {
                router.openInPlay("https://www.chess.com/puzzles")
            }
            navButton(icon: "chart.line.uptrend.xyaxis", label: "Analysis") {
                router.openInPlay("https://www.chess.com/analysis")
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 6)
        .background(Color.black.opacity(0.6))
    }

    private func navButton(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon).font(.system(size: 14))
                Text(label).font(.system(size: 10))
            }
            .frame(maxWidth: .infinity)
            .foregroundColor(.white.opacity(0.9))
        }
    }
}

struct DisclaimerCard: View {
    var onDismiss: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.shield.fill")
                .font(.system(size: 40)).foregroundColor(.yellow)
            Text("Fair-Play Notice").font(.title2.bold())
            Text("Using engine assistance during live online games violates Chess.com's Terms of Service and will get your account banned.\n\nStealth mode is enabled by default:\n• Shake or 3-finger tap to hide instantly\n• Auto-hides on screen recording\n• Auto-hides on screenshot\n• Auto-hides when app backgrounded\n\nUse this helper responsibly.")
                .font(.callout).multilineTextAlignment(.leading)
                .foregroundColor(.white.opacity(0.85))
            Button(action: onDismiss) {
                Text("I understand")
                    .font(.headline).frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.accentColor).foregroundColor(.white)
                    .cornerRadius(10)
            }
        }
        .padding(20)
        .background(.ultraThinMaterial)
        .cornerRadius(18).shadow(radius: 20)
    }
}
