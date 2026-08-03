import SwiftUI

struct MoveRow: View {
    let index: Int
    let san: String
    let uci: String
    let evalText: String

    var body: some View {
        HStack {
            Text("#\(index + 1)")
                .bold()
            VStack(alignment: .leading) {
                Text(san)
                Text(evalText).font(.caption).foregroundColor(.gray)
            }
            Spacer()
            Text(uci).font(.caption)
        }
        .padding(6)
    }
}

struct MoveSelectorView: View {
    @State private var moves: [TopMoveViewModel] = []
    @State private var selectedIndex: Int = 0
    let engineService: EngineService
    let fen: String

    var body: some View {
        VStack {
            Button("Get Top 3 Moves") {
                engineService.analyze(fen: fen, multiPV: 3) { result in
                    DispatchQueue.main.async {
                        switch result {
                        case .success(let topMoves):
                            self.moves = topMoves.enumerated().map { idx, m in
                                TopMoveViewModel(index: idx, san: m.move_san, uci: m.move, eval: m.evaluation)
                            }
                        case .failure(let err):
                            print("analyze error", err)
                            self.moves = []
                        }
                    }
                }
            }
            .padding()

            if moves.count > 0 {
                Picker("Choose move", selection: $selectedIndex) {
                    ForEach(0..<moves.count, id: \.self) { idx in
                        Text("\(idx + 1): \(moves[idx].san)").tag(idx)
                    }
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding()

                MoveRow(index: moves[selectedIndex].index, san: moves[selectedIndex].san, uci: moves[selectedIndex].uci, evalText: moves[selectedIndex].evalText)
                Button("Use this move") {
                    let chosen = moves[selectedIndex]
                    // call your existing move application logic in the app
                    NotificationCenter.default.post(name: Notification.Name("EngineMoveSelected"), object: chosen.uci)
                }
                .padding()
            } else {
                Text("No moves loaded")
            }
        }
    }
}

struct TopMoveViewModel {
    let index: Int
    let san: String
    let uci: String
    let eval: [String: Int]?
    var evalText: String { 
        if let cp = eval?["cp"] { return "\(cp) cp" }
        if let mate = eval?["mate"] { return "Mate in \(mate)" }
        return ""
    }
}
