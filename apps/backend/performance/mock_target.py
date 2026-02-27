#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        if self.path.endswith("/events"):
            self.wfile.write(b"event: snapshot\ndata: {\"draw_revision\":1,\"results_revision\":1}\n\n")
        else:
            self.wfile.write(b"{\"ok\":true}")

    def do_POST(self):  # noqa: N802
        _ = self.rfile.read(int(self.headers.get("Content-Length", "0") or "0"))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b"{\"accepted\":true}")

    def log_message(self, format, *args):  # noqa: A003
        return


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18080), Handler)
    try:
        server.serve_forever()
    finally:
        server.server_close()
