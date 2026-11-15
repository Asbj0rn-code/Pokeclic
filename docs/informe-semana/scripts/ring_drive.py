"""Headless Misty driver: taps when the closing ring meets the fixed ring."""
import subprocess
import sys
import time

import numpy as np
from PIL import Image

ADB = r"C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"


def capture(path="cap_ring.png"):
    with open(path, "wb") as fh:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=fh, check=True)
    return Image.open(path).convert("RGB")


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))],
                   stdout=subprocess.DEVNULL, check=False)


def ring_radius(img, cy=1370, cx=540):
    arr = np.asarray(img).astype(np.int16)
    row = arr[cy]
    r, g, b = row[:, 0], row[:, 1], row[:, 2]
    # Strong ring blue: clearly blue and fairly saturated.
    blue = (b > 150) & (b > r + 45) & (g > 60) & (g < 190)
    xs = np.nonzero(blue)[0]
    if len(xs) == 0:
        return None
    left = [x for x in xs if x < cx]
    right = [x for x in xs if x > cx]
    if not left or not right:
        return None
    return (right[-1] - left[0]) / 2.0


def probe(frames=8):
    for _ in range(frames):
        img = capture()
        print(round(ring_radius(img) or -1, 1))
        time.sleep(0.12)


def main(rounds=220):
    for _ in range(rounds):
        img = capture()
        rad = ring_radius(img)
        if rad is not None and rad < 195:
            tap(540, 1370)
        time.sleep(0.04)
    print("done")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--probe"]:
        probe()
    else:
        main(int(sys.argv[1]) if len(sys.argv) > 1 else 220)
