"""Decode the exported file and check video/audio properties and timing."""
from pathlib import Path
import subprocess,json,os,re
ROOT=Path(__file__).resolve().parent
ffmpeg=os.environ.get('FFMPEG_PATH',str(ROOT.parents[1]/'MDmeow/宣传动画/.tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'))
video=ROOT/'miku-camera-promo.mp4'
probe=subprocess.run([ffmpeg,'-hide_banner','-i',str(video)],capture_output=True,text=True,encoding='utf-8',errors='replace')
info=probe.stderr
assert '1920x1080' in info and '30 fps' in info and 'Video: h264' in info and 'Audio: aac' in info,info
assert 'Subtitle:' not in info
decode=subprocess.run([ffmpeg,'-v','error','-i',str(video),'-f','null','-'],capture_output=True,text=True)
assert decode.returncode==0 and not decode.stderr,decode.stderr
timing=json.loads((ROOT/'timing.json').read_text(encoding='utf-8'))
duration=re.search(r'Duration: (\d+):(\d+):([\d.]+)',info)
duration=int(duration[1])*3600+int(duration[2])*60+float(duration[3])
assert abs(duration-timing['duration'])<.1
for c in timing['captions']:
 assert 0<=c['start']<c['end']<=duration
report=dict(width=1920,height=1080,fps=30,duration=duration,frames=round(timing['duration']*30),video_codec='h264',audio_codec='aac',caption_layers=1,full_decode='passed',bytes=video.stat().st_size)
(ROOT/'qa/video-checks.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
# Extract from the encoded MP4, independently of the source canvas.
subprocess.run([ffmpeg,'-v','error','-y','-ss','35','-i',str(video),'-frames:v','1',str(ROOT/'qa/encoded-frame.jpg')],check=True)
print(json.dumps(report,indent=2))
