"""Rebuild callback-order adapters with Android NDK r27c. Original pinned cores stay unmodified."""
import hashlib, json, os, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
ndk = Path(os.environ.get('ANDROID_NDK_HOME', Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/ndk/27.2.12479018'))
toolchain = ndk/'toolchains/llvm/prebuilt/windows-x86_64/bin'
compiler = toolchain/'clang++.exe'
include = root.parent/'lemuroid-core-noods/src/main/cpp/upstream/libretro'
targets = {'armeabi-v7a':'armv7a-linux-androideabi21', 'arm64-v8a':'aarch64-linux-android21', 'x86':'i686-linux-android21', 'x86_64':'x86_64-linux-android21'}
rows = []
for abi, target in targets.items():
    for core in ['mesen', 'mesen-s']:
        output = root/f'src/main/jniLibs/{abi}/lib{core}_compat_libretro_android.so'
        args = [str(compiler), f'--target={target}', '-shared', '-fPIC', '-O2', '-std=c++17', '-static-libstdc++', '-fvisibility=hidden', '-Wl,-Bsymbolic', '-Wl,--exclude-libs,ALL', '-I'+str(include), '-DORIGINAL_LIBRARY="lib'+core+'_libretro_android.so"', str(root/'compat/mesen_callbacks.cpp'), '-ldl', '-o', str(output)]
        subprocess.run(args, check=True)
        rows.append({'file':output.relative_to(root).as_posix(),'sha256':hashlib.sha256(output.read_bytes()).hexdigest()})
(root/'compat/manifest.json').write_text(json.dumps({'ndk':'27.2.12479018','libraries':rows},indent=2)+'\n',encoding='utf-8')
print('Built',len(rows),'callback-order adapters')
