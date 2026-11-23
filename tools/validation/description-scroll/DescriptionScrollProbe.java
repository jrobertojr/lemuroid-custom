package org.lemuroid.validation;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.util.LruCache;
import java.lang.reflect.*;
import java.util.*;

/** Runs in the debuggable app; changes only its in-memory metadata cache. No games launched. */
public class DescriptionScrollProbe extends Instrumentation {
    Activity activity;
    Object input;
    LruCache<Integer,String> cache;
    Map<Integer,String> originals;
    String report = "";
    void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    Object call(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
    Object panels() throws Exception {
        for (Method m : input.getClass().getMethods()) if (m.getName().startsWith("getPanels")) return m.invoke(input);
        throw new AssertionError("Missing panels");
    }
    Object panel() throws Exception {
        Set<?> set = (Set<?>) panels();
        if(set.size()!=1){String details="";for(Object x:set)details+=" value="+call(x,"getValue")+" max="+call(x,"getMaxValue");throw new AssertionError("Exactly one description must receive the stick: " + set.size()+details);}
        return set.iterator().next();
    }
    int value() throws Exception { return (Integer) call(panel(), "getValue"); }
    int maximum() throws Exception { return (Integer) call(panel(), "getMaxValue"); }
    boolean motion(int axis, float value) throws Exception {
        final boolean[] consumed = {false};
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties(); properties.id=0;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); coords.setAxisValue(axis,value);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{properties},new MotionEvent.PointerCoords[]{coords},0,0,1,1,-1,0,InputDevice.SOURCE_JOYSTICK,0);
        runOnMainSync(() -> { try { consumed[0] = axis == MotionEvent.AXIS_RZ ? activity.dispatchGenericMotionEvent(event) : (Boolean)input.getClass().getMethod("dispatch",MotionEvent.class).invoke(input,event); } catch(Exception e) { throw new RuntimeException(e); } });
        event.recycle(); return consumed[0];
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"com.swordfish.lemuroid.app.tv.main.MainTVActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            input = call(activity,"getDescriptionScroll");
            for(int attempt=0;attempt<100 && ((Set<?>)panels()).size()!=1;attempt++)SystemClock.sleep(300);
            check(((Set<?>)panels()).size() == 1,"Open a theme's game library before running the probe");
            Class<?> helper=Class.forName("com.swordfish.lemuroid.app.tv.shared.TVGameMetadataHelper");
            Method match=helper.getDeclaredMethod("matchesGameTitle",String.class,String.class);match.setAccessible(true);
            Object instance=helper.getField("INSTANCE").get(null);
            check((Boolean)match.invoke(instance,"Bio Menace","Bio Menace"),"Exact game title rejected");
            check(!(Boolean)match.invoke(instance,"Bio Menace","Lista de jogos de Star Wars"),"Unrelated list accepted");
            check((Boolean)match.invoke(instance,"Mario Kart 64","Mario Kart 64"),"Numbered game rejected");
            check(!(Boolean)match.invoke(instance,"Mario Kart 64","Mario Kart"),"Series accepted instead of numbered game");
            check((Boolean)match.invoke(instance,"1943 - The Battle of Midway","1943: The Battle of Midway"),"Punctuation variation rejected");
            check((Boolean)match.invoke(instance,"Pokemon Emerald","Pokémon Emerald (video game)"),"Accents/qualifier rejected");
            Field field=helper.getDeclaredField("descriptionCache");field.setAccessible(true);
            cache=(LruCache<Integer,String>)field.get(null);
            for(int attempt=0;attempt<100 && cache.size()==0;attempt++)SystemClock.sleep(300);
            originals=cache.snapshot();
            check(!originals.isEmpty(),"Metadata has not loaded");
            StringBuilder full=new StringBuilder("INICIO DA DESCRICAO COMPLETA. ");
            for(int i=0;i<60;i++)full.append("Paragrafo ").append(i).append(": texto longo de validacao para conferir rolagem e botoes fixos. ");
            full.append("FIM DA DESCRICAO COMPLETA.");
            for(Integer id:originals.keySet())cache.put(id,full.toString());
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(700);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);SystemClock.sleep(1200);
            for(int attempt=0;attempt<30 && ((Set<?>)panels()).size()!=1;attempt++)SystemClock.sleep(100);
            SystemClock.sleep(1500);
            check(maximum()>500,"Description must contain the full long synopsis: max="+maximum());
            int start=value();check(motion(MotionEvent.AXIS_RZ,1f),"Right stick was not consumed");
            for(int sample=0;sample<100;sample++){motion(MotionEvent.AXIS_RZ,sample%2==0?1f:0.95f);SystemClock.sleep(6);}
            motion(MotionEvent.AXIS_RZ,0f);SystemClock.sleep(100);
            int down=value();check(down>start+100,"Down must scroll: "+start+" -> "+down);
            SystemClock.sleep(180);check(Math.abs(value()-down)<3,"Neutral must stop scrolling");
            check(!motion(MotionEvent.AXIS_Y,1f),"Left stick must remain available to navigation");
            check(!motion(MotionEvent.AXIS_RZ,0.1f),"Dead zone drift must not scroll");SystemClock.sleep(100);check(value()==down,"Dead zone drift changed scroll");
            motion(MotionEvent.AXIS_RZ,-1f);SystemClock.sleep(350);motion(MotionEvent.AXIS_RZ,0f);int up=value();check(up<down,"Up must reverse scrolling");
            motion(MotionEvent.AXIS_RZ,1f);SystemClock.sleep(120);runOnMainSync(()->{try{input.getClass().getMethod("stop").invoke(input);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(100);int stopped=value();SystemClock.sleep(150);check(value()==stopped,"Focus/lifecycle stop must halt scrolling");
            result.putString("result","PASS full="+full.length()+" max="+maximum()+" down="+down+" up="+up+" neutral/left-stick/stop/title-matching=PASS");
            cache.evictAll();for(Map.Entry<Integer,String> e:originals.entrySet())cache.put(e.getKey(),e.getValue());
            finish(0,result);
        } catch(Throwable e) { result.putString("error",android.util.Log.getStackTraceString(e));finish(1,result); }
        finally { if(cache!=null&&originals!=null){cache.evictAll();for(Map.Entry<Integer,String> e:originals.entrySet())cache.put(e.getKey(),e.getValue());} }
    }
}
