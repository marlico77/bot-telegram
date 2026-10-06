package br.com.marlico.bot;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;
import java.util.*;

public final class BotService extends Service {
    private static final String CHANNEL="marlico.connection";
    private static volatile BotService instance;
    private volatile boolean alive;
    private Thread worker;
    private TelegramApi api;
    private PowerManager.WakeLock wakeLock;
    private static long lastWake=-100000;
    private static final long CHECK_INTERVAL_MS=3500, CHECK_TIMEOUT_MS=45000;
    private PendingCheck pendingCheck;
    private final Map<Long,String> powerTickets=new HashMap<>();
    private final Map<Long,Long> powerDeadlines=new HashMap<>();
    private static final class PendingCheck {
        final long chat,message,deadline;
        final String nonce;
        final Config config;
        long next;
        Boolean online;
        PendingCheck(long chat,long message,String nonce,Config config) {
            this.chat=chat;this.message=message;this.nonce=nonce;this.config=config;
            long now=SystemClock.elapsedRealtime();deadline=now+CHECK_TIMEOUT_MS;next=now;
        }
    }
    public static final class WakeResult {
        public final String message;
        public final boolean packetSent;
        WakeResult(String message,boolean packetSent){this.message=message;this.packetSent=packetSent;}
    }

    public static boolean running() { BotService s=instance;return s!=null&&s.alive; }
    public static void start(Context c) {
        if(!Config.load(c).ready()) throw new IllegalArgumentException("Importe seu .env antes de conectar.");
        Config.prefs(c).edit().putBoolean("enabled",true).apply();
        Intent i=new Intent(c,BotService.class);
        try { if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i); }
        catch(RuntimeException e){Config.prefs(c).edit().putBoolean("enabled",false).apply();throw e;}
    }
    public static void shutdown(Context c) {
        Config.prefs(c).edit().putBoolean("enabled",false).apply();
        c.stopService(new Intent(c,BotService.class));
        status(c,"off","Bot pausado. Você pode reconectar quando quiser.");
    }
    public static synchronized void event(Context c,String kind,String text) {
        try {
            JSONArray old=new JSONArray(Config.prefs(c).getString("events","[]")), out=new JSONArray();
            out.put(new JSONObject().put("kind",kind).put("text",text).put("time",System.currentTimeMillis()));
            for(int i=0;i<Math.min(old.length(),29);i++)out.put(old.get(i));
            Config.prefs(c).edit().putString("events",out.toString()).apply();
        }catch(JSONException ignored){}
    }
    public static void status(Context c,String state,String detail) {
        Config.prefs(c).edit().putString("state",state).putString("detail",detail).apply();
    }
    public static synchronized WakeResult wake(Context c,Config cfg,String source) throws Exception {
        long now=SystemClock.elapsedRealtime();
        if(now-lastWake<10000)return new WakeResult("O sinal já foi enviado. Aguarde alguns segundos.",false);
        Core.sendWake(cfg.mac,cfg.broadcast,cfg.port);
        lastWake=now;
        Config.prefs(c).edit().putLong("lastWake",System.currentTimeMillis()).putInt("wakeCount",Config.prefs(c).getInt("wakeCount",0)+1).apply();
        event(c,"good","Sinal para ligar o PC enviado · "+source);
        return new WakeResult("📡 Sinal enviado. Verificando se o PC responde…",true);
    }
    private Notification notification(String text) {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BotService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_notification).setContentTitle("MarlicoBot").setContentText(text).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Pausar",stop).build()).build();
    }
    private void report(String state,String detail) {
        if(!alive)return;
        status(this,state,detail);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification(detail));
    }
    @Override public void onCreate() {
        super.onCreate();instance=this;
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"Conexão com o Telegram",NotificationManager.IMPORTANCE_LOW);channel.setDescription("Mantém o bot disponível para receber seus comandos.");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null&&"STOP".equals(intent.getAction())){shutdown(this);return START_NOT_STICKY;}
        Config cfg=Config.load(this);
        if(!Config.prefs(this).getBoolean("enabled",false)||!cfg.ready()){stopSelf();return START_NOT_STICKY;}
        if(Build.VERSION.SDK_INT>=34)startForeground(7,notification("Conectando ao Telegram…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7,notification("Conectando ao Telegram…"));
        if(worker!=null&&worker.isAlive())return START_STICKY;
        alive=true;
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MarlicoBot:receiver");wakeLock.setReferenceCounted(false);wakeLock.acquire();
        api=new TelegramApi(cfg.token);
        worker=new Thread(()->loop(cfg),"MarlicoBot-receiver");worker.start();
        return START_STICKY;
    }
    private JSONObject params() throws JSONException {return new JSONObject().put("allowed_updates",new JSONArray().put("message").put("callback_query"));}
    private void loop(Config cfg) {
        int failures=0,conflicts=0;boolean initialized=false;long offset=0;
        String nonce=UUID.randomUUID().toString().substring(0,12);
        Set<Long> allowed=Core.users(cfg.users);
        try {
            while(alive) {
                try {
                    advanceCheck();
                    if(!initialized) {
                        report("connecting","Conectando ao Telegram…");
                        JSONObject me=api.call("getMe",new JSONObject()).getJSONObject("result");
                        Config.prefs(this).edit().putString("botName",me.optString("username","MarlicoBot")).apply();
                        JSONObject hook=api.call("getWebhookInfo",new JSONObject()).getJSONObject("result");
                        if(!hook.optString("url").isEmpty()) {fatal("Este bot tem um webhook ativo. Remova-o antes de usar a TV Box.");break;}
                        // Discard queued commands on startup; never wake a PC from yesterday's queue.
                        JSONArray tail=api.call("getUpdates",params().put("offset",-1).put("limit",1).put("timeout",0)).getJSONArray("result");
                        if(tail.length()>0)offset=tail.getJSONObject(tail.length()-1).getLong("update_id")+1;
                        initialized=true;event(this,"good","Telegram conectado. Pronto para receber comandos.");
                        report("online","Seu bot está pronto para receber comandos.");
                    }
                    JSONArray updates=api.call("getUpdates",params().put("offset",offset).put("limit",20).put("timeout",pendingCheck==null?25:2)).getJSONArray("result");
                    report("online","Seu bot está pronto para receber comandos.");
                    failures=0;
                    Config.prefs(this).edit().putLong("lastContact",System.currentTimeMillis()).apply();
                    for(int i=0;i<updates.length()&&alive;i++) {
                        JSONObject u=updates.getJSONObject(i);offset=u.getLong("update_id")+1;
                        // A command is consumed once, even if the reply fails after sending WOL.
                        try { handle(u,cfg,allowed,nonce); }
                        catch(TelegramApi.ApiException e) {if(e.code==401)throw e;if(e.code==429)pause(Math.max(5,e.retry));event(this,"warn","Não foi possível responder a uma mensagem. Envie o comando novamente se necessário.");}
                        catch(Exception e) {if(alive)event(this,"warn","Falha ao processar um comando. Confira a conexão e tente novamente.");}
                    }
                } catch(TelegramApi.ApiException e) {
                    if(!alive)break;
                    nonce=UUID.randomUUID().toString().substring(0,12);
                    if(e.code==401||e.code==404){fatal("Token inválido ou revogado. Importe o .env atualizado nas configurações.");break;}
                    if(e.code==409) {
                        conflicts++;
                        if(conflicts>=3){fatal("Outra cópia está usando este bot. Pare o bot no Termux e no PC; depois toque em Conectar.");break;}
                        report("retry","Outra cópia do bot está conectada. Feche o bot no Termux e no PC.");event(this,"warn","Conflito no Telegram: aguardando a outra sessão encerrar.");pause(35);
                    } else { int delay=e.code==429?Math.max(5,Math.min(e.retry,3600)):Math.min(60,5*(1<<Math.min(failures++,3)));report("retry","Telegram indisponível. Tentando reconectar automaticamente…");pause(delay); }
                } catch(Exception e) {
                    if(!alive)break;
                    // Old queued button presses must not wake the PC after an outage.
                    nonce=UUID.randomUUID().toString().substring(0,12);
                    int delay=Math.min(60,5*(1<<Math.min(failures++,3)));
                    report("retry","Sem conexão com o Telegram. Nova tentativa automática em "+delay+"s.");
                    if(failures==1)event(this,"warn","Conexão interrompida. A reconexão é automática.");
                    pause(delay);
                }
            }
        } finally { if(alive){alive=false;stopSelf();} }
    }
    private void fatal(String text) {report("error",text);event(this,"warn",text);Config.prefs(this).edit().putBoolean("enabled",false).apply();}
    private void pause(int seconds) {try{Thread.sleep(seconds*1000L);}catch(InterruptedException e){Thread.currentThread().interrupt();alive=false;}}
    private JSONObject keyboard(String nonce,Conversation.Action screen,boolean pcOnline) throws JSONException {
        JSONArray rows=new JSONArray();
        if(screen==Conversation.Action.MENU){
            if(!pcOnline)rows.put(new JSONArray().put(button("⚡ Ligar PC","wake",nonce)));
            rows.put(new JSONArray().put(button("📊 Métricas","status",nonce)).put(button("📂 Programas abertos","programs",nonce)));
            rows.put(new JSONArray().put(button("📸 Print da tela","screen",nonce)).put(button("🧰 Hardware","inventory",nonce)));
            rows.put(new JSONArray().put(button("⏻ Desligar PC","shutdown",nonce)));
        } else rows.put(new JSONArray().put(new JSONObject().put("text","Menu").put("callback_data",Conversation.callback("menu",nonce))));
        return new JSONObject().put("inline_keyboard",rows);
    }
    private JSONObject button(String label,String action,String nonce)throws JSONException{return new JSONObject().put("text",label).put("callback_data",Conversation.callback(action,nonce));}
    private void reply(long chat,long messageId,String text,String nonce,Conversation.Action screen) throws Exception {
        boolean pcOnline=AgentApi.isOnline(Config.load(this));String shown=pcOnline&&screen==Conversation.Action.MENU?"✅ Computador ligado.\n\n"+text:text;
        JSONObject p=new JSONObject().put("chat_id",chat).put("text",shown).put("reply_markup",keyboard(nonce,screen,pcOnline));
        if(messageId>0) {
            // Reuse the bot's own message as the user navigates, without filling the chat.
            p.put("message_id",messageId);
            try {api.call("editMessageText",p);return;}
            catch(TelegramApi.ApiException e) {
                if(e.code==400&&e.unchanged)return;
                if(e.code!=400)throw e;
                // A deleted or uneditable bot message can be replaced with a fresh menu.
                p.remove("message_id");
            }
        }
        api.call("sendMessage",p);
    }
    private void handle(JSONObject u,Config cfg,Set<Long> allowed,String nonce) throws Exception {
        JSONObject cb=u.optJSONObject("callback_query");
        JSONObject message=cb!=null?cb.optJSONObject("message"):u.optJSONObject("message");
        JSONObject from=cb!=null?cb.optJSONObject("from"):(message==null?null:message.optJSONObject("from"));
        if(from==null||!allowed.contains(from.optLong("id",0)))return;
        JSONObject chat=message==null?null:message.optJSONObject("chat");
        if(chat==null||!"private".equals(chat.optString("type"))||chat.optLong("id")!=from.optLong("id"))return;
        long chatId=chat.getLong("id");
        if(cb!=null) {
            String callback=cb.optString("data","");
            if(callback.startsWith("power:")||callback.startsWith("abortpower:")){
                api.call("answerCallbackQuery",new JSONObject().put("callback_query_id",cb.getString("id")));
                if(callback.startsWith("abortpower:")){
                    if(!callback.equals("abortpower:"+nonce)){replyQuiet(chatId,0,"Este botão expirou. Use /cancelar_desligamento.",nonce);return;}
                    cancelPower(chatId,nonce);return;
                }
                String ticket=powerTickets.get(chatId);
                if(ticket==null||!callback.equals("power:"+ticket)||SystemClock.elapsedRealtime()>(powerDeadlines.containsKey(chatId)?powerDeadlines.get(chatId):0L)){replyQuiet(chatId,0,"Confirmação expirada. Envie /desligar novamente.",nonce);return;}
                powerTickets.remove(chatId);powerDeadlines.remove(chatId);
                try{AgentApi.post(cfg,"/api/power","confirm:"+ticket);
                    JSONObject cancel=new JSONObject().put("inline_keyboard",new JSONArray().put(new JSONArray().put(new JSONObject().put("text","Cancelar desligamento").put("callback_data","abortpower:"+nonce))));
                    api.call("sendMessage",new JSONObject().put("chat_id",chatId).put("text","Desligamento agendado em 30 segundos. Os programas serão fechados à força. Isso ainda não confirma que o PC desligou.").put("reply_markup",cancel));
                    event(this,"warn","Desligamento forçado confirmado pelo Telegram.");
                }catch(Exception e){replyQuiet(chatId,0,"Não foi possível concluir o pedido: "+e.getMessage(),nonce);}return;
            }
            Conversation.Action action=Conversation.action(cb.optString("data"),nonce);
            JSONObject answer=new JSONObject().put("callback_query_id",cb.getString("id"));
            if(action==Conversation.Action.GREETING)answer.put("text","Menu atualizado. Toque em Menu para continuar.");
            api.call("answerCallbackQuery",answer);
            long messageId=message.optLong("message_id",0);
            if(action==Conversation.Action.MENU)reply(chatId,messageId,"Escolha uma opção, Marlon:",nonce,Conversation.Action.MENU);
            else if(action==Conversation.Action.WAKE){if(AgentApi.isOnline(cfg))reply(chatId,messageId,"✅ Computador ligado. Não é necessário enviar o sinal de ligar.",nonce,Conversation.Action.GREETING);else wakeAndReply(chatId,messageId,cfg,nonce);}
            else if(action==Conversation.Action.STATUS)showStatus(chatId,messageId,nonce);
            else if(action==Conversation.Action.PROGRAMS)showPrograms(chatId,messageId,nonce);
            else if(action==Conversation.Action.SCREEN)showScreen(chatId,messageId,nonce);
            else if(action==Conversation.Action.INVENTORY)showInventory(chatId,messageId,nonce);
            else if(action==Conversation.Action.SHUTDOWN)preparePower(chatId,nonce);
            else reply(chatId,messageId,Conversation.greeting(System.currentTimeMillis()),nonce,Conversation.Action.GREETING);
        } else {
            String text=message==null?"":message.optString("text","").trim();String command=text.isEmpty()?"":text.split("\\s+",2)[0].toLowerCase(Locale.ROOT).replaceFirst("@[^@]+$","");
            if(command.equals("/metricas")||command.equals("/métricas")||command.equals("/status"))showStatus(chatId,0,nonce);
            else if(command.equals("/programas")||command.equals("/processos"))showPrograms(chatId,0,nonce);
            else if(command.equals("/print")||command.equals("/print_tela")||command.equals("/screenshot"))showScreen(chatId,0,nonce);
            else if(command.equals("/desligar"))preparePower(chatId,nonce);
            else if(command.equals("/cancelar_desligamento"))cancelPower(chatId,nonce);
            else if(command.equals("/ligar")||command.equals("/wake")){if(AgentApi.isOnline(cfg))reply(chatId,0,"✅ Computador ligado. Não é necessário enviar o sinal de ligar.",nonce,Conversation.Action.GREETING);else wakeAndReply(chatId,0,cfg,nonce);}
            else reply(chatId,0,Conversation.greeting(System.currentTimeMillis()),nonce,Conversation.Action.GREETING);
        }
    }
    private void preparePower(long chat,String nonce){try{
        String ticket=AgentApi.post(Config.load(this),"/api/power","prepare").getString("ticket");powerTickets.put(chat,ticket);powerDeadlines.put(chat,SystemClock.elapsedRealtime()+60000);
        JSONObject keys=new JSONObject().put("inline_keyboard",new JSONArray().put(new JSONArray().put(new JSONObject().put("text","Confirmar: desligar e fechar tudo").put("callback_data","power:"+ticket))).put(new JSONArray().put(button("Voltar ao menu","menu",nonce))));
        api.call("sendMessage",new JSONObject().put("chat_id",chat).put("text","Desligar o PC? Todos os programas serão forçados a fechar. Alterações não salvas serão perdidas. Após confirmar, haverá 30 segundos para cancelar. A confirmação expira em 60 segundos.").put("reply_markup",keys));
    }catch(Exception e){replyQuiet(chat,0,"Não foi possível solicitar o desligamento: "+e.getMessage(),nonce);}}
    private void cancelPower(long chat,String nonce){try{AgentApi.post(Config.load(this),"/api/power","cancel");replyQuiet(chat,0,"Desligamento cancelado.",nonce);}catch(Exception e){replyQuiet(chat,0,"Não foi possível cancelar: "+e.getMessage(),nonce);}}
    private void showStatus(long chat,long message,String nonce){try{JSONObject data=AgentApi.get(Config.load(this),"/api/status");reply(chat,message,AgentApi.statusText(data),nonce,Conversation.Action.GREETING);event(this,"info","Status do PC consultado pelo Telegram.");}catch(Exception e){replyQuiet(chat,message,"⚠️ Não consegui consultar o PC. Confirme se o agente Windows está aberto, o IP está correto e o pareamento foi importado.",nonce);}}
    private void showPrograms(long chat,long message,String nonce){try{JSONArray list=AgentApi.get(Config.load(this),"/api/processes").optJSONArray("processes");StringBuilder out=new StringBuilder("📂 Programas e processos ativos:\n");if(list==null||list.length()==0)out.append("Nenhum processo listado.");else for(int i=0;i<Math.min(list.length(),22);i++){JSONObject p=list.getJSONObject(i);out.append("• ").append(p.optString("name","Programa")).append(" · ").append(AgentApi.bytes(p.optLong("memoryBytes",0))).append("\n");}reply(chat,message,out.toString(),nonce,Conversation.Action.GREETING);event(this,"info","Lista de processos do PC consultada pelo Telegram.");}catch(Exception e){event(this,"warn","A consulta de processos falhou: "+(e.getMessage()==null?"erro de rede":e.getMessage()));replyQuiet(chat,message,"⚠️ Não consegui consultar os programas. Confira se o agente Windows está ativo, se o IP está correto e se o pareamento pela rede foi aprovado.",nonce);}}
    private void showInventory(long chat,long message,String nonce){try{JSONObject data=AgentApi.get(Config.load(this),"/api/inventory");JSONObject fields=data.optJSONObject("fields");StringBuilder out=new StringBuilder("🧰 Inventário do computador\n");if(fields!=null){java.util.Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();String value=fields.optString(key,"");if(!value.isEmpty())out.append("• ").append(key).append(": ").append(value).append("\n");}}JSONArray apps=data.optJSONArray("installedPrograms");int count=apps==null?0:apps.length();out.append("\n📦 Aplicativos instalados (").append(count).append("):\n");if(apps!=null)for(int i=0;i<Math.min(count,12);i++){JSONObject app=apps.optJSONObject(i);if(app!=null)out.append("• ").append(app.optString("name","Aplicativo")).append(app.optString("version","").isEmpty()?"":" · "+app.optString("version")).append("\n");}if(count>12)out.append("… e mais ").append(count-12).append(". Veja a lista completa na aba Programas instalados do Windows.");reply(chat,message,out.toString(),nonce,Conversation.Action.GREETING);event(this,"info","Inventário de hardware e programas instalado consultado pelo Telegram.");}catch(Exception e){replyQuiet(chat,message,"⚠️ Não consegui consultar o inventário. Confirme que o agente Windows está aberto e o pareamento foi importado.",nonce);}}
    private void showScreen(long chat,long message,String nonce){try{byte[] image=AgentApi.screen(Config.load(this));api.sendPhoto(chat,image,"Captura única aprovada no computador. Nenhum controle remoto está disponível.");reply(chat,message,"✅ Imagem enviada. A captura foi aprovada no Windows.",nonce,Conversation.Action.GREETING);event(this,"good","Captura única de tela autorizada localmente e enviada ao Telegram.");}catch(Exception e){event(this,"warn","Captura única não concluída: "+(e.getMessage()==null?"erro de rede":e.getMessage()));replyQuiet(chat,message,"📷 A imagem não chegou. Confira se você aprovou a captura no Windows e aguarde o envio terminar. Detalhe: "+(e.getMessage()==null?"conexão indisponível":e.getMessage()),nonce);}}
    private void replyQuiet(long chat,long message,String text,String nonce){try{reply(chat,message,text,nonce,Conversation.Action.GREETING);}catch(Exception ignored){try{JSONObject p=new JSONObject().put("chat_id",chat).put("text",text).put("reply_markup",keyboard(nonce,Conversation.Action.GREETING,false));api.call("sendMessage",p);}catch(Exception ignoredAgain){}}}
    private void wakeAndReply(long chat,long messageId,Config cfg,String nonce) throws Exception {
        WakeResult result;
        try {result=wake(this,cfg,"Telegram");}
        catch(Exception e){event(this,"warn","Falha ao enviar o sinal pela rede local.");reply(chat,messageId,"❌ Não consegui enviar o sinal. Confira a conexão da TV Box.",nonce,Conversation.Action.GREETING);return;}
        reply(chat,messageId,result.message,nonce,Conversation.Action.GREETING);
        if(result.packetSent) {
            pendingCheck=new PendingCheck(chat,messageId,nonce,cfg);
            Config.prefs(this).edit().putString("pcState","checking").apply();
            event(this,"info","Sinal enviado. Verificando resposta do PC.");
        }
    }
    private void advanceCheck() {
        PendingCheck check=pendingCheck;
        if(check==null||check.online!=null)return;
        long now=SystemClock.elapsedRealtime();if(now<check.next)return;
        try {check.online=Core.hostResponds(check.config.pcIp,check.config.pcCheckPort);}
        catch(Exception ignored) {check.online=false;}
        if(Boolean.TRUE.equals(check.online)) {
            Config.prefs(this).edit().putString("pcState","online").putLong("pcStateAt",System.currentTimeMillis()).apply();
            event(this,"good","PC respondeu na rede local: ligado.");
            try {reply(check.chat,check.message,"✅ Seu PC está ligado e respondendo.",check.nonce,Conversation.Action.GREETING);pendingCheck=null;}
            catch(Exception ignored) {check.online=null;check.next=now+CHECK_INTERVAL_MS;}
        } else if(now>=check.deadline) {
            Config.prefs(this).edit().putString("pcState","unconfirmed").putLong("pcStateAt",System.currentTimeMillis()).apply();
            event(this,"warn","O PC não respondeu à verificação. O sinal foi enviado, mas o estado não foi confirmado.");
            try {reply(check.chat,check.message,"📨 O sinal foi enviado, mas não consegui confirmar se o PC ligou. Ele pode estar ligado com o ping e a porta de verificação bloqueados.",check.nonce,Conversation.Action.GREETING);pendingCheck=null;}
            catch(Exception ignored) {check.online=null;check.next=now+CHECK_INTERVAL_MS;}
        } else {check.online=null;check.next=SystemClock.elapsedRealtime()+CHECK_INTERVAL_MS;}
    }
    @Override public void onDestroy() {
        alive=false;if(api!=null)api.cancel();if(worker!=null)worker.interrupt();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        if(instance==this)instance=null;
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}


