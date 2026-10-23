const canvas=document.querySelector('#film'),ctx=canvas.getContext('2d');
const timing=window.FILM_TIMING;
const load=src=>new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=reject;im.src=src;});
let header,frames;
const ease=t=>1-Math.pow(1-t,4);
window.filmReady=Promise.all([load('scenes/header.png'),...timing.scenes.map(s=>load(s.file))]).then(images=>{[header,...frames]=images;window.renderAt(0);});
window.renderAt=t=>{
  if(!frames)return;
  // Clamp to the last frame: no wraparound or closing-to-opening transition.
  t=Math.max(0,Math.min(t,timing.duration));
  let index=timing.scenes.findIndex(scene=>t<scene.end);
  if(index<0)index=frames.length-1;
  const elapsed=t-timing.scenes[index].start;
  ctx.fillStyle='#faf8f2';ctx.fillRect(0,0,1920,1080);
  ctx.save();ctx.beginPath();ctx.rect(0,80,1920,1000);ctx.clip();
  if(index>0&&elapsed<timing.transition){
    const x=ease(elapsed/timing.transition)*1920;
    ctx.drawImage(frames[index-1],-x,80);
    ctx.drawImage(frames[index],1920-x,80);
  }else ctx.drawImage(frames[index],0,80);
  ctx.restore();ctx.drawImage(header,0,0);
};
const sound=document.querySelector('#sound');
sound.addEventListener('seeked',()=>window.renderAt(sound.currentTime));
sound.addEventListener('ended',()=>window.renderAt(timing.duration));
function tick(){if(!sound.paused)window.renderAt(sound.currentTime);requestAnimationFrame(tick);}tick();
