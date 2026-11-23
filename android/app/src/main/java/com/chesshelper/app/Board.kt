package com.chesshelper.app

/**
 * A compact chess board: FEN in/out, SAN parsing/generation, move making and
 * legal move generation.
 *
 * Port of Board.swift (PGN.swift) with three upgrades over the iOS version:
 *   1. full legal move generation (king-safety aware) — powers the built-in
 *      fallback engine and makes SAN output exact,
 *   2. FEN round-tripping (fromFEN) so any position can be loaded,
 *   3. `toSAN()` so engine UCI moves are shown in real notation (Nf3, exd5…).
 *
 * Square indexing: a1 = 0 … h8 = 63 (file = idx % 8, rank = idx / 8).
 * Pieces: uppercase = white, lowercase = black, '.' = empty.
 */

data class Move(
    val from: Int,
    val to: Int,
    val promo: Char? = null,   // lowercase 'q','r','b','n'
    val isCastle: Boolean = false,
    val isEP: Boolean = false
) {
    /** UCI string like "e2e4" / "e7e8q". */
    fun uci(): String {
        val base = Board.squareName(from) + Board.squareName(to)
        return if (promo != null) base + promo else base
    }
}

class Board {
    val squares = CharArray(64) { '.' }
    var whiteToMove: Boolean = true
    var castleKw = true
    var castleQw = true
    var castleKb = true
    var castleQb = true
    var epTarget: Int? = null

