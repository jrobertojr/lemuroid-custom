"""Blender background: normalize generated atlas to 512² with nearest sampling and RGB565 colors.
Usage: blender -b --python tools/art/normalize_pixel_atlas.py -- SOURCE OUTPUT
Original source is never modified. This is an asset-format conversion, not an AI generation step.
"""
import bpy
import sys
from array import array
from pathlib import Path

source_path, output_path = map(Path, sys.argv[sys.argv.index('--') + 1:])
source = bpy.data.images.load(str(source_path.resolve()), check_existing=False)
width, height = source.size
assert width == height and width >= 512, (width, height)
source_pixels = array('f', source.pixels[:])
size = 512
pixels = array('f', [0.0]) * (size * size * 4)
for y in range(size):
    sy = min(height - 1, int((y + 0.5) * height / size))
    for x in range(size):
        sx = min(width - 1, int((x + 0.5) * width / size))
        src = (sy * width + sx) * 4
        dst = (y * size + x) * 4
        for channel, levels in enumerate((31, 63, 31)):
            pixels[dst + channel] = round(source_pixels[src + channel] * levels) / levels
        pixels[dst + 3] = 1.0
output = bpy.data.images.new('Pixel Atlas v2 RGB565', width=size, height=size, alpha=False)
output.pixels.foreach_set(pixels)
output_path.parent.mkdir(parents=True, exist_ok=True)
output.filepath_raw = str(output_path.resolve())
output.file_format = 'PNG'
output.save()
print(f'ATLAS_OK source={width}x{height} output={size}x{size} bytes={output_path.stat().st_size}')
