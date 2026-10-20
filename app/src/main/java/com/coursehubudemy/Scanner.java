package com.coursehubudemy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class Scanner {
    public interface Progress { void update(String text); }
    private static final String ROOT="https://freewebcart.com/";
    private static String abs(String base,String url){ try{return new URL(new URL(base),url).toString();}catch(Exception e){return "";} }
    private static String hash(String s){ try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder x=new StringBuilder();for(int i=0;i<10;i++)x.append(String.format("%02x",b[i]));return x.toString();}catch(Exception e){return s.hashCode()+"";} }
    private static boolean isUdemyCourse(String value){
        try{URL u=new URL(value);String host=u.getHost().toLowerCase(Locale.ROOT);return (host.equals("udemy.com")||host.endsWith(".udemy.com"))&&u.getPath().contains("/course/");}catch(Exception e){return false;}
    }
    private static String directFromHtml(String html,String base,Document doc){
        for(Element a:doc.select("a[href]")){String target=abs(base,a.attr("href"));if(isUdemyCourse(target))return target;}
        for(Element meta:doc.select("meta[http-equiv]")){
            if(!meta.attr("http-equiv").equalsIgnoreCase("refresh"))continue;
            Matcher mm=Pattern.compile("(?i)url\\s*=\\s*['\"]?([^;'\"]+)").matcher(meta.attr("content"));
            if(mm.find()){String target=abs(base,mm.group(1).trim());if(isUdemyCourse(target))return target;}
        }
        String all=html+"\n"+doc.select("script").html();
        String[] patterns={"https?://(?:www\\.)?udemy\\.com/course/[^\\s\"'<>\\\\]+","https?%3A%2F%2F(?:www\\.)?udemy\\.com%2Fcourse%2F[^\\s\"'<>]+"};
        for(String pat:patterns){Matcher m=Pattern.compile(pat,Pattern.CASE_INSENSITIVE).matcher(all);while(m.find()){
            String target=org.jsoup.parser.Parser.unescapeEntities(m.group(),false).replace("&amp;","&").replaceAll("[.,;)]$","");
            if(target.toLowerCase(Locale.ROOT).contains("%3a%2f%2f")){try{target=URLDecoder.decode(target,"UTF-8");}catch(Exception ignored){}}
            if(isUdemyCourse(target))return target;
        }}
        return "";
    }
    private static String resolveDirectUdemy(String candidate){
        String current=candidate;
        for(int hop=0;hop<3;hop++){
            if(isUdemyCourse(current))return current;
            try{
                org.jsoup.Connection.Response response=Jsoup.connect(current).userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36").timeout(18000).followRedirects(true).ignoreHttpErrors(true).execute();
                String finalUrl=response.url().toString();if(isUdemyCourse(finalUrl))return finalUrl;
                Document doc=response.parse();String direct=directFromHtml(response.body(),finalUrl,doc);if(!direct.isEmpty())return direct;
                String next="";for(Element a:doc.select("a[href]")){String target=abs(finalUrl,a.attr("href"));if(target.contains("/redirect/")){next=target;break;}}
                if(next.isEmpty()||next.equals(current))return "";current=next;
            }catch(Exception e){return "";}
        }
        return "";
    }
    private static String coupon(String text,String url){
        Matcher m=Pattern.compile("(?:coupon(?:\\s+code)?|code)\\s*[:：\\-]?\\s*([A-Z0-9]{5,25})",Pattern.CASE_INSENSITIVE).matcher(url+"\n"+text);
        if(m.find()) return m.group(1).toUpperCase(Locale.ROOT); return "";
    }
    public static List<Offer> scan(int pages, Progress progress) throws Exception {
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
            Elements links=d.select("a[href]");
            for(Element a:links){
                String href=abs(page,a.attr("href")), title=a.text().trim();
                if(!href.contains("freewebcart.com")||!href.contains("/course/")||title.length()<5||found.containsKey(href)) continue;
                try{
                    Document detail=Jsoup.connect(href).userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36").timeout(15000).get();
                    Element h=detail.selectFirst("h1"); if(h!=null&&!h.text().trim().isEmpty()) title=h.text().trim();
                    String img=""; Element im=detail.selectFirst("meta[property=og:image]"); if(im==null) im=detail.selectFirst("meta[name=twitter:image]"); if(im!=null) img=abs(href,im.attr("content"));
                    String enroll="";
                    // Prefer a direct Udemy course URL; preserve coupon/affiliate query parameters.
                    for(Element e:detail.select("a[href]")){
                        String u=abs(href,e.attr("href"));if(isUdemyCourse(u)){enroll=u;break;}
                    }
                    if(enroll.isEmpty()){
                        for(Element e:detail.select("a[href]")){
                            String u=abs(href,e.attr("href"));String label=e.text().toLowerCase(Locale.ROOT);
                            if(u.contains("/redirect/")||label.contains("get course")||label.contains("claim offer")||label.contains("get coupon")||label.contains("grab offer")){
                                enroll=resolveDirectUdemy(u);if(!enroll.isEmpty())break;
                            }
                        }
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
        return new ArrayList<>(found.values());
    }
}
