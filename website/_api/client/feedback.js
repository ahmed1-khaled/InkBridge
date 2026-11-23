import { createHash, timingSafeEqual } from "node:crypto";
import { firebaseServices, json } from "../../lib/firebase-admin.js";
const hash=v=>createHash("sha256").update(v,"utf8").digest("hex");
const equal=(a,b)=>{const x=Buffer.from(a||""),y=Buffer.from(b||"");return x.length===y.length&&timingSafeEqual(x,y)};
export default async function handler(req,res){
 if(req.method!=="POST") return json(res,405,{error:"Method not allowed."});
 try{
  const {db,FieldValue}=firebaseServices();const {deviceId,deviceSecret,category,message,email,appVersion}=req.body||{};
  if(typeof deviceId!=="string"||typeof deviceSecret!=="string"||deviceSecret.length<32||typeof message!=="string"||message.trim().length<5||message.length>3000) return json(res,400,{error:"Valid device credentials and a message (5–3000 characters) are required."});
  const device=await db.collection("devices").doc(deviceId).get();
  if(!device.exists||!equal(device.data().secretHash,hash(deviceSecret))) return json(res,401,{error:"Unknown installation."});
  const ref=await db.collection("feedback").add({deviceId,platform:device.data().platform||"unknown",appVersion:String(appVersion||device.data().appVersion||"unknown").slice(0,32),category:["bug","feature","question","other"].includes(category)?category:"other",message:message.trim(),email:typeof email==="string"?email.trim().slice(0,254):null,status:"open",createdAt:FieldValue.serverTimestamp()});
  return json(res,201,{accepted:true,id:ref.id});
 }catch(e){return json(res,e.status||500,{error:e.message||"Feedback could not be submitted."})}
}
