// Fixed 1920 × 1080 composition, independent from the responsive website.
const canvas = document.querySelector('#film'), ctx = canvas.getContext('2d');
const W=1920,H=1080, colors={paper:'#faf8f2',ink:'#233f3d',mint:'#39c5bb',green:'#157e77',muted:'#6d817d',pink:'#e79cae'};
const sceneCopy = [
  {tag:'00 / A LITTLE MIKU, EVERY DAY',title:['今天，也和','Miku 一起。'],body:['把喜欢，拍进生活里。','一张日常照片，一点初音绿。'],shots:['anime'],sticker:'hello',note:'今日份的心动，已捕获 ♡',chips:['原生 Android 相机','普通水印 + AI 创作']},
  {tag:'01 / SAY CHEESE',title:['你的镜头里，','给她留个位置。'],body:['打开相机，Miku 就在身边。','时间与地点，替你记住这一刻。'],shots:['camera'],sticker:'happy',note:'咔嚓！我们同框啦。',chips:['所见即所得','点击对焦 · 曝光调节']},
  {tag:'02 / MAKE IT YOURS',title:['可爱这件事，','你说了算。'],body:['换一张 PNG，移动、旋转、调整描边。','收藏成预设，下次还记得你的选择。'],shots:['presets','watermark'],sticker:'shy',note:'放这里？放那里？都可爱！',chips:['自定义 PNG','水印预设']},
  {tag:'03 / A LITTLE AI MAGIC',title:['让想象，','走进这一张。'],body:['拍摄或导入，确认提示词与参考图。','让 Miku 与人物、风景互动融合。'],shots:['prompt'],sticker:'flowers',note:'不止同框，还能走进风景。',chips:['选图 → 提示词 → 生成 → 保存'],fine:'AI 模式需配置自己的 API 服务'},
  {tag:'04 / EVERY SIDE OF MIKU',title:['不一样的她，','一样的心动。'],body:['二次元，或真人写实。','同一处风景，遇见不同的 Miku。'],shots:['anime','real'],sticker:'love',note:'同一处风景，两种心动。',chips:['二次元 / 真人','独立参考图'],fine:'应用内实际生成示例，效果随模型与输入变化'},
  {tag:'05 / DRESS UP YOUR DAY',title:['换一个造型，','再心动一次。'],body:['经典公式服，或随场景搭配。','参考图、分辨率，都由你选择。'],shots:['outfit','real-outfit'],sticker:'happy',note:'今天的她，也好可爱～',chips:['参考图造型','1K · 2K · 4K'],fine:'应用内实际生成示例'},
  {tag:'06 / KEEP THE MOMENT',title:['把这一刻，','好好收藏。'],body:['对比原图，再决定怎样保存。','AI 图、原图，或两张都留着。'],shots:['real-outfit'],sticker:'flowers',note:'这份喜欢，存进相册。',chips:['原图对比','保存至系统相册']},
  {tag:'07 / OUR NEXT LITTLE ADVENTURE',title:['下一张照片，','一起吧。'],body:['miku camera','让喜欢，出现在生活里。'],shots:['anime'],sticker:'love',note:'下次见到好风景，还要一起呀。',chips:['Android · 开源项目'],fine:'github.com/zakee039/miku-camera'},
];
const images={};
window.filmReady=Promise.all([...new Set(sceneCopy.flatMap(s=>[...s.shots,s.sticker]))].map(name=>new Promise((resolve,reject)=>{const im=new Image(); im.onload=()=>{images[name]=im;resolve();};im.onerror=()=>reject(new Error(`Missing asset ${name}`));im.src=`assets/${name}.webp`;}))).then(()=>document.fonts.ready);
function rounded(x,y,w,h,r,fill,stroke){ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=2;ctx.stroke();}}
function text(str,x,y,size=30,color=colors.ink,weight=400){ctx.fillStyle=color;ctx.font=`${weight} ${size}px "Microsoft YaHei",sans-serif`;ctx.fillText(str,x,y);}
function phone(name,x,y,h,angle=0){const im=images[name],w=(h-16)*im.width/im.height+16;ctx.save();ctx.translate(x+w/2,y+h/2);ctx.rotate(angle);ctx.shadowColor='#24483d2b';ctx.shadowBlur=30;ctx.shadowOffsetY=16;rounded(-w/2,-h/2,w,h,32,'#fff');ctx.shadowColor='transparent';ctx.save();ctx.beginPath();ctx.roundRect(-w/2+8,-h/2+8,w-16,h-16,24);ctx.clip();ctx.drawImage(im,-w/2+8,-h/2+8,w-16,h-16);ctx.restore();ctx.restore();return w;}
function sticker(name,x,y,t){ctx.save();ctx.translate(x+69,y+69+Math.sin(t*1.8)*5);ctx.rotate(-.1);rounded(-76,-76,152,152,26,'#fff');ctx.beginPath();ctx.roundRect(-68,-68,136,136,20);ctx.clip();ctx.drawImage(images[name],-68,-68,136,136);ctx.restore();}
const clamp=x=>Math.max(0,Math.min(1,x));const ease=x=>1-Math.pow(1-clamp(x),3);
window.renderAt=async time=>{
  await window.filmReady;
  const timing=window.FILM_TIMING;
  const index=Math.max(0,timing.scenes.findIndex(s=>time>=s.start&&time<s.end));
  const s=sceneCopy[index],cut=timing.scenes[index],local=time-cut.start;
  const enter=ease(local/.65),exit=clamp((cut.end-time)/.25);
  ctx.clearRect(0,0,W,H);ctx.fillStyle=colors.paper;ctx.fillRect(0,0,W,H);
  ctx.fillStyle='#dce7df';for(let x=25;x<W;x+=30)for(let y=25;y<H;y+=30){ctx.globalAlpha=.30;ctx.fillRect(x,y,1,1);}ctx.globalAlpha=1;
  text('miku camera',100,77,32,colors.ink,700);text('A LITTLE MIKU, EVERY DAY',340,76,15,colors.muted,500);text('#39C5BB  /  一点初音绿，一点喜欢。',1400,76,17,colors.green,500);
  ctx.fillStyle='#dce7df';ctx.fillRect(100,105,1720,1);
  ctx.save();ctx.globalAlpha=enter*exit;ctx.translate(0,20*(1-enter));
  const visualLeft=index%2===0, vx=visualLeft?100:1060, tx=visualLeft?1020:120;
  rounded(vx+20,245,700,525,160,index%3===2?'#f5e5e3':'#e4f0e6');
  ctx.save();ctx.strokeStyle='#b4d7c9';ctx.lineWidth=2;ctx.setLineDash([6,10]);ctx.beginPath();ctx.ellipse(vx+370,505,330,335,-.12,0,Math.PI*2);ctx.stroke();ctx.restore();
  const drift=Math.sin(local*.65)*4;
  if(s.shots.length===2){phone(s.shots[0],vx+28,195+drift,642,-.055);phone(s.shots[1],vx+360,155-drift,682,.045);}else{phone(s.shots[0],vx+210,148+drift,704,.025);}
  text('✧',vx+645,242,70,colors.pink,400);text('✳',vx+25,760,63,colors.green,400);
  ctx.save();ctx.translate(vx+105,895);ctx.rotate(-.015);rounded(0,-25,555,44,10,'#fffef8','#dce7df');text(s.note,22,6,23,colors.green,500);ctx.restore();
  text(s.tag,tx,242,20,colors.green,700);
  s.title.forEach((line,i)=>text(line,tx,365+i*108,78,i===1?colors.green:colors.ink,700));
  s.body.forEach((line,i)=>text(line,tx,556+i*52,29,colors.muted,400));
  let chipX=tx;s.chips.forEach(label=>{ctx.font='20px "Microsoft YaHei"';const width=ctx.measureText(label).width+32;rounded(chipX,651,width,44,22,'#e9f1e9','#d4e3da');text(label,chipX+16,681,20,colors.green,500);chipX+=width+12;});
  sticker(s.sticker,tx+5,733,local);text(index===7?'现在，就一起出门吧 ♡':'今天也要开心呀～',tx+175,790,25,colors.green,500);if(s.fine)text(s.fine,tx+175,832,index===7?23:17,colors.muted,400);
  ctx.restore();
  const caption=timing.captions.find(c=>time>=c.start&&time<c.end);
  ctx.fillStyle='#e2e9e1';ctx.fillRect(100,929,1720,1);
  if(caption){ctx.font='500 34px "Microsoft YaHei"';const measured=ctx.measureText(caption.text).width;text(caption.text,(W-measured)/2,991,34,colors.ink,500);}
  text(`${String(index+1).padStart(2,'0')} / 08`,100,1048,16,colors.muted,500);text('MIKU IN YOUR MOMENTS',1515,1048,16,colors.muted,500);
  ctx.fillStyle=colors.mint;ctx.fillRect(0,1075,W*clamp(time/timing.duration),5);
};
window.filmReady.then(()=>{const audio=document.querySelector('#sound');window.renderAt(0.8);audio.addEventListener('seeked',()=>window.renderAt(Math.min(audio.currentTime,window.FILM_TIMING.duration-.01)));const loop=()=>{if(!audio.paused)window.renderAt(Math.min(audio.currentTime,window.FILM_TIMING.duration-.01));requestAnimationFrame(loop);};loop();});
