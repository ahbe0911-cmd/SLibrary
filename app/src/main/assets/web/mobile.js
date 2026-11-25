function(css, expectedHost, hideSupport) {
 "use strict";
 if(self!==top||location.protocol!=="https:"||location.hostname.toLowerCase()!==expectedHost||(location.port&&location.port!=="443"))return "skipped-domain";
 if(!document.head||!document.documentElement)return "not-ready";
 const id="saman-app-mobile-style-v4";
 if(window.__samanMobile&&document.getElementById(id)){return "already-applied";}
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
 let active=null,manualField=null,pendingEnter=null;
 function matches(input){
  if(!input||input.tagName!=="INPUT"||input.disabled||input.readOnly||!["text","number","search","tel",""].includes(input.type||""))return false;
  const info=clean([input.id,input.name,input.placeholder,input.getAttribute("aria-label"),input.title].join(" "));
  return /bar[\s_-]*code|بار\s*کد/.test(info);
 }
 function request(input){
  if(!matches(input))return;
  active=input;manualField=null;pendingEnter=null;input.blur();
  if(window.SLibraryScan&&typeof window.SLibraryScan.postMessage==="function")window.SLibraryScan.postMessage("scan");
  else location.href="slibrary-scan://start";
 }
 const barcodeDone=new WeakSet();
 function addBarcode(input){
   if(barcodeDone.has(input)||!matches(input))return;
   const group=input.closest(".input-group")||input;
   if(!group.parentNode)return;
   barcodeDone.add(input);input.dataset.samanBarcode="yes";
   const actions=document.createElement("div");actions.className="saman-barcode-actions";
   const scan=document.createElement("button");scan.type="button";scan.textContent="▥ اسکن بارکد";scan.addEventListener("click",e=>{e.preventDefault();request(input);});
   const manual=document.createElement("button");manual.type="button";manual.textContent="ورود دستی";manual.addEventListener("click",e=>{e.preventDefault();manualField=input;input.focus();});
   actions.append(scan,manual);group.insertAdjacentElement("afterend",actions);
 }
 function send(message){if(window.SLibraryScan&&typeof window.SLibraryScan.postMessage==="function")window.SLibraryScan.postMessage(message);}
 function editable(el){return !!(el&&el.matches&&el.matches('input:not([type=hidden]):not([type=button]):not([type=submit]):not([type=checkbox]):not([type=radio]):not([readonly]):not([disabled]),textarea:not([readonly]):not([disabled]),[contenteditable=true]'));}
 function inModal(el){return !!(el&&el.closest&&el.closest('.modal,[role=dialog],dialog,.ui-dialog,.k-window,.bootbox'));}
 function onClick(e){
  const input=e.target;
  if(!e.isTrusted)return;
  if(editable(input)&&inModal(input)){
   send("modal:1");
   // Preserve the website's own event handlers, focus trap and native keyboard.
   // In dialogs, use the explicit scan button for barcode fields.
   setTimeout(()=>{if(document.activeElement===input)send("edit");},0);
   return;
  }
  if(!matches(input)||manualField===input)return;
  e.preventDefault();e.stopImmediatePropagation();request(input);
 }
 const modalSelector='.modal,[role=dialog],dialog,.ui-dialog,.k-window,.bootbox';
 const modals=new Set();
 const modalObserver=new MutationObserver(()=>scheduleModal());
 let modalState=null,modalTimer=0;
 function trackModal(el){
  if(modals.has(el))return;
  modals.add(el);modalObserver.observe(el,{attributes:true,attributeFilter:["class","style","aria-hidden","open","hidden"]});
  scheduleModal();
 }
 function updateModal(){
  modalTimer=0;
  let opened=false;
  let removed=false;
  for(const el of modals){if(!el.isConnected){modals.delete(el);removed=true;}}
  if(removed){modalObserver.disconnect();for(const el of modals)modalObserver.observe(el,{attributes:true,attributeFilter:["class","style","aria-hidden","open","hidden"]});}
  for(const el of modals){
   const s=getComputedStyle(el);
   if(el.getClientRects().length&&s.visibility!=="hidden"&&s.display!=="none"&&el.getAttribute("aria-hidden")!=="true"){opened=true;break;}
  }
  document.documentElement.classList.toggle("saman-modal-open",opened);
  if(modalState!==opened){modalState=opened;send(opened?"modal:1":"modal:0");}
 }
 function scheduleModal(){if(!modalTimer)modalTimer=setTimeout(updateModal,0);}
 function onModalEvent(){scheduleModal();}
 function onFocusOut(e){if(e.target===manualField)manualField=null;}
 document.addEventListener("click",onClick,true);
 document.addEventListener("focusout",onFocusOut,true);
 document.addEventListener("focusin",onModalEvent,true);
 document.addEventListener("shown.bs.modal",onModalEvent,true);
 document.addEventListener("hidden.bs.modal",onModalEvent,true);
 window.__samanBarcode={
  fill(code){
   const input=active;active=null;pendingEnter=null;
   if(!input||!input.isConnected||!matches(input))return false;
   const value=String(code);
   input.focus({preventScroll:true});
   const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set;
   setter.call(input,value);
   input.dispatchEvent(new Event("input",{bubbles:true}));
   input.dispatchEvent(new Event("change",{bubbles:true}));
   // Only send the scanner terminator if this exact field remains focused.
   // Do not click a guessed button or submit a different form.
   if(input.isConnected&&document.activeElement===input&&input.value===value)pendingEnter={input,value};
   return input.value===value;
  },
  takeEnter(){
   const pending=pendingEnter;pendingEnter=null;
   return !!(pending&&pending.input.isConnected&&document.activeElement===pending.input&&
      matches(pending.input)&&pending.input.value===pending.value);
  },
  manual(){if(active&&active.isConnected){manualField=active;active.focus();return true;}return false;},
  clear(){active=null;manualField=null;pendingEnter=null;}
 };
 const supportLabels=["پرسش از کتابدار","ارسال پستی منابع","مشاوره خواندن","پیشنهاد و انتقاد","درخواست مقاله","پیگیری درخواست","گزارش خطای فنی"];
 const supportNodes=new Set();
 const supportCandidates=new Set();
 const watchedSupport=new Set();
 const supportObserver=new MutationObserver(records=>{if(!hideSupport)return;for(const record of records)if(record.target.isConnected)supportCandidates.add(record.target);if(supportCandidates.size)schedule();});
 const tables=new Set(), dirtyTables=new Set();
 const inputSelector="input";
 const discoverySelector=inputSelector+",table,"+modalSelector;
 const queue=new Set();
 let scheduled=0, idle=false, disposed=false;
 const editableSelector="input,textarea,select,[contenteditable]";
 function closeButton(target){
  return Array.from(target.querySelectorAll("button,a,[role=button],span,i")).find(el=>{
   const t=clean(el.textContent),a=clean(el.getAttribute("aria-label")||el.title);
   return ["×","✕","✖","x"].includes(t)||/^(close|بستن)$/.test(a)||el.matches(".close,[data-dismiss=popover],[data-bs-dismiss=popover]");
  });
 }
 function candidateFromText(node){
  if(!hideSupport||!node.parentElement)return;
  const t=clean(node.nodeValue);if(!supportLabels.some(label=>t.includes(label)))return;
  let el=node.parentElement;
  for(let depth=0;el&&el!==document.body&&depth<7;depth++,el=el.parentElement){
   if(el.matches("div,section,aside,.popover,.modal,.dropdown-menu,[role=dialog]"))supportCandidates.add(el);
  }
 }
 function processSupport(){
  if(!hideSupport){supportCandidates.clear();return;}
  // Only inspect bounded ancestors of actual service-label text.
  // Never hide containers containing an editable form.
  const matches=[];
  for(const el of supportCandidates){
   if(!el.isConnected||el.classList.contains("saman-support-hidden"))continue;
   const raw=el.textContent||"";
   if(raw.length>2200||el.querySelector(editableSelector))continue;
   const t=clean(raw);
   if(supportLabels.filter(label=>t.includes(label)).length>=5){
    if(!watchedSupport.has(el)){watchedSupport.add(el);supportObserver.observe(el,{attributes:true,attributeFilter:["class","style","hidden","aria-hidden"]});}
    if(el.getClientRects().length)matches.push(el);
   }
  }
  supportCandidates.clear();
  for(const panel of matches.filter(el=>!matches.some(other=>other!==el&&el.contains(other)))){
   let target=panel;
   for(let i=0;i<4&&target.parentElement&&target.parentElement!==document.body;i++){
    const p=target.parentElement,raw=p.textContent||"";
    if(raw.length>2400||p.querySelector(editableSelector))break;
    if(p.matches(".popover,.dropdown-menu,[role=dialog],.modal")||closeButton(p)){target=p;break;}
    // Don't climb arbitrary page wrappers.
    if(supportLabels.filter(label=>clean(raw).includes(label)).length<5)break;
   }
   const close=closeButton(target);
   if(close&&!close.dataset.samanClosed){close.dataset.samanClosed="1";close.click();}
   if(panel.isConnected&&panel.getClientRects().length){target.classList.add("saman-support-hidden");supportNodes.add(target);}

  }
 }
 function discover(el){
  if(el.nodeType===Node.TEXT_NODE){candidateFromText(el);return;}
  if(el.nodeType!==Node.ELEMENT_NODE||!el.isConnected)return;
  if(el.closest(".saman-barcode-actions"))return;
  const nodes=el.matches(discoverySelector)?[el]:[];
  el.querySelectorAll(discoverySelector).forEach(node=>nodes.push(node));
  for(const node of nodes){
   if(node.tagName==="INPUT")addBarcode(node);
   if(node.tagName==="TABLE"){tables.add(node);dirtyTables.add(node);}
   if(node.matches(modalSelector))trackModal(node);
  }
  if(hideSupport){
   const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
   let node;
   while((node=walker.nextNode()))candidateFromText(node);
  }
 }
 function measureTables(){
  const width=document.documentElement.clientWidth;
  if(!width||width>991){dirtyTables.clear();return;}
  const wide=[];
  for(const table of dirtyTables){
   if(!table.isConnected){tables.delete(table);continue;}
   if(table.closest(".table-responsive,.saman-app-wide-table")||!table.getClientRects().length||!table.parentElement)continue;
   const p=table.parentElement;
   if(["auto","scroll"].includes(getComputedStyle(p).overflowX))continue;
   if(Math.max(table.scrollWidth,table.getBoundingClientRect().width)>Math.min(p.clientWidth||width,width)+4)wide.push(table);
  }
  dirtyTables.clear();
  wide.forEach(table=>table.classList.add("saman-app-wide-table"));
 }
 function run(deadline){
  scheduled=0;
  if(disposed)return;
  const end=performance.now()+8;
  do{
   const next=queue.values().next();
   if(next.done)break;
   queue.delete(next.value);discover(next.value);
  }while(performance.now()<end&&(!deadline||deadline.timeRemaining()>1));
  processSupport();measureTables();
  if(queue.size)schedule();
 }
 function schedule(){
  if(disposed||scheduled)return;
  idle=typeof requestIdleCallback==="function";
  scheduled=idle?requestIdleCallback(run,{timeout:180}):setTimeout(()=>run(null),32);
 }
 function enqueue(node){
  if(!node||!(node.nodeType===Node.ELEMENT_NODE||node.nodeType===Node.TEXT_NODE))return;
  if(node.nodeType===Node.ELEMENT_NODE&&node.closest(".saman-barcode-actions"))return;
  queue.add(node);
 }
 const observer=new MutationObserver(records=>{
  for(const r of records){
   if(r.target.nodeType===Node.ELEMENT_NODE){
    const table=r.target.closest("table");if(table)dirtyTables.add(table);
   }
   for(const node of r.addedNodes)enqueue(node);
   if(r.removedNodes.length){
    if(modals.size)scheduleModal();
    let removed=false;for(const el of watchedSupport){if(!el.isConnected){watchedSupport.delete(el);supportNodes.delete(el);removed=true;}}
    if(removed){supportObserver.disconnect();for(const el of watchedSupport)supportObserver.observe(el,{attributes:true,attributeFilter:["class","style","hidden","aria-hidden"]});}
    for(const table of tables)if(!table.isConnected){tables.delete(table);dirtyTables.delete(table);}
   }
  }
  if(queue.size||dirtyTables.size||supportCandidates.size)schedule();
 });
 function startBody(){
  if(!document.body||disposed)return;
  observer.observe(document.body,{childList:true,subtree:true});
  enqueue(document.body);schedule();
 }
 function onResize(){
  for(const table of tables)dirtyTables.add(table);
  scheduleModal();if(dirtyTables.size)schedule();
 }
 function onLoad(e){
  const el=e.target;
  if(el&&el.closest){const t=el.closest("table");if(t){dirtyTables.add(t);schedule();}}
 }
 function onModalShown(){scheduleModal();}
 function fixViewport(){
  if(disposed)return;
  viewport();
 }
 // Recheck once after the site's own meta tags have been parsed.
 document.addEventListener("DOMContentLoaded",fixViewport,{once:true});
 if(document.body)startBody();else document.addEventListener("DOMContentLoaded",startBody,{once:true});
 addEventListener("resize",onResize);
 document.addEventListener("load",onLoad,true);
 window.__samanMobile={
  refresh(){modalState=null;scheduleModal();},
  setHideSupport(value){
   hideSupport=!!value;
   if(!hideSupport){supportObserver.disconnect();watchedSupport.clear();supportNodes.forEach(el=>el.classList.remove("saman-support-hidden"));supportNodes.clear();supportCandidates.clear();}
   else if(document.body){enqueue(document.body);schedule();}
  },
  dispose(){
   disposed=true;observer.disconnect();modalObserver.disconnect();supportObserver.disconnect();watchedSupport.clear();
   if(scheduled){if(idle)cancelIdleCallback(scheduled);else clearTimeout(scheduled);}
   clearTimeout(modalTimer);queue.clear();modals.clear();tables.clear();dirtyTables.clear();supportCandidates.clear();supportNodes.clear();
   removeEventListener("resize",onResize);document.removeEventListener("load",onLoad,true);
   document.removeEventListener("DOMContentLoaded",startBody);document.removeEventListener("DOMContentLoaded",fixViewport);
   document.removeEventListener("click",onClick,true);document.removeEventListener("focusout",onFocusOut,true);
   document.removeEventListener("focusin",onModalEvent,true);document.removeEventListener("shown.bs.modal",onModalEvent,true);document.removeEventListener("hidden.bs.modal",onModalEvent,true);
  }
 };
 return "optimization-applied";
}
