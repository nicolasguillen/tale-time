"""`story serve`: serves the dev channel (story package) to debug builds of the app, from this Mac.

Debug builds download from http://localhost:8765/ instead of the content host, through the same download code. The
iOS simulator reaches the Mac's localhost directly; Android devices and emulators reach it through `adb reverse`, which
this command sets up for every device adb sees, and keeps up while it runs: adb forgets the rule whenever its server
restarts (Android Studio and Gradle installs restart it) or a device reconnects, and the app then can't reach this Mac.

The stories are packaged again whenever the app asks for the catalog, so a picture or clip delivered while the server
runs (or a text edit) reaches the app at its next launch, exactly like an update from the host would.
"""
import shutil
import subprocess
import sys
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

from .package import CATALOG_PATH, Packager, describe

PORT = 8765
REVERSE_EVERY = 3.0  # seconds between checks that every device still has its `adb reverse`
TYPES = {".json": "application/json", ".webp": "image/webp", ".m4a": "audio/mp4"}


class _Handler(SimpleHTTPRequestHandler):
    packager: Packager
    lock = threading.Lock()

    def do_GET(self):
        if self.path.split("?")[0].lstrip("/") == CATALOG_PATH:
            with self.lock:
                try:
                    self.packager.build(clean=False)
                except Exception as e:  # keep serving the last catalog
                    print(f"  packaging failed, serving the last catalog: {e}")
        super().do_GET()

    def guess_type(self, path):
        for ext, kind in TYPES.items():
            if str(path).endswith(ext):
                return kind
        return super().guess_type(path)

    def end_headers(self):
        catalog = self.path.split("?")[0].lstrip("/") == CATALOG_PATH
        self.send_header("Cache-Control", "no-cache" if catalog else "public, max-age=31536000, immutable")
        super().end_headers()

    def log_message(self, fmt, *args):
        print(f"  {self.command} {self.path.split('?')[0]} {args[1] if len(args) > 1 else ''}")


def adb_reverse(port: int, only_missing: bool = False) -> list[str]:
    """`adb reverse tcp:<port> tcp:<port>` on every device adb sees, so `localhost` on it is this Mac, or, with
    `only_missing`, on those that don't have it (any more). The serials it was set on."""
    adb = shutil.which("adb")
    if not adb:
        return []
    listed = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    serials = [line.split()[0] for line in listed if line.strip().endswith("\tdevice")]
    rule = f"tcp:{port} tcp:{port}"
    if only_missing:
        serials = [s for s in serials
                   if rule not in subprocess.run([adb, "-s", s, "reverse", "--list"], capture_output=True, text=True).stdout]
    for serial in serials:
        subprocess.run([adb, "-s", serial, "reverse", f"tcp:{port}", f"tcp:{port}"], capture_output=True)
    return serials


def keep_reverse(port: int, stop: threading.Event, every: float = REVERSE_EVERY) -> None:
    """Puts `adb reverse` back on every device that lost it (or was just connected), until `stop` is set."""
    while not stop.wait(every):
        try:
            for serial in adb_reverse(port, only_missing=True):
                print(f"  adb reverse set up on {serial}")
        except OSError:
            pass  # adb went away for a moment; try again at the next check


def serve(port: int = PORT, lan: bool = False) -> None:
    sys.stdout.reconfigure(line_buffering=True)  # each request shows as it happens, even when piped to a file
    packager = Packager("dev")
    for line in describe(packager.build(clean=True)):
        print(line)
    handler = type("Handler", (_Handler,), {"packager": packager})
    server = ThreadingHTTPServer(("0.0.0.0" if lan else "127.0.0.1", port),
                                 partial(handler, directory=str(packager.root)))
    devices = adb_reverse(port)
    print(f"\nServing the dev channel on http://localhost:{port}/{CATALOG_PATH}")
    print(f"  Android: adb reverse set up on {', '.join(devices)}" if devices else
          "  Android: no device yet; a device connected later gets its adb reverse within a few seconds")
    print("  iOS simulator: reaches localhost directly")
    print("Stop with Ctrl-C.\n")
    stop = threading.Event()
    threading.Thread(target=keep_reverse, args=(port, stop), daemon=True).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        stop.set()
        server.server_close()
