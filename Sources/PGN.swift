import Foundation

struct BoardState {
    var fen: String
    var moveSAN: String
}

enum PGN {

    static func replay(pgn: String) -> [BoardState] {
        let moves = extractMoves(from: pgn)
        var board = Board.initial
        var states: [BoardState] = [BoardState(fen: board.fen(), moveSAN: "")]
        for san in moves {
            if let mv = board.parseSAN(san) {
                board.make(mv)
                states.append(BoardState(fen: board.fen(), moveSAN: san))
            } else {
                break
            }
        }
        return states
    }

    private static func extractMoves(from pgn: String) -> [String] {
        var s = pgn
        s = s.replacingOccurrences(of: "\\[[^\\]]*\\]", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\\{[^}]*\\}", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\\$\\d+", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\\d+\\.(\\.\\.)?", with: "", options: .regularExpression)
        for r in ["1-0","0-1","1/2-1/2","*"] {
            s = s.replacingOccurrences(of: r, with: "")
        }
        return s.split(whereSeparator: { $0.isWhitespace }).map(String.init)
    }
}

fileprivate struct Move {
    var from: Int
    var to: Int
    var promo: Character?
    var isCastle: Bool = false
    var isEP: Bool = false
}

fileprivate struct Board {
    // squares[0..63], index 0 = a1, index 63 = h8
    // Row 0 (indexes 0-7) = rank 1 (white's back rank)
    var squares: [Character]
    var whiteToMove: Bool
    var castleK_w = true, castleQ_w = true, castleK_b = true, castleQ_b = true
    var epTarget: Int? = nil

    static var initial: Board {
        // FIXED: correct orientation. Rank 1 (row 0) = white pieces
        let start = "RNBQKBNR" + "PPPPPPPP" +
                    "........" + "........" +
                    "........" + "........" +
                    "pppppppp" + "rnbqkbnr"
        return Board(squares: Array(start), whiteToMove: true)
    }

    func fen() -> String {
        var rows: [String] = []
        for r in stride(from: 7, through: 0, by: -1) {
            var row = "", empty = 0
            for f in 0..<8 {
                let c = squares[r * 8 + f]
                if c == "." { empty += 1 }
                else { if empty > 0 { row += "\(empty)"; empty = 0 }; row.append(c) }
            }
            if empty > 0 { row += "\(empty)" }
            rows.append(row)
        }
        var castle = ""
        if castleK_w { castle += "K" }
        if castleQ_w { castle += "Q" }
        if castleK_b { castle += "k" }
        if castleQ_b { castle += "q" }
        if castle.isEmpty { castle = "-" }
        let ep = epTarget.map(Self.squareName) ?? "-"
        return "\(rows.joined(separator: "/")) \(whiteToMove ? "w" : "b") \(castle) \(ep) 0 1"
    }

    static func squareName(_ i: Int) -> String {
        let f = i % 8, r = i / 8
        return "\(Character(UnicodeScalar(97 + f)!))\(r + 1)"
    }

    mutating func parseSAN(_ raw: String) -> Move? {
        let san = raw.trimmingCharacters(in: CharacterSet(charactersIn: "+#!?"))
        if san == "O-O" || san == "0-0" { return castleMove(kingside: true) }
        if san == "O-O-O" || san == "0-0-0" { return castleMove(kingside: false) }

        // FIXED: promotion parsing — split off "=X" cleanly
        var body = san
        var promo: Character? = nil
        if let eqRange = body.range(of: "=") {
            let promoPart = body[body.index(after: eqRange.lowerBound)...]
            promo = promoPart.first.map { Character($0.lowercased()) }
            body = String(body[..<eqRange.lowerBound])
        }

        let bodyChars = Array(body)
        guard bodyChars.count >= 2 else { return nil }

        let destStr = String(bodyChars.suffix(2))
        guard let dest = squareIndex(destStr) else { return nil }

        var pieceType: Character = "P"
        var disambig: [Character] = []
        var isCapture = false

        var prefix = Array(bodyChars.dropLast(2))
        if let first = prefix.first, "NBRQK".contains(first) {
            pieceType = first
            prefix.removeFirst()
        }
        for c in prefix {
            if c == "x" { isCapture = true; continue }
            disambig.append(c)
        }

        let side: Character = whiteToMove ? pieceType : Character(String(pieceType).lowercased())
        var candidates: [Int] = []
        for i in 0..<64 where squares[i] == side {
            if canReach(from: i, to: dest, piece: side, capture: isCapture) {
                if matchesDisambig(from: i, disambig: disambig) {
                    candidates.append(i)
                }
            }
        }
        guard let from = candidates.first else { return nil }

        var mv = Move(from: from, to: dest, promo: promo)
        if pieceType == "P" && dest == epTarget && squares[dest] == "." && isCapture {
            mv.isEP = true
        }
        return mv
    }

    private func matchesDisambig(from i: Int, disambig: [Character]) -> Bool {
        if disambig.isEmpty { return true }
        let name = Board.squareName(i)
        for c in disambig {
            if c.isLetter && !"abcdefgh".contains(c) { continue }
            if c.isNumber { if name.last != c { return false } }
            else          { if name.first != c { return false } }
        }
        return true
    }

    private func canReach(from: Int, to: Int, piece: Character, capture: Bool) -> Bool {
        let p = Character(String(piece).uppercased())
        let f1 = from % 8, r1 = from / 8, f2 = to % 8, r2 = to / 8
        let df = f2 - f1, dr = r2 - r1
        let target = squares[to]
        let isOwn = (target != ".") && (piece.isUppercase == target.isUppercase)
        if isOwn { return false }

        switch p {
        case "N": return (abs(df), abs(dr)) == (1, 2) || (abs(df), abs(dr)) == (2, 1)
        case "B": return abs(df) == abs(dr) && clear(from: from, to: to)
        case "R": return (df == 0 || dr == 0) && clear(from: from, to: to)
        case "Q": return (abs(df) == abs(dr) || df == 0 || dr == 0) && clear(from: from, to: to)
        case "K": return abs(df) <= 1 && abs(dr) <= 1
        case "P":
            let dir = piece.isUppercase ? 1 : -1
            let startRank = piece.isUppercase ? 1 : 6
            if df == 0 && target == "." {
                if dr == dir { return true }
                if dr == 2 * dir && r1 == startRank && squares[from + 8 * dir] == "." { return true }
            }
            if abs(df) == 1 && dr == dir && (target != "." || to == (epTarget ?? -1)) { return true }
            return false
        default: return false
        }
    }

    private func clear(from: Int, to: Int) -> Bool {
        let f1 = from % 8, r1 = from / 8, f2 = to % 8, r2 = to / 8
        let df = f2 - f1, dr = r2 - r1
        let steps = max(abs(df), abs(dr))
        if steps <= 1 { return true }
        let sf = df.signum(), sr = dr.signum()
        for i in 1..<steps {
            let idx = (r1 + sr * i) * 8 + (f1 + sf * i)
            if squares[idx] != "." { return false }
        }
        return true
    }

    private mutating func castleMove(kingside: Bool) -> Move? {
        let rank = whiteToMove ? 0 : 7
        let king = whiteToMove ? "K" : "k"
        guard squares[rank * 8 + 4] == Character(king) else { return nil }
        let to = kingside ? rank * 8 + 6 : rank * 8 + 2
        return Move(from: rank * 8 + 4, to: to, promo: nil, isCastle: true)
    }

    private func squareIndex(_ s: String) -> Int? {
        guard s.count == 2 else { return nil }
        let cs = Array(s)
        guard let ascii = cs[0].asciiValue else { return nil }
        let f = Int(ascii) - 97
        guard let d = cs[1].wholeNumberValue else { return nil }
        let r = d - 1
        if !(0..<8).contains(f) || !(0..<8).contains(r) { return nil }
        return r * 8 + f
    }

    mutating func make(_ mv: Move) {
        var piece = squares[mv.from]
        squares[mv.from] = "."
        if mv.isEP {
            let capRank = whiteToMove ? mv.to - 8 : mv.to + 8
            squares[capRank] = "."
        }
        if mv.isCastle {
            let rank = whiteToMove ? 0 : 7
            if mv.to % 8 == 6 {
                squares[rank * 8 + 5] = squares[rank * 8 + 7]
                squares[rank * 8 + 7] = "."
            } else {
                squares[rank * 8 + 3] = squares[rank * 8 + 0]
                squares[rank * 8 + 0] = "."
            }
        }
        if let promo = mv.promo {
            piece = whiteToMove ? Character(String(promo).uppercased()) : promo
        }
        squares[mv.to] = piece

        if piece == "K" { castleK_w = false; castleQ_w = false }
        if piece == "k" { castleK_b = false; castleQ_b = false }
        if mv.from == 0  || mv.to == 0  { castleQ_w = false }
        if mv.from == 7  || mv.to == 7  { castleK_w = false }
        if mv.from == 56 || mv.to == 56 { castleQ_b = false }
        if mv.from == 63 || mv.to == 63 { castleK_b = false }

        epTarget = nil
        if piece == "P" && mv.to - mv.from == 16 { epTarget = mv.from + 8 }
        if piece == "p" && mv.from - mv.to == 16 { epTarget = mv.from - 8 }

        whiteToMove.toggle()
    }
}
