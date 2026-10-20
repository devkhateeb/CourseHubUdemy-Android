package com.coursehubudemy;
public class Offer {
    public String title="", sourceUrl="", enrollUrl="", imageUrl="", coupon="", id="";
    public Offer(String title,String sourceUrl,String enrollUrl,String imageUrl,String coupon,String id){
        this.title=title; this.sourceUrl=sourceUrl; this.enrollUrl=enrollUrl; this.imageUrl=imageUrl; this.coupon=coupon; this.id=id;
    }
    public String link(){ return enrollUrl; }
}
