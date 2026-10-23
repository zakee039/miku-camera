"""Chinese synthetic narration, original instrumental score and aligned captions."""
import asyncio,json,sys,math,os
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,os.environ.get('EDGE_TTS_DIR',str(ROOT/'.deps')))
SR=48000
LINES=[
 ['今天，也和 Miku 一起。','把喜欢拍进生活里，让她陪你收藏普通的一天。'],
 ['打开相机，给 Miku 留个位置。','可爱的合影水印，和时间地点一起，记住这一刻。'],
 ['可爱这件事，你说了算。','换一张喜欢的图片，自由摆放，再收进自己的水印预设。'],
 ['让想象，走进这一张。','选好照片，确认提示词和参考图，让 AI 创作她与风景的互动。'],
 ['二次元，或者真人写实。','同一处风景，遇见不同的 Miku，一样让人心动。'],
 ['换一个造型，再心动一次。','服装、参考图和分辨率，都可以按你的喜欢来选择。'],
 ['把这一刻，好好收藏。','对比原图，保存 AI 图、原图，或者两张都留着。'],
 ['Miku Camera，下一张照片，一起吧。','带上 Miku 出门，让喜欢，出现在生活里。']]
AUDIO=ROOT/'audio';AUDIO.mkdir(exist_ok=True)
def clip(path):
 x,sr=sf.read(path,always_2d=True);x=x.mean(axis=1)
 if sr!=SR:
  g=math.gcd(sr,SR);x=resample_poly(x,SR//g,sr//g)
 active=np.flatnonzero(np.abs(x)>.003)
 if len(active):x=x[max(0,active[0]-int(.06*SR)):min(len(x),active[-1]+int(.12*SR))]
 return x
async def voices():
 import edge_tts
 sem=asyncio.Semaphore(2)
 async def one(i,j,line):
  path=AUDIO/f'voice-{i}-{j}.mp3'
  async with sem:
   if not path.exists() or path.stat().st_size < 1000:
    for attempt in range(3):
     try:
      await edge_tts.Communicate(line.replace('Miku Camera','米库 Camera').replace('Miku','米库'),'zh-CN-XiaoxiaoNeural',rate='+12%',connect_timeout=15,receive_timeout=35).save(str(path));break
     except Exception:
      if attempt==2:raise
   print(path.name,round(len(clip(path))/SR,2),flush=True)
 await asyncio.gather(*(one(i,j,line) for i,lines in enumerate(LINES) for j,line in enumerate(lines)))
def build():
 scenes=[];captions=[];tracks=[];cursor=0
 for i,lines in enumerate(LINES):
  onset=cursor+.6
  for j,line in enumerate(lines):
   x=clip(AUDIO/f'voice-{i}-{j}.mp3');end=onset+len(x)/SR
   captions.append(dict(start=onset,end=end+.1,text=line));tracks.append((onset,x));onset=end+.24
  end=math.ceil(max(cursor+7.5,onset+.6)*30)/30
  scenes.append(dict(start=cursor,end=end));cursor=end
 data=dict(duration=cursor,fps=30,scenes=scenes,captions=captions)
 (ROOT/'timing.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
 (ROOT/'timing.js').write_text('window.FILM_TIMING='+json.dumps(data,ensure_ascii=False)+';',encoding='utf-8')
 n=round(cursor*SR);voice=np.zeros(n)
 for onset,x in tracks:
  x=x*.64/max(abs(x).max(),.001);a=round(onset*SR);voice[a:a+len(x)]+=x
 # Original mellow music-box score, C / Am / F / G; no external samples.
 music=np.zeros((n,2));beat=60/104
 chords=[[60,64,67,71],[57,60,64,69],[53,57,60,65],[55,59,62,67]]
 def note(start,midi,length,amp,pan):
  a=int(start*SR);count=min(int(length*SR),n-a)
  if count<=0:return
  t=np.arange(count)/SR;f=440*2**((midi-69)/12)
  env=(1-np.exp(-t*55))*np.exp(-t*3/length)*np.minimum(1,(length-t)*10)
  wave=(np.sin(2*np.pi*f*t)+.25*np.sin(2*np.pi*f*2*t)+.08*np.sin(2*np.pi*f*3*t))*env*amp
  music[a:a+count,0]+=wave*np.sqrt((1-pan)/2);music[a:a+count,1]+=wave*np.sqrt((1+pan)/2)
 for b in range(math.ceil(cursor/beat)):
  chord=chords[(b//8)%4]
  note(b*beat,chord[[0,2,1,3,2,1,3,2][b%8]]+12,beat*2,.10,.25 if b%2 else -.25)
  if b%4==0:
   note(b*beat,chord[0]-12,beat*4,.11,0)
   for m in chord:note(b*beat,m,beat*4,.025,0)
 gain=np.full(n,.5)
 for c in captions:
  a=max(0,int((c['start']-.2)*SR));b=int(c['start']*SR);e=min(n,int(c['end']*SR));d=min(n,int((c['end']+.3)*SR))
  gain[a:b]=np.minimum(gain[a:b],np.linspace(.5,.23,b-a));gain[b:e]=.23;gain[e:d]=np.minimum(gain[e:d],np.linspace(.23,.5,d-e))
 fade=np.minimum(1,np.arange(n)/SR/1.3)*np.minimum(1,(n-np.arange(n))/SR/1.8)
 music*=fade[:,None];mix=voice[:,None]+music*gain[:,None]
 sf.write(AUDIO/'music.wav',music,SR,subtype='PCM_16');sf.write(AUDIO/'narration.wav',voice,SR,subtype='PCM_16');sf.write(AUDIO/'mix.wav',mix,SR,subtype='PCM_16')
 def stamp(t):
  ms=round(t*1000);return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'
 (ROOT/'captions-reference.srt').write_text('\n\n'.join(f'{i+1}\n{stamp(c["start"])} --> {stamp(c["end"])}\n{c["text"]}' for i,c in enumerate(captions))+'\n',encoding='utf-8')
 print('Duration:',cursor,'Peak:',abs(mix).max(),flush=True)
if __name__=='__main__':
 asyncio.run(voices());build()
