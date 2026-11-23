import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(req.method!=="GET") return json(res,405,{error:"Method not allowed."});
 try{
  const {auth,db}=firebaseServices(); await requireAdmin(req,auth);
  const platform=["android","windows"].includes(req.query?.platform)?req.query.platform:null;
  const limit=Math.min(Math.max(Number.parseInt(req.query?.limit||"100",10)||100,1),200);
  const snap=await db.collection("devices").orderBy("lastSeen","desc").limit(500).get();
  const rows=snap.docs.filter(doc=>!platform||doc.data().platform===platform).slice(0,limit);
  return json(res,200,{devices:rows.map(doc=>{const d=doc.data();return{id:doc.id,platform:d.platform||"unknown",appVersion:d.appVersion||"unknown",lastSeen:d.lastSeen?.toDate?.()?.toISOString?.()||null,createdAt:d.createdAt?.toDate?.()?.toISOString?.()||null,notificationsEnabled:Boolean(d.notificationsEnabled&&d.fcmToken),model:d.deviceModel||null,osVersion:d.osVersion||null,locale:d.locale||null}})});
 }catch(e){return json(res,e.status||500,{error:e.message||"Could not load devices."})}
}
