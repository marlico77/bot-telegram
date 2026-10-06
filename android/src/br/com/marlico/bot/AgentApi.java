package br.com.marlico.bot;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Read-only client for the paired Windows companion, restricted to private LAN IPv4 addresses. */
public final class AgentApi {
    private AgentApi() {}
    private static HttpURLConnection open(Config cfg,String path) throws Exception {
        if(cfg.agentToken==null||cfg.agentToken.length()<40)throw new IOException("Pareie o agente Windows antes de consultar o PC.");
        InetAddress host=InetAddress.getByName(Core.hostIpv4(cfg.pcIp));
        if(!host.isSiteLocalAddress())throw new IOException("O agente só pode ser acessado pela rede local privada.");
        HttpURLConnection c=(HttpURLConnection)new URL("http://"+host.getHostAddress()+":"+cfg.agentPort+path).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(5000);c.setReadTimeout(path.startsWith("/api/screen")||path.equals("/api/inventory")?35000:8000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Authorization","Bearer "+cfg.agentToken);c.setRequestProperty("Cache-Control","no-store");return c;
    }
    private static byte[] read(HttpURLConnection c,int limit) throws Exception {
        int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();if(in==null)throw new IOException("O agente Windows não respondeu.");
        try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=stream.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Resposta do agente excedeu o limite.");out.write(buf,0,n);}if(status>=400)throw new AgentException(status,"Agente Windows: "+out.toString("UTF-8"));return out.toByteArray();}
    }
    public static JSONObject get(Config cfg,String path) throws Exception {HttpURLConnection c=open(cfg,path);try{return new JSONObject(new String(read(c,1024*1024),"UTF-8"));}finally{c.disconnect();}}
    public static JSONObject pair(String ip,int port) throws Exception {
        InetAddress host=InetAddress.getByName(Core.hostIpv4(ip));if(!host.isSiteLocalAddress())throw new IOException("Informe o IP privado do PC na mesma rede da TV Box.");
        HttpURLConnection c=(HttpURLConnection)new URL("http://"+host.getHostAddress()+":"+Core.port(""+port)+"/api/pair").openConnection();
        try{c.setRequestMethod("POST");c.setConnectTimeout(5000);c.setReadTimeout(95000);c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write("MarlicoBot Android pairing request".getBytes(StandardCharsets.UTF_8));}return new JSONObject(new String(read(c,8192),"UTF-8"));}finally{c.disconnect();}
    }
    public static boolean isOnline(Config cfg) {
        HttpURLConnection c=null;try{if(cfg.agentToken==null||cfg.agentToken.length()<40)return false;InetAddress host=InetAddress.getByName(Core.hostIpv4(cfg.pcIp));if(!host.isSiteLocalAddress())return false;c=(HttpURLConnection)new URL("http://"+host.getHostAddress()+":"+Core.port(""+cfg.agentPort)+"/api/status").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(1200);c.setReadTimeout(1200);c.setUseCaches(false);c.setRequestProperty("Authorization","Bearer "+cfg.agentToken);return c.getResponseCode()==200;}catch(Exception ignored){return false;}finally{if(c!=null)c.disconnect();}
    }
    public static final class AgentException extends IOException { public final int status; AgentException(int status,String message){super(message);this.status=status;} }
    public static JSONObject post(Config cfg,String path,String body)throws Exception {
        HttpURLConnection c=open(cfg,path);try{c.setRequestMethod("POST");c.setDoOutput(true);c.setReadTimeout(12000);c.setRequestProperty("Content-Type","text/plain; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}return new JSONObject(new String(read(c,16384),"UTF-8"));}finally{c.disconnect();}
    }
    public static byte[] screen(Config cfg)throws Exception{return screen(cfg,0);}
    public static byte[] screen(Config cfg,int monitor)throws Exception {
        HttpURLConnection c=open(cfg,"/api/screen?monitor="+Math.max(0,monitor));c.setReadTimeout(110000);try{return image(c,"one-shot");}finally{c.disconnect();}
    }
    private static byte[] image(HttpURLConnection c,String consent)throws Exception {
        byte[] data=read(c,6*1024*1024);String type=c.getContentType();
        if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/jpeg")||data.length<4||(data[0]&255)!=255||(data[1]&255)!=216)throw new IOException("O agente não retornou uma imagem JPEG válida.");
        if(!consent.equals(c.getHeaderField("X-Capture-Consent")))throw new IOException("A imagem não veio de uma sessão autorizada no Windows.");return data;
    }
    private static JSONObject livePost(Config cfg,int monitor,String action,String session)throws Exception {
        HttpURLConnection c=open(cfg,"/api/screen/live?monitor="+Math.max(0,monitor));try{
            c.setRequestMethod("POST");c.setReadTimeout("start".equals(action)?110000:8000);c.setDoOutput(true);
            if(session!=null)c.setRequestProperty("X-View-Session",session);
            try(OutputStream out=c.getOutputStream()){out.write(action.getBytes(StandardCharsets.UTF_8));}
            return new JSONObject(new String(read(c,8192),"UTF-8"));
        }finally{c.disconnect();}
    }
    public static String liveStart(Config cfg,int monitor)throws Exception{return livePost(cfg,monitor,"start",null).getString("session");}
    public static void liveStop(Config cfg,int monitor,String session)throws Exception{livePost(cfg,monitor,"stop",session);}
    public static byte[] liveFrame(Config cfg,int monitor,String session)throws Exception {
        HttpURLConnection c=open(cfg,"/api/screen/live?monitor="+Math.max(0,monitor));c.setReadTimeout(12000);c.setRequestProperty("X-View-Session",session);try{return image(c,"continuous");}finally{c.disconnect();}
    }    public static String bytes(long value){if(value<0)return "indisponível";String[] u={"B","KB","MB","GB","TB"};double n=value;int i=0;while(n>=1024&&i<u.length-1){n/=1024;i++;}return i==0?value+" "+u[i]:String.format(java.util.Locale.US,"%.1f %s",n,u[i]);}
    public static String duration(long seconds){if(seconds<0)return "indisponível";long d=seconds/86400,h=seconds/3600%24,m=seconds/60%60;return d+"d "+h+"h "+m+"min";}
    public static String statusText(JSONObject o){double cpu=o.optDouble("cpuPercent",-1);return "🖥 "+o.optString("computer","Windows PC")+"\n⚙️ CPU: "+(cpu<0?"indisponível":String.format(java.util.Locale.US,"%.0f%%",cpu))+"\n🧠 Memória: "+bytes(o.optLong("memoryUsedBytes",-1))+" / "+bytes(o.optLong("memoryTotalBytes",-1))+"\n💽 Armazenamento: "+bytes(o.optLong("diskUsedBytes",-1))+" / "+bytes(o.optLong("diskTotalBytes",-1))+"\n⏱ Ligado há: "+duration(o.optLong("uptimeSeconds",-1))+"\n🪟 Janela ativa: "+o.optString("activeWindow","indisponível")+"\n\nAtualizado: "+o.optString("capturedAt","");}
}

