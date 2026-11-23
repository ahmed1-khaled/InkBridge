import { createHash, timingSafeEqual } from "node:crypto";
import { firebaseServices, json } from "../../lib/firebase-admin.js";
const hash=v=>createHash("sha256").update(v,"utf8").digest("hex");
const equal=(a,b)=>{const x=Buffer.from(a||""),y=Buffer.from(b||"");return x.length===y.length&&timingSafeEqual(x,y)};
const allowed=new Set(["app_opened","connection_succeeded","connection_failed","pdf_opened","pdf_exported","file_transfer_completed","update_available","update_installed","app_error","app_crash"]);
export default async function handler(req,res){
 if(req.method!=="POST") return json(res,405,{error:"Method not allowed."});
 try{
  const {db,FieldValue}=firebaseServices();const {deviceId,deviceSecret,event,platform,appVersion,metadata}=req.body||{};
  if(typeof deviceId!=="string"||typeof deviceSecret!=="string"||deviceSecret.length<32) return json(res,400,{error:"Valid installation credentials are required."});
  if(!allowed.has(event)) return json(res,400,{error:"Unsupported telemetry event."});
  const ref=db.collection("devices").doc(deviceId),snap=await ref.get();
  if(!snap.exists||!equal(snap.data().secretHash,hash(deviceSecret))) return json(res,401,{error:"Unknown installation."});
  const safeMeta=metadata&&typeof metadata==="object"&&!Array.isArray(metadata)?Object.fromEntries(Object.entries(metadata).filter(([k,v])=>/^(durationMs|errorCode|feature|fromVersion|toVersion|fileType|reason)$/.test(k)&&["string","number","boolean"].includes(typeof v)).slice(0,12)):{};
  await db.collection("events").add({deviceId,platform:snap.data().platform||platform||"unknown",event,appVersion:String(appVersion||snap.data().appVersion||"unknown").slice(0,32),metadata:safeMeta,createdAt:FieldValue.serverTimestamp()});
  await ref.update({lastSeen:FieldValue.serverTimestamp()});
  return json(res,202,{accepted:true});
 }catch(e){return json(res,e.status||500,{error:e.message||"Telemetry could not be accepted."})}
}
