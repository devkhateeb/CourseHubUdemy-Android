package com.coursehubudemy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a FreeWebCart /redirect/ link into the real Udemy course link (optionally without tracking parameters). */
public final class LinkResolver {
    private LinkResolver() {}
    private static final String UA = "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/124 Safari/537.36";

    private static boolean isUdemy(String url) {
        try {
            String h = new URL(url).getHost().toLowerCase(Locale.ROOT);
            return h.equals("udemy.com") || h.endsWith(".udemy.com");
        } catch (Exception e) { return false; }
    }
    private static boolean isCourse(String url) { return isUdemy(url) && url.contains("/course/"); }
    private static String abs(String base, String rel) {
        try { return new URL(new URL(base), rel.trim()).toString(); } catch (Exception e) { return ""; }
    }

    /** Affiliate links often carry the real destination in a query parameter (u=, url=, ...). */
    private static String embedded(String url) {
        try {
            String q = new URL(url).getQuery();
            if (q == null) return "";
            for (String part : q.split("&")) {
                int i = part.indexOf('=');
                if (i < 0) continue;
                String v = URLDecoder.decode(part.substring(i + 1), "UTF-8");
                if (isCourse(v)) return v;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String readLimited(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n, total = 0;
        while (total < limit && (n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Redirects done by the page itself instead of an HTTP header: meta refresh, JavaScript, or a plain Udemy link. */
    private static String clientRedirect(String body, String base) {
        Document d = Jsoup.parse(body, base);
        for (Element m : d.select("meta[http-equiv]")) {
            if (!m.attr("http-equiv").equalsIgnoreCase("refresh")) continue;
            Matcher mm = Pattern.compile("url\\s*=\\s*['\"]?([^'\";]+)", Pattern.CASE_INSENSITIVE).matcher(m.attr("content"));
            if (mm.find()) return abs(base, mm.group(1));
        }
        Matcher js = Pattern.compile("(?:window\\.|document\\.|top\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']").matcher(body);
        if (js.find()) return abs(base, js.group(1));
        js = Pattern.compile("location\\.(?:replace|assign)\\(\\s*[\"']([^\"']+)[\"']").matcher(body);
        if (js.find()) return abs(base, js.group(1));
        for (Element a : d.select("a[href]")) {
            String u = abs(base, a.attr("href"));
            if (isCourse(u)) return u;
        }
        return "";
    }

    /** Follows the redirect chain (max 8 hops) and returns the Udemy course URL, or "" if it can't be found. */
    public static String resolve(String start) {
        String u = start;
        for (int hop = 0; hop < 8 && u != null && !u.isEmpty(); hop++) {
            if (isUdemy(u)) return isCourse(u) ? u : embedded(u);
            String emb = embedded(u);
            if (!emb.isEmpty()) return emb;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(12000); c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "text/html,*/*");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) return "";
                    u = abs(u, loc);
                    continue;
                }
                if (code >= 400) return "";
                u = clientRedirect(readLimited(c.getInputStream(), 300000), u);
            } catch (Exception e) {
                return "";
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return "";
    }

    /** Keeps only the course path and couponCode; drops affiliate/tracking parameters. */
    public static String clean(String url) {
        try {
            URL x = new URL(url);
            String coupon = "";
            if (x.getQuery() != null) for (String p : x.getQuery().split("&"))
                if (p.toLowerCase(Locale.ROOT).startsWith("couponcode=")) coupon = p.substring(p.indexOf('=') + 1);
            String base = x.getProtocol() + "://" + x.getHost() + x.getPath();
            return coupon.isEmpty() ? base : base + "?couponCode=" + coupon;
        } catch (Exception e) { return url; }
    }

    public static String finalLink(Offer o, boolean clean) throws Exception {
        String u = resolve(o.link());
        if (u.isEmpty()) throw new Exception("Could not find the Udemy link for: " + o.title);
        return clean ? clean(u) : u;
    }
}
