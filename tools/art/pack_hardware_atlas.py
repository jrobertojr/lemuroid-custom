"""Technical nearest/RGB565 packing of two generated 4x4 sheets; originals preserved.
Blender -b --python tools/art/pack_hardware_atlas.py
"""
import bpy
from pathlib import Path
from array import array
root=Path(__file__).resolve().parents[2]
sources=[root/'art-source/faithful-hardware'/name for name in ['atlas-source.png','decals-source.png']]
size=512
pixels=array('f',[0])*(size*2*size*4)
for sheet,path in enumerate(sources):
    src=bpy.data.images.load(str(path));w,h=src.size;assert w==h
    values=array('f',src.pixels[:])
    revised=root/'art-source/faithful-hardware/atlas-source-revised.png'
    replacement=None
    if sheet==0 and revised.exists():
        changed=bpy.data.images.load(str(revised));cw,ch=changed.size;assert cw==ch
        replacement=array('f',changed.pixels[:])
    for y in range(size):
        for x in range(size):
            a=(min(h-1,int((y+.5)*h/size))*w+min(w-1,int((x+.5)*w/size)))*4
            b=(y*size*2+sheet*size+x)*4
            tile=(3-y//128)*4+x//128
            selected=values
            # Admit only corrected faceplates; keep the user's approved arcade tiles byte-stable.
            if replacement is not None and tile in {1,2,8,9,10,11}:
                a=(min(ch-1,int((y+.5)*ch/size))*cw+min(cw-1,int((x+.5)*cw/size)))*4
                selected=replacement
            for c,levels in enumerate([31,63,31]):pixels[b+c]=round(selected[a+c]*levels)/levels
            pixels[b+3]=1
# Admit only the new neutral ABS tile. All faceplates and the approved arcade artwork stay intact.
grain=root/'art-source/theme-art-20261121/abs-original.png'
if grain.exists():
    src=bpy.data.images.load(str(grain));w,h=src.size;values=array('f',src.pixels[:])
    for y in range(128):
        for x in range(128):
            a=(min(h-1,int((y+.5)*h/128))*w+min(w-1,int((x+.5)*w/128)))*4
            b=((y+384)*size*2+x)*4
            for c,levels in enumerate([31,63,31]):pixels[b+c]=round(values[a+c]*levels)/levels
            pixels[b+3]=1
out=bpy.data.images.new('Hardware atlas RGB565',width=size*2,height=size,alpha=False)
out.pixels.foreach_set(pixels);out.file_format='PNG'
out.filepath_raw=str(root/'lemuroid-app/src/main/assets/textures/hardware_atlas_v3.png');out.save()
print('PACKED_HARDWARE_ATLAS',list(out.size))
