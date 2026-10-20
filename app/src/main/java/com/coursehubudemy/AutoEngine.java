package com.coursehubudemy;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import org.json.JSONObject;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Scan / post logic shared by the foreground service and the watchdog worker.
 *
 * Two independent timers (user-configurable):
 *   - scan timer: scans FreeWebCart and refreshes the pending queue with offers not yet posted
 *   - post timer: posts up to N queued offers to Telegram
 * Next-run times are stored as wall-clock timestamps so they survive restarts and Doze.
 */
public final class AutoEngine {
    private AutoEngine() {}

    /** Held while the posted-history is read/modified so manual and automatic posting can't double-post. */
    public static final Object LOCK = new Object();
    public static final String PREFS = "coursehub";
    public static final int DEFAULT_SCAN_MIN = 120, DEFAULT_POST_MIN = 30, DEFAULT_POST_MAX = 5;
    public static final int MIN_SCAN_MIN = 5, MIN_POST_MIN = 1;
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private static final int PAGES = 3;
    private static final long CACHE_MS = 6 * 60 * 60 * 1000L;   // reuse parsed course pages for 6 h

    private static boolean hasNetwork(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) { return true; }
    }
    private static void loadCache(SharedPreferences sp, Map<String, Offer> cache, Map<String, Long> stamps) {
        try {
            JSONObject co = new JSONObject(sp.getString("offerCache", "{}"));
            long now = System.currentTimeMillis();
            Iterator<String> it = co.keys();
            while (it.hasNext()) {
                String k = it.next(); JSONObject e = co.getJSONObject(k); long t = e.optLong("t");
                if (now - t < CACHE_MS) { cache.put(k, Offer.fromJson(e.getJSONObject("o"))); stamps.put(k, t); }
            }
        } catch (Exception ignored) {}
    }
    private static void saveCache(SharedPreferences sp, List<Offer> all, Map<String, Long> stamps) {
        try {
            JSONObject co = new JSONObject(); long now = System.currentTimeMillis();
            for (Offer o : all) {
                Long t = stamps.get(o.sourceUrl);
                JSONObject e = new JSONObject(); e.put("t", t == null ? now : t); e.put("o", o.toJson());
                co.put(o.sourceUrl, e);
            }
            sp.edit().putString("offerCache", co.toString()).apply();
        } catch (Exception ignored) {}
    }

    public interface Listener { void progress(String phase, String text, int percent); }
    private static final Listener NOOP = (p, t, c) -> {};

    public static final class Summary {
        public int found, queued, sent, skipped, errors;
        public String error = "";
    }
    public static final class Cycle {
        public Summary scan, post;
        public boolean ran() { return scan != null || post != null; }
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static int scanMin(SharedPreferences sp) { return Math.max(MIN_SCAN_MIN, sp.getInt("scanMin", DEFAULT_SCAN_MIN)); }
    static int postMin(SharedPreferences sp) { return Math.max(MIN_POST_MIN, sp.getInt("postMin", DEFAULT_POST_MIN)); }
    static int postMax(SharedPreferences sp) { return Math.max(1, sp.getInt("postMax", DEFAULT_POST_MAX)); }

    private static String msg(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static String shorten(String s) { return s == null ? "" : (s.length() > 60 ? s.substring(0, 57) + "…" : s); }

    // ---------- pending queue ----------
    static List<Offer> loadPending(SharedPreferences sp) {
        List<Offer> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("pending", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Offer.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return l;
    }
    static void savePending(SharedPreferences sp, List<Offer> offers) {
        JSONArray a = new JSONArray();
        for (Offer o : offers) a.put(o.toJson());
        sp.edit().putString("pending", a.toString()).apply();
    }
    public static int pendingCount(SharedPreferences sp) { return loadPending(sp).size(); }

    // ---------- scan ----------
    private static final class ScanProgress implements Scanner.Progress {
        final Listener l; String text = "Starting scan…"; int pct = -1;
        ScanProgress(Listener l) { this.l = l; }
        @Override public void update(String t) { text = t; l.progress("scan", text, pct); }
        @Override public void percent(int p) { pct = p; l.progress("scan", text, pct); }
    }

    public static Summary scan(Context ctx, Listener l) {
        SharedPreferences sp = prefs(ctx);
        Summary s = new Summary();
        Listener ls = l == null ? NOOP : l;
        try {
            Map<String, Offer> cache = new HashMap<>(); Map<String, Long> stamps = new HashMap<>();
            loadCache(sp, cache, stamps);
            List<Offer> all = Scanner.scan(PAGES, new ScanProgress(ls), cache);
            if (!all.isEmpty()) saveCache(sp, all, stamps);
            synchronized (LOCK) {
                Set<String> posted = new HashSet<>(sp.getStringSet("posted", new HashSet<>()));
                List<Offer> fresh = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (Offer o : all) if (!posted.contains(o.id) && seen.add(o.id)) fresh.add(o);
                s.found = all.size();
                // An empty scan usually means a site hiccup: keep the existing queue rather than wiping it.
                // Otherwise the queue is replaced, so offers that left the site (expired) drop out.
                if (!all.isEmpty()) savePending(sp, fresh);
                s.queued = pendingCount(sp);
            }
        } catch (Exception e) {
            s.error = msg(e);
        }
        return s;
    }

    // ---------- post ----------
    private static void ensureSeeded(SharedPreferences sp, String url, String key, Set<String> posted) throws Exception {
        String marker = url.trim();
        if (marker.equals(sp.getString("seededUrl", ""))) return;   // seed local history only once per cloud project
        SupabaseHistory.seedPostedIds(url, key, posted);
        sp.edit().putString("seededUrl", marker).apply();
    }

    public static Summary post(Context ctx, Listener l) {
        SharedPreferences sp = prefs(ctx);
        Summary s = new Summary();
        Listener ls = l == null ? NOOP : l;
        String token = sp.getString("token", "").trim(), channel = sp.getString("channel", "").trim();
        String cloudUrl = sp.getString("supabaseUrl", ""), cloudKey = sp.getString("supabaseKey", "");
        boolean shared = SupabaseHistory.configured(cloudUrl, cloudKey);
        if (token.isEmpty() || channel.isEmpty()) { s.error = "Telegram token/channel not set"; return s; }

        synchronized (LOCK) {
            Set<String> posted = new HashSet<>(sp.getStringSet("posted", new HashSet<>()));
            List<Offer> todo = new ArrayList<>();
            for (Offer o : loadPending(sp)) { if (posted.contains(o.id)) s.skipped++; else todo.add(o); }
            if (todo.isEmpty()) { savePending(sp, todo); return s; }
            if (shared) {
                try { ensureSeeded(sp, cloudUrl, cloudKey, posted); }
                catch (Exception e) { s.error = "Shared history unavailable: " + msg(e); savePending(sp, todo); return s; }
            }
            int max = postMax(sp), attempts = 0, total = Math.min(max, todo.size());
            Set<String> finished = new HashSet<>();
            for (int i = 0; i < todo.size() && attempts < max && s.errors < 3; i++) {
                Offer o = todo.get(i);
                ls.progress("post", "Posting " + (attempts + 1) + " of " + total + ": " + shorten(o.title), attempts * 100 / Math.max(1, total));
                boolean claimed = false;
                if (shared) {
                    try {
                        claimed = SupabaseHistory.claim(cloudUrl, cloudKey, o);
                        if (!claimed) { s.skipped++; finished.add(o.id); continue; }
                    } catch (Exception e) { s.errors++; attempts++; s.error = "Shared history: " + msg(e); continue; }
                }
                attempts++;
                try {
                    Telegram.send(token, channel, o, sp.getBoolean("cleanLinks", true));
                    posted.add(o.id);
                    sp.edit().putStringSet("posted", new HashSet<>(posted)).apply();
                    s.sent++; finished.add(o.id);
                    if (shared) { try { SupabaseHistory.markStatus(cloudUrl, cloudKey, o.id, "posted"); } catch (Exception ignored) {} }
                } catch (Exception e) {
                    s.errors++; s.error = msg(e);
                    if (shared && claimed) { try { SupabaseHistory.markStatus(cloudUrl, cloudKey, o.id, "failed"); } catch (Exception ignored) {} }
                }
                if (attempts < max && i < todo.size() - 1) {
                    try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
            List<Offer> left = new ArrayList<>();
            for (Offer o : todo) if (!finished.contains(o.id)) left.add(o);
            savePending(sp, left);
            s.queued = left.size();
            ls.progress("post", "Posting finished", 100);
        }
        return s;
    }

    // ---------- scheduling ----------
    /** If the user shortens a timer, pull an already-scheduled later run in to match. */
    private static void capSchedule(SharedPreferences sp) {
        long now = System.currentTimeMillis();
        long ns = sp.getLong("nextScanAt", 0), np = sp.getLong("nextPostAt", 0);
        long cs = Math.min(ns, now + scanMin(sp) * 60000L), cp = Math.min(np, now + postMin(sp) * 60000L);
        if (cs != ns || cp != np) sp.edit().putLong("nextScanAt", cs).putLong("nextPostAt", cp).apply();
    }
    public static boolean anyDue(SharedPreferences sp) {
        capSchedule(sp);
        long now = System.currentTimeMillis();
        return now >= sp.getLong("nextScanAt", 0) || (now >= sp.getLong("nextPostAt", 0) && pendingCount(sp) > 0);
    }
    public static long nextWake(SharedPreferences sp) {
        long ns = sp.getLong("nextScanAt", 0), np = sp.getLong("nextPostAt", 0);
        return pendingCount(sp) > 0 ? Math.min(ns, np) : ns;   // nothing queued -> no need to wake for posting
    }

    /** Runs whichever timers are due (scan first, then post so fresh offers can go out in the same run). */
    public static Cycle runDue(Context ctx, Listener l) {
        SharedPreferences sp = prefs(ctx);
        Cycle c = new Cycle();
        capSchedule(sp);
        if (!hasNetwork(ctx)) {   // don't spin up radios/wake locks with no internet; retry shortly
            long now = System.currentTimeMillis(), retry = now + RETRY_MS;
            SharedPreferences.Editor e = sp.edit();
            if (now >= sp.getLong("nextScanAt", 0)) e.putLong("nextScanAt", retry);
            if (now >= sp.getLong("nextPostAt", 0)) e.putLong("nextPostAt", retry);
            e.putString("lastStatus", "Waiting for internet connection").apply();
            return c;
        }
        if (System.currentTimeMillis() >= sp.getLong("nextScanAt", 0)) {
            c.scan = scan(ctx, l);
            long full = scanMin(sp) * 60000L;
            long gap = c.scan.error.isEmpty() ? full : Math.min(full, RETRY_MS);   // failed scan retries sooner
            sp.edit().putLong("nextScanAt", System.currentTimeMillis() + gap).apply();
        }
        if (System.currentTimeMillis() >= sp.getLong("nextPostAt", 0) && pendingCount(sp) > 0) {
            c.post = post(ctx, l);
            sp.edit().putLong("nextPostAt", System.currentTimeMillis() + postMin(sp) * 60000L).apply();
        }
        if (c.ran()) sp.edit().putString("lastRun", new Date().toString()).putString("lastStatus", "Auto · " + describe(c)).apply();
        return c;
    }

    public static String describe(Cycle c) {
        StringBuilder b = new StringBuilder();
        if (c.scan != null) {
            if (!c.scan.error.isEmpty()) b.append("Scan failed: ").append(c.scan.error);
            else b.append("Scan: ").append(c.scan.found).append(" found, ").append(c.scan.queued).append(" waiting to post");
        }
        if (c.post != null) {
            if (b.length() > 0) b.append(" · ");
            b.append("Post: ").append(c.post.sent).append(" sent");
            if (c.post.skipped > 0) b.append(", ").append(c.post.skipped).append(" duplicate");
            if (c.post.errors > 0) b.append(", ").append(c.post.errors).append(" error(s)");
            if (!c.post.error.isEmpty() && c.post.sent == 0) b.append(" (").append(c.post.error).append(")");
        }
        return b.toString();
    }

    public static String idleText(SharedPreferences sp) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        long now = System.currentTimeMillis(), ns = sp.getLong("nextScanAt", 0), np = sp.getLong("nextPostAt", 0);
        return "Next scan " + (ns <= now ? "now" : f.format(new Date(ns))) + " · next post " + (np <= now ? "now" : f.format(new Date(np)))
            + " · " + pendingCount(sp) + " queued\n" + sp.getString("lastStatus", "No run yet");
    }
}
