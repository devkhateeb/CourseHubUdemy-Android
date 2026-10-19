package com.coursehubudemy;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Shared course-posting history. Uses the project's public/anon key and table RLS policies. */
public final class SupabaseHistory {
    private SupabaseHistory() {}

    public static boolean configured(String baseUrl, String key) {
        return baseUrl != null && baseUrl.trim().startsWith("https://") && key != null && !key.trim().isEmpty();
    }

    private static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String request(String url, String method, String key, String body, String prefer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setRequestProperty("apikey", key);
        // Legacy anon keys are JWTs. New sb_publishable_ keys should be sent as apikey only.
        if (key.startsWith("eyJ")) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        if (prefer != null && !prefer.isEmpty()) c.setRequestProperty("Prefer", prefer);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            in.close();
        }
        String response = new String(out.toByteArray(), StandardCharsets.UTF_8);
        c.disconnect();
        if (code >= 400) throw new Exception("Supabase HTTP " + code + (response.isEmpty() ? "" : ": " + response));
        return response;
    }

    private static String endpoint(String baseUrl, String query) {
        String base = baseUrl.trim().replaceAll("/+$", "");
        return base + "/rest/v1/course_history" + query;
    }

    /** Seed IDs already in this device's history, so enabling cloud history doesn't repost old courses. */
    public static void seedPostedIds(String baseUrl, String key, Set<String> ids) throws Exception {
        if (!configured(baseUrl, key) || ids == null || ids.isEmpty()) return;
        JSONArray batch = new JSONArray();
        int count = 0;
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            JSONObject row = new JSONObject();
            row.put("offer_id", id); row.put("status", "posted"); row.put("posted_at", now());
            batch.put(row); count++;
            if (count >= 100) {
                request(endpoint(baseUrl, "?on_conflict=offer_id"), "POST", key, batch.toString(), "resolution=ignore-duplicates,return=minimal");
                batch = new JSONArray(); count = 0;
            }
        }
        if (batch.length() > 0) request(endpoint(baseUrl, "?on_conflict=offer_id"), "POST", key, batch.toString(), "resolution=ignore-duplicates,return=minimal");
    }

    /** Atomically reserves a new offer ID. Only the device that inserts the row may post it. */
    public static boolean claim(String baseUrl, String key, Offer offer) throws Exception {
        JSONObject row = new JSONObject();
        row.put("offer_id", offer.id);
        row.put("title", offer.title == null ? "" : offer.title);
        row.put("source_url", offer.sourceUrl == null ? "" : offer.sourceUrl);
        row.put("enroll_url", offer.link() == null ? "" : offer.link());
        row.put("coupon", offer.coupon == null ? "" : offer.coupon);
        row.put("posted_at", now());
        row.put("status", "posting");
        String result = request(endpoint(baseUrl, "?on_conflict=offer_id"), "POST", key, row.toString(), "resolution=ignore-duplicates,return=representation");
        JSONArray inserted = new JSONArray(result.isEmpty() ? "[]" : result);
        if (inserted.length() > 0) return true;

        // An earlier Telegram attempt may have failed. Reclaim only if the row is still marked failed.
        JSONObject update = new JSONObject(); update.put("status", "posting"); update.put("posted_at", now());
        String filter = "?offer_id=eq." + enc(offer.id) + "&status=eq.failed";
        String reclaimed = request(endpoint(baseUrl, filter), "PATCH", key, update.toString(), "return=representation");
        return !reclaimed.isEmpty() && new JSONArray(reclaimed).length() > 0;
    }

    public static void markStatus(String baseUrl, String key, String offerId, String status) throws Exception {
        if (!("posting".equals(status) || "posted".equals(status) || "failed".equals(status))) throw new IllegalArgumentException("Invalid status");
        JSONObject update = new JSONObject(); update.put("status", status); update.put("posted_at", now());
        String filter = "?offer_id=eq." + enc(offerId);
        request(endpoint(baseUrl, filter), "PATCH", key, update.toString(), "return=minimal");
    }
}
