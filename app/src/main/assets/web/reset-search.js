function(expectedHost) {
 "use strict";
 if(self!==top||location.protocol!=="https:"||location.hostname.toLowerCase()!==expectedHost||(location.port&&location.port!=="443"))return 0;
 const clean=s=>(s||"").replace(/ي/g,"ی").replace(/ك/g,"ک").replace(/[\u200c\u200e\u200f]/g," ").toLowerCase().replace(/[^a-z0-9\u0600-\u06ff]+/g,"");
 const search=/کدملی|شمارهملی|شمارهعضویت|کدعضویت|شمارهعضو|کدعضو|بارکد|شمارهثبت|کدمدرک|نامخانوادگی|نامعضو|نامامانتگیرنده|نامکتاب|عنوانکتاب|جستجو|nationalcode|nationalid|nationalnumber|melli|meli|melicode|mellicode|codemeli|codemelli|ssn|barcode|membercode|memberid|memberno|membernumber|membershipnumber|membershipcode|borrowercode|borrowerid|accessionnumber|accessionno|documentcode|bookcode|firstname|lastname|familyname|fullname|search|keyword|query|patronid|patroncode|cardnumber/;
 const excluded=/password|passcode|username|login|captcha|csrf|token|verify|verification|رمز|نامکاربری|کدامنیتی/;
 function fields(doc){
  let count=0;
  doc.querySelectorAll('input:not([type]),input[type=text],input[type=number],input[type=search],input[type=tel],textarea').forEach(input=>{
   if(input.disabled||input.readOnly||!input.value)return;
   const form=input.form;
   if(form&&form.querySelector('input[type=password]'))return;
   const labels=Array.from(input.labels||[]).map(x=>x.textContent).join(" ");
   const info=clean([input.name,input.id,input.placeholder,input.getAttribute("aria-label"),input.title,labels].join(" "));
   const parent=input.closest(".form-group,.field,.form-row");
   const context=parent?clean(Array.from(parent.querySelectorAll("label")).map(x=>x.textContent).join(" ")):"";
   if(excluded.test(info)||excluded.test(context))return;
   if(!search.test(info)&&!search.test(context)&&input.type!=="search")return;
   const proto=input.tagName==="TEXTAREA"?doc.defaultView.HTMLTextAreaElement.prototype:doc.defaultView.HTMLInputElement.prototype;
   const setter=Object.getOwnPropertyDescriptor(proto,"value").set;
   setter.call(input,"");
   input.removeAttribute("value");
   input.dispatchEvent(new doc.defaultView.Event("input",{bubbles:true}));
   count++;
  });
  Array.from(doc.querySelectorAll("iframe")).forEach(frame=>{try{if(frame.contentDocument&&frame.contentWindow.location.origin===location.origin)count+=fields(frame.contentDocument);}catch(_){}});
  return count;
 }
 if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();
 if(window.__samanBarcode&&window.__samanBarcode.clear)window.__samanBarcode.clear();
 return fields(document);
}