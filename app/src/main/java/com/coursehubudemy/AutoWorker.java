package com.coursehubudemy;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.*;

public class AutoWorker extends Worker {
    public AutoWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        SharedPreferences sp=getApplicationContext().getSharedPreferences("coursehub",0);
        if(!sp.getBoolean("auto",false)) return Result.success();
        String token=sp.getString("token",""), channel=sp.getString("channel","@coursehubudemy");
        String cloudUrl=sp.getString("supabaseUrl",""), cloudKey=sp.getString("supabaseKey","");
        boolean shared=SupabaseHistory.configured(cloudUrl,cloudKey);
        if(token.trim().isEmpty()||channel.trim().isEmpty()) return Result.failure();
        try{
            List<Offer> offers=Scanner.scan(3,s->{});
            Set<String> posted=new HashSet<>(sp.getStringSet("posted",new HashSet<>()));
            if(shared) SupabaseHistory.seedPostedIds(cloudUrl,cloudKey,posted);
            int sent=0, skipped=0, errors=0;
            for(Offer o:offers){
                if(posted.contains(o.id)){skipped++;continue;}
                boolean claimed=false;
                if(shared){
                    try{claimed=SupabaseHistory.claim(cloudUrl,cloudKey,o);if(!claimed){skipped++;continue;}}
                    catch(Exception e){errors++;continue;}
                }
                try{
                    Telegram.send(token,channel,o);
                    posted.add(o.id);sp.edit().putStringSet("posted",new HashSet<>(posted)).apply();sent++;
                    if(shared){try{SupabaseHistory.markStatus(cloudUrl,cloudKey,o.id,"posted");}catch(Exception ignored){errors++;}}
                }catch(Exception e){
                    if(shared&&claimed){try{SupabaseHistory.markStatus(cloudUrl,cloudKey,o.id,"failed");}catch(Exception ignored){}}
                    errors++;
                }
            }
            sp.edit().putString("lastRun",new Date().toString()).putString("lastStatus","Auto scan completed · "+sent+" posted, "+skipped+" duplicates, "+errors+" errors").apply();
            return errors>0?Result.retry():Result.success();
        }catch(Exception e){sp.edit().putString("lastStatus","Auto scan failed: "+e.getMessage()).apply();return Result.retry();}
    }
}
