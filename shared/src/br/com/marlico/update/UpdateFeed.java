package br.com.marlico.update;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** Shared, dependency-free HTTPS update protocol. Never executes an unverified download. */
public final class UpdateFeed {
    public static final String SITE="https://botmy.netlify.app";
    private static final long LIMIT=512L*1024*1024;
    public interface Progress { void changed(int percent); }
    public static final class Release {
        public final String version,url,sha256,notes;
        public final int build,minimumBuild;
        public final long size;
        Release(Map<String,Object> r,String platform)throws IOException {
            if(!platform.equals(string(r,"platform")))throw new IOException("Plataforma incorreta.");
            version=string(r,"version");url=string(r,"url");sha256=string(r,"sha256");notes=string(r,"notes");
            build=integer(r,"build");minimumBuild=integer(r,"minimumBuild");size=number(r,"size");
            if(version.length()>60||!sha256.matches("[a-fA-F0-9]{64}")||build<1||minimumBuild<0||minimumBuild>build||size<1||size>LIMIT)throw new IOException("Manifesto de atualização inválido.");
            URL u=new URL(url);
            if(!"https".equals(u.getProtocol())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||!u.getPath().startsWith("/marlico77/bot-telegram/releases/download/")||!u.getPath().endsWith(platform.equals("android")?".apk":".exe"))throw new IOException("Origem do instalador inválida.");
        }
    }
    public static final class Result {
        public final Release release;
        public final boolean fresh;
        public final String raw;
        Result(Release r,boolean f,String raw){release=r;fresh=f;this.raw=raw;}
    }
    public static Result parse(String raw,String platform)throws IOException {
        Map<String,Object> root=object(new Json(raw).parse());
        if(integer(root,"schemaVersion")!=1||!platform.equals(string(root,"platform")))throw new IOException("Resposta de atualização incompatível.");
        String status=string(root,"status");
        if(!status.equals("ready")&&!status.equals("stale"))throw new IOException("Catálogo temporariamente indisponível.");
        Object r=root.get("release");return new Result(r==null?null:new Release(object(r),platform),status.equals("ready"),raw);
    }
    public static Result check(String platform,int build)throws IOException {
        HttpURLConnection c=open(new URL(SITE+"/api/v1/update?platform="+platform+"&build="+build),false);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>262144)throw new IOException("Resposta muito grande.");out.write(b,0,n);}
            return parse(new String(out.toByteArray(),"UTF-8"),platform);
        }finally{c.disconnect();}
    }
    private static HttpURLConnection open(URL url,boolean asset)throws IOException {
        for(int i=0;i<6;i++){
            String host=url.getHost();boolean allowed=asset?(host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")):host.equals("botmy.netlify.app");
            if(!allowed||!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))throw new IOException("Redirecionamento de atualização recusado.");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","MarlicoBot-Updater/1");c.setRequestProperty("Accept",asset?"application/octet-stream":"application/json");
            int code=c.getResponseCode();if(code==200)return c;
            String location=c.getHeaderField("Location");c.disconnect();
            if(code>=300&&code<=399&&location!=null){url=new URL(url,location);continue;}
            throw new IOException("Servidor de atualizações respondeu HTTP "+code+".");
        }throw new IOException("Muitos redirecionamentos.");
    }
    public static void download(Release r,File destination,Progress progress)throws Exception {
        File part=new File(destination.getPath()+".part");HttpURLConnection c=open(new URL(r.url),true);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int last=-1;
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Download cancelado.");total+=n;if(total>r.size||total>LIMIT)throw new IOException("Tamanho inesperado.");out.write(b,0,n);digest.update(b,0,n);int p=(int)(total*100/r.size);if(p!=last){last=p;progress.changed(p);}}
                out.getFD().sync();
            }
            if(total!=r.size||!hex(digest.digest()).equalsIgnoreCase(r.sha256))throw new IOException("O arquivo não passou pela verificação SHA-256. Tente baixar novamente.");
            if(destination.exists()&&!destination.delete())throw new IOException("Não foi possível substituir o download anterior.");
            if(!part.renameTo(destination))throw new IOException("Não foi possível salvar o instalador.");
        }finally{c.disconnect();if(part.exists())part.delete();}
    }
    public static void verify(Release r,File file)throws Exception {
        if(file.length()!=r.size)throw new IOException("Instalador incompleto.");MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        if(!hex(d.digest()).equalsIgnoreCase(r.sha256))throw new IOException("Instalador alterado. Baixe novamente.");
    }
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o)throws IOException {if(!(o instanceof Map))throw new IOException("Objeto JSON esperado.");return (Map<String,Object>)o;}
    private static String string(Map<String,Object> m,String k)throws IOException {Object o=m.get(k);if(!(o instanceof String))throw new IOException("Campo inválido: "+k);return (String)o;}
    private static long number(Map<String,Object> m,String k)throws IOException {Object o=m.get(k);if(!(o instanceof Long))throw new IOException("Número inválido: "+k);return (Long)o;}
    private static int integer(Map<String,Object> m,String k)throws IOException {long n=number(m,k);if(n<0||n>Integer.MAX_VALUE)throw new IOException("Número fora do limite.");return (int)n;}

    /** Strict bounded JSON reader; duplicate keys, trailing content and malformed numbers are rejected. */
    private static final class Json {
        final String s;int p;Json(String s)throws IOException{if(s.length()>262144)throw new IOException("JSON muito grande.");this.s=s;}
        Object parse()throws IOException{Object o=value(0);ws();if(p!=s.length())throw error();return o;}
        void ws(){while(p<s.length()&&" \r\n\t".indexOf(s.charAt(p))>=0)p++;}
        IOException error(){return new IOException("Resposta JSON inválida.");}
        boolean take(char c){ws();if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        Object value(int depth)throws IOException {
            if(depth>20)throw error();ws();if(p>=s.length())throw error();char c=s.charAt(p);
            if(c=='"')return str();
            if(take('{')){Map<String,Object> m=new LinkedHashMap<>();if(take('}'))return m;do{ws();if(p>=s.length()||s.charAt(p)!='"')throw error();String k=str();if(!take(':')||m.containsKey(k))throw error();m.put(k,value(depth+1));}while(take(','));if(!take('}'))throw error();return m;}
            if(take('[')){List<Object> a=new ArrayList<>();if(take(']'))return a;do{a.add(value(depth+1));}while(take(','));if(!take(']'))throw error();return a;}
            for(String literal:new String[]{"true","false","null"})if(s.startsWith(literal,p)){p+=literal.length();return literal.equals("null")?null:Boolean.valueOf(literal);}
            int start=p;while(p<s.length()&&"0123456789+-.eE".indexOf(s.charAt(p))>=0)p++;String n=s.substring(start,p);
            if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw error();
            try{if(n.indexOf('.')<0&&n.indexOf('e')<0&&n.indexOf('E')<0)return Long.valueOf(n);return Double.valueOf(n);}catch(NumberFormatException e){throw error();}
        }
        String str()throws IOException {
            p++;StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c<32)throw error();if(c=='\\'){if(p>=s.length())throw error();c=s.charAt(p++);switch(c){case '"':case '\\':case '/':break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':if(p+4>s.length())throw error();try{c=(char)Integer.parseInt(s.substring(p,p+4),16);}catch(NumberFormatException e){throw error();}p+=4;break;default:throw error();}}b.append(c);}throw error();
        }
    }
}
