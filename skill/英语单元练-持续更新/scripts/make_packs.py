import json, hashlib, shutil, zipfile, sys
from pathlib import Path
ws, unit_json, audio_dir, pub, version, repo = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4]), int(sys.argv[5]), sys.argv[6]
unit = json.load(open(unit_json)); amap = json.load(open(audio_dir/'audio_map.json'))
uid = unit['unitId']; out = pub/uid; (out/'audio').mkdir(parents=True, exist_ok=True)
items = []; copied = {}
for sec in unit['sections']:
    for it in sec['items']:
        t = it.get('text') or it.get('stem', ''); key = it.get('audioResource') or amap.get(t)
        if not key: continue
        src = audio_dir/('%s.wav' % key); digest = hashlib.sha256(src.read_bytes()).hexdigest(); name = 'audio/%s.wav' % digest
        if digest not in copied:
            shutil.copy2(src, out/name); copied[digest] = name
        items.append({'itemId': it.get('itemId', '%s-%03d' % (uid, len(items))), 'textHash': hashlib.sha256(t.encode()).hexdigest(), 'audioKey': key, 'file': name, 'sha256': digest, 'size': src.stat().st_size, 'mime': 'audio/wav'})
manifest = {'schema': 'english-unit-audio/v1', 'packageVersion': version, 'textbookId': unit.get('textbookId', 'shanghai-4a'), 'unitId': uid, 'contentVersion': unit.get('contentVersion', 1), 'voice': {'language': 'en-GB', 'name': 'Emma', 'engine': 'Kokoro-82M bf_emma', 'license': 'Apache-2.0'}, 'format': 'wav', 'itemCount': len(items), 'fileCount': len(copied), 'items': items}
(out/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
zip_path = pub/('%s-audio-v%d.zip' % (uid, version))
with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted(out.rglob('*')):
        if f.is_file(): z.write(f, f.relative_to(out))
# expanded pack for incremental updates: packs/<uid>/{manifest.json,audio/*}
exp = pub/'packs'/uid
if exp.exists():
    import shutil as _sh; _sh.rmtree(exp)
(exp/'audio').mkdir(parents=True)
(exp/'manifest.json').write_text((out/'manifest.json').read_text())
for f in (out/'audio').iterdir():
    if f.is_file(): shutil.copy2(f, exp/'audio'/f.name)
minapp = int(sys.argv[7]) if len(sys.argv) > 7 else 0
cat_path = pub/'catalog.json'
cat = json.load(open(cat_path)) if cat_path.exists() else {'schema': 'english-unit-audio-catalog/v1', 'catalogVersion': 1, 'units': []}
entry = {'unitId': uid, 'order': unit.get('order', 999), 'title': unit.get('shortTitle', unit.get('title')), 'subtitle': unit.get('subtitle', ''), 'audioVersion': version, 'minAppVersionCode': minapp, 'size': zip_path.stat().st_size, 'sha256': hashlib.sha256(zip_path.read_bytes()).hexdigest(), 'mirrors': {'gitee': 'https://gitee.com/%s/raw/master/%s' % (repo, zip_path.name), 'github': 'https://raw.githubusercontent.com/%s/main/%s' % (repo, zip_path.name)}, 'manifestUrl': {'gitee': 'https://gitee.com/%s/raw/master/packs/%s/manifest.json' % (repo, uid), 'github': 'https://raw.githubusercontent.com/%s/main/packs/%s/manifest.json' % (repo, uid)}}
cat['units'] = [u for u in cat['units'] if u['unitId'] != uid] + [entry]
cat['units'].sort(key=lambda u: u['order'])
cat_path.write_text(json.dumps(cat, ensure_ascii=False, indent=2))
(pub/'units').mkdir(exist_ok=True)
(pub/'units'/('%s.json' % uid)).write_text(json.dumps(unit, ensure_ascii=False, separators=(',', ':')))
print('PACK_OK', uid, len(items), zip_path.stat().st_size)
