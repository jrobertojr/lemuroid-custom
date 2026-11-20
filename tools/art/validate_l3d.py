"""Independent bounds, UV, hash and geometry-budget verification. No Blender dependency."""
import hashlib, json, math, struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'art-source/lowpoly-hardware'
manifest=json.loads((SOURCE/'manifest.json').read_text(encoding='utf-8'))
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
            points.append(v[:3])
            # Cover part has full artwork UV; all other hardware parts use neutral tile 3,
            # screen 4 or marquee 7. The contact-sheet authoring uses identical UVs.
            if part!=row['cover_part']:
                tile=int(min(v[7],.999)*2)*4+int(min(v[6],.999)*4)
                assert tile in (3,4,7),(row['key'],part,tile)
        vertices+=count
    assert offset==len(raw)
    assert vertices//3==row['triangles']
    assert vertices//3<=(1200 if 'arcade' in row['key'] else 600)
    assert parts<=(5 if 'arcade' in row['key'] else 4)
    if not row['key'].startswith('media_'):
        assert abs(min(v[1] for v in points))<.001,(row['key'],'bottom must rest on shelf')
    total+=vertices//3
print('Verified',len(manifest['assets']),'L3D2 assets,',total,'triangles; UV, normals, bounds and SHA256 pass.')
