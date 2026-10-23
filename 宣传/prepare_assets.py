"""Prepare local, traceable promotional assets without changing originals."""
from pathlib import Path
from PIL import Image
import json, shutil, sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/assets'
OUT.mkdir(parents=True, exist_ok=True)
shots = sorted((ROOT / '设计/miku_camera_screenshot').glob('*.jpg'))
memes = sorted(Path('C:/Users/mamin/OneDrive/Pictures/miku/表情包').glob('*'))
mapping = {'desktop': 0, 'camera': 1, 'presets': 2, 'watermark': 3,
           'api-configs': 4, 'providers': 5, 'settings': 6, 'prompt': 7, 'background': 8,
           'anime': 9, 'real': 10, 'outfit': 11, 'real-outfit': 12}
sources = {}
for name, i in mapping.items():
    image = Image.open(shots[i]).convert('RGB')
    image.thumbnail((1000, 2200))
    image.save(OUT / f'{name}.webp', quality=88)
    sources[f'{name}.webp'] = str(shots[i].relative_to(ROOT))
for name, i in {'hello': 32, 'love': 36, 'flowers': 26, 'happy': 63, 'shy': 25}.items():
    image = Image.open(memes[i]).convert('RGB')
    image.thumbnail((400, 400))
    image.save(OUT / f'{name}.webp', quality=90)
    sources[f'{name}.webp'] = str(memes[i])
Image.open(ROOT / 'icon.png').convert('RGBA').resize((192,192)).save(OUT/'icon.png')
(ROOT/'宣传/asset-sources.json').write_text(json.dumps(sources, ensure_ascii=False, indent=2), encoding='utf-8')
if '--web-only' not in sys.argv:
    shutil.copytree(OUT, ROOT/'宣传/assets', dirs_exist_ok=True)
print('Prepared', len(sources)+1, 'assets')
