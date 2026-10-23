"""Check the encoded deliverable, scene coverage, visual fidelity, and soundtrack."""
from pathlib import Path
import json,re,subprocess
import numpy as np
import soundfile as sf
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parent
ffmpeg=ROOT.parents[2]/'MDmeow/宣传动画/.tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
video=ROOT/'miku-camera-50s-1080p.mp4'
timing=json.loads((ROOT/'timing.json').read_text(encoding='utf-8'))
qa=ROOT/'qa';qa.mkdir(exist_ok=True)
info=subprocess.run([str(ffmpeg),'-hide_banner','-i',str(video)],capture_output=True,encoding='utf-8',errors='replace').stderr
assert all(s in info for s in ['1920x1080','30 fps','Video: h264','Audio: aac']),info
match=re.search(r'Duration: (\d+):(\d+):([\d.]+)',info)
duration=int(match[1])*3600+int(match[2])*60+float(match[3])
assert abs(duration-timing['duration'])<.04
decoded=subprocess.run([str(ffmpeg),'-v','error','-i',str(video),'-f','null','-'],capture_output=True)
assert decoded.returncode==0 and not decoded.stderr,decoded.stderr
shots=[shot for scene in timing['scenes'] for shot in scene['shots']]
assert len(shots)==len(set(shots))==13
errors=[];header=Image.open(ROOT/'scenes/header.png').convert('RGB')
sheet=Image.new('RGB',(1280,1800),'#faf8f2')
for i,scene in enumerate(timing['scenes']):
    target=qa/f'encoded-{scene["id"]}.jpg'
    midpoint=(scene['start']+scene['end'])/2
    subprocess.run([str(ffmpeg),'-v','error','-y','-ss',str(midpoint),'-i',str(video),'-frames:v','1','-q:v','2',str(target)],check=True)
    expected=Image.new('RGB',(1920,1080));expected.paste(header,(0,0));expected.paste(Image.open(ROOT/scene['file']),(0,80))
    actual=Image.open(target).convert('RGB')
    # Captions are rendered by the animation below the static page, separately.
    error=float(np.abs(np.asarray(actual,dtype=float)[:1008]-np.asarray(expected,dtype=float)[:1008]).mean())
    assert error<3,(scene['id'],error)
    errors.append({'id':scene['id'],'mean_pixel_error':round(error,3)})
    sheet.paste(actual.resize((640,360)),((i%2)*640,(i//2)*360))
sheet.save(qa/'encoded-storyboard.jpg',quality=92)
subprocess.run([str(ffmpeg),'-v','error','-y','-ss','4.6','-i',str(video),'-frames:v','1','-q:v','2',str(qa/'encoded-transition.jpg')],check=True)
raw=subprocess.run([str(ffmpeg),'-v','error','-i',str(video),'-vn','-f','f32le','-ac','2','-ar','48000','pipe:1'],capture_output=True,check=True).stdout
audio=np.frombuffer(raw,dtype='<f4').reshape(-1,2)
source,sr=sf.read(ROOT/'audio/mix.wav',always_2d=True)
n=min(len(source),len(audio));correlation=float(np.corrcoef(source[:n:48].flatten(),audio[:n:48].flatten())[0,1])
assert correlation>.98,correlation
peak=float(abs(audio).max());assert peak<1,peak
voice,sr=sf.read(ROOT/'audio/narration.wav');music,sr=sf.read(ROOT/'audio/bgm.wav',always_2d=True)
assert len(timing['captions'])==len(timing['scenes'])==10
ratios=[]
for c,scene in zip(timing['captions'],timing['scenes']):
 assert scene['start']<=c['start']<c['end']<=scene['end']
 a,b=round(c['start']*sr),round(c['end']*sr)
 v=np.sqrt(np.mean(voice[a:b]**2));m=np.sqrt(np.mean(music[a:b]**2))
 assert v>.03 and m>0 and v/m>3,(v,m)
 ratios.append(round(float(20*np.log10(v/m)),1))
report={'width':1920,'height':1080,'fps':30,'duration':duration,'unique_screenshots':13,'chapters':10,'narration_clips':10,'voice_over_music_db':ratios,'full_decode':'passed','encoded_scene_checks':errors,'audio_source_correlation':correlation,'audio_peak':peak,'bytes':video.stat().st_size}
(qa/'checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
