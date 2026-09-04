import sys, json, wave
from pathlib import Path
piper_dir, model_dir, unit_path, out_dir = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4])
sys.path.insert(0, piper_dir)
try:
    from piper import PiperVoice
    from piper.config import SynthesisConfig
except Exception:
    print('PIPER_MISSING'); sys.exit(3)
voice = PiperVoice.load(model_dir/'en_GB-alba-medium.onnx', config_path=model_dir/'en_GB-alba-medium.onnx.json')
cfg = SynthesisConfig(length_scale=1.12, noise_scale=0.667, noise_w_scale=0.8, volume=1.0)
data = json.load(open(unit_path))
texts = []
for sec in data['sections']:
    for it in sec['items']:
        t = it.get('text') or it.get('stem')
        if t and t not in texts: texts.append(t)
out_dir.mkdir(parents=True, exist_ok=True)
amap = {}
for i, t in enumerate(texts):
    key = 'u_%03d' % i
    p = out_dir/('%s.wav' % key)
    if not p.exists():
        with wave.open(str(p), 'wb') as f: voice.synthesize_wav(t, f, syn_config=cfg)
    amap[t] = key
json.dump(amap, open(out_dir/'audio_map.json', 'w'), ensure_ascii=False)
print('AUDIO_OK', len(texts))
