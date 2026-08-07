import SwiftUI

struct HelperOverlay: View {
    @ObservedObject var engine: EngineViewModel
    @ObservedObject var stealth: StealthManager
    @State private var expanded = true

    var body: some View {
        Group {
            if stealth.overlayVisible {
                if stealth.minimalMode {
                    minimalDot
                } else {
                    fullPanel
                }
            } else {
                // Invisible tap target to reveal overlay again
                Button { stealth.show() } label: {
                    Circle().fill(Color.white.opacity(0.05))
                        .frame(width: 24, height: 24)
                        .overlay(Circle().stroke(Color.white.opacity(0.1)))
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .stealthPanic)) { _ in
            stealth.toggleOverlay()
        }
        .onThreeFingerPanic { stealth.toggleOverlay() }
    }

    // MARK: - Minimal (tiny dot)
    private var minimalDot: some View {
        Button { stealth.toggleMinimalMode() } label: {
            Circle()
                .fill(engine.isMyTurn ? Color.green : Color.gray)
                .frame(width: 14, height: 14)
                .overlay(Circle().stroke(Color.white.opacity(0.3), lineWidth: 1))
        }
    }

    // MARK: - Full panel
    private var fullPanel: some View {
        VStack(spacing: 0) {
            header
            if expanded {
                colorOverride
                topMovesSection
                lastMoveSection
                openingSection
                errorSection
            }
        }
        .background(.ultraThinMaterial)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08)))
        .shadow(color: .black.opacity(0.35), radius: 10, y: 3)
    }

    // MARK: - Header
    private var header: some View {
        HStack(spacing: 10) {
            Circle().fill(engine.enabled ? Color.green : Color.gray)
                .frame(width: 8, height: 8)
            Text("Chess Helper").font(.subheadline.bold())

            HStack(spacing: 4) {
                Image(systemName: engine.isMyTurn ? "person.fill" : "hourglass")
                    .font(.caption2)
                Text(engine.isMyTurn ? "Your turn" : "Waiting")
                    .font(.caption2.bold())
            }
            .foregroundColor(engine.isMyTurn ? .green : .secondary)

            if engine.isThinking { ProgressView().scaleEffect(0.7) }

            Spacer()

            if engine.confidence != .high {
                Image(systemName: "exclamationmark.circle.fill")
                    .foregroundColor(.yellow).font(.caption2)
            }

            // Stealth quick toggle
            Button { stealth.toggleMinimalMode() } label: {
                Image(systemName: stealth.minimalMode ? "eye.slash.fill" : "eye.fill")
                    .font(.footnote)
            }
            Button { stealth.hide(reason: "manual") } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.footnote).foregroundColor(.red.opacity(0.7))
            }

            Toggle("", isOn: $engine.enabled).labelsHidden().scaleEffect(0.8)
            Button { withAnimation { expanded.toggle() } } label: {
                Image(systemName: expanded ? "chevron.down" : "chevron.up")
                    .font(.footnote.bold())
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    // MARK: - Color override
    private var colorOverride: some View {
        HStack(spacing: 6) {
            Text("I'm playing:").font(.caption2).foregroundColor(.secondary)
            colorButton(color: "w", label: "White", symbol: "♔")
            colorButton(color: "b", label: "Black", symbol: "♚")
            if engine.manualPlayerColor != nil {
                Button { engine.setManualColor(nil) } label: {
                    Text("Auto").font(.caption2)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(Color.blue.opacity(0.25))
                        .foregroundColor(.blue).cornerRadius(4)
                }
            }
            Spacer()
            if engine.plyCount >= 0 {
                Text("ply \(engine.plyCount)")
                    .font(.caption2.monospaced()).foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 12).padding(.bottom, 6)
    }

    private func colorButton(color: String, label: String, symbol: String) -> some View {
        let active = engine.effectivePlayerColor == color
        return Button { engine.setManualColor(color) } label: {
            HStack(spacing: 3) {
                Text(symbol).font(.subheadline)
                Text(label).font(.caption2.bold())
            }
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(active ? Color.green.opacity(0.35) : Color.white.opacity(0.10))
            .foregroundColor(active ? .green : .white.opacity(0.85))
            .cornerRadius(6)
        }
    }

    // MARK: - Top-3 moves
    private var topMovesSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Divider().opacity(0.3)
            HStack {
                Text(engine.isMyTurn ? "Top moves for you" : "Opponent's best replies")
                    .font(.caption).foregroundColor(.secondary)
                Spacer()
                Text("depth \(engine.depth)")
                    .font(.caption2.monospaced()).foregroundColor(.secondary)
            }
            .padding(.horizontal, 14).padding(.top, 6)

            if engine.topMoves.isEmpty {
                Text("—").font(.title.bold())
                    .padding(.horizontal, 14).padding(.bottom, 4)
            } else {
                ForEach(Array(engine.topMoves.enumerated()), id: \.element.id) { idx, move in
                    topMoveRow(rank: idx + 1, move: move, isBest: idx == 0)
                }
            }

            // Win chance bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.black.opacity(0.6))
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.white)
                        .frame(width: geo.size.width * CGFloat(myWinChance / 100.0))
                }
            }
            .frame(height: 8)
            .padding(.horizontal, 14).padding(.top, 4)
        }
    }

    private func topMoveRow(rank: Int, move: TopMove, isBest: Bool) -> some View {
        HStack(spacing: 8) {
            Text("#\(rank)")
                .font(.caption.bold().monospaced())
                .foregroundColor(rankColor(rank))
                .frame(width: 22)
            Text(move.san.isEmpty ? move.uci : move.san)
                .font(isBest ? .title3.bold() : .callout.bold())
                .foregroundColor(rankColor(rank))
            Text(move.uci)
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
            Spacer()
            Text(move.evalString)
                .font(.caption.bold().monospaced())
                .foregroundColor(rankColor(rank))
        }
        .padding(.horizontal, 14).padding(.vertical, 3)
        .background(isBest ? rankColor(rank).opacity(0.10) : Color.clear)
    }

    private func rankColor(_ rank: Int) -> Color {
        switch rank {
        case 1: return .green
        case 2: return .yellow
        case 3: return .orange
        default: return .white.opacity(0.7)
        }
    }

    // MARK: - Last move quality
    @ViewBuilder
    private var lastMoveSection: some View {
        if engine.lastMoveQuality != .none && !engine.lastMoveUCI.isEmpty {
            HStack(spacing: 8) {
                Text(engine.lastMoveWasMine ? "You:" : "Opp:")
                    .font(.caption).foregroundColor(.secondary)
                Text(engine.lastMoveUCI)
                    .font(.caption.monospaced())
                    .foregroundColor(.white.opacity(0.85))
                Spacer()
                Text(engine.lastMoveQuality.rawValue)
                    .font(.caption.bold())
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(engine.lastMoveQuality.color.opacity(0.25))
                    .foregroundColor(engine.lastMoveQuality.color)
                    .cornerRadius(4)
                if engine.lastMoveCentipawnLoss >= 30 {
                    Text("-\(engine.lastMoveCentipawnLoss)cp")
                        .font(.caption2.monospaced()).foregroundColor(.orange)
                }
            }
            .padding(.horizontal, 14).padding(.top, 4)
        }
    }

    // MARK: - Opening (offline)
    @ViewBuilder
    private var openingSection: some View {
        if !engine.openingName.isEmpty {
            HStack(spacing: 6) {
                Image(systemName: "book.fill")
                    .font(.caption2).foregroundColor(.orange)
                if !engine.openingECO.isEmpty {
                    Text(engine.openingECO)
                        .font(.caption2.bold().monospaced())
                        .foregroundColor(.orange)
                }
                Text(engine.openingName)
                    .font(.caption.bold())
                    .foregroundColor(.white.opacity(0.9))
                    .lineLimit(2)
                Spacer()
            }
            .padding(.horizontal, 14).padding(.top, 4)
        }
    }

    // MARK: - Error
    @ViewBuilder
    private var errorSection: some View {
        if !engine.errorMessage.isEmpty {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.red).font(.caption2)
                Text(engine.errorMessage)
                    .font(.caption2).foregroundColor(.red).lineLimit(2)
            }
            .padding(.horizontal, 14).padding(.bottom, 8)
        } else {
            Color.clear.frame(height: 8)
        }
    }

    // MARK: - Derived
    private var myWinChance: Double {
        engine.effectivePlayerColor == engine.sideToMove
            ? engine.winChance
            : (100 - engine.winChance)
    }
}
