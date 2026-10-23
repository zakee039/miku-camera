"""Concise Chinese narration aligned to the 50-second cut, with ducked user BGM."""
from pathlib import Path
import asyncio,hashlib,json,math,subprocess,sys
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/'.deps'))
import edge_tts
SR=48000
AUDIO=ROOT/'audio';AUDIO.mkdir(exist_ok=True)
FFMPEG=ROOT.parents[2]/'MDmeow/宣传动画/.tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
LINES=[
 '打开 Miku Camera，拍摄初音水印合影。',
 '取景时预览水印，时间地点一起保留。',
 '选择或新建预设，上传图片，自由调整水印样式。',
 '保存多套接口配置，随时切换服务和模型。',
 '设好默认模式、提示词和参考图，下次直接用。',
 '导入照片，选择形象、服装和分辨率，开始创作。',
 '生成时回到桌面，悬浮窗随时查看多项任务。',
 '同一处风景，试试二次元和真人两种形象。',
 '也能换上参考造型，长按对比原图，再保存喜欢的结果。',
 '打开项目主页，下载 Miku Camera，开始拍摄。'
]
def path_for(line):
 return AUDIO/('voice-'+hashlib.sha256((line+'+16%').encode()).hexdigest()[:12]+'.mp3')
def read_mono(path):
 x,sr=sf.read(path,always_2d=True);x=x.mean(axis=1)
 if sr!=SR:
  g=math.gcd(sr,SR);x=resample_poly(x,SR//g,sr//g)
 active=np.flatnonzero(abs(x)>.003)
 if len(active):x=x[max(0,active[0]-round(.04*SR)):min(len(x),active[-1]+round(.09*SR))]
 return x
async def generate():
 sem=asyncio.Semaphore(2)
 async def one(i,line):
  target=path_for(line)
  async with sem:
   if not target.exists() or target.stat().st_size<1000:
    for attempt in range(3):
     try:
      await edge_tts.Communicate(line.replace('Miku Camera','米库相机'),'zh-CN-XiaoxiaoNeural',rate='+16%',connect_timeout=15,receive_timeout=35).save(str(target));break
     except Exception:
      if attempt==2:raise
   print(f'Voice {i+1}: {len(read_mono(target))/SR:.2f}s',flush=True)
 await asyncio.gather(*(one(i,line) for i,line in enumerate(LINES)))
def build():
 timing=json.loads((ROOT/'timing.json').read_text(encoding='utf-8'));n=round(timing['duration']*SR)
 voice=np.zeros(n);captions=[];fit=[]
 for i,(line,scene) in enumerate(zip(LINES,timing['scenes'])):
  x=read_mono(path_for(line));available=scene['end']-scene['start']-.65
  speed=max(1,len(x)/SR/available)
  if speed>1:
   assert speed<1.25,(i,speed)
   sf.write(AUDIO/f'raw-{i}.wav',x,SR)
   subprocess.run([str(FFMPEG),'-v','error','-y','-i',str(AUDIO/f'raw-{i}.wav'),'-af',f'atempo={speed:.7f}',str(AUDIO/f'fit-{i}.wav')],check=True)
   x=read_mono(AUDIO/f'fit-{i}.wav')[:round(available*SR)]
  x*=min(.72/max(abs(x).max(),.001),.15/max(np.sqrt(np.mean(x*x)),.001))
  start=scene['start']+.35;end=start+len(x)/SR
  assert end<=scene['end']-.2,(i,end,scene['end'])
  a=round(start*SR);voice[a:a+len(x)]+=x
  captions.append({'start':start,'end':min(scene['end']-.05,end+.16),'text':line})
  fit.append({'id':scene['id'],'start':start,'end':end,'additional_speed':speed})
 source=Path(timing['bgm']);music,sr=sf.read(source,always_2d=True)
 if sr!=SR:
  g=math.gcd(sr,SR);music=resample_poly(music,SR//g,sr//g,axis=0)
 music=music[:n];assert len(music)==n
 music*=.065/np.sqrt(np.mean(music*music))
 gain=np.ones(n)
 for c in captions:
  a=max(0,round((c['start']-.15)*SR));b=round(c['start']*SR);e=min(n,round(c['end']*SR));d=min(n,round((c['end']+.22)*SR))
  gain[a:b]=np.minimum(gain[a:b],np.linspace(1,.28,b-a));gain[b:e]=np.minimum(gain[b:e],.28);gain[e:d]=np.minimum(gain[e:d],np.linspace(.28,1,d-e))
 fade=np.minimum(1,np.arange(n)/(.35*SR))*np.minimum(1,(n-1-np.arange(n))/(1.3*SR))
 music*=gain[:,None]*fade[:,None]
 mix=voice[:,None]+music
 assert abs(mix).max()<.98,abs(mix).max()
 for name,data in [('narration',voice),('bgm',music),('mix',mix)]:sf.write(AUDIO/f'{name}.wav',data,SR,subtype='PCM_16')
 timing['captions']=captions;timing['voice']='zh-CN-XiaoxiaoNeural';timing['narration']=True
 (ROOT/'timing.json').write_text(json.dumps(timing,ensure_ascii=False,indent=2),encoding='utf-8')
 (ROOT/'timing.js').write_text('window.FILM_TIMING='+json.dumps(timing,ensure_ascii=False)+';',encoding='utf-8')
 (ROOT/'audio-source.json').write_text(json.dumps({'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'duration':timing['duration'],'narration':True,'voice':timing['voice'],'rate':'+16%','fit':fit,'peak':float(abs(mix).max()),'music_ducking_db':round(20*np.log10(.28),1),'fade_out':1.3},ensure_ascii=False,indent=2),encoding='utf-8')
 def stamp(t):
  ms=round(t*1000);return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'
 (ROOT/'captions.srt').write_text('\n\n'.join(f'{i+1}\n{stamp(c["start"])} --> {stamp(c["end"])}\n{c["text"]}' for i,c in enumerate(captions))+'\n',encoding='utf-8')
 print(json.dumps({'duration':timing['duration'],'narration_clips':len(fit),'max_fit_speed':max(f['additional_speed'] for f in fit),'peak':float(abs(mix).max())}),flush=True)
if __name__=='__main__':asyncio.run(generate());build()
