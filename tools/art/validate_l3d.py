"""Independent bounds, UV, hash and geometry-budget verification. No Blender dependency."""
import hashlib, json, math, struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'art-source/faithful-hardware'
manifest=json.loads((SOURCE/'manifest.json').read_text(encoding='utf-8'))
assert len(manifest['assets'])==48
assert manifest['atlas_grid']==[8,4]
total=0
for row in manifest['assets']:
    raw=(ROOT/'lemuroid-app/src/main/assets/models3d'/(row['key']+'.l3d')).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==row['sha256'],row['key']
    magic,parts=struct.unpack_from('<II',raw);assert magic==0x3244334c and parts==row['parts']
    offset=8;vertices=0;points=[]
    for part in range(parts):
        header=struct.unpack_from('<7fI',raw,offset);offset+=32
        assert all(math.isfinite(x) for x in header[:7]);count=header[-1]
        assert count>0 and count%3==0
        for _ in range(count):
            v=struct.unpack_from('<8f',raw,offset);offset+=32
            assert all(math.isfinite(x) for x in v)
            assert 0<=v[6]<=1 and 0<=v[7]<=1
            assert .98<=sum(n*n for n in v[3:6])<=1.02
            if part==row['cover_part']:
                assert v[5]>=-.001,(row['key'],'front cover must survive GL_BACK culling')
            points.append(v[:3])
            if part!=row['cover_part']:
                # Both generated sheets are packed into one 8x4 hardware atlas.
                tile=int(min(v[7],.999)*4)*8+int(min(v[6],.999)*8)
                assert 0<=tile<32,(row['key'],part,tile)
        vertices+=count
    assert offset==len(raw)
    assert vertices//3==row['triangles']
    assert vertices//3<=(800 if 'arcade' in row['key'] else 600)
    assert parts<=8
    if not row['key'].startswith('media_'):
        assert abs(min(v[1] for v in points))<.001,(row['key'],'bottom must rest on shelf')
    total+=vertices//3
print('Verified',len(manifest['assets']),'L3D2 assets,',total,'triangles; UV, normals, bounds and SHA256 pass.')

# Every scrolling thumbnail must match a real media mesh and expose a valid label aperture.
preview_specs=json.loads((SOURCE/'preview-bounds.json').read_text(encoding='utf-8'))
media_keys={row['key'].removeprefix('media_') for row in manifest['assets'] if row['key'].startswith('media_')}
assert set(preview_specs)==media_keys
preview_dir=ROOT/'lemuroid-app/src/main/assets/media_previews'
assert {p.stem for p in preview_dir.glob('*.png')}==media_keys
for key,spec in preview_specs.items():
    left,top,right,bottom=spec['bounds']
    assert 0<=left<right<=1 and 0<=top<bottom<=1,(key,spec)
    png=(preview_dir/(key+'.png')).read_bytes()
    assert png[:8]==b'\x89PNG\r\n\x1a\n'
    width,height,depth,color=struct.unpack_from('>IIBB',png,16)
    assert (width,height,depth,color)==(256,256,8,6),(key,'expected RGBA8 thumbnail')
assert {k for k,v in preview_specs.items() if v['ring']}=={'psx','segacd','psp'}
print('Verified 24 RGBA thumbnails and dynamic-label bounds.')
