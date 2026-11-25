function(css, expectedHost) {
 "use strict";
 if (self !== top || location.protocol !== "https:" || location.hostname.toLowerCase() !== expectedHost || (location.port && location.port !== "443")) return "skipped-domain";
 if (!document.head || !document.documentElement) return "not-ready";
 const id="saman-app-mobile-style-v1";
 if (window.__samanMobile && document.getElementById(id)) return "already-applied";
 if (window.__samanMobile) window.__samanMobile.dispose();
 let viewport=document.querySelector('meta[name="viewport"]');
 if(!viewport){viewport=document.createElement("meta");viewport.name="viewport";document.head.appendChild(viewport);}
 const replaced=new Set(["width","initial-scale","minimum-scale","maximum-scale","user-scalable"]);
 const preserved=(viewport.content||"").split(",").map(x=>x.trim()).filter(x=>x&&!replaced.has(x.split("=")[0].trim().toLowerCase()));
 viewport.content=["width=device-width","initial-scale=1","minimum-scale=1","maximum-scale=1","user-scalable=no"].concat(preserved).join(", ");
 let style=document.getElementById(id);
 if(!style){style=document.createElement("style");style.id=id;document.head.appendChild(style);}
 style.textContent=css;
 if(!style.sheet)return "css-blocked";
 document.documentElement.classList.add("saman-app-mobile");
 if(!document.documentElement.hasAttribute("dir")&&/^fa(?:-|$)/i.test(document.documentElement.lang))document.documentElement.dir="rtl";
 function optimize(){
  if(!matchMedia("(max-width: 991px)").matches)return;
  const width=document.documentElement.clientWidth;
  if(!width)return;
  document.querySelectorAll("table").forEach(table=>{
   if(table.closest(".table-responsive,.saman-app-wide-table")||!table.getClientRects().length||!table.parentElement)return;
   const p=table.parentElement;
   if(["auto","scroll"].includes(getComputedStyle(p).overflowX))return;
   if(Math.max(table.scrollWidth,table.getBoundingClientRect().width)>Math.min(p.clientWidth||width,width)+4)table.classList.add("saman-app-wide-table");
  });
 }
 let timer;
 function schedule(){clearTimeout(timer);timer=setTimeout(optimize,120);}
 const observer=new MutationObserver(schedule);
 if(document.body)observer.observe(document.body,{childList:true,subtree:true,characterData:true});
 addEventListener("resize",schedule);
 document.addEventListener("load",schedule,true);
 window.__samanMobile={dispose(){observer.disconnect();clearTimeout(timer);removeEventListener("resize",schedule);document.removeEventListener("load",schedule,true);}};
 optimize();schedule();return "optimization-applied";
}