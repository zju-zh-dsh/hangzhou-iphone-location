"""Local launcher and HTTP interface for the Hangzhou location picker."""
import argparse
from hmac import compare_digest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
from secrets import token_hex
import signal
import subprocess
from threading import Lock

import device


ASSETS = Path(__file__).resolve().parent
FILES = {
    "/": ("index.html", "text/html; charset=utf-8"),
    "/maplibre-gl.js": ("maplibre-gl.js", "application/javascript; charset=utf-8"),
    "/maplibre-gl.css": ("maplibre-gl.css", "text/css; charset=utf-8"),
    "/maplibre-license.txt": ("maplibre-license.txt", "text/plain; charset=utf-8"),
}
MIN_LAT, MAX_LAT = 29.08, 30.65
MIN_LON, MAX_LON = 118.25, 120.70


class LocationServer(ThreadingHTTPServer):
    def __init__(self):
        self.session_token = token_hex(32)
        self.nonce = token_hex(20)
        self.device_operation = Lock()
        super().__init__(("127.0.0.1", 0), Handler)
        self.address = f"127.0.0.1:{self.server_port}"
        self.origin = f"http://{self.address}"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, content_type, content):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(content)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "strict-origin-when-cross-origin")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'none'; "
            f"script-src 'self' 'nonce-{self.server.nonce}'; "
            "style-src 'self' 'unsafe-inline'; "
            "worker-src blob:; "
            "connect-src 'self' https://tiles.openfreemap.org; "
            "img-src 'self' data: blob: https://tiles.openfreemap.org; "
            "frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
        )
        self.end_headers()
        self.wfile.write(content)

    def json_reply(self, status, payload):
        self.reply(status, "application/json; charset=utf-8", json.dumps(
            payload, ensure_ascii=False
        ).encode("utf-8"))

    def reject(self, status, message):
        self.json_reply(status, {"ok": False, "message": message})

    def authorized(self):
        return compare_digest(
            self.headers.get("X-Session-Token", "").encode("utf-8"),
            self.server.session_token.encode("ascii"),
        )

    def do_GET(self):
        if self.headers.get("Host") != self.server.address:
            return self.reject(403, "请从本机启动窗口打开地图。")
        if self.path == "/api/device" and self.authorized():
            return self.call_device(device.device_status)
        if self.path == "/api/device":
            return self.reject(403, "请刷新工具页面后重试。")
        if self.path == "/favicon.ico":
            return self.reply(204, "image/x-icon", b"")
        if self.path not in FILES:
            return self.reject(404, "找不到页面。")
        name, content_type = FILES[self.path]
        content = (ASSETS / name).read_bytes()
        if self.path == "/":
            content = content.replace(b"__SESSION_TOKEN__", self.server.session_token.encode())
            content = content.replace(b"__CSP_NONCE__", self.server.nonce.encode())
        self.reply(200, content_type, content)

    def do_POST(self):
        if (
            self.headers.get("Host") != self.server.address
            or self.headers.get("Origin") != self.server.origin
            or not self.authorized()
        ):
            return self.reject(403, "请从本机工具页面操作。")
        if self.path not in ("/api/location/set", "/api/location/clear"):
            return self.reject(404, "找不到此操作。")
        if self.headers.get_content_type() != "application/json":
            return self.reject(415, "请求格式有误。")
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 1 <= size <= 1024:
                raise ValueError
            self.connection.settimeout(5)
            payload = json.loads(self.rfile.read(size))
            self.connection.settimeout(None)
        except (ValueError, TimeoutError, OverflowError):
            return self.reject(400, "请求内容有误。")
        if not isinstance(payload, dict):
            return self.reject(400, "请求内容有误。")
        if self.path == "/api/location/clear":
            return self.call_device(device.clear_location)
        lat, lon = payload.get("lat"), payload.get("lon")
        if (
            type(lat) not in (int, float)
            or type(lon) not in (int, float)
            or not MIN_LAT <= lat <= MAX_LAT
            or not MIN_LON <= lon <= MAX_LON
        ):
            return self.reject(400, "请选择杭州地图范围内的有效坐标。")
        self.call_device(device.set_location, lat, lon)

    def call_device(self, operation, *args):
        if not self.server.device_operation.acquire(blocking=False):
            return self.reject(409, "iPhone 正在处理上一步，请稍后再试。")
        try:
            result = operation(*args)
        except Exception as error:
            print(f"设备操作未完成 ({type(error).__name__})", flush=True)
            return self.reject(500, "设备操作未完成。请保持 USB 连接，检查 iPhone 是否已解锁后重试。")
        finally:
            self.server.device_operation.release()
        self.json_reply(200, result)


def main():
    parser = argparse.ArgumentParser(description="杭州 iPhone 定位工具")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()

    def stop(*_):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, stop)
    if hasattr(signal, "SIGHUP"):
        signal.signal(signal.SIGHUP, stop)
    try:
        with LocationServer() as server:
            print(f"杭州 iPhone 定位工具已启动\n{server.origin}", flush=True)
            print("使用期间请保持此窗口打开。结束前先在网页上恢复真实定位。", flush=True)
            if not args.no_browser:
                try:
                    if os.name == "nt":
                        os.startfile(server.origin)
                    else:
                        subprocess.run(["/usr/bin/open", server.origin], check=False)
                except OSError:
                    print("请手动在浏览器中打开上方地址。", flush=True)
            server.serve_forever()
    except KeyboardInterrupt:
        print("\n正在关闭定位工具…", flush=True)
    finally:
        device.shutdown_device()


if __name__ == "__main__":
    main()
