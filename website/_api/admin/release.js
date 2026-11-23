import { json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(req.method!=="GET")return json(res,405,{error:"Method not allowed."});
 try{
  const response=await fetch("https://api.github.com/repos/ahmed1-khaled/InkBridge/releases/latest",{headers:{Accept:"application/vnd.github+json","User-Agent":"InkBridge-Admin"}});
  if(!response.ok)throw new Error("GitHub Releases returned "+response.status);
  const r=await response.json();
  res.setHeader("Cache-Control","private, max-age=300");
  return json(res,200,{tag:r.tag_name,name:r.name,publishedAt:r.published_at,htmlUrl:r.html_url,body:(r.body||"").slice(0,6000),assets:(r.assets||[]).map(a=>({name:a.name,size:a.size,downloadCount:a.download_count,browserDownloadUrl:a.browser_download_url}))});
 }catch(e){return json(res,502,{error:e.message||"Release information unavailable."})}
}