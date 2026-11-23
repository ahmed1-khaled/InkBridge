import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(req.method!=="GET") return json(res,405,{error:"Method not allowed."});
 try{
  const {auth,db}=firebaseServices(); await requireAdmin(req,auth);
  const snap=await db.collection("announcements").orderBy("createdAt","desc").limit(50).get();
  return json(res,200,{announcements:snap.docs.map(doc=>{const d=doc.data();return{id:doc.id,title:d.title||"",body:d.body||"",target:d.target||"all",createdBy:d.createdBy||"Admin",createdAt:d.createdAt?.toDate?.()?.toISOString?.()||null,androidAccepted:d.androidAccepted??null,androidFailed:d.androidFailed??null,windowsQueued:d.windowsQueued??null}})});
 }catch(e){return json(res,e.status||500,{error:e.message||"Could not load announcements."})}
}
