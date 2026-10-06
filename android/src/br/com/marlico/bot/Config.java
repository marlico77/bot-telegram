package br.com.marlico.bot;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.util.*;
import org.json.*;

public final class Config {
    public final String token, agentToken, users, mac, broadcast, pcIp;
    public final int port, pcCheckPort, agentPort;
    public final boolean autoStart;
    public Config(String t,String u,String m,String b,int p,boolean a) { this(t,"",u,m,b,p,"192.168.0.6",445,8765,a); }
    public Config(String t,String u,String m,String b,int p,String host,int probePort,boolean a) { this(t,"",u,m,b,p,host,probePort,8765,a); }
    public Config(String t,String agent,String u,String m,String b,int p,String host,int probePort,int agentHttpPort,boolean a) { token=t;agentToken=agent;users=u;mac=m;broadcast=b;port=p;pcIp=host;pcCheckPort=probePort;agentPort=agentHttpPort;autoStart=a; }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("marlico",Context.MODE_PRIVATE); }
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("marlico.token")) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("marlico.token",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)store.getKey("marlico.token",null);
    }
    public static Config load(Context c) {
        SharedPreferences p=prefs(c); String token="";
        try {
            if(p.contains("tokenCipher")) {
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("tokenIv",""),Base64.NO_WRAP)));
                token=new String(cipher.doFinal(Base64.decode(p.getString("tokenCipher",""),Base64.NO_WRAP)),"UTF-8");
            }
        } catch(Exception ignored) { /* Fail closed; request reimport if the key is unavailable. */ }
        String agent="";
        try { if(p.contains("agentCipher")){Cipher agentDec=Cipher.getInstance("AES/GCM/NoPadding");agentDec.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("agentIv",""),Base64.NO_WRAP)));agent=new String(agentDec.doFinal(Base64.decode(p.getString("agentCipher",""),Base64.NO_WRAP)),"UTF-8");} } catch(Exception ignored) { }
        return new Config(token,agent,p.getString("users","8703897768"),p.getString("mac","00:E2:69:7C:B1:AA"),p.getString("broadcast","255.255.255.255"),p.getInt("port",9),p.getString("pcIp","192.168.0.6"),p.getInt("pcCheckPort",445),p.getInt("agentPort",8765),p.getBoolean("autoStart",true));
    }
    public Config validated() {
        if(token!=null&&!token.isEmpty())Core.token(token);
        else if(agentToken==null||agentToken.isEmpty())throw new IllegalArgumentException("Importe a configuração do Telegram ou pareie com o PC pela rede local.");
        Core.users(users);
        if(agentToken!=null&&!agentToken.isEmpty()&&!agentToken.matches("[A-Za-z0-9_-]{40,80}"))throw new IllegalArgumentException("Código de pareamento do PC inválido.");
        return new Config(token,agentToken==null?"":agentToken,users.trim(),Core.mac(mac),Core.ipv4(broadcast),Core.port(""+port),Core.hostIpv4(pcIp),Core.port(""+pcCheckPort),Core.port(""+agentPort),autoStart);
    }
    public boolean ready() { try{validated();return token!=null&&!token.isEmpty();}catch(Exception e){return false;} }
    public void save(Context c) throws Exception {
        Config valid=validated(); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        SharedPreferences.Editor edit=prefs(c).edit();
        edit.putString("tokenCipher",Base64.encodeToString(cipher.doFinal(valid.token.getBytes("UTF-8")),Base64.NO_WRAP));
        edit.putString("tokenIv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        Cipher agentCipher=Cipher.getInstance("AES/GCM/NoPadding");agentCipher.init(Cipher.ENCRYPT_MODE,key());
        edit.putString("agentCipher",Base64.encodeToString(agentCipher.doFinal(valid.agentToken.getBytes("UTF-8")),Base64.NO_WRAP)).putString("agentIv",Base64.encodeToString(agentCipher.getIV(),Base64.NO_WRAP));
        edit.putString("users",valid.users).putString("mac",valid.mac).putString("broadcast",valid.broadcast).putInt("port",valid.port).putString("pcIp",valid.pcIp).putInt("pcCheckPort",valid.pcCheckPort).putInt("agentPort",valid.agentPort).putBoolean("autoStart",valid.autoStart);
        edit.putBoolean("enabled",false).remove("botName");
        if(!edit.commit()) throw new java.io.IOException("Não foi possível salvar as configurações.");
    }
    public static void savePairing(Context c,String agent,String ip,int port) throws Exception {
        if(agent==null||!agent.matches("[A-Za-z0-9_-]{40,80}"))throw new IllegalArgumentException("O Windows retornou um código de pareamento inválido.");
        String validIp=Core.hostIpv4(ip);int validPort=Core.port(""+port);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        SharedPreferences.Editor edit=prefs(c).edit().putString("agentCipher",Base64.encodeToString(cipher.doFinal(agent.getBytes("UTF-8")),Base64.NO_WRAP)).putString("agentIv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("pcIp",validIp).putInt("agentPort",validPort);
        if(!edit.commit())throw new java.io.IOException("Não foi possível guardar o pareamento neste Android.");
    }
    public JSONObject publicJson() throws JSONException {
        return new JSONObject().put("configured",ready()).put("users",users).put("mac",mac).put("broadcast",broadcast).put("port",port).put("pcIp",pcIp).put("pcCheckPort",pcCheckPort).put("agentPort",agentPort).put("autoStart",autoStart).put("hasToken",!token.isEmpty()).put("hasAgentToken",!agentToken.isEmpty());
    }
    public static Config fromEnv(String text, Config old) {
        Map<String,String> e=Core.env(text);
        if(!e.containsKey("TELEGRAM_BOT_TOKEN")&&!e.containsKey("PC_AGENT_TOKEN")) throw new IllegalArgumentException("Arquivo sem configuração do bot ou pareamento do agente Windows.");
        String newToken=value(e,"TELEGRAM_BOT_TOKEN",old.token);
        return new Config(newToken,value(e,"PC_AGENT_TOKEN",old.agentToken),value(e,"TELEGRAM_ALLOWED_USER_IDS",old.users),value(e,"PC_MAC_ADDRESS",old.mac),value(e,"WOL_BROADCAST_IP",old.broadcast),Core.port(value(e,"WOL_PORT",""+old.port)),Core.hostIpv4(value(e,"PC_IP_ADDRESS",old.pcIp)),Core.port(value(e,"PC_CHECK_PORT",""+old.pcCheckPort)),Core.port(value(e,"PC_AGENT_PORT",""+old.agentPort)),old.autoStart).validated();
    }
    // Map.getOrDefault was only added to Android in API 24.
    private static String value(Map<String,String> map,String key,String fallback) { return map.containsKey(key)?map.get(key):fallback; }
}
