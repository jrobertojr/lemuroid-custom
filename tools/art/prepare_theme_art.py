"""Repack generated originals; no creative image synthesis or network calls.

python tools/art/prepare_theme_art.py
Then pack_hardware_atlas.py and the established Blender hardware exporters.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art-source/theme-art-20261121"
DRAWABLES = ROOT / "lemuroid-app/src/main/res/drawable-nodpi"
THEMES = "essential locadora cinema console crt synthwave thrash showcase".split()


def rgb565(image):
    result = Image.new("RGB", image.size)
    result.putdata([
        tuple(round(round(value / 255 * levels) * 255 / levels)
              for value, levels in zip(pixel, (31, 63, 31)))
        for pixel in image.convert("RGB").get_flattened_data()
    ])
    return result


def receipt(key, original, runtime):
    return dict(
        key=key,
        original=str(original.relative_to(ROOT)).replace("\\", "/"),
        runtime=str(runtime.relative_to(ROOT)).replace("\\", "/"),
        sha256=hashlib.sha256(runtime.read_bytes()).hexdigest(),
        bytes=runtime.stat().st_size,
        runtime_dimensions=list(Image.open(runtime).size),
    )


rows = []
for key in THEMES:
    original = SOURCE / (key + "-original.png")
    image = Image.open(original).convert("RGB")
    target = DRAWABLES / ("theme_art_" + key + ".webp")
    image.resize((1280, round(image.height * 1280 / image.width)),
                 Image.Resampling.NEAREST).save(target, "WEBP", quality=88, method=6)
    rows.append(receipt(key, original, target))

rows.append(receipt(
    "hard-rock",
    ROOT / "art-source/hard-rock-vinyl/backstage-original.png",
    DRAWABLES / "hard_rock_backstage.webp",
))
environment = ROOT / "lemuroid-app/src/main/assets/textures/pixel_atlas_v2.png"
rgb565(Image.open(SOURCE / "environment-original.png").resize(
    (512, 512), Image.Resampling.NEAREST,
)).save(environment)
rows.append(receipt("environment", SOURCE / "environment-original.png", environment))
(SOURCE / "manifest.json").write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
print("Packed", len(THEMES), "new backgrounds; total", sum(row["bytes"] for row in rows[:8]), "bytes")
