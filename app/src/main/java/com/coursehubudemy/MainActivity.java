package com.coursehubudemy;

import android.app.*;
import android.os.Bundle;
import android.os.Handler;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    final String BG="#11111B", PANEL="#1C1C2B", ACCENT="#8174FF", MUTED="#A8A8BD", WHITE="#F7F7FC";
    android.content.SharedPreferences prefs;
    EditText tokenInput, channelInput, supabaseUrlInput, supabaseKeyInput;
    TextView status, stats, log;
    LinearLayout list;
    ProgressBar progress;
    List<Offer> offers=new ArrayList<>();
    ExecutorService executor=Executors.newSingleThreadExecutor();
    Handler handler=new Handler();
    String token(){return tokenInput.getText().toString().trim();}
    String channel(){return channelInput.getText().toString().trim();}
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("coursehub",MODE_PRIVATE);buildUI();renderSavedStatus();scheduleAuto();}
    TextView text(String s,int size,String color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.parseColor(color));t.setPadding(0,6,0,6);return t;}
    GradientDrawable bg(String color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(radius);return d;}
    Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(bg(ACCENT,24));b.setOnClickListener(v->r.run());return b;}
    LinearLayout section(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,14,16,14);l.setBackground(bg(PANEL,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);l.setLayoutParams(p);return l;}
    void buildUI(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.parseColor(BG));
        // Respect Android status/navigation bars while retaining the app's side margins.
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,16,18,24);scroll.addView(root);setContentView(scroll);
        TextView brand=text("CourseHub",28,WHITE);brand.setTypeface(null,1);root.addView(brand);root.addView(text("UDEMY OFFER CONTROL CENTER",11,ACCENT));
        LinearLayout top=section();root.addView(top);top.addView(text("Dashboard",20,WHITE));stats=text("0 offers found  ·  0 posted",14,MUTED);top.addView(stats);status=text("● READY · Waiting to scan",13,"#79D8AD");top.addView(status);
        LinearLayout controls=section();root.addView(controls);controls.addView(text("Telegram setup",18,WHITE));
        tokenInput=new EditText(this);tokenInput.setSingleLine(true);tokenInput.setHint("Telegram bot token");tokenInput.setHintTextColor(Color.GRAY);tokenInput.setTextColor(Color.WHITE);tokenInput.setInputType(129);tokenInput.setText(prefs.getString("token",""));controls.addView(tokenInput);
        channelInput=new EditText(this);channelInput.setSingleLine(true);channelInput.setHint("Channel username, e.g. @coursehubudemy");channelInput.setHintTextColor(Color.GRAY);channelInput.setTextColor(Color.WHITE);channelInput.setText(prefs.getString("channel","@coursehubudemy"));controls.addView(channelInput);
        controls.addView(button("Save Telegram settings",()->saveSettings()));
        LinearLayout cloud=section();root.addView(cloud);cloud.addView(text("Shared cloud history (optional)",18,WHITE));
        cloud.addView(text("Use the same Supabase project on your PC and phone to share duplicate history.",12,MUTED));
        supabaseUrlInput=new EditText(this);supabaseUrlInput.setSingleLine(true);supabaseUrlInput.setHint("Supabase Project URL");supabaseUrlInput.setHintTextColor(Color.GRAY);supabaseUrlInput.setTextColor(Color.WHITE);supabaseUrlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);supabaseUrlInput.setText(prefs.getString("supabaseUrl",""));cloud.addView(supabaseUrlInput);
        supabaseKeyInput=new EditText(this);supabaseKeyInput.setSingleLine(true);supabaseKeyInput.setHint("Supabase publishable / anon key");supabaseKeyInput.setHintTextColor(Color.GRAY);supabaseKeyInput.setTextColor(Color.WHITE);supabaseKeyInput.setInputType(129);supabaseKeyInput.setText(prefs.getString("supabaseKey",""));cloud.addView(supabaseKeyInput);
        cloud.addView(button("Save shared history settings",()->saveSettings()));
        cloud.addView(text("Without both values, this device uses local-only history. Keep your database policies restricted and never use a service_role or secret key here.",11,MUTED));
        LinearLayout actions=section();root.addView(actions);actions.addView(text("Scan & posting",18,WHITE));
        actions.addView(button("↻  Scan Courses",()->scan(false)));
        actions.addView(button("➤  Post New Offers (Manual)",()->postManual()));
        Switch auto=new Switch(this);auto.setText("Automatic scanning & posting");auto.setTextColor(Color.WHITE);auto.setChecked(prefs.getBoolean("auto",false));actions.addView(auto);
        TextView autoHelp=text("Runs periodically in the background (Android may delay it to save battery).",12,MUTED);actions.addView(autoHelp);
        auto.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("auto",on).apply();scheduleAuto();say(on?"Automatic mode enabled":"Automatic mode paused",on?"#79D8AD":"#F3C969");});
        actions.addView(button("Run automatic scan now",()->{saveSettings();scan(true);}));
        root.addView(text("Course offers",20,WHITE));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        LinearLayout history=section();root.addView(history);history.addView(text("Activity",18,WHITE));log=text("No activity yet.",13,MUTED);history.addView(log);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.GONE);root.addView(progress);
        root.addView(text("Note: source-listed offers are not verified on Udemy. Coupon validity may change.",11,MUTED));
    }
    void saveSettings(){
        String t=token(),c=channel();
        String url=supabaseUrlInput==null?prefs.getString("supabaseUrl",""):supabaseUrlInput.getText().toString().trim();
        String key=supabaseKeyInput==null?prefs.getString("supabaseKey",""):supabaseKeyInput.getText().toString().trim();
        if(t.isEmpty()||c.isEmpty()){say("Enter bot token and channel first","#F3C969");return;}
        if(url.isEmpty()!=key.isEmpty()){say("Enter both Supabase URL and key, or leave both blank","#F3C969");return;}
        if(!url.isEmpty()&&!url.startsWith("https://")){say("Supabase URL must start with https://","#F3C969");return;}
        prefs.edit().putString("token",t).putString("channel",c).putString("supabaseUrl",url).putString("supabaseKey",key).apply();
        say(url.isEmpty()?"Settings saved · local history only":"Settings saved · shared cloud history configured","#79D8AD");
    }
    void say(String s,String color){status.setText("● "+s);status.setTextColor(Color.parseColor(color));addLog(s);}
    void addLog(String s){if(log!=null)log.setText(new Date().toString()+"\n"+s+"\n\n"+log.getText());}
    void renderSavedStatus(){String s=prefs.getString("lastStatus","Ready to scan");status.setText("● "+s);stats.setText("0 current session offers  ·  "+prefs.getStringSet("posted",new HashSet<>()).size()+" posted");}
    void scan(boolean autoPost){
        saveSettings();if(token().isEmpty())return;
        progress.setVisibility(View.VISIBLE);say("Scanning FreeWebCart…",ACCENT);
        final String t=token(),c=channel();
        executor.execute(()->{try{List<Offer> result=Scanner.scan(3,msg->runOnUiThread(()->status.setText("● "+msg)));
            offers=result;runOnUiThread(()->{progress.setVisibility(View.GONE);showOffers();stats.setText(offers.size()+" offers found  ·  "+prefs.getStringSet("posted",new HashSet<>()).size()+" posted");say("Scan complete", "#79D8AD");});
            if(autoPost)postList(result,t,c,true);
        }catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);say("Scan failed: "+e.getMessage(),"#FF8D9B");});}});
    }
    void showOffers(){list.removeAllViews();if(offers.isEmpty()){list.addView(text("No offers found. Try scanning again later.",14,MUTED));return;}
        Set<String> posted=prefs.getStringSet("posted",new HashSet<>());
        for(Offer o:offers){LinearLayout card=section();list.addView(card);card.addView(text(o.title,16,WHITE));card.addView(text(o.coupon.isEmpty()?"Coupon not detected":"Coupon: "+o.coupon,12,MUTED));card.addView(text(posted.contains(o.id)?"✓ Already posted":"● New offer",12,posted.contains(o.id)?"#79D8AD":"#F3C969"));
            card.addView(button("Post this course",()->postList(Collections.singletonList(o),token(),channel(),false)));
            card.addView(button("Open course source",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(o.sourceUrl)));}catch(Exception ignored){}}));
        }
    }
    void postManual(){saveSettings();if(offers.isEmpty()){say("Scan courses first", "#F3C969");return;}new AlertDialog.Builder(this).setTitle("Post new offers?").setMessage("Only offers not in your posted history will be sent to Telegram.").setNegativeButton("Cancel",null).setPositiveButton("Post", (d,w)->postList(offers,token(),channel(),false)).show();}
    void postList(List<Offer> batch,String t,String c,boolean automatic){
        if(t.isEmpty()||c.isEmpty()){say("Add Telegram settings first","#F3C969");return;}
        executor.execute(()->{
            int sent=0,skipped=0,errors=0;
            Set<String> posted=new HashSet<>(prefs.getStringSet("posted",new HashSet<>()));
            String cloudUrl=prefs.getString("supabaseUrl",""); String cloudKey=prefs.getString("supabaseKey","");
            boolean shared=SupabaseHistory.configured(cloudUrl,cloudKey);
            if(shared){try{SupabaseHistory.seedPostedIds(cloudUrl,cloudKey,posted);}catch(Exception e){String err=e.getMessage();runOnUiThread(()->say("Shared history unavailable; posting stopped: "+err,"#FF8D9B"));return;}}
            for(Offer o:batch){
                if(posted.contains(o.id)){skipped++;continue;}
                boolean claimed=false;
                if(shared){
                    try{claimed=SupabaseHistory.claim(cloudUrl,cloudKey,o);if(!claimed){skipped++;continue;}}
                    catch(Exception e){errors++;String err=e.getMessage();runOnUiThread(()->say("Shared history error; skipped course: "+err,"#FF8D9B"));continue;}
                }
                try{
                    Telegram.send(t,c,o); sent++;
                    if(shared){try{SupabaseHistory.markStatus(cloudUrl,cloudKey,o.id,"posted");}catch(Exception e){String err=e.getMessage();runOnUiThread(()->say("Posted to Telegram, but could not update cloud status: "+err,"#F3C969"));}}
                    posted.add(o.id);prefs.edit().putStringSet("posted",new HashSet<>(posted)).apply();
                }catch(Exception e){errors++;if(shared&&claimed){try{SupabaseHistory.markStatus(cloudUrl,cloudKey,o.id,"failed");}catch(Exception ignored){}}
                    String err=e.getMessage();runOnUiThread(()->say("Telegram error: "+err,"#FF8D9B"));}
            }
            int finalSent=sent,finalSkipped=skipped,finalErrors=errors;
            runOnUiThread(()->{stats.setText(offers.size()+" offers found  ·  "+prefs.getStringSet("posted",new HashSet<>()).size()+" posted on this device");say((automatic?"Auto":"Manual")+" posting finished: "+finalSent+" sent, "+finalSkipped+" duplicates skipped, "+finalErrors+" errors",finalErrors>0?"#F3C969":"#79D8AD");showOffers();});
        });
    }

    void scheduleAuto(){
        WorkManager wm=WorkManager.getInstance(this);wm.cancelUniqueWork("coursehub-auto-scan");
        if(!prefs.getBoolean("auto",false))return;
        PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(AutoWorker.class,15,TimeUnit.MINUTES).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        wm.enqueueUniquePeriodicWork("coursehub-auto-scan",ExistingPeriodicWorkPolicy.UPDATE,req);
    }
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
