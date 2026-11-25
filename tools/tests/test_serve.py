import subprocess
import threading

from storytool import serve


class FakeAdb:
    """Stands in for adb: the devices it sees, and the reverse rules each one has."""

    def __init__(self, devices):
        self.rules = {serial: set() for serial in devices}
        self.set_on = []

    def run(self, args, **kw):
        out = ""
        if args[1:] == ["devices"]:
            out = "List of devices attached\n" + "".join(f"{s}\tdevice\n" for s in self.rules) + "\n"
        elif args[3:] == ["reverse", "--list"]:
            out = "".join(f"UsbFfs {r}\n" for r in self.rules[args[2]])
        elif args[3] == "reverse":
            self.rules[args[2]].add(f"{args[4]} {args[5]}")
            self.set_on.append(args[2])
        return subprocess.CompletedProcess(args, 0, stdout=out, stderr="")


def fake(monkeypatch, devices):
    adb = FakeAdb(devices)
    monkeypatch.setattr(serve.shutil, "which", lambda name: "/fake/adb")
    monkeypatch.setattr(serve.subprocess, "run", adb.run)
    return adb


def test_every_device_gets_its_reverse(monkeypatch):
    adb = fake(monkeypatch, ["phone", "emulator-5554"])
    assert serve.adb_reverse(8765) == ["phone", "emulator-5554"]
    assert adb.rules["phone"] == {"tcp:8765 tcp:8765"}


def test_a_device_that_lost_its_reverse_gets_it_back_and_the_others_are_left_alone(monkeypatch):
    adb = fake(monkeypatch, ["phone", "emulator-5554"])
    serve.adb_reverse(8765)
    adb.rules["phone"].clear()  # adb's server restarted, or the phone reconnected
    adb.set_on.clear()
    assert serve.adb_reverse(8765, only_missing=True) == ["phone"]
    assert adb.set_on == ["phone"]


def test_keep_reverse_checks_until_stopped(monkeypatch):
    adb = fake(monkeypatch, ["phone"])
    stop = threading.Event()
    watcher = threading.Thread(target=serve.keep_reverse, args=(8765, stop, 0.01))
    watcher.start()
    for _ in range(200):
        if adb.rules["phone"]:
            break
        stop.wait(0.01)
    stop.set()
    watcher.join(1)
    assert adb.rules["phone"] == {"tcp:8765 tcp:8765"} and not watcher.is_alive()
