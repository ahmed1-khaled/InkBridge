import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";
export default async function handler(req,res){
 if(req.method!=="GET") return json(res,405,{error:"Method not allowed."});
 try{
  const {auth,db}=firebaseServices();await requireAdmin(req,auth);
  const [devices,events,announcements,feedback]=await Promise.all([
   db.collection("devices").get(),db.collection("events").orderBy("createdAt","desc").limit(500).get(),
   db.collection("announcements").orderBy("createdAt","desc").limit(8).get(),db.collection("feedback").orderBy("createdAt","desc").limit(8).get()
  ]);
  const now=Date.now(),activeCutoff=now-30*86400000,weekCutoff=now-7*86400000;let android=0,windows=0,active30=0,push=0;const versions={};
  for(const doc of devices.docs){const d=doc.data();if(d.platform==="android")android++;if(d.platform==="windows")windows++;const seen=d.lastSeen?.toDate?.()?.getTime?.()||0;if(seen>=activeCutoff)active30++;if(d.platform==="android"&&d.notificationsEnabled&&d.fcmToken)push++;const v=d.appVersion||"unknown";versions[v]=(versions[v]||0)+1}
  const days=Array.from({length:7},(_,i)=>{const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-(6-i));return{date:d.toISOString().slice(0,10),label:d.toLocaleDateString("en",{weekday:"short"}),events:0}});
  const dayMap=new Map(days.map(d=>[d.date,d]));const eventCounts={};let weekEvents=0,errors7d=0;
  for(const doc of events.docs){const e=doc.data(),time=e.createdAt?.toDate?.()?.getTime?.()||0;eventCounts[e.event]=(eventCounts[e.event]||0)+1;if(time>=weekCutoff){weekEvents++;if(e.event==="app_error"||e.event==="app_crash")errors7d++;const day=dayMap.get(new Date(time).toISOString().slice(0,10));if(day)day.events++}}
  const recentEvents=events.docs.slice(0,12).map(doc=>{const e=doc.data();return{id:doc.id,event:e.event||"event",platform:e.platform||"unknown",appVersion:e.appVersion||"unknown",createdAt:e.createdAt?.toDate?.()?.toISOString?.()||null,deviceId:e.deviceId||null}});
  const recentAnnouncements=announcements.docs.map(doc=>{const a=doc.data();return{id:doc.id,title:a.title||"",body:a.body||"",target:a.target||"all",createdAt:a.createdAt?.toDate?.()?.toISOString?.()||null,androidAccepted:a.androidAccepted??null,androidFailed:a.androidFailed??null,windowsQueued:a.windowsQueued??null}});
  const recentFeedback=feedback.docs.map(doc=>{const f=doc.data();return{id:doc.id,category:f.category||"other",message:(f.message||"").slice(0,180),platform:f.platform||"unknown",status:f.status||"open",createdAt:f.createdAt?.toDate?.()?.toISOString?.()||null}});
  return json(res,200,{registeredDevices:devices.size,activeDevices30d:active30,androidDevices:android,windowsDevices:windows,androidPushOptIns:push,versions,telemetryEvents7d:weekEvents,errors7d,eventsByType:eventCounts,activityByDay:days,recentEvents,recentAnnouncements,recentFeedback,measuredAt:new Date().toISOString(),instrumentationStatus:events.size?"receiving":"waiting_for_client_instrumentation",note:"Installation counts are not unique human users. Analytics are based only on clients that have registered and sent telemetry."});
 }catch(e){return json(res,e.status||500,{error:e.message||"Could not load product analytics."})}
}