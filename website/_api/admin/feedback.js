import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(!["GET","PATCH"].includes(req.method)) return json(res,405,{error:"Method not allowed."});
 try{
  const {auth,db,FieldValue}=firebaseServices(); const admin=await requireAdmin(req,auth);
  if(req.method==="GET"){
   const snap=await db.collection("feedback").orderBy("createdAt","desc").limit(100).get();
   return json(res,200,{feedback:snap.docs.map(doc=>{const d=doc.data();return{id:doc.id,category:d.category||"general",message:d.message||"",platform:d.platform||"unknown",appVersion:d.appVersion||"unknown",deviceId:d.deviceId||null,email:d.email||null,status:d.status||"open",createdAt:d.createdAt?.toDate?.()?.toISOString?.()||null}})});
  }
  const {id,status}=req.body||{};
  if(typeof id!=="string"||!["open","in_progress","resolved","closed"].includes(status)) return json(res,400,{error:"A valid feedback ID and status are required."});
  const ref=db.collection("feedback").doc(id); const snap=await ref.get();
  if(!snap.exists) return json(res,404,{error:"Feedback item not found."});
  await ref.update({status,updatedAt:FieldValue.serverTimestamp(),updatedBy:admin.email});
  await db.collection("adminAudit").add({action:"feedback.status_changed",targetId:id,status,actor:admin.email,createdAt:FieldValue.serverTimestamp()});
  return json(res,200,{success:true});
 }catch(e){return json(res,e.status||500,{error:e.message||"Could not update feedback."})}
}
