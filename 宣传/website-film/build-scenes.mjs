import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';

// Capture a separate video edition of the approved site. Never modify docs/.
const root=path.dirname(fileURLToPath(import.meta.url));
const docs=path.resolve(root,'../../docs');
const require=createRequire(import.meta.url);
const {chromium}=require('C:/Users/mamin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
fs.mkdirSync(path.join(root,'scenes'),{recursive:true});
fs.mkdirSync(path.join(root,'assets'),{recursive:true});
for(const name of fs.readdirSync(path.join(docs,'assets'))){
  fs.copyFileSync(path.join(docs,'assets',name),path.join(root,'assets',name));
}
fs.copyFileSync(path.join(docs,'site.css'),path.join(root,'site.css'));
const html=fs.readFileSync(path.join(docs,'index.html'),'utf8').replace('<script src="site.js"></script>','');
fs.writeFileSync(path.join(root,'scene-source.html'),html);
const mime={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.webp':'image/webp','.png':'image/png'};
const server=http.createServer((req,res)=>{
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  const file=path.resolve(root,'.'+pathname);
  if(path.relative(root,file).startsWith('..')||!fs.existsSync(file)){res.writeHead(404).end();return;}
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const base=`http://127.0.0.1:${server.address().port}`;
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try{
  const page=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:1});
  await page.route('**/*',route=>route.request().url().startsWith(base)?route.continue():route.abort());
  await page.goto(base+'/scene-source.html',{waitUntil:'load'});
  await page.addStyleTag({content:`
    #track{display:block;transform:none!important;transition:none!important}
    .slide{display:none}.slide.video-active{display:flex}
    #viewport{cursor:default}
    .project-address{display:inline-flex;align-items:center;justify-content:center;background:var(--green);color:white;border-radius:30px;padding:13px 24px;font-weight:700;font-size:22px;letter-spacing:.2px;white-space:nowrap}
    .actions .project-address{font-size:27px;padding:15px 29px}
  `});
  await page.evaluate(async()=>{
    document.title='miku camera · 视频版分镜';
    const header=document.querySelector('.header-download');
    const address=document.createElement('span');address.className='project-address';address.textContent='camera.zakee.fun';header.replaceWith(address);
    document.querySelector('#download .actions').replaceChildren(address.cloneNode(true));
    document.querySelector('#download .eyebrow').textContent='09 / PROJECT';
    document.querySelectorAll('button').forEach(el=>el.remove());
    document.querySelectorAll('a').forEach(el=>{const span=document.createElement('span');span.className=el.className;span.innerHTML=el.innerHTML;el.replaceWith(span);});
    document.querySelector('#viewport').removeAttribute('tabindex');
    await document.fonts.ready;
    await Promise.all([...document.images].map(async img=>{try{await img.decode();}catch{throw Error('Image decode failed: '+img.src);}}));
  });
  const ids=await page.locator('.slide').evaluateAll(els=>els.map(el=>el.id));
  // Hold times follow reading load and screenshot detail, independently of BGM length.
  const lengths=[10,11,13,13,11,12,11,10,13,8];
  const scenes=[];let cursor=0;
  for(let i=0;i<ids.length;i++){
    await page.evaluate(id=>document.querySelectorAll('.slide').forEach(el=>el.classList.toggle('video-active',el.id===id)),ids[i]);
    const metrics=await page.evaluate(()=>{
      const slide=document.querySelector('.video-active');
      const bounds=[...slide.querySelectorAll('.screenshot-frame,.copy,.shot figcaption')].map(el=>{const r=el.getBoundingClientRect();return {element:el.className,x:r.x,y:r.y,right:r.right,bottom:r.bottom};});
      return {id:slide.id,shots:[...slide.querySelectorAll('.shot img')].map(img=>img.getAttribute('src')),bounds,buttons:document.querySelectorAll('a,button').length,addresses:document.querySelectorAll('.project-address').length};
    });
    if(metrics.buttons!==0||metrics.addresses!==2)throw Error('Unexpected video UI controls');
    for(const b of metrics.bounds)if(b.x<0||b.y<80||b.right>1920||b.bottom>1080)throw Error('Clipped content: '+JSON.stringify(b));
    const file=`scenes/${String(i+1).padStart(2,'0')}-${ids[i]}.png`;
    await page.screenshot({path:path.join(root,file),clip:{x:0,y:80,width:1920,height:1000}});
    if(i===0)await page.screenshot({path:path.join(root,'scenes/header.png'),clip:{x:0,y:0,width:1920,height:80}});
    scenes.push({id:ids[i],file,start:cursor,end:cursor+lengths[i],...metrics});cursor+=lengths[i];
  }
  const shots=scenes.flatMap(s=>s.shots);
  if(shots.length!==13||new Set(shots).size!==13)throw Error('All 13 screenshots must appear exactly once');
  const timing={width:1920,height:1080,fps:30,duration:cursor,transition:.7,projectUrl:'https://camera.zakee.fun/',bgm:'E:/project/miku cure/miku/sing/月が綺麗ねと言われたい！_E35C46C1.ogg',narration:false,sourceHash:crypto.createHash('sha256').update(html).digest('hex'),scenes};
  fs.writeFileSync(path.join(root,'timing.json'),JSON.stringify(timing,null,2));
  fs.writeFileSync(path.join(root,'timing.js'),'window.FILM_TIMING='+JSON.stringify(timing)+';');
  fs.writeFileSync(path.join(root,'scene-source.html'),await page.content());
  console.log(JSON.stringify({scenes:scenes.length,uniqueScreenshots:shots.length,duration:cursor,projectUrl:timing.projectUrl}));
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
