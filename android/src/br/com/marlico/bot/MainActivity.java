package br.com.marlico.bot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private final ExecutorService streamTasks=Executors.newSingleThreadExecutor();
    private final AtomicBoolean liveViewRunning=new AtomicBoolean();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean destroyed;
    private volatile int selectedMonitor;
    private boolean pcProbeInFlight;
    private long lastPcProbe;
    private final Runnable ticker=new Runnable(){public void run(){emitState();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiModeManager mode=(UiModeManager)getSystemService(UI_MODE_SERVICE);if(mode!=null&&mode.getCurrentModeType()==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        web=new WebView(this);web.setBackgroundColor(0xff0c1017);setContentView(web);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(),"Marlico");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return asset(r.getUrl());}
            @Override public void onPageFinished(WebView v,String url){emitState();}
        });
        web.loadUrl("https://app.marlico.local/index.html");
    }
    private WebResourceResponse asset(Uri uri) {
        try {
            if(!"https".equals(uri.getScheme())||!"app.marlico.local".equals(uri.getHost()))throw new IOException();
            String file=uri.getPath().substring(1);
            if(!file.matches("index\\.html|app\\.css|app\\.js|avatar\\.png"))throw new IOException();
            String mime=file.endsWith("html")?"text/html":file.endsWith("css")?"text/css":file.endsWith("js")?"application/javascript":"image/png";
            return new WebResourceResponse(mime,"UTF-8",getAssets().open(file));
        }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    }
    @Override public void onResume(){super.onResume();handler.post(ticker);}
    @Override public void onPause(){liveViewRunning.set(false);handler.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroy(){destroyed=true;liveViewRunning.set(false);handler.removeCallbacksAndMessages(null);tasks.shutdownNow();streamTasks.shutdownNow();if(web!=null){web.removeJavascriptInterface("Marlico");web.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.closePanel ? window.closePanel() : false",result->{if(!"true".equals(result))finish();});}
    private void js(String code){runOnUiThread(()->{if(!destroyed)web.evaluateJavascript(code,null);});}
    private void toast(String text){js("window.notify("+JSONObject.quote(text)+")");}
    private void emitState() {
        if(destroyed)return;
        try {
            Config cfg=Config.load(this);SharedPreferences p=Config.prefs(this);
            if(cfg.agentToken!=null&&cfg.agentToken.length()>=40&&!pcProbeInFlight&&System.currentTimeMillis()-lastPcProbe>10000){pcProbeInFlight=true;lastPcProbe=System.currentTimeMillis();tasks.submit(()->{boolean online=AgentApi.isOnline(cfg);runOnUiThread(()->{pcProbeInFlight=false;Config.prefs(MainActivity.this).edit().putString("pcState",online?"online":"offline").putLong("pcStateAt",System.currentTimeMillis()).apply();emitState();});});}
            String state=p.getString("state","off");boolean active=BotService.running();
            if(!active&&!state.equals("error"))state="off";
            JSONObject data=cfg.publicJson().put("state",state).put("running",active).put("detail",p.getString("detail","Importe seu .env para começar.")).put("botName",p.getString("botName","")).put("wakeCount",p.getInt("wakeCount",0)).put("lastWake",p.getLong("lastWake",0)).put("events",new JSONArray(p.getString("events","[]")));
            data.put("pcState",p.getString("pcState","unknown")).put("pcStateAt",p.getLong("pcStateAt",0));
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkInfo info=cm.getActiveNetworkInfo();
            data.put("network",info!=null&&info.isConnected()?(info.getType()==ConnectivityManager.TYPE_ETHERNET?"Cabo conectado":"Rede conectada"):"Sem rede");
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);data.put("batteryFree",pm.isIgnoringBatteryOptimizations(getPackageName()));
            js("window.renderState("+data.toString()+")");
        }catch(Exception e){toast("Não foi possível ler as configurações. Importe o .env novamente.");}
    }
    private void startBot() {
        try {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            BotService.start(this);toast("Conectando ao Telegram…");handler.postDelayed(()->emitState(),500);
        }catch(Exception e){toast("Não foi possível iniciar. Confira as configurações do bot.");}
    }
    public final class Bridge {
        @JavascriptInterface public void refresh(){runOnUiThread(()->emitState());}
        @JavascriptInterface public void connect(){runOnUiThread(()->startBot());}
        @JavascriptInterface public void stop(){runOnUiThread(()->{BotService.shutdown(MainActivity.this);emitState();toast("Bot pausado.");});}
        @JavascriptInterface public void importEnv(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(i,20);}catch(ActivityNotFoundException e){toast("A TV Box não tem seletor de arquivos. Preencha os dados nas configurações.");}});}
        @JavascriptInterface public void pairAgent(){tasks.submit(()->{
            try{Config old=Config.load(MainActivity.this);String ip=old.pcIp;JSONObject answer=AgentApi.pair(ip,old.agentPort);String agent=answer.optString("token","");int port=answer.optInt("port",old.agentPort);boolean wasRunning=BotService.running();if(wasRunning)BotService.shutdown(MainActivity.this);Config.savePairing(MainActivity.this,agent,ip,port);js("window.pairingSaved()");event("good","TV Box pareada com o agente Windows pela rede local.");toast("✅ Pareamento concluído pela rede local.");emitState();if(wasRunning)handler.postDelayed(()->BotService.start(MainActivity.this),700);}
            catch(Exception e){String message=e.getMessage()==null?"Não foi possível parear. Confira o IP e a rede local.":e.getMessage();toast(message);}
        });}
        @JavascriptInterface public void save(String json){tasks.submit(()->{
            try {
                JSONObject o=new JSONObject(json);Config old=Config.load(MainActivity.this);
                String t=o.optString("token","").trim();if(t.isEmpty())t=old.token;
                String agent=o.optString("agentToken","").trim();if(agent.isEmpty())agent=old.agentToken;
                Config next=new Config(t,agent,o.optString("users"),o.optString("mac"),o.optString("broadcast"),Core.port(o.optString("port")),Core.hostIpv4(o.optString("pcIp")),Core.port(o.optString("pcCheckPort")),Core.port(o.optString("agentPort")),o.optBoolean("autoStart",true)).validated();
                applyConfig(next);
            }catch(IllegalArgumentException e){toast(e.getMessage());}catch(Exception e){toast("Não foi possível salvar. Confira os dados e tente novamente.");}
        });}
        @JavascriptInterface public void wake(){tasks.submit(()->{try{
            Config c=Config.load(MainActivity.this);if(!c.ready())throw new IllegalArgumentException("Importe seu .env primeiro.");if(AgentApi.isOnline(c)){Config.prefs(MainActivity.this).edit().putString("pcState","online").putLong("pcStateAt",System.currentTimeMillis()).apply();toast("✅ Computador ligado. Não enviei o sinal.");emitState();return;}
            BotService.WakeResult result=BotService.wake(MainActivity.this,c,"TV Box");toast(result.message);
            if(result.packetSent){Config.prefs(MainActivity.this).edit().putString("pcState","checking").apply();event("info","Sinal enviado. Verificando resposta do PC.");new Thread(()->checkPc(c),"MarlicoBot-PC-check").start();}
            emitState();
        }catch(Exception e){toast("Não foi possível enviar o sinal. Confira as configurações e a conexão com a rede local.");}});}
        @JavascriptInterface public void battery(){runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(ActivityNotFoundException e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){toast("Abra as configurações do Android e permita a execução em segundo plano do MarlicoBot.");}}});}
        @JavascriptInterface public void monitor(){tasks.submit(()->{try{JSONObject value=AgentApi.get(Config.load(MainActivity.this),"/api/status");js("window.pcStatus("+value.toString()+")");}catch(Exception e){js("window.pcFailure("+JSONObject.quote(e.getMessage()==null?"Não foi possível consultar o agente Windows.":e.getMessage())+")");}});}
        @JavascriptInterface public void processes(){tasks.submit(()->{try{JSONObject value=AgentApi.get(Config.load(MainActivity.this),"/api/processes");js("window.pcProcesses("+value.toString()+")");}catch(Exception e){js("window.pcFailure("+JSONObject.quote(e.getMessage()==null?"Não foi possível consultar os programas.":e.getMessage())+")");}});}
        @JavascriptInterface public void inventory(){tasks.submit(()->{try{JSONObject value=AgentApi.get(Config.load(MainActivity.this),"/api/inventory");js("window.pcInventory("+value.toString()+")");}catch(Exception e){js("window.pcFailure("+JSONObject.quote(e.getMessage()==null?"Não foi possível consultar o inventário.":e.getMessage())+")");}});}
        @JavascriptInterface public void screen(int monitor){tasks.submit(()->{try{byte[] image=AgentApi.screen(Config.load(MainActivity.this),monitor);String encoded=android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP);js("window.pcScreen("+JSONObject.quote("data:image/jpeg;base64,"+encoded)+")");}catch(Exception e){js("window.pcFailure("+JSONObject.quote(e.getMessage()==null?"Captura recusada ou indisponível.":e.getMessage())+")");}});}
        @JavascriptInterface public void selectMonitor(int monitor){selectedMonitor=Math.max(0,monitor);}
        @JavascriptInterface public void startLiveScreen(int monitor){
            if(!liveViewRunning.compareAndSet(false,true))return;selectedMonitor=Math.max(0,monitor);
            js("window.screenState('waiting','Aguardando autorização no Windows…')");
            streamTasks.submit(()->{Config cfg=Config.load(MainActivity.this);String session=null;int failures=0;boolean first=true;
                try{session=AgentApi.liveStart(cfg,selectedMonitor);while(liveViewRunning.get()&&!Thread.currentThread().isInterrupted()){
                    try{byte[] image=AgentApi.liveFrame(cfg,selectedMonitor,session);if(!liveViewRunning.get())break;
                        String encoded=android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP);
                        js("window.pcScreen("+JSONObject.quote("data:image/jpeg;base64,"+encoded)+")");
                        if(first||failures>0)js("window.screenState('live','Tela ao vivo · somente visualização')");first=false;failures=0;Thread.sleep(180);
                    }catch(Exception e){if(!liveViewRunning.get())break;if(e instanceof AgentApi.AgentException&&((AgentApi.AgentException)e).status!=429)throw e;if(++failures>3)throw e;js("window.screenState('retry','Conexão instável. Tentando recuperar a imagem…')");Thread.sleep(800);}
                }}catch(Exception e){if(liveViewRunning.get())js("window.screenState('error',"+JSONObject.quote(e.getMessage()==null?"Não foi possível receber a tela.":e.getMessage())+")");}
                finally{if(session!=null)try{AgentApi.liveStop(cfg,0,session);}catch(Exception ignored){}liveViewRunning.set(false);js("window.screenStopped()");}
            });
        }
        @JavascriptInterface public void stopLiveScreen(){liveViewRunning.set(false);}
        @JavascriptInterface public void shutdownPc(){tasks.submit(()->{
            try{Config cfg=Config.load(MainActivity.this);String ticket=AgentApi.post(cfg,"/api/power","prepare").getString("ticket");
                runOnUiThread(()->{if(destroyed)return;new AlertDialog.Builder(MainActivity.this).setTitle("Desligar o computador?").setMessage("Todos os programas serão forçados a fechar. Arquivos e alterações não salvos serão perdidos.\n\nApós confirmar, o PC desligará em 30 segundos. Esta confirmação vale por 60 segundos.").setNegativeButton("Voltar",null).setPositiveButton("Desligar e fechar tudo",(dialog,which)->tasks.submit(()->{try{AgentApi.post(cfg,"/api/power","confirm:"+ticket);event("warn","Desligamento forçado confirmado pelo aplicativo.");toast("Desligamento agendado em 30 segundos. Você pode cancelar pelo botão Cancelar desligamento.");}catch(Exception e){toast(e.getMessage());}})).show();});
            }catch(Exception e){toast(e.getMessage());}
        });}
        @JavascriptInterface public void cancelShutdown(){tasks.submit(()->{try{AgentApi.post(Config.load(MainActivity.this),"/api/power","cancel");toast("Desligamento cancelado.");}catch(Exception e){toast(e.getMessage());}});}
    }
    private void applyConfig(Config cfg) throws Exception {
        BotService.shutdown(this);cfg.save(this);
        BotService.status(this,"off","Configuração salva. Toque em Conectar bot.");BotService.event(this,"info","Configurações atualizadas.");
        js("window.configSaved()");emitState();toast("Configuração salva. Agora toque em Conectar bot.");
    }
    private void event(String kind,String text){BotService.event(this,kind,text);}
    private void checkPc(Config cfg){
        long deadline=SystemClock.elapsedRealtime()+45000;
        do {
            try {
                if(Core.hostResponds(cfg.pcIp,cfg.pcCheckPort)){
                    Config.prefs(this).edit().putString("pcState","online").putLong("pcStateAt",System.currentTimeMillis()).apply();
                    event("good","PC respondeu na rede local: ligado.");toast("✅ Seu PC está ligado e respondendo.");emitState();return;
                }
            } catch(Exception ignored){}
            try{Thread.sleep(3500);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }while(SystemClock.elapsedRealtime()<deadline);
        Config.prefs(this).edit().putString("pcState","unconfirmed").putLong("pcStateAt",System.currentTimeMillis()).apply();
        event("warn","O PC não respondeu à verificação. O sinal foi enviado, mas o estado não foi confirmado.");
        toast("📨 O sinal foi enviado, mas não consegui confirmar se o PC ligou. Ping ou a porta de verificação podem estar bloqueados.");emitState();
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req!=20||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        tasks.submit(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null)throw new IOException();ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[1024];int count;
                while((count=in.read(buf))!=-1){if(bytes.size()+count>32768)throw new IllegalArgumentException("Arquivo muito grande. Selecione o .env do bot.");bytes.write(buf,0,count);}
                applyConfig(Config.fromEnv(bytes.toString("UTF-8"),Config.load(this)));
            }catch(IllegalArgumentException e){toast(e.getMessage());}catch(Exception e){toast("Não consegui importar esse arquivo. Escolha o .env do pendrive.");}
        });
    }
}


