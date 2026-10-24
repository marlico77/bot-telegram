package br.com.marlico.bot;

import android.content.Context;
import android.net.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated allowlist proxy bound only to the TV Box Tailscale address. */
final class TailscaleBridgeServer {
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptor;
    private ExecutorService requests;
    static String findAddress(Context context) {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return "";
        for(Network n:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||lp==null)continue;for(LinkAddress a:lp.getLinkAddresses()){String ip=a.getAddress().getHostAddress();if(Core.tailnetIpv4(ip))return ip;}}
        return "";
    }
    synchronized boolean start(Context context) {
        if(running)return true;stop();String ip=findAddress(context);if(ip.isEmpty())return false;
        try { server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(InetAddress.getByName(ip),8766),8);requests=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),r->{Thread t=new Thread(r,"MarlicoBot-bridge-request");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());running=true;acceptor=new Thread(()->accept(context.getApplicationContext()),"MarlicoBot-tailnet-bridge");acceptor.start();return true; }
        catch(Exception ignored){stop();return false;}
    }
    synchronized void stop(){running=false;if(server!=null)try{server.close();}catch(Exception ignored){}server=null;if(acceptor!=null)acceptor.interrupt();acceptor=null;if(requests!=null)requests.shutdownNow();requests=null;}
    private void accept(Context context){while(running){try{Socket s=server.accept();s.setSoTimeout(40000);try{requests.execute(()->handle(context,s));}catch(RejectedExecutionException full){s.close();}}catch(Exception e){if(running)continue;}}}
    private static void handle(Context context,Socket socket){try(Socket s=socket;InputStream in=s.getInputStream();OutputStream out=s.getOutputStream()){
        String line=readLine(in,3000);if(line==null||line.length()>3000){reply(out,400,"text/plain","",new byte[0]);return;}
        String[] first=line.split(" ");if(first.length!=3||!"HTTP/1.1".equals(first[2])){reply(out,400,"text/plain","",new byte[0]);return;}
        String auth="",session="",lengthText="0";int total=0;while(true){line=readLine(in,3000);if(line==null){reply(out,400,"text/plain","",new byte[0]);return;}if(line.isEmpty())break;total+=line.length();if(total>8192){reply(out,431,"text/plain","",new byte[0]);return;}int k=line.indexOf(':');if(k<1)continue;String name=line.substring(0,k).trim(),value=line.substring(k+1).trim();if(name.equalsIgnoreCase("Authorization"))auth=value;else if(name.equalsIgnoreCase("X-View-Session"))session=value;else if(name.equalsIgnoreCase("Content-Length"))lengthText=value;}
        Config cfg=Config.load(context);byte[] expected=("Bearer "+cfg.agentToken).getBytes(StandardCharsets.UTF_8),provided=auth.getBytes(StandardCharsets.UTF_8);if(cfg.agentToken.length()<40||!MessageDigest.isEqual(expected,provided)||!Core.tailnetIpv4(socket.getInetAddress().getHostAddress())){reply(out,403,"text/plain","",new byte[0]);return;}
        int size;try{size=Integer.parseInt(lengthText);}catch(Exception e){size=-1;}if(size<0||size>16384){reply(out,413,"text/plain","",new byte[0]);return;}byte[] body=new byte[size];int off=0;while(off<size){int n=in.read(body,off,size-off);if(n<0)throw new EOFException();off+=n;}
        if("POST".equals(first[0])&&"/bridge/wake".equals(first[1])&&size==0){BotService.WakeResult wake=BotService.wakeLocal(context,cfg,"celular Tailscale");byte[] result=new org.json.JSONObject().put("message",wake.message).put("sent",wake.packetSent).toString().getBytes(StandardCharsets.UTF_8);reply(out,200,"application/json; charset=utf-8","",result);return;}
        AgentApi.BridgeResponse response=AgentApi.bridgeRequest(cfg,first[0],first[1],new String(body,StandardCharsets.UTF_8),session);reply(out,response.status,response.type,response.consent,response.body);
    }catch(Exception e){try{reply(socket.getOutputStream(),502,"application/json; charset=utf-8","",("{\"error\":"+org.json.JSONObject.quote(e.getMessage()==null?"bridge unavailable":e.getMessage())+"}").getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}}
    private static String readLine(InputStream in,int limit)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c,prev=-1;while((c=in.read())!=-1){if(prev=='\r'&&c=='\n'){byte[] bytes=b.toByteArray();return new String(bytes,0,Math.max(0,bytes.length-1),StandardCharsets.US_ASCII);}b.write(c);prev=c;if(b.size()>limit)throw new IOException("HTTP header too large");}return null;}
    private static void reply(OutputStream out,int status,String type,String consent,byte[] body)throws IOException{String reason=status==200?"OK":status==400?"Bad Request":status==403?"Forbidden":status==413?"Payload Too Large":status==431?"Request Header Fields Too Large":"Bad Gateway";String h="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n"+(consent.isEmpty()?"":"X-Capture-Consent: "+consent+"\r\n")+"\r\n";out.write(h.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
}
