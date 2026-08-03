# Security & usage notes — Stockfish analyze service

- Do not automate playing on chess.com in ways that impersonate a human or violate Terms of Service.
- Keep the engine server-side; do NOT ship Stockfish or API keys in the client.
- Rate-limiting: use Redis or in-memory token buckets to limit requests per user/IP.
- Validate FEN before analyzing.
- Run engine under non-root user; use CPU/memory limits (cgroups/docker).
- Use HTTPS + authentication for /analyze endpoint.
- Monitor engine CPU, request rate, and set alerts for spikes.
- Configure MultiPV to a safe limit (we use <=5) to avoid CPU exhaustion.

## Quick run

1. Install dependencies:

    pip install python-chess flask

2. Install Stockfish binary and set STOCKFISH_PATH env var if needed.

3. Run the server (dev):

    python server.py

For production use gunicorn and a process manager, and put the service behind HTTPS.
