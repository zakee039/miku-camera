import {createRequire} from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_DIR||'C:/Users/mamin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.dirname(fileURLToPath(import.meta.url));
const browser=await chromium.launch({headless:true});
const report=[];fs.mkdirSync(path.join(root,'qa'),{recursive:true});
try{
 for(const [width,height] of [[1440,1000],[1920,1080],[2560,1440],[390,844],[768,1024]]){
  const page=await browser.newPage({viewport:{width,height},reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5199/docs/',{waitUntil:'networkidle'});
  await page.evaluate(async()=>{await Promise.all([...document.images].map(im=>{im.loading='eager';return im.decode();}));});
  await page.screenshot({path:path.join(root,`qa/web-${width}.jpg`),fullPage:true});
  const result=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,missing:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src),videos:document.querySelectorAll('video,iframe').length,scenes:[...document.querySelectorAll('.scene')].map(e=>({width:e.offsetWidth,visualOrder:getComputedStyle(e.querySelector('.visual')).order}))}));
  await page.getByRole('button',{name:'参考图造型',exact:true}).click();
  const switched=await page.locator('#style-left').getAttribute('src');
  await page.getByRole('button',{name:'经典公式服',exact:true}).click();
  const restored=await page.locator('#style-left').getAttribute('src');
  const item={width,...result,switched,restored,errors};report.push(item);
  if(result.overflow||result.missing.length||result.videos||errors.length||switched!=='assets/outfit.webp'||restored!=='assets/anime.webp')throw new Error(JSON.stringify(item));
  await page.close();
 }
 fs.writeFileSync(path.join(root,'qa/web-checks.json'),JSON.stringify(report,null,2));console.log('PASS: responsive layouts, images, screenshot switch, no video embedding',report.map(r=>r.width));
 if(fs.existsSync(path.join(root,'timing.json'))){
  const timing=JSON.parse(fs.readFileSync(path.join(root,'timing.json'),'utf8'));const page=await browser.newPage({viewport:{width:1920,height:1080}});await page.goto('http://127.0.0.1:5199/宣传/film.html');await page.evaluate(()=>window.filmReady);
  for(let i=0;i<timing.scenes.length;i++){await page.evaluate(t=>window.renderAt(t),timing.scenes[i].start+2);const b64=await page.evaluate(()=>document.querySelector('#film').toDataURL('image/jpeg',.94).split(',')[1]);fs.writeFileSync(path.join(root,`qa/film-${i}.jpg`),Buffer.from(b64,'base64'));}
  const captions=await page.evaluate(()=>window.FILM_TIMING.captions.map(c=>{const x=document.querySelector('canvas').getContext('2d');x.font='500 34px "Microsoft YaHei"';return {text:c.text,width:x.measureText(c.text).width};}));if(captions.some(c=>c.width>1720))throw new Error('Caption exceeds safe area');console.log('PASS: 8 scene frames, subtitle safe width');
 }
}finally{await browser.close();}
