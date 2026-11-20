import concurrent.futures as cf, hashlib, io, json, struct, urllib.request, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CORES='stella2014 quicknes nestopia snes9x2005_plus snes9x2010 picodrive gearsystem sameboy gpsp vba_next parallel_n64 swanstation fbalpha2012 mame2000 mame2003 mednafen_pce mednafen_lynx race'.split()
ABIS={'armeabi-v7a':(1,40),'arm64-v8a':(2,183),'x86':(1,3),'x86_64':(2,62)}
def get(url):
 with urllib.request.urlopen(url,timeout=120) as f:return f.read()
def fetch(item):
 core,abi=item; name=core+'_libretro_android.so';url=f'https://buildbot.libretro.com/nightly/android/latest/{abi}/{name}.zip'; raw=get(url)
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  matches=[n for n in z.namelist() if Path(n).name==name];assert len(matches)==1
  data=z.read(matches[0])
 assert data[:4]==b'\x7fELF' and data[4]==ABIS[abi][0] and struct.unpack_from('<H',data,18)[0]==ABIS[abi][1],(core,abi,'wrong ELF')
 dest=ROOT/'src/main/jniLibs'/abi/('lib'+name);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
 return dict(core=core,abi=abi,url=url,archive_sha256=hashlib.sha256(raw).hexdigest(),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),file=str(dest.relative_to(ROOT)).replace('\\','/'))
def main():
 if (ROOT/'manifest.json').exists():
  raise SystemExit('Snapshot already pinned. Use restore_snapshot.py; replacing a snapshot requires explicit review of new hashes, source versions and TV tests.')
 with cf.ThreadPoolExecutor(max_workers=6) as pool: rows=list(pool.map(fetch,[(c,a) for c in CORES for a in ABIS]))
 (ROOT/'manifest.json').write_text(json.dumps(dict(retrieved_utc='2026-11-19',source='Official Libretro buildbot snapshot. SHA256 values pin retrieved artifacts, not publisher signatures.',libraries=rows),indent=2)+'\n')
 info=ROOT/'metadata';info.mkdir(exist_ok=True)
 for c in CORES:
  try:(info/(c+'.info')).write_bytes(get('https://raw.githubusercontent.com/libretro/libretro-super/master/dist/info/'+c+'_libretro.info'))
  except Exception as e: print('INFO',c,str(e))
 print('VERIFIED',len(rows),'LIBRARIES',sum(r['bytes'] for r in rows),'BYTES')
if __name__=='__main__':main()
