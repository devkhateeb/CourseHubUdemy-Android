package com.coursehubudemy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class Scanner {
    public interface Progress { void update(String text); default void percent(int pct){} }
    private static final String ROOT="https://freewebcart.com/";
    private static String abs(String base,String url){ try{return new URL(new URL(base),url).toString();}catch(Exception e){return "";} }
    private static String hash(String s){ try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder x=new StringBuilder();for(int i=0;i<10;i++)x.append(String.format("%02x",b[i]));return x.toString();}catch(Exception e){return s.hashCode()+"";} }
    private static String coupon(String text,String url){
        Matcher m=Pattern.compile("(?:coupon(?:\\s+code)?|code)\\s*[:：\\-]?\\s*([A-Z0-9]{5,25})",Pattern.CASE_INSENSITIVE).matcher(url+"\n"+text);
        if(m.find()) return m.group(1).toUpperCase(Locale.ROOT); return "";
    }
    public static List<Offer> scan(int pages, Progress progress) throws Exception { return scan(pages,progress,null); }
    /** cache: source URL -> previously parsed Offer; those course pages are not downloaded again. */
    public static List<Offer> scan(int pages, Progress progress, Map<String,Offer> cache) throws Exception {
        LinkedHashMap<String,Offer> found=new LinkedHashMap<>();
        List<String> listingUrls=new ArrayList<>();
        listingUrls.add(ROOT);
        // Prefer pagination URLs actually advertised by the site.
        try {
            Document home=Jsoup.connect(ROOT).userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36").timeout(18000).get();
            for(Element a:home.select("a[href]")){
                String u=abs(ROOT,a.attr("href"));
                if(u.matches("https?://[^/]*freewebcart\\.com/page/\\d+/?") && !listingUrls.contains(u) && listingUrls.size()<pages) listingUrls.add(u);
            }
        } catch(Exception e) {
            // The normal scan below will report the homepage error if it is unavailable.
        }
        // Some site versions have no visible pagination links. Try the old URLs as a fallback,
        // but treat a missing page as non-fatal rather than failing the entire scan.
        if(listingUrls.size()==1) for(int p=2;p<=pages;p++) listingUrls.add(ROOT+"page/"+p+"/");

        for(int p=0;p<listingUrls.size();p++){
            String page=listingUrls.get(p);
            progress.update("Reading listing "+(p+1)+" of "+listingUrls.size()+"…");
            progress.percent(p*100/listingUrls.size());
            Document d;
            try {
                d=Jsoup.connect(page).userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36").timeout(18000).get();
            } catch(org.jsoup.HttpStatusException e) {
                if(p==0) throw e;
                progress.update("Listing page unavailable (HTTP "+e.getStatusCode()+"); continuing with available listings…");
                continue;
            } catch(Exception e) {
                if(p==0) throw e;
                progress.update("Could not read an additional listing; continuing…");
                continue;
            }
            LinkedHashMap<String,String> cands=new LinkedHashMap<>();
            for(Element a:d.select("a[href]")){
                String u0=abs(page,a.attr("href")), t0=a.text().trim();
                if(!u0.contains("freewebcart.com")||!u0.contains("/course/")||t0.length()<5||found.containsKey(u0)||cands.containsKey(u0)) continue;
                cands.put(u0,t0);
            }
            int idx=0;
            for(Map.Entry<String,String> en:cands.entrySet()){
                idx++;
                String href=en.getKey(), title=en.getValue();
                progress.update("Listing "+(p+1)+"/"+listingUrls.size()+" · reading course "+idx+" of "+cands.size()+" · "+found.size()+" found");
                progress.percent((int)((p+(double)idx/cands.size())*100.0/listingUrls.size()));
                Offer cachedOffer=cache==null?null:cache.get(href);
                if(cachedOffer!=null){found.put(href,cachedOffer);continue;}
                try{
                    Document detail=Jsoup.connect(href).userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36").timeout(15000).get();
                    Element h=detail.selectFirst("h1"); if(h!=null&&!h.text().trim().isEmpty()) title=h.text().trim();
                    String img=""; Element im=detail.selectFirst("meta[property=og:image]"); if(im==null) im=detail.selectFirst("meta[name=twitter:image]"); if(im!=null) img=abs(href,im.attr("content"));
                    String enroll="";
                    for(Element e:detail.select("a[href]")){
                        String u=abs(href,e.attr("href")); String host="";
                        try{host=new URL(u).getHost().toLowerCase(Locale.ROOT);}catch(Exception ignored){}
                        String label=e.text().toLowerCase(Locale.ROOT);
                        if((host.equals("udemy.com")||host.endsWith(".udemy.com"))&&(u.toLowerCase(Locale.ROOT).contains("coupon")||u.contains("/course/")||label.contains("enroll"))){enroll=u;break;}
                    }
                    if(enroll.isEmpty()){
                        for(Element e:detail.select("a[href]")){String u=abs(href,e.attr("href"));String label=e.text().toLowerCase(Locale.ROOT);
                            if(u.contains("/redirect/")||label.contains("get course")||label.contains("claim offer")||label.contains("get coupon")){enroll=u;break;}}
                    }
                    String cp=coupon(detail.text(),enroll.isEmpty()?href:enroll);
                    // Match the Windows bot's canonical identity exactly so both devices share IDs.
                    String canonicalSource=href.split("#",2)[0].replaceAll("/+$","").toLowerCase(Locale.ROOT);
                    String canonicalEnroll=(enroll.isEmpty()?"":enroll).split("#",2)[0].trim().toLowerCase(Locale.ROOT);
                    String normalizedCoupon=cp.trim().toUpperCase(Locale.ROOT);
                    String identity=canonicalSource+"|"+(normalizedCoupon.isEmpty()?"link:"+canonicalEnroll:"coupon:"+normalizedCoupon);
                    String id=hash(identity);
                    found.put(href,new Offer(title,href,enroll,img,cp,id));
                }catch(Exception ignored){ }
            }
        }
        progress.update("Scan complete · "+found.size()+" course(s) found");
        progress.percent(100);
        return new ArrayList<>(found.values());
    }
}
