"""Build an isolated platform instrumentation probe; no test libraries/downloads required."""
from pathlib import Path
import os, subprocess, zipfile
root=Path(__file__).resolve().parents[3]
out=root/'build/description-validation/probe';out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
java=Path(os.environ['JAVA_HOME'])/'bin'
manifest=out/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.lemuroid.validation.description"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="31"/><application android:label="Description scroll validation" android:debuggable="true"/><instrumentation android:name="org.lemuroid.validation.DescriptionScrollProbe" android:targetPackage="com.swordfish.lemuroid.debug"/></manifest>''',encoding='utf-8')
classes=out/'classes';classes.mkdir(exist_ok=True)
def run(args):subprocess.run([str(a) for a in args],check=True)
run([java/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',classes,Path(__file__).with_name('DescriptionScrollProbe.java')])
run([bt/'d8.bat','--lib',android,'--output',out,*classes.rglob('*.class')])
apk=out/'unsigned.apk'
run([bt/'aapt.exe','package','-f','-M',manifest,'-I',android,'-F',apk])
with zipfile.ZipFile(apk,'a') as z:z.write(out/'classes.dex','classes.dex')
run([bt/'apksigner.bat','sign','--ks',root/'debug.keystore','--ks-pass','pass:android','--out',out/'probe.apk',apk])
print(out/'probe.apk')
