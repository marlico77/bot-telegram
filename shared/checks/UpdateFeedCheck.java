import br.com.marlico.update.UpdateFeed;
import java.io.*;
import java.nio.file.*;

/** Run with "live" to also check the public API and download an official APK without installing it. */
public final class UpdateFeedCheck {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static String fixture(){return "{\"schemaVersion\":1,\"status\":\"ready\",\"platform\":\"android\",\"release\":{\"platform\":\"android\",\"version\":\"1.3.2\",\"build\":14,\"minimumBuild\":13,\"size\":3,\"sha256\":\""+HASH+"\",\"notes\":\"Olá\\nAtualização\",\"url\":\"https://github.com/marlico77/bot-telegram/releases/download/1.3.2/app.apk\"}}";}
    private static void reject(String json){boolean failed=false;try{UpdateFeed.parse(json,"android");}catch(IOException e){failed=true;}check(failed,"Invalid manifest accepted");}
    public static void main(String[] args)throws Exception {
        UpdateFeed.Result result=UpdateFeed.parse(fixture(),"android");check(result.fresh,"ready");check(result.release.build==14,"build");check(result.release.minimumBuild==13,"minimum");check(result.release.notes.equals("Olá\nAtualização"),"Unicode and escaped text");
        check(!UpdateFeed.parse(fixture().replace("ready","stale"),"android").fresh,"stale");
        check(UpdateFeed.parse("{\"schemaVersion\":1,\"status\":\"ready\",\"platform\":\"android\",\"release\":null}","android").release==null,"empty catalog");
        reject(fixture()+"x");reject(fixture().replace("\"build\":14","\"build\":14,\"build\":15"));reject(fixture().replace("\"build\":14","\"build\":1.4"));reject(fixture().replace("\"build\":14","\"build\":014"));reject(fixture().replace("\"minimumBuild\":13","\"minimumBuild\":15"));
        reject(fixture().replace("github.com/","github.com.evil.test/"));reject(fixture().replace("https:","http:"));reject(fixture().replace("marlico77/bot-telegram","another/repo"));reject(fixture().replace("app.apk","app.exe"));reject(fixture().replace(HASH,"missing"));reject(fixture().replace("\"size\":3","\"size\":0"));reject(fixture().replace("\"size\":3","\"size\":9999999999"));reject(fixture().replace("\"schemaVersion\":1","\"schemaVersion\":2"));reject(fixture().replace("android","windows"));
        Path file=Files.createTempFile("marlico-updater-check-",".bin");try{Files.write(file,new byte[]{97,98,99});UpdateFeed.verify(result.release,file.toFile());checks++;Files.write(file,new byte[]{97,98,100});boolean failed=false;try{UpdateFeed.verify(result.release,file.toFile());}catch(IOException e){failed=true;}check(failed,"corruption rejected");}finally{Files.deleteIfExists(file);}
        if(args.length>0&&args[0].equals("live")){
            for(String platform:new String[]{"android","windows"}){UpdateFeed.Result live=UpdateFeed.check(platform,0);check(live.fresh&&live.release!=null,"Live API "+platform);System.out.println("API "+platform+": "+live.release.version+" build "+live.release.build);if(platform.equals("android")){Path apk=Files.createTempFile("marlico-official-check-",".apk");try{UpdateFeed.download(live.release,apk.toFile(),p->{});UpdateFeed.verify(live.release,apk.toFile());checks++;System.out.println("APK oficial baixado e SHA-256 conferido; nenhuma instalação executada.");}finally{Files.deleteIfExists(apk);}}}
        }
        System.out.println("OK: "+checks+" verificações de atualização.");
    }
}
