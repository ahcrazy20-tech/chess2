// reader.js v3 — robust detection with multiple fallback signals.
//
// Sends to Swift:
//   { grid: [64 chars], sideToMove, flipped, lastMoveUCI,
//     plyCount, detectionConfidence: 'high'|'medium'|'low' }

(function () {
  if (window.__ch_v3) return;
  window.__ch_v3 = true;

  const PIECE_MAP = {
    wp: 'P', wn: 'N', wb: 'B', wr: 'R', wq: 'Q', wk: 'K',
    bp: 'p', bn: 'n', bb: 'b', br: 'r', bq: 'q', bk: 'k',
  };

  // ---- Board finding ----
  function findBoard() {
    return document.querySelector(
      'wc-chess-board, chess-board, cg-board, .board-layout-chessboard'
    );
  }

  // Support both chess.com layouts:
  //  A) legacy:  <div class="piece wn square-52">
  //  B) v3:      <piece class="white knight" style="transform: translate(50%, 200%)">
  //  C) newest:  <div class="piece" data-piece="wn" data-square="e2">
  const PIECE_NAMES = { king:'K', queen:'Q', rook:'R', bishop:'B', knight:'N', pawn:'P' };

  function readGrid(board) {
    const grid = Array.from({ length: 8 }, () => Array(8).fill('.'));
    const nodes = board.querySelectorAll('.piece, piece, [data-piece]');
    if (nodes.length === 0) return null;

    nodes.forEach(n => {
      let piece = null, file = null, rank = null;
      const cls = (n.className || '').toString();

      // ---- Piece code ----
      // Format A: class "wn" / "bp"
      cls.split(/\s+/).forEach(c => { if (PIECE_MAP[c]) piece = PIECE_MAP[c]; });
      // Format C: data-piece="wn"
      if (!piece) {
        const dp = n.getAttribute && n.getAttribute('data-piece');
        if (dp && PIECE_MAP[dp]) piece = PIECE_MAP[dp];
      }
      // Format B: class "white knight" / "black pawn"
      if (!piece) {
        const parts = cls.toLowerCase().split(/\s+/);
        const colorPart = parts.find(p => p === 'white' || p === 'black');
        const namePart  = parts.find(p => PIECE_NAMES[p]);
        if (colorPart && namePart) {
          const letter = PIECE_NAMES[namePart];
          piece = colorPart === 'white' ? letter : letter.toLowerCase();
        }
      }

      // ---- Square coord ----
      // Format A: class "square-52"
      cls.split(/\s+/).forEach(c => {
        const m = /^square-(\d)(\d)$/.exec(c);
        if (m) { file = +m[1]; rank = +m[2]; }
      });
      // Format C: data-square="e2"
      if (file === null) {
        const ds = n.getAttribute && n.getAttribute('data-square');
        if (ds && ds.length === 2) {
          file = ds.charCodeAt(0) - 96;
          rank = parseInt(ds[1], 10);
        }
      }
      // Format B: style="transform: translate(X%, Y%)"
      if (file === null) {
        const st = (n.getAttribute && n.getAttribute('style')) || '';
        const m = /translate\(\s*(-?\d+(?:\.\d+)?)%?\s*,\s*(-?\d+(?:\.\d+)?)%?/.exec(st);
        if (m) {
          const x = parseFloat(m[1]), y = parseFloat(m[2]);
          // Each square is ~12.5% (100/8). Round to 0..7.
          const col = Math.round(x / 12.5);
          const row = Math.round(y / 12.5);
          if (col >= 0 && col <= 7 && row >= 0 && row <= 7) {
            file = col + 1;
            rank = 8 - row;
          }
        }
      }

      if (piece && file && rank && rank >= 1 && rank <= 8 && file >= 1 && file <= 8) {
        grid[8 - rank][file - 1] = piece;
      }
    });
    return grid;
  }

  // ---- Orientation detection (are we playing Black?) ----
  function detectFlipped(board) {
    const signals = [];

    // Signal A: `flipped` class on board or ancestor
    let el = board;
    for (let i = 0; i < 6 && el; i++) {
      if (el.classList && el.classList.contains('flipped')) {
        signals.push(true); break;
      }
      el = el.parentElement;
    }
    // Signal B: `flipped` attribute
    if (board.hasAttribute('flipped')) signals.push(true);
    // Signal C: orientation attribute
    const orient = board.getAttribute('orientation');
    if (orient === 'black') signals.push(true);
    if (orient === 'white') signals.push(false);

    // Signal D: bottom player card contains a black-color indicator
    const bottomEls = document.querySelectorAll(
      '.player-bottom, .board-layout-bottom, [class*="player-row-bottom"]'
    );
    for (const b of bottomEls) {
      const html = (b.innerHTML || '').toLowerCase();
      if (html.includes('clock-black') || html.includes('icon-black')) {
        signals.push(true); break;
      }
      if (html.includes('clock-white') || html.includes('icon-white')) {
        signals.push(false); break;
      }
    }

    // Signal E: coordinate labels — chess.com puts the "1" or "8" at the
    // bottom-left of the visible board. If "8" is at bottom, it's flipped.
    const coordEls = document.querySelectorAll(
      '.coordinates text, .coordinate-light, .coordinate-dark, coordinates > *'
    );
    if (coordEls.length > 0) {
      // Find the one with the smallest y (topmost) and see its text
      let topmost = null, topmostY = Infinity;
      coordEls.forEach(c => {
        const y = parseFloat(c.getAttribute('y') || c.style.top || '999');
        if (!isNaN(y) && y < topmostY) { topmostY = y; topmost = c; }
      });
      if (topmost) {
        const t = (topmost.textContent || '').trim();
        if (t === '1') signals.push(true);
        if (t === '8') signals.push(false);
      }
    }

    // Consensus: return most common signal, or null if none
    if (signals.length === 0) return null;
    const trueCount = signals.filter(x => x === true).length;
    const falseCount = signals.filter(x => x === false).length;
    if (trueCount === falseCount) return null; // ambiguous
    return trueCount > falseCount;
  }

  // ---- Ply count (how many half-moves have been played) ----
  function detectPlyCount() {
    // Best signal: data-ply attributes on move nodes
    const plyNodes = document.querySelectorAll('[data-ply]');
    if (plyNodes.length > 0) {
      let maxPly = -1;
      plyNodes.forEach(n => {
        const p = parseInt(n.getAttribute('data-ply'), 10);
        if (!isNaN(p) && p > maxPly) maxPly = p;
      });
      if (maxPly >= 0) return maxPly;
    }
    // Fallback: data-node attributes
    const nodeEls = document.querySelectorAll('[data-node]');
    if (nodeEls.length > 0) {
      let maxN = -1;
      nodeEls.forEach(n => {
        const v = parseInt(n.getAttribute('data-node'), 10);
        if (!isNaN(v) && v > maxN) maxN = v;
      });
      if (maxN >= 0) return maxN;
    }
    // Fallback: count SAN-looking text tokens in move list
    const list = document.querySelector(
      'wc-move-list, .move-list-container, .vertical-move-list, .moves-container'
    );
    if (list) {
      const text = list.textContent || '';
      const tokens = text.match(
        /\b(O-O-O|O-O|[NBRQK]?[a-h]?[1-8]?x?[a-h][1-8](=[NBRQ])?[+#]?)\b/g
      );
      if (tokens) return tokens.length;
    }
    return -1;
  }

  // ---- Side to move via active clock (secondary source) ----
  function detectSideFromClock() {
    const active = document.querySelectorAll(
      '.clock-player-turn, .clock.active, [class*="clock"][class*="turn"]'
    );
    for (const c of active) {
      const cls = c.className || '';
      if (cls.includes('white')) return 'w';
      if (cls.includes('black')) return 'b';
      // Look at parent context
      const parent = c.closest('[class*="player"], [class*="clock-component"]');
      if (parent) {
        const html = (parent.innerHTML || '').toLowerCase();
        if (html.includes('white')) return 'w';
        if (html.includes('black')) return 'b';
      }
    }
    return null;
  }

  // ---- Diff-based side detection (fallback when everything else fails) ----
  let prevGrid = null;
  function diffLastMove(prev, curr) {
    if (!prev || !curr) return null;
    const changes = [];
    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        if (prev[r][c] !== curr[r][c]) {
          changes.push({ r, c, before: prev[r][c], after: curr[r][c] });
        }
      }
    }
    if (changes.length < 2 || changes.length > 4) return null;
    // 'to' = square with a new piece (before empty or different)
    const toSq = changes.find(x => x.after !== '.' && x.after !== x.before);
    const fromSq = changes.find(x => x.before !== '.' && x.after === '.');
    if (!toSq || !fromSq) return null;
    const from = String.fromCharCode(97 + fromSq.c) + (8 - fromSq.r);
    const to   = String.fromCharCode(97 + toSq.c)   + (8 - toSq.r);
    // Whose piece moved? uppercase = white
    const movedColor = toSq.after === toSq.after.toUpperCase() ? 'w' : 'b';
    return { from, to, uci: from + to, movedColor };
  }

  function isInitialPosition(grid) {
    const start = "rnbqkbnr,pppppppp,........,........,........,........,PPPPPPPP,RNBQKBNR";
    return grid.map(r => r.join('')).join(',') === start;
  }

  // ---- Main tick ----
  let lastPayload = '';
  let lastDetectedSide = 'w';

  function tick() {
    const board = findBoard();
    if (!board) return;
    const grid = readGrid(board);
    if (!grid) return;

    // Detect side-to-move — try multiple sources and combine
    let sideToMove = null;
    let confidence = 'low';

    const plyCount = detectPlyCount();
    if (plyCount >= 0) {
      sideToMove = (plyCount % 2 === 0) ? 'w' : 'b';
      confidence = 'high';
    } else {
      const clockSide = detectSideFromClock();
      if (clockSide) {
        sideToMove = clockSide;
        confidence = 'medium';
      }
    }

    // Diff-based last move + fallback side detection
    const mv = diffLastMove(prevGrid, grid);
    if (mv && !sideToMove) {
      // The color that just moved is opposite of who's to move now
      sideToMove = (mv.movedColor === 'w') ? 'b' : 'w';
      confidence = 'low';
    }
    if (!sideToMove) {
      sideToMove = isInitialPosition(grid) ? 'w' : lastDetectedSide;
    }
    lastDetectedSide = sideToMove;

    // Detect orientation
    const flippedRaw = detectFlipped(board);
    const flipped = (flippedRaw === null) ? false : flippedRaw;

    // Build 64-char grid string
    const gridStr = grid.map(r => r.join('')).join('');

    const payload = {
      grid: gridStr,
      sideToMove: sideToMove,
      flipped: flipped,
      flippedConfident: flippedRaw !== null,
      lastMoveUCI: mv ? mv.uci : '',
      plyCount: plyCount,
      detectionConfidence: confidence,
    };

    const key = JSON.stringify(payload);
    if (key !== lastPayload) {
      lastPayload = key;
      try {
        window.webkit.messageHandlers.fenBridge.postMessage(payload);
      } catch (_) {}
    }
    prevGrid = grid;
  }

  window.__ch_startReader = function () {
    if (window.__ch_timer) return;
    window.__ch_timer = setInterval(tick, 500);
  };

  const mo = new MutationObserver(() => tick());
  try {
    mo.observe(document.documentElement,
      { childList: true, subtree: true, attributes: true });
  } catch (_) {}

  window.__ch_startReader();
})();
