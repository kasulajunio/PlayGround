#!/usr/bin/env bash
# Fris Black preview server — static dist/ served in the foreground.
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
PORT="${PORT:-3000}"
DIST_DIR="$PROJECT_DIR/dist"
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"

/usr/bin/time -p test -f "$DIST_DIR/index.html"
/usr/bin/time -p mkdir -p "$WEB_DIR"
/usr/bin/time -p env WEB_DIR="$WEB_DIR" PROJECT_DIR="$PROJECT_DIR" DIST_DIR="$DIST_DIR" node -e '
const fs = require("fs");
const path = require("path");
const webDir = process.env.WEB_DIR;
const project = process.env.PROJECT_DIR;
const directory = process.env.DIST_DIR;
fs.mkdirSync(webDir, { recursive: true });
fs.writeFileSync(path.join(webDir, "deployment-output.json"), JSON.stringify({ project, directory }));
console.log("deployment-output.json -> " + path.join(webDir, "deployment-output.json"));
'

echo "Serving $DIST_DIR on port $PORT (project $PROJECT_DIR)"

/usr/bin/time -p env DIST_DIR="$DIST_DIR" PORT="$PORT" node -e '
const http = require("http");
const fs = require("fs");
const path = require("path");
const root = process.env.DIST_DIR;
const port = Number(process.env.PORT || "3000");
const mime = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp" };
const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, "http://localhost");
    let p = path.resolve(root, "." + decodeURIComponent(url.pathname));
    if (p !== path.resolve(root) && !p.startsWith(path.resolve(root) + path.sep)) { res.writeHead(404); res.end(); return; }
    if (fs.existsSync(p) && fs.statSync(p).isDirectory()) p = path.join(p, "index.html");
    const data = fs.readFileSync(p);
    res.setHeader("Content-Type", mime[path.extname(p)] || "application/octet-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.end(data);
  } catch (e) { res.writeHead(404); res.end("Not found"); }
});
server.listen(port, "0.0.0.0", () => console.log("Fris Black preview listening on " + port));
'
