"""Headless gym driver: breaks falling rocks and taps the exposed leader."""
import subprocess
import sys
import time

import numpy as np
from PIL import Image

ADB = r"C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"


def capture(path="cap_drive.png"):
    with open(path, "wb") as fh:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=fh, check=True)
    return Image.open(path).convert("RGB")


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))],
                   stdout=subprocess.DEVNULL, check=False)


def tap_many(points):
    if not points:
        return
    chain = "; ".join(
        f"input tap {int(x)} {int(y)}" for x, y in points
    )
    subprocess.run([ADB, "shell", chain], stdout=subprocess.DEVNULL, check=False)


def rock_mask(img):
    arr = np.asarray(img).astype(np.int16)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    mask = (r > 175) & (r < 232) & (g > 112) & (g < 178) & (b < 105)
    mask &= (r > g + 28) & (g > b + 38)
    mask[:760, :] = False
    mask[1460:, :] = False
    mask[:, :120] = False
    mask[:, 960:] = False
    return mask


def rock_centers(img):
    mask = rock_mask(img)
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return []
    cell = 40
    buckets = {}
    for x, y in zip(xs, ys):
        buckets.setdefault((x // cell, y // cell), []).append((x, y))
    dense = [(k, v) for k, v in buckets.items() if len(v) > 55]
    dense.sort(key=lambda kv: -len(kv[1]))
    centers = []
    for (cx, cy), pts in dense[:5]:
        px = sum(p[0] for p in pts) / len(pts)
        py = sum(p[1] for p in pts) / len(pts)
        if all(abs(px - c[0]) > 55 or abs(py - c[1]) > 55 for c in centers):
            centers.append((px, py))
    return centers


def main(rounds=140):
    idle = 0
    for i in range(rounds):
        img = capture()
        centers = rock_centers(img)
        if centers:
            idle = 0
            tap_many(centers)
        else:
            idle += 1
            if idle >= 2:
                tap_many([(540, 620)] * 7)
                idle = 0
        if i % 30 == 29:
            img.save(f"drive_{i}.png")
        time.sleep(0.03)
    print("done")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--probe"]:
        img = Image.open("capturas/43-gym-brock-combate.png").convert("RGB")
        print(rock_centers(img))
    else:
        main(int(sys.argv[1]) if len(sys.argv) > 1 else 140)
