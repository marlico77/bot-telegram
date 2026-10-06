package br.com.marlico.bot;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import br.com.marlico.update.UpdateFeed;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdates {
    static final int BUILD=14;
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean checking=new AtomicBoolean(),downloading=new AtomicBoolean();
    private static volatile long checked;
    private static volatile UpdateFeed.Release release;
    private static volatile String message="Atualizações verificadas automaticamente a cada 6 horas.";
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private static boolean scheduled;
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("updates",Context.MODE_PRIVATE);}
    static boolean required(Context c){return prefs(c).getInt("minimum",0)>BUILD;}
    static String status(Context c){return (required(c)?"Atualização obrigatória. ":"")+message;}
    static boolean available(){return release!=null&&release.build>BUILD;}
    static boolean busy(){return checking.get()||downloading.get();}
    static synchronized void start(Context context){
        Context c=context.getApplicationContext();if(scheduled)return;scheduled=true;
        try{UpdateFeed.Result cached=UpdateFeed.parse(prefs(c).getString("manifest",""),"android");release=cached.release;}catch(Exception ignored){}
        worker.scheduleWithFixedDelay(()->check(c,false),0,6,TimeUnit.HOURS);
    }
    static void check(Context context,boolean force){
        Context c=context.getApplicationContext();if(!force&&System.currentTimeMillis()-checked<6*3600000L)return;
        if(!checking.compareAndSet(false,true))return;message="Consultando versões oficiais…";
        worker.execute(()->{
            try{
                UpdateFeed.Result result=UpdateFeed.check("android",BUILD);
                if(!result.fresh)throw new IOException("Catálogo temporariamente desatualizado. Tentaremos novamente.");
                if(result.release!=null){release=result.release;prefs(c).edit().putString("manifest",result.raw).putInt("minimum",release.minimumBuild).apply();}checked=System.currentTimeMillis();
                message=available()?"Versão "+release.version+" disponível. Toque para baixar.":result.release==null?"Nenhuma versão publicada no catálogo.":"Você está usando a versão mais recente (1.3.2).";
                if(available()&&prefs(c).getInt("notified",0)!=release.build){notifyUpdate(c);prefs(c).edit().putInt("notified",release.build).apply();}
            }catch(Exception e){message="Não foi possível consultar atualizações. Verifique a internet e tente novamente.";checked=System.currentTimeMillis()-5*3600000L;}
            finally{checking.set(false);}
        });
    }
    private static void notifyUpdate(Context c){
        try{NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String channel="marlico.updates";
            if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(channel,"Atualizações do aplicativo",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open=PendingIntent.getActivity(c,31,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);
            n.notify(31,b.setSmallIcon(R.drawable.ic_notification).setContentTitle(required(c)?"Atualização obrigatória":"MarlicoBot · nova versão").setContentText(message).setContentIntent(open).setAutoCancel(true).build());
        }catch(RuntimeException ignored){}
    }
    static void action(Activity a){
        if(busy())return;if(!available()){check(a,true);return;}
        UpdateFeed.Release r=release;
        new AlertDialog.Builder(a).setTitle("MarlicoBot "+r.version).setMessage("Baixar e instalar a atualização oficial? Suas configurações serão mantidas.\n\n"+r.notes).setNegativeButton("Agora não",null).setPositiveButton("Baixar atualização",(d,w)->download(a,r)).show();
    }
    private static void download(Activity a,UpdateFeed.Release r){
        if(!downloading.compareAndSet(false,true))return;Context c=a.getApplicationContext();message="Preparando download…";
        worker.execute(()->{try{
            File file=new File(c.getFilesDir(),"official-update.apk");UpdateFeed.download(r,file,p->message="Baixando atualização: "+p+"%");
            validate(c,r,file);message="Download verificado. Confirme a instalação no Android.";
            ui.post(()->{if(!a.isFinishing()&&!a.isDestroyed())install(a,r);});
        }catch(Exception e){message="Não foi possível instalar: "+e.getMessage();}finally{downloading.set(false);}});
    }
    private static void validate(Context c,UpdateFeed.Release r,File file)throws Exception {
        UpdateFeed.verify(r,file);PackageManager pm=c.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES),installed=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNATURES);
        if(candidate==null||!c.getPackageName().equals(candidate.packageName)||candidate.versionCode!=r.build||candidate.versionCode<=installed.versionCode||candidate.signatures==null||installed.signatures==null||!Arrays.equals(candidate.signatures,installed.signatures))throw new IOException("Pacote ou assinatura incompatível com este aplicativo.");
    }
    private static void install(Activity a,UpdateFeed.Release r){
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a).setTitle("Permitir atualização").setMessage("Ative a permissão para instalar atualizações deste aplicativo. Depois volte e toque em Instalar download.").setPositiveButton("Abrir permissão",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(ActivityNotFoundException e){message="Abra as configurações do Android e permita instalar apps desta fonte.";}}).setNegativeButton("Depois",null).show();return;
        }
        try{Uri uri=Uri.parse("content://"+a.getPackageName()+".updates/official-update.apk");Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Atualização MarlicoBot",uri));a.startActivity(i);}catch(Exception e){message="Instalador indisponível neste Android. Tente novamente ou baixe pelo site.";}
    }
    static void installDownloaded(Activity a){
        UpdateFeed.Release r=release;if(r==null||r.build<=BUILD||busy()){action(a);return;}
        File file=new File(a.getFilesDir(),"official-update.apk");if(!file.exists()){action(a);return;}
        if(!downloading.compareAndSet(false,true))return;
        worker.execute(()->{try{validate(a,r,file);ui.post(()->{if(!a.isFinishing()&&!a.isDestroyed())install(a,r);});}catch(Exception e){message="Download inválido. Toque em Baixar para tentar novamente.";ui.post(()->{if(!a.isFinishing())action(a);});}finally{downloading.set(false);}});
    }
}