    companion object {
        const val START =
            "RNBQKBNRPPPPPPPP................................pppppppprnbqkbnr"

        private val KNIGHT_DELTAS = intArrayOf(
            -17, -15, -10, -6, 6, 10, 15, 17
        )
        private val BISHOP_DIRS = arrayOf(
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        )
        private val ROOK_DIRS = arrayOf(
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)
        )
        private val QUEEN_DIRS = arrayOf(
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1),
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)
        )

        fun initial(): Board {
            val b = Board()
            for (i in 0 until 64) b.squares[i] = START[i]
            return b
        }

        /** Parse a FEN (board + side to move + castling + ep; clock fields optional). */
        fun fromFEN(fen: String): Board {
            val b = Board()
            val parts = fen.trim().split(Regex("\\s+"))
            val rows = parts.getOrElse(0) { "8/8/8/8/8/8/8/8" }.split("/")
            for (r in 0 until 8) {
                val row = rows.getOrElse(r) { "8" }
                val rankFromTop = 7 - r   // FEN row 0 = rank 8
                var file = 0
                for (ch in row) {
                    if (ch.isDigit()) {
                        file += ch - '0'
                    } else if (file in 0..7) {
                        b.squares[rankFromTop * 8 + file] = ch
                        file++
                    }
                }
            }
            b.whiteToMove = (parts.getOrElse(1) { "w" } != "b")
            val castle = parts.getOrElse(2) { "-" }
            b.castleKw = castle.contains('K')
            b.castleQw = castle.contains('Q')
            b.castleKb = castle.contains('k')
            b.castleQb = castle.contains('q')
            val ep = parts.getOrElse(3) { "-" }
            b.epTarget = if (ep.length == 2 && ep != "-") squareIndex(ep) else null
            return b
        }

        fun squareName(i: Int): String {
            val f = i % 8
            val r = i / 8
            return "" + ('a' + f) + ('1' + r)
        }

        fun squareIndex(s: String): Int? {
            if (s.length != 2) return null
            val f = s[0] - 'a'
            val r = s[1] - '1'
            if (f !in 0..7 || r !in 0..7) return null
            return r * 8 + f
        }
    }

    fun copy(): Board {
        val b = Board()
        System.arraycopy(squares, 0, b.squares, 0, 64)
        b.whiteToMove = whiteToMove
        b.castleKw = castleKw; b.castleQw = castleQw
        b.castleKb = castleKb; b.castleQb = castleQb
        b.epTarget = epTarget
        return b
    }

    /** FEN with halfmove/fullmove fixed at "0 1" (fine for engine analysis). */
    fun fen(): String {
        val rows = ArrayList<String>()
        for (r in 7 downTo 0) {
            var row = ""
            var empty = 0
            for (f in 0 until 8) {
                val c = squares[r * 8 + f]
                if (c == '.') {
                    empty++
                } else {
                    if (empty > 0) { row += empty; empty = 0 }
                    row += c
                }
            }
            if (empty > 0) row += empty
            rows.add(row)
        }
        var castle = ""
        if (castleKw) castle += "K"
        if (castleQw) castle += "Q"
        if (castleKb) castle += "k"
        if (castleQb) castle += "q"
        if (castle.isEmpty()) castle = "-"
        val ep = epTarget?.let { squareName(it) } ?: "-"
        return rows.joinToString("/") + " " + (if (whiteToMove) "w" else "b") +
            " $castle $ep 0 1"
    }

    // ------------------------------------------------------------------
    // Move making
    // ------------------------------------------------------------------

    fun make(mv: Move) {
        var piece = squares[mv.from]
        squares[mv.from] = '.'

        if (mv.isEP) {
            val capRank = if (whiteToMove) mv.to - 8 else mv.to + 8
            squares[capRank] = '.'
        }

        // Castling: move the rook too (detect 2-file king move).
        val isKing = piece == 'K' || piece == 'k'
        val df = (mv.to % 8) - (mv.from % 8)
        val castleMove = mv.isCastle || (isKing && kotlin.math.abs(df) == 2)
        if (castleMove) {
            val rank = if (whiteToMove) 0 else 7
            if (mv.to % 8 == 6) { // kingside
                squares[rank * 8 + 5] = squares[rank * 8 + 7]
                squares[rank * 8 + 7] = '.'
            } else {              // queenside
                squares[rank * 8 + 3] = squares[rank * 8 + 0]
                squares[rank * 8 + 0] = '.'
            }
        }

        if (mv.promo != null) {
            piece = if (whiteToMove) mv.promo.uppercaseChar() else mv.promo
        }
        squares[mv.to] = piece

        // Castling rights.
        if (piece == 'K') { castleKw = false; castleQw = false }
        if (piece == 'k') { castleKb = false; castleQb = false }
        if (mv.from == 0 || mv.to == 0) castleQw = false
        if (mv.from == 7 || mv.to == 7) castleKw = false
        if (mv.from == 56 || mv.to == 56) castleQb = false
        if (mv.from == 63 || mv.to == 63) castleKb = false

        // En-passant target.
        epTarget = null
        if (piece == 'P' && mv.to - mv.from == 16) epTarget = mv.from + 8
        if (piece == 'p' && mv.from - mv.to == 16) epTarget = mv.from - 8

        whiteToMove = !whiteToMove
    }

    // ------------------------------------------------------------------
    // Attack detection / legality
    // ------------------------------------------------------------------

    fun kingSquare(white: Boolean): Int {
        val k = if (white) 'K' else 'k'
        for (i in 0 until 64) if (squares[i] == k) return i
        return -1
    }

    /** Is [sq] attacked by a piece of the side [byWhite]? */
    fun isSquareAttacked(sq: Int, byWhite: Boolean): Boolean {
        val target = sq
        val tf = sq % 8
        val tr = sq / 8

        // Pawns: a white pawn on sq-7/sq-9 attacks sq (moving up).
        if (byWhite) {
            for (delta in intArrayOf(-7, -9)) {
                val s = target + delta
                if (s in 0..63 && squares[s] == 'P') {
                    val df = kotlin.math.abs((s % 8) - tf)
                    if (df == 1) return true
                }
            }
        } else {
            for (delta in intArrayOf(7, 9)) {
                val s = target + delta
                if (s in 0..63 && squares[s] == 'p') {
                    val df = kotlin.math.abs((s % 8) - tf)
                    if (df == 1) return true
                }
            }
        }

        // Knights.
        val knight = if (byWhite) 'N' else 'n'
        for (d in KNIGHT_DELTAS) {
            val s = target + d
            if (s in 0..63 && squares[s] == knight &&
                kotlin.math.abs((s % 8) - tf) <= 2
            ) return true
        }

        // King.
        val king = if (byWhite) 'K' else 'k'
        for (dr in -1..1) for (df in -1..1) {
            if (dr == 0 && df == 0) continue
            val f = tf + df; val r = tr + dr
            if (f in 0..7 && r in 0..7 && squares[r * 8 + f] == king) return true
        }

        // Sliding pieces.
        val bishop = if (byWhite) 'B' else 'b'
        val rook = if (byWhite) 'R' else 'r'
        val queen = if (byWhite) 'Q' else 'q'
        for ((dr, df) in BISHOP_DIRS) {
            val s = slide(tf, tr, dr, df)
            if (s >= 0 && (squares[s] == bishop || squares[s] == queen)) return true
        }
        for ((dr, df) in ROOK_DIRS) {
            val s = slide(tf, tr, dr, df)
            if (s >= 0 && (squares[s] == rook || squares[s] == queen)) return true
        }
        return false
    }

    private fun slide(f0: Int, r0: Int, dr: Int, df: Int): Int {
        var f = f0 + df
        var r = r0 + dr
        while (f in 0..7 && r in 0..7) {
            val idx = r * 8 + f
            if (squares[idx] != '.') return idx
            f += df; r += dr
        }
        return -1
    }

    fun inCheck(white: Boolean): Boolean {
        val k = kingSquare(white)
        return k >= 0 && isSquareAttacked(k, byWhite = !white)
    }

    // ------------------------------------------------------------------
    // Move generation
    // ------------------------------------------------------------------

    /** All legal moves for the side to move. */
    fun legalMoves(): List<Move> {
        val out = ArrayList<Move>(40)
        for (mv in pseudoMoves()) {
            val b = copy()
            b.make(mv)
            if (!b.inCheck(!b.whiteToMove)) out.add(mv)
        }
        return out
    }

    private fun pseudoMoves(): List<Move> {
        val out = ArrayList<Move>(64)
        val white = whiteToMove
        for (i in 0 until 64) {
            val p = squares[i]
            if (p == '.') continue
            val isWhitePiece = p.isUpperCase()
            if (isWhitePiece != white) continue
            when (p.uppercaseChar()) {
                'P' -> genPawn(i, white, out)
                'N' -> genStep(i, white, KNIGHT_DELTAS, out)
                'B' -> genSlide(i, white, BISHOP_DIRS, out)
                'R' -> genSlide(i, white, ROOK_DIRS, out)
                'Q' -> genSlide(i, white, QUEEN_DIRS, out)
                'K' -> genKing(i, white, out)
            }
        }
        return out
    }

    private fun canLand(i: Int, white: Boolean): Boolean {
        val t = squares[i]
        return t == '.' || t.isUpperCase() != white
    }

    private fun genPawn(i: Int, white: Boolean, out: MutableList<Move>) {
        val f = i % 8
        val r = i / 8
        val dir = if (white) 1 else -1
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0

        val r1 = r + dir
        if (r1 in 0..7) {
            val one = r1 * 8 + f
            if (squares[one] == '.') {
                if (r1 == promoRank) {
                    for (pr in charArrayOf('q', 'r', 'b', 'n')) out.add(Move(i, one, pr))
                } else {
                    out.add(Move(i, one))
                    if (r == startRank) {
                        val two = (r + 2 * dir) * 8 + f
                        if (squares[two] == '.') out.add(Move(i, two))
                    }
                }
            }
            // Captures (incl. en passant).
            for (df in intArrayOf(-1, 1)) {
                val cf = f + df
                if (cf !in 0..7) continue
                val dest = r1 * 8 + cf
                val t = squares[dest]
                val isCapture = (t != '.' && t.isUpperCase() != white) || dest == epTarget
                if (!isCapture) continue
                val isEp = t == '.' && dest == epTarget
                if (r1 == promoRank) {
                    for (pr in charArrayOf('q', 'r', 'b', 'n')) {
                        out.add(Move(i, dest, pr, isCastle = false, isEP = isEp))
                    }
                } else {
                    out.add(Move(i, dest, isEP = isEp))
                }
            }
        }
    }

    private fun genStep(i: Int, white: Boolean, deltas: IntArray, out: MutableList<Move>) {
        val f = i % 8
        for (d in deltas) {
            val t = i + d
            if (t !in 0..63) continue
            if (kotlin.math.abs((t % 8) - f) > 2) continue
            if (canLand(t, white)) out.add(Move(i, t))
        }
    }

    private fun genSlide(i: Int, white: Boolean, dirs: Array<Pair<Int, Int>>, out: MutableList<Move>) {
        val f = i % 8
        val r = i / 8
        for ((dr, df) in dirs) {
            var cf = f + df
            var cr = r + dr
            while (cf in 0..7 && cr in 0..7) {
                val t = cr * 8 + cf
                if (squares[t] == '.') {
                    out.add(Move(i, t))
                } else {
                    if (squares[t].isUpperCase() != white) out.add(Move(i, t))
                    break
                }
                cf += df; cr += dr
            }
        }
    }

    private fun genKing(i: Int, white: Boolean, out: MutableList<Move>) {
        val f = i % 8
        val r = i / 8
        for (dr in -1..1) for (df in -1..1) {
            if (dr == 0 && df == 0) continue
            val cf = f + df; val cr = r + dr
            if (cf in 0..7 && cr in 0..7) {
                val t = cr * 8 + cf
                if (canLand(t, white)) out.add(Move(i, t))
            }
        }
        // Castling.
        val rank = if (white) 0 else 7
        if (i == rank * 8 + 4 && !inCheck(white)) {
            val kSide = if (white) castleKw else castleKb
            val qSide = if (white) castleQw else castleQb
            val rookK = if (white) 'R' else 'r'
            if (kSide && squares[rank * 8 + 7] == rookK &&
                squares[rank * 8 + 5] == '.' && squares[rank * 8 + 6] == '.' &&
                !isSquareAttacked(rank * 8 + 5, byWhite = !white) &&
                !isSquareAttacked(rank * 8 + 6, byWhite = !white)
            ) {
                out.add(Move(i, rank * 8 + 6, isCastle = true))
            }
            if (qSide && squares[rank * 8 + 0] == rookK &&
                squares[rank * 8 + 1] == '.' && squares[rank * 8 + 2] == '.' &&
                squares[rank * 8 + 3] == '.' &&
                !isSquareAttacked(rank * 8 + 3, byWhite = !white) &&
                !isSquareAttacked(rank * 8 + 2, byWhite = !white)
            ) {
                out.add(Move(i, rank * 8 + 2, isCastle = true))
            }
        }
    }

    // ------------------------------------------------------------------
    // SAN parsing / generation
    // ------------------------------------------------------------------

    /** Parse a SAN move like "Nf3", "exd5", "O-O", "e8=Q+". */
    fun parseSAN(raw: String): Move? {
        val san = raw.trim().trimEnd('+', '#', '!', '?')
        if (san == "O-O" || san == "0-0") return castleMove(true)
        if (san == "O-O-O" || san == "0-0-0") return castleMove(false)

        val body = san.split("=")[0]
        val chars = body.toCharArray()
        if (chars.size < 2) return null

        val destStr = body.substring(body.length - 2)
        val dest = squareIndex(destStr) ?: return null

        var pieceType = 'P'
        val disambig = ArrayList<Char>()
        var isCapture = false

        var prefix = body.substring(0, body.length - 2)
        if (prefix.isNotEmpty() && prefix[0] in "NBRQK") {
            pieceType = prefix[0]
            prefix = prefix.substring(1)
        }
        for (c in prefix) {
            if (c == 'x') { isCapture = true; continue }
            disambig.add(c)
        }

        val promo: Char? = if (san.contains('=')) {
            san.split("=").getOrElse(1) { "" }.firstOrNull()?.lowercaseChar()
        } else null

        val side = if (whiteToMove) pieceType else pieceType.lowercaseChar()
        val candidates = ArrayList<Int>()
        for (i in 0 until 64) {
            if (squares[i] != side) continue
            if (canReach(i, dest, side, isCapture) && matchesDisambig(i, disambig)) {
                candidates.add(i)
            }
        }
        // Prefer the candidate whose move is legal (king safe).
        for (from in candidates) {
            var mv = Move(from, dest, promo)
            if (pieceType == 'P' && dest == epTarget && squares[dest] == '.' && isCapture) {
                mv = Move(from, dest, promo, isEP = true)
            }
            val b = copy()
            b.make(mv)
            if (!b.inCheck(!b.whiteToMove)) return mv
        }
        return null
    }

    private fun castleMove(kingside: Boolean): Move? {
        val rank = if (whiteToMove) 0 else 7
        val king = if (whiteToMove) 'K' else 'k'
        if (squares[rank * 8 + 4] != king) return null
        val to = if (kingside) rank * 8 + 6 else rank * 8 + 2
        return Move(rank * 8 + 4, to, isCastle = true)
    }

    private fun matchesDisambig(from: Int, disambig: List<Char>): Boolean {
        if (disambig.isEmpty()) return true
        val name = squareName(from)
        for (c in disambig) {
            if (c.isDigit()) {
                if (name[1] != c) return false
            } else if (c in 'a'..'h') {
                if (name[0] != c) return false
            }
        }
        return true
    }

    private fun canReach(from: Int, to: Int, piece: Char, capture: Boolean): Boolean {
        val p = piece.uppercaseChar()
        val f1 = from % 8; val r1 = from / 8
        val f2 = to % 8; val r2 = to / 8
        val df = f2 - f1; val dr = r2 - r1
        val target = squares[to]
        if (target != '.' && target.isUpperCase() == piece.isUpperCase()) return false

        return when (p) {
            'N' -> (kotlin.math.abs(df) == 1 && kotlin.math.abs(dr) == 2) ||
                (kotlin.math.abs(df) == 2 && kotlin.math.abs(dr) == 1)
            'B' -> kotlin.math.abs(df) == kotlin.math.abs(dr) && clear(from, to)
            'R' -> (df == 0 || dr == 0) && clear(from, to)
            'Q' -> (kotlin.math.abs(df) == kotlin.math.abs(dr) || df == 0 || dr == 0) &&
                clear(from, to)
            'K' -> kotlin.math.abs(df) <= 1 && kotlin.math.abs(dr) <= 1
            'P' -> {
                val dir = if (piece.isUpperCase()) 1 else -1
                val startRank = if (piece.isUpperCase()) 1 else 6
                when {
                    df == 0 && target == '.' -> {
                        when (dr) {
                            dir -> true
                            2 * dir -> r1 == startRank && squares[from + 8 * dir] == '.'
                            else -> false
                        }
                    }
                    kotlin.math.abs(df) == 1 && dr == dir ->
                        target != '.' || to == (epTarget ?: -1)
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun clear(from: Int, to: Int): Boolean {
        val f1 = from % 8; val r1 = from / 8
        val f2 = to % 8; val r2 = to / 8
        val df = f2 - f1; val dr = r2 - r1
        val steps = maxOf(kotlin.math.abs(df), kotlin.math.abs(dr))
        if (steps <= 1) return true
        val sf = Integer.signum(df); val sr = Integer.signum(dr)
        for (i in 1 until steps) {
            val idx = (r1 + sr * i) * 8 + (f1 + sf * i)
            if (squares[idx] != '.') return false
        }
        return true
    }

    /**
     * Produce SAN for [mv] in the current position (disambiguation, captures,
     * promotion, check/mate suffixes). Falls back to UCI if no piece can make it.
     */
    fun toSAN(mv: Move): String {
        val piece = squares[mv.from]
        if (piece == '.') return mv.uci()

        // Castling.
        if (mv.isCastle || (piece.uppercaseChar() == 'K' &&
            kotlin.math.abs((mv.to % 8) - (mv.from % 8)) == 2)
        ) {
            val base = if (mv.to % 8 == 6) "O-O" else "O-O-O"
            return base + checkSuffix(mv)
        }

        val isPawn = piece.uppercaseChar() == 'P'
        val capture = squares[mv.to] != '.' || mv.isEP

        val sb = StringBuilder()
        if (!isPawn) {
            sb.append(piece.uppercaseChar())
            // Disambiguation among same-type pieces that can reach mv.to.
            val side = piece
            val others = ArrayList<Int>()
            for (i in 0 until 64) {
                if (i == mv.from || squares[i] != side) continue
                if (canReach(i, mv.to, side, capture) && matchesDisambig(i, emptyList())) {
                    val b = copy()
                    b.make(Move(i, mv.to, mv.promo, mv.isEP))
                    if (!b.inCheck(!b.whiteToMove)) others.add(i)
                }
            }
            if (others.isNotEmpty()) {
                val sameFile = others.any { it % 8 == mv.from % 8 }
                val sameRank = others.any { it / 8 == mv.from / 8 }
                when {
                    !sameFile -> sb.append('a' + (mv.from % 8))
                    !sameRank -> sb.append('1' + (mv.from / 8))
                    else -> {
                        sb.append('a' + (mv.from % 8))
                        sb.append('1' + (mv.from / 8))
                    }
                }
            }
        } else if (capture) {
            sb.append('a' + (mv.from % 8))
        }

        if (capture) sb.append('x')
        sb.append(squareName(mv.to))
        if (mv.promo != null) {
            sb.append('=')
            sb.append(mv.promo.uppercaseChar())
        }
        sb.append(checkSuffix(mv))
        return sb.toString()
    }

    private fun checkSuffix(mv: Move): String {
        val b = copy()
        b.make(mv)
        val them = !b.whiteToMove
        return if (b.inCheck(them)) {
            if (b.legalMoves().isEmpty()) "#" else "+"
        } else ""
    }

    /** Convert a UCI string ("e2e4", "e7e8q") to a [Move] (no legality check). */
    fun parseUCI(uci: String): Move? {
        val s = uci.trim()
        if (s.length < 4) return null
        val from = squareIndex(s.substring(0, 2)) ?: return null
        val to = squareIndex(s.substring(2, 4)) ?: return null
        val promo = if (s.length >= 5) s[4].lowercaseChar() else null
        val piece = squares[from]
        val isKing = piece == 'K' || piece == 'k'
        val castle = isKing && kotlin.math.abs((to % 8) - (from % 8)) == 2
        val isEp = (piece == 'P' || piece == 'p') && squares[to] == '.' &&
            (to % 8) != (from % 8) && to == epTarget
        return Move(from, to, promo, isCastle = castle, isEP = isEp)
    }

}
