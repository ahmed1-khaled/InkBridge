import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(req.method!=="GET") return json(res,405,{error:"Method not allowed."});
 try{
  const {auth,db}=firebaseServices(); await requireAdmin(req,auth);
  const snap=await db.collection("adminAudit").orderBy("createdAt","desc").limit(100).get();
  return json(res,200,{activity:snap.docs.map(doc=>{const d=doc.data();return{id:doc.id,action:d.action||"event",targetId:d.targetId||null,actor:d.actor||"system",status:d.status||null,createdAt:d.createdAt?.toDate?.()?.toISOString?.()||null}})});
 }catch(e){return json(res,e.status||500,{error:e.message||"Could not load activity."})}
}
