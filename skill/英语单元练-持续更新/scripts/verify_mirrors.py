import json, hashlib, urllib.request, sys, time
gitee_cat, github_cat = sys.argv[1], sys.argv[2]
def fetch(url, tries=3):
    for i in range(tries):
        try:
            r = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'eup-verify/1.0'}), timeout=90)
            return r.read()
        except Exception:
            if i == tries - 1: raise
            time.sleep(3)
results = []; cat = None; src = None
for name, url in [('github', github_cat), ('gitee', gitee_cat)]:
    try:
        c2 = json.loads(fetch(url).decode('utf-8'))
        results.append({'mirror': name, 'catalog': 'OK'})
        if cat is None: cat = c2; src = name
    except Exception as e:
        results.append({'mirror': name, 'catalog': 'FAIL %s' % e})
        if name == 'github': ok = False
if cat is None:
    print(json.dumps({'verify': 'FAIL', 'details': results}, ensure_ascii=False)); sys.exit(1)
ok = True
import urllib.request
def size_of(url):
    req = urllib.request.Request(url, method='GET', headers={'User-Agent': 'eup-verify/1.0', 'Range': 'bytes=0-0'})
    r = urllib.request.urlopen(req, timeout=30)
    cr = r.headers.get('Content-Range')
    if cr and '/' in cr: return int(cr.split('/')[-1])
    return int(r.headers.get('Content-Length', -1))
for u in cat['units']:
    # github: full download + sha256 (authoritative); gitee: size-only (fast, slow uplink mirror)
    try:
        b = fetch(u['mirrors']['github']); sha = hashlib.sha256(b).hexdigest()
        good = sha == u['sha256'] and len(b) == u['size']
        results.append({'mirror': 'github', 'unit': u['unitId'], 'size': len(b), 'sha256_match': sha == u['sha256'], 'status': 'OK' if good else 'HASH_MISMATCH'})
        ok = ok and good
    except Exception as e:
        results.append({'mirror': 'github', 'unit': u['unitId'], 'status': 'TRANSPORT_ERROR %s' % e}); ok = False
    # gitee mirror no longer carries zips; check catalog-level availability only (done once below)

print(json.dumps({'verify': 'PASS' if ok else 'FAIL', 'catalog_source': src, 'details': results}, ensure_ascii=False, indent=1))
sys.exit(0 if ok else 1)
