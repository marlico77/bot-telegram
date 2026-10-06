package br.com.marlico.bot;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
        Config cfg=Config.load(c);
        if(cfg.autoStart&&cfg.ready()&&Config.prefs(c).getBoolean("enabled",false)) {
            try{BotService.start(c);}catch(RuntimeException e){BotService.status(c,"error","Abra o aplicativo e toque em Conectar para retomar o bot.");}
        }
    }
}
