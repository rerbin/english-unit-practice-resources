#!/bin/sh
set -eu
VC="${1:-1}"; VN="${2:-1.0.0}"; P=english-paper-reader
export PATH=/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/jdk/bin:$PATH
AAPT2=/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/android-sdk/build-tools/35.0.0/aapt2; ANDROID=/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/android-sdk/platforms/android-35/android.jar; D8=/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/android-sdk/build-tools/35.0.0/d8; BT=/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/android-sdk/build-tools/35.0.0
mkdir -p "$P/build"
$AAPT2 compile --dir "$P/res" -o "$P/build/compiled.zip"
$AAPT2 link -o "$P/build/base.apk" -I "$ANDROID" --manifest "$P/AndroidManifest.xml" "$P/build/compiled.zip" --min-sdk-version 30 --target-sdk-version 35 --version-code "$VC" --version-name "$VN"
mkdir -p "$P/build/classes" "$P/build/dex"
/run/csi/mount-root/nas/4079184d856ecc166ed19d4887083405/tools/jdk/bin/javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID" -d "$P/build/classes" $(find "$P/src" -name '*.java')
$D8 --lib "$ANDROID" --min-api 30 --output "$P/build/dex" $(find "$P/build/classes" -name '*.class')
cp "$P/build/base.apk" "$P/build/unsigned.apk"
python3 -c "import zipfile;z=zipfile.ZipFile('$P/build/unsigned.apk','a',compression=zipfile.ZIP_DEFLATED);z.write('$P/build/dex/classes.dex','classes.dex');z.close()"
$BT/zipalign -f -p 4 "$P/build/unsigned.apk" "$P/build/aligned.apk"
$BT/apksigner sign --ks "${KS_PATH:?set KS_PATH to your keystore}" --ks-key-alias mooc-tv --ks-pass pass:"${KS_PASS:?set KS_PASS}" --key-pass pass:"${KEY_PASS:?set KEY_PASS}" --out "$P/EnglishPaperReader-${VN}.apk" "$P/build/aligned.apk"
$BT/apksigner verify --verbose "$P/EnglishPaperReader-${VN}.apk"
