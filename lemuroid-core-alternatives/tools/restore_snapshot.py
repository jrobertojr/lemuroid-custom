"""Verify local snapshot, or restore a missing file only if official archive matches the pinned hash."""
import concurrent.futures as cf, hashlib, io, json, sys, urllib.request, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def verify(row):
 path=ROOT/row['file']
 if path.is_file():
  if sha(path.read_bytes())!=row['sha256']: raise ValueError('Hash mismatch: '+str(path))
  return
 if '--restore' not in sys.argv:raise FileNotFoundError(path)
 with urllib.request.urlopen(row['url'],timeout=120) as response:archive=response.read()
 if sha(archive)!=row['archive_sha256']:raise ValueError('Official latest artifact changed; refusing replacement: '+row['url'])
 with zipfile.ZipFile(io.BytesIO(archive)) as z:
  entry=next(name for name in z.namelist() if Path(name).name==row['core']+'_libretro_android.so');data=z.read(entry)
 if sha(data)!=row['sha256']:raise ValueError('Library hash mismatch: '+row['file'])
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
rows=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))['libraries']
with cf.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(verify,rows))
print('Verified',len(rows),'pinned native libraries.')

compat = json.loads((ROOT/'compat/manifest.json').read_text(encoding='utf-8'))['libraries']
for row in compat:
 path = ROOT/row['file']
 if not path.is_file(): raise FileNotFoundError(str(path) + '; rebuild with tools/build_mesen_compat.py')
 if sha(path.read_bytes()) != row['sha256']: raise ValueError('Adapter hash mismatch: '+row['file'])
print('Verified', len(compat), 'callback-order adapters.')
