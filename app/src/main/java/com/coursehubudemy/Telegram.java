package com.coursehubudemy;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Telegram {
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static JSONObject post(String url, String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}
        InputStream in=(c.getResponseCode()<400)?c.getInputStream():c.getErrorStream();
        ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);
        JSONObject j=new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8)); c.disconnect();
        if(!j.optBoolean("ok")) throw new Exception(j.optString("description","Telegram API error"));
        return j;
    }
    private static String enc(String s) throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
    public static void send(String token,String channel,Offer o) throws Exception {
        String link=o.link();
        if(link==null||!link.matches("(?i)^https?://([^/]+\\.)?udemy\\.com/course/.*"))
            throw new Exception("No direct Udemy course URL was resolved; skipped to avoid posting a FreeWebCart redirect.");
        String msg="🔥 <b>100% Free Course Alert!</b> 🔥\n\n📚 <b>Course Title:</b> "+escape(o.title)+
          "\n\n🔗 <b>Enroll Link:</b> <a href=\""+escape(link)+"\">Click Here to Enroll</a>\n\nHurry up and enroll now! This is a limited time offer.\n\n#FreeCourse #CourseHubUdemy";
        String api="https://api.telegram.org/bot"+token+"/";
        if(!o.imageUrl.isEmpty()){
            try {post(api+"sendPhoto","chat_id="+enc(channel)+"&photo="+enc(o.imageUrl)+"&caption="+enc(msg)+"&parse_mode=HTML");return;}
            catch(Exception ignored) { /* retry as text only if photo endpoint fails */ }
        }
        post(api+"sendMessage","chat_id="+enc(channel)+"&text="+enc(msg)+"&parse_mode=HTML&disable_web_page_preview=false");
    }
}
