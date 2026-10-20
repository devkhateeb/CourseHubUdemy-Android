package com.coursehubudemy;
import org.json.JSONObject;
public class Offer {
    public String title="", sourceUrl="", enrollUrl="", imageUrl="", coupon="", id="";
    public Offer(String title,String sourceUrl,String enrollUrl,String imageUrl,String coupon,String id){
        this.title=title; this.sourceUrl=sourceUrl; this.enrollUrl=enrollUrl; this.imageUrl=imageUrl; this.coupon=coupon; this.id=id;
    }
    public String link(){ return enrollUrl.isEmpty()?sourceUrl:enrollUrl; }

    /** Used to persist the pending-post queue between background runs. */
    public JSONObject toJson(){
        JSONObject j=new JSONObject();
        try{
            j.put("title",title); j.put("sourceUrl",sourceUrl); j.put("enrollUrl",enrollUrl);
            j.put("imageUrl",imageUrl); j.put("coupon",coupon); j.put("id",id);
        }catch(Exception ignored){}
        return j;
    }
    public static Offer fromJson(JSONObject j){
        return new Offer(j.optString("title"),j.optString("sourceUrl"),j.optString("enrollUrl"),
            j.optString("imageUrl"),j.optString("coupon"),j.optString("id"));
    }
}
