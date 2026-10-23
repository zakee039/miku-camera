"""Fit the supplied song to the completed storyboard, never the other way around."""
from pathlib import Path
import hashlib,json,subprocess
import soundfile as sf
ROOT=Path(__file__).resolve().parent
timing=json.loads((ROOT/'timing.json').read_text(encoding='utf-8'))
source=Path(timing['bgm'])
audio=ROOT/'audio';audio.mkdir(exist_ok=True)
ffmpeg=ROOT.parents[2]/'MDmeow/宣传动画/.tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
duration=timing['duration']
filters=f'atrim=start=0:end={duration},asetpts=PTS-STARTPTS,loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=in:st=0:d=0.5,afade=t=out:st={duration-2.5}:d=2.5'
subprocess.run([str(ffmpeg),'-hide_banner','-loglevel','error','-y','-i',str(source),'-vn','-af',filters,'-ar','48000','-ac','2','-c:a','pcm_s16le',str(audio/'bgm.wav')],check=True)
info=sf.info(audio/'bgm.wav')
assert abs(info.duration-duration)<1/30
provenance={'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'source_duration':sf.info(source).duration,'output_duration':info.duration,'start_seconds':0,'fade_in':.5,'fade_out':2.5,'target_lufs':-16,'narration':False}
(ROOT/'audio-source.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(provenance,ensure_ascii=False))
