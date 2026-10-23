import {createRequire} from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_DIR||'C:/Users/mamin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.dirname(fileURLToPath(import.meta.url)),out=path.join(root,'qa/complete-story');fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true});const report=[];
const assert=(condition,message)=>{if(!condition)throw new Error(message);};
try{
 for(const [width,height] of [[1920,1080],[1440,900],[1280,720],[2560,1440],[390,844],[375,667],[768,1024],[844,390]]){
  const page=await browser.newPage({viewport:{width,height},reducedMotion:'reduce',hasTouch:true});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5199/docs/',{waitUntil:'networkidle'});await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode().catch(()=>{}))));
  for(let i=0;i<10;i++){
   await page.keyboard.press(i===0?'Home':'ArrowRight');
   const info=await page.evaluate(()=>{const s=document.querySelector('.slide[aria-hidden="false"]'),v=document.querySelector('#viewport').getBoundingClientRect();return {hash:location.hash,scrollY,bodyHeight:document.body.scrollHeight,viewportHeight:innerHeight,copy:s.querySelector('.copy').getBoundingClientRect().toJSON(),bounds:v.toJSON(),shots:[...s.querySelectorAll('.shot .screenshot-frame')].map(x=>x.getBoundingClientRect().toJSON()),missing:[...document.images].filter(x=>x.getAttribute('src')&&!x.naturalWidth).length,video:document.querySelectorAll('video,iframe').length};});
   assert(info.scrollY===0&&info.bodyHeight<=height+1,`Vertical overflow ${width} ${i}: ${JSON.stringify(info)}`);
   assert(info.copy.top>=info.bounds.top-1&&info.copy.bottom<=info.bounds.bottom+1,`Clipped copy ${width} ${i}: ${JSON.stringify(info)}`);
   assert(info.shots.every(x=>x.top>=info.bounds.top-1&&x.bottom<=info.bounds.bottom+1&&x.height>70),`Clipped shot ${width} ${i}: ${JSON.stringify(info)}`);
   assert(!info.missing&&!info.video,`Missing images or embedded video ${width}`);
   const fitting=await page.evaluate(()=>({frames:[...document.querySelectorAll('.slide[aria-hidden="false"] .shot .screenshot-frame')].map(f=>{const r=f.getBoundingClientRect(),im=f.querySelector('img'),ir=im.getBoundingClientRect();return {border:parseFloat(getComputedStyle(f).borderTopWidth),ratio:r.width/r.height,source:im.naturalWidth/im.naturalHeight,delta:Math.abs(r.height-ir.height)};}),header:document.querySelector('header').getBoundingClientRect().toJSON(),panel:document.querySelector('.slide[aria-hidden="false"] .panel').getBoundingClientRect().toJSON()}));
   assert(fitting.frames.every(f=>f.border===0&&f.delta<1&&Math.abs(f.ratio-f.source)<.002),'No borders, cropping or letterboxing');
   if(width>=1280)assert(fitting.header.width<=1280&&Math.abs(fitting.header.x-fitting.panel.x)<1&&Math.abs(fitting.header.width-fitting.panel.width)<1,'Header and content share centered column');
   if(width===1440||[0,4,6,9].includes(i))await page.screenshot({path:path.join(out,`${width}-${height}-${i}.jpg`)});
  }
  const assets=await page.locator('.shot img').evaluateAll(images=>images.map(i=>i.getAttribute('src').replace('assets/','')));
  const expected=JSON.parse(fs.readFileSync(path.join(root,'web-story.json'),'utf8')).flatMap(p=>p.screenshots.map(s=>s.asset));
  assert(assets.length===13&&new Set(assets).size===13&&JSON.stringify(assets)===JSON.stringify(expected),'All 13 screenshots exactly once in source order');
  const ui=await page.evaluate(()=>({footer:!!document.querySelector('footer'),buttons:document.querySelectorAll('main button').length,header:[...document.querySelectorAll('header a')].map(a=>a.textContent),links:[...document.querySelectorAll('main a')].map(a=>({text:a.textContent,parent:a.closest('.slide').id,href:a.href,background:getComputedStyle(a).backgroundColor})),topBackground:getComputedStyle(document.querySelector('.header-download')).backgroundColor}));
  assert(!ui.footer&&ui.buttons===1,'Only return-home button, no footer');assert(ui.header.length===2&&ui.header[1]==='下载安卓版','Header cleaned');
  assert(ui.links.length===2&&ui.links.every(a=>a.parent==='download'&&a.background===ui.topBackground),'Only two matching final-page actions');
  assert(ui.links[0].text==='下载Android版'&&ui.links[1].text==='查看项目','Final button labels');
  await page.keyboard.press('Home');await page.locator('#home .description').click();assert(new URL(page.url()).hash==='#camera','Copy click advances');
  await page.waitForTimeout(560);await page.locator('#camera .shot img').click();assert(new URL(page.url()).hash==='#watermark','Image click advances');
  await page.keyboard.press('ArrowLeft');assert(new URL(page.url()).hash==='#camera','Keyboard backwards');
  await page.locator('#viewport').dispatchEvent('wheel',{deltaY:120});assert(new URL(page.url()).hash==='#watermark','Wheel advances');
  await page.locator('#viewport').evaluate(el=>{el.dispatchEvent(new TouchEvent('touchstart',{touches:[new Touch({identifier:1,target:el,clientX:280,clientY:300})],bubbles:true}));el.dispatchEvent(new TouchEvent('touchend',{changedTouches:[new Touch({identifier:1,target:el,clientX:60,clientY:305})],bubbles:true}));});assert(new URL(page.url()).hash==='#api','Swipe advances');
  await page.keyboard.press('End');
  // Block navigation in the test only, to verify links do not trigger the page click handler.
  await page.locator('#download .actions a').first().evaluate(a=>a.addEventListener('click',e=>e.preventDefault()));
  await page.locator('#download .actions a').first().click();assert(new URL(page.url()).hash==='#download','Download click does not advance');
  await page.waitForTimeout(560);await page.locator('#download .description').click();assert(new URL(page.url()).hash==='#download','Final page stays on click');
  await page.locator('#viewport').focus();await page.keyboard.press('Enter');await page.keyboard.press('ArrowRight');assert(new URL(page.url()).hash==='#download','Final page stays with keyboard');
  await page.locator('.return-home').click();assert(new URL(page.url()).hash==='#home','Explicit return home');
  assert(errors.length===0,errors.join('\n'));report.push({width,height,pages:10,layout:'passed',navigation:'passed',cleanControls:'passed',actionLinks:'passed',errors});await page.close();
 }
 fs.writeFileSync(path.join(out,'checks.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}finally{await browser.close();}

