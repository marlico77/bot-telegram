package br.com.marlico.bot;

import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

public final class TelegramApi {
    private final String token;
    private volatile HttpsURLConnection active;
    private volatile boolean cancelled;
    public TelegramApi(String token) { this.token=token; }
    public void cancel() { cancelled=true; HttpsURLConnection c=active; if(c!=null)c.disconnect(); }
    public static class ApiException extends IOException {
        public final int code, retry;
        public final boolean unchanged;
        ApiException(int c,int r) { this(c,r,false); }
        ApiException(int c,int r,boolean same) { super("Telegram HTTP "+c);code=c;retry=r;unchanged=same; }
    }
    public JSONObject call(String method,JSONObject body) throws Exception {
        if(cancelled) throw new InterruptedIOException();
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.telegram.org/bot"+token+"/"+method).openConnection();
        active=c;
        try {
            if(cancelled) throw new InterruptedIOException();
            c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(method.equals("getUpdates")?40000:20000);
            c.setInstanceFollowRedirects(false); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] request=body.toString().getBytes("UTF-8"); c.setFixedLengthStreamingMode(request.length);
            try(OutputStream out=c.getOutputStream()){out.write(request);}
            int status=c.getResponseCode(); InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            if(stream==null) throw new ApiException(status,0);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream in=stream){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(buffer.size()+n>2*1024*1024)throw new IOException("Resposta muito grande.");buffer.write(bytes,0,n);}}
            JSONObject result=new JSONObject(buffer.toString("UTF-8"));
            if(status>=400||!result.optBoolean("ok")) {
                JSONObject params=result.optJSONObject("parameters");
                throw new ApiException(result.optInt("error_code",status),params==null?0:params.optInt("retry_after",0),result.optString("description","").contains("message is not modified"));
            }
            return result;
        } finally { c.disconnect(); active=null; }
    }
    public JSONObject sendPhoto(long chatId,byte[] image,String caption) throws Exception {
        if(cancelled)throw new InterruptedIOException();String boundary="----Marlico"+Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        part(body,boundary,"chat_id",Long.toString(chatId));part(body,boundary,"caption",caption);
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"pc-screen.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes("UTF-8"));body.write(image);body.write("\r\n".getBytes("UTF-8"));body.write(("--"+boundary+"--\r\n").getBytes("UTF-8"));
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.telegram.org/bot"+token+"/sendPhoto").openConnection();active=c;
        try{if(cancelled)throw new InterruptedIOException();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setDoOutput(true);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setFixedLengthStreamingMode(body.size());try(OutputStream out=c.getOutputStream()){body.writeTo(out);}int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();if(stream==null)throw new ApiException(status,0);ByteArrayOutputStream buf=new ByteArrayOutputStream();try(InputStream in=stream){byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(buf.size()+n>1024*1024)throw new IOException("Resposta muito grande.");buf.write(chunk,0,n);}}JSONObject result=new JSONObject(buf.toString("UTF-8"));if(status>=400||!result.optBoolean("ok")){JSONObject p=result.optJSONObject("parameters");throw new ApiException(result.optInt("error_code",status),p==null?0:p.optInt("retry_after",0),result.optString("description","").contains("message is not modified"));}return result;
        }finally{c.disconnect();active=null;}
    }
    private static void part(OutputStream out,String boundary,String name,String value) throws IOException {out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes("UTF-8"));}
}
