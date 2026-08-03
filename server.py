from flask import Flask, request, jsonify
import chess
import chess.engine
import os
import time
import threading

app = Flask(__name__)

STOCKFISH_PATH = os.getenv("STOCKFISH_PATH", "/usr/bin/stockfish")
ENGINE_THREADS = int(os.getenv("ENGINE_THREADS", "1"))
MAX_MULTIPV = 5

# create a persistent engine instance (thread-safe wrapper)
engine_lock = threading.Lock()
engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH_PATH)
engine.configure({"Threads": ENGINE_THREADS})

def validate_fen(fen):
    try:
        chess.Board(fen)
        return True
    except Exception:
        return False

@app.route("/analyze", methods=["POST"])
def analyze():
    data = request.json or {}
    fen = data.get("fen")
    if not fen or not validate_fen(fen):
        return jsonify({"error": "valid fen required"}), 400

    multi_pv = int(data.get("multi_pv", 3))
    multi_pv = max(1, min(multi_pv, MAX_MULTIPV))
    time_limit = float(data.get("time", 0.1))  # seconds (default 0.1s)

    board = chess.Board(fen)

    # Simple server-side rate-limiting placeholder:
    # replace with a Redis/token-bucket or library for production
    time.sleep(0.02)  # small, consistent delay to reduce tight loops

    results = []
    with engine_lock:
        # Use analysis with multipv
        limit = chess.engine.Limit(time=time_limit)
        with engine.analysis(board, limit=limit, multipv=multi_pv) as analysis:
            for info in analysis:
                if "pv" in info and info.get("pv"):
                    pv = info["pv"]
                    score = info.get("score")
                    if isinstance(score, chess.engine.Mate):
                        score_val = {"mate": score.pov(board.turn).mate}
                    else:
                        cp = score.pov(board.turn).score(mate_score=100000)
                        score_val = {"cp": cp}
                    move_obj = pv[0]
                    san = board.san(move_obj)
                    results.append({
                        "move": move_obj.uci(),
                        "move_san": san,
                        "evaluation": score_val,
                        "depth": info.get("depth")
                    })
                if len(results) >= multi_pv:
                    break

    return jsonify({"top_moves": results})

if __name__ == "__main__":
    # production: run under gunicorn/uWSGI in container; not with Flask dev server
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "5000")))
