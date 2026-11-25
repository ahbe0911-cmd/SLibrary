function(css, expectedHost, hideSupport) {
 "use strict";
 if(self!==top||location.protocol!=="https:"||location.hostname.toLowerCase()!==expectedHost||(location.port&&location.port!=="443"))return "skipped-domain";
 if(!document.head||!document.documentElement)return "not-ready";
 const id="saman-app-mobile-style-v2";
 if(window.__samanMobile&&document.getElementById(id)){window.__samanMobile.refresh();return "already-applied";}
 if(window.__samanMobile)window.__samanMobile.dispose();
 function viewport(){
  let tags=document.querySelectorAll('meta[name="viewport"]');
  if(!tags.length){const m=document.createElement("meta");m.name="viewport";document.head.appendChild(m);tags=[m];}
  tags.forEach(v=>{const s="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no";if(v.content!==s)v.content=s;});
 }
 viewport();
 let style=document.getElementById(id);
 if(!style){style=document.createElement("style");style.id=id;document.head.appendChild(style);}
 style.textContent=css;
 document.documentElement.classList.add("saman-app-mobile");
 const clean=s=>(s||"").replace(/ي/g,"ی").replace(/ك/g,"ک").replace(/[\u200c\u200f\u200e]/g," ").replace(/\s+/g," ").trim().toLowerCase();
 let active=null,manualField=null;
 function matches(input){
  if(!input||input.tagName!=="INPUT"||input.disabled||input.readOnly||!input.getClientRects().length||!["text","number","search","tel",""].includes(input.type||""))return false;
  const info=clean([input.id,input.name,input.placeholder,input.getAttribute("aria-label"),input.title].join(" "));
  return /bar[\s_-]*code|بار\s*کد/.test(info);
 }
 function request(input){
  if(!matches(input))return;
  active=input;manualField=null;input.blur();
  if(window.SLibraryScan&&typeof window.SLibraryScan.postMessage==="function")window.SLibraryScan.postMessage("scan");
  else location.href="slibrary-scan://start";
 }
 function addBarcode(){
  document.querySelectorAll("input").forEach(input=>{
   if(!matches(input)||input.dataset.samanBarcode==="yes")return;
   const group=input.closest(".input-group")||input;
   if(!group.parentNode)return;
   input.dataset.samanBarcode="yes";
   const actions=document.createElement("div");actions.className="saman-barcode-actions";
   const scan=document.createElement("button");scan.type="button";scan.textContent="▥ اسکن بارکد";scan.addEventListener("click",e=>{e.preventDefault();request(input);});
   const manual=document.createElement("button");manual.type="button";manual.textContent="ورود دستی";manual.addEventListener("click",e=>{e.preventDefault();manualField=input;input.focus();});
   actions.append(scan,manual);group.insertAdjacentElement("afterend",actions);
  });
 }
 function onClick(e){
  const input=e.target;
  if(!e.isTrusted||!matches(input)||manualField===input)return;
  e.preventDefault();e.stopImmediatePropagation();request(input);
 }
 function onFocusOut(e){if(e.target===manualField)manualField=null;}
 document.addEventListener("click",onClick,true);
 document.addEventListener("focusout",onFocusOut,true);
 window.__samanBarcode={
  fill(code){
   const input=active;active=null;
   if(!input||!input.isConnected||!matches(input))return false;
   const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set;
   setter.call(input,String(code));
   input.dispatchEvent(new Event("input",{bubbles:true}));input.dispatchEvent(new Event("change",{bubbles:true}));
   return input.value===String(code);
  },
  manual(){if(active&&active.isConnected){manualField=active;active.focus();return true;}return false;}
 };
 const supportLabels=["پرسش از کتابدار","ارسال پستی منابع","مشاوره خواندن","پیشنهاد و انتقاد","درخواست مقاله","پیگیری درخواست","گزارش خطای فنی"];
 function support(){
  if(!hideSupport)return;
  const candidates=Array.from(document.querySelectorAll(".popover,.modal,.dropdown-menu,[role=dialog],div,section,aside")).filter(e=>{
   if(!e.getClientRects().length||e.classList.contains("saman-support-hidden")||e.querySelector("input[type=password]"))return false;
   const t=clean(e.textContent);return t.length<2200&&supportLabels.filter(label=>t.includes(label)).length>=5;
  });
  if(!candidates.length)return;
  const panel=candidates.find(e=>!candidates.some(other=>other!==e&&e.contains(other)))||candidates[0];
  let target=panel;
  for(let i=0;i<4&&target.parentElement&&target.parentElement!==document.body;i++){
   const parent=target.parentElement;if(parent.querySelector("input[type=password]")||clean(parent.textContent).length>2400)break;
   if(parent.matches(".popover,.dropdown-menu,[role=dialog],.modal")){target=parent;break;}
   const close=Array.from(parent.querySelectorAll("button,a,[role=button],span,i")).find(el=>{const t=clean(el.textContent),a=clean(el.getAttribute("aria-label")||el.title);return ["×","✕","✖","x"].includes(t)||/^(close|بستن)$/.test(a)||el.matches(".close,[data-dismiss=popover],[data-bs-dismiss=popover]");});
   if(close){target=parent;break;}target=parent;
  }
  const close=Array.from(target.querySelectorAll("button,a,[role=button],span,i")).find(el=>{const t=clean(el.textContent),a=clean(el.getAttribute("aria-label")||el.title);return ["×","✕","✖","x"].includes(t)||/^(close|بستن)$/.test(a)||el.matches(".close,[data-dismiss=popover],[data-bs-dismiss=popover]");});
  if(close&&!close.dataset.samanClosed){close.dataset.samanClosed="1";close.click();}
  if(panel.isConnected&&panel.getClientRects().length)target.classList.add("saman-support-hidden");
 }
 function optimize(){
  viewport();addBarcode();support();
  if(!matchMedia("(max-width: 991px)").matches)return;
  const width=document.documentElement.clientWidth;if(!width)return;
  document.querySelectorAll("table").forEach(table=>{
   if(table.closest(".table-responsive,.saman-app-wide-table")||!table.getClientRects().length||!table.parentElement)return;
   const p=table.parentElement;if(["auto","scroll"].includes(getComputedStyle(p).overflowX))return;
   if(Math.max(table.scrollWidth,table.getBoundingClientRect().width)>Math.min(p.clientWidth||width,width)+4)table.classList.add("saman-app-wide-table");
  });
 }
 let timer=null;function schedule(){if(timer!==null)return;timer=setTimeout(()=>{timer=null;optimize();},180);}
 const observer=new MutationObserver(schedule);
 function observeBody(){if(document.body)observer.observe(document.body,{childList:true,subtree:true,characterData:true});schedule();}
 if(document.body)observeBody();else document.addEventListener("DOMContentLoaded",observeBody,{once:true});
 addEventListener("resize",schedule);document.addEventListener("load",schedule,true);
 window.__samanMobile={refresh:schedule,dispose(){observer.disconnect();clearTimeout(timer);removeEventListener("resize",schedule);document.removeEventListener("load",schedule,true);document.removeEventListener("click",onClick,true);document.removeEventListener("focusout",onFocusOut,true);}};
 optimize();schedule();return "optimization-applied";
}
