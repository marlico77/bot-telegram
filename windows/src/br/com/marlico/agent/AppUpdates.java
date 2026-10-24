package br.com.marlico.agent;

import br.com.marlico.update.UpdateFeed;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdates {
    static final int BUILD=9;
    interface Listener {void changed(String status,boolean available,boolean required,boolean busy);void discovered(String version,boolean required);void installerReady();}
    private final Path folder;
    private final Listener listener;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"official-updates");t.setDaemon(true);return t;});
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile UpdateFeed.Release release;
    private volatile int minimum;
    private volatile String status="Consulta automática a cada 6 horas";
    private int notified;
    AppUpdates(Path home,Listener listener){folder=home.resolve("updates");this.listener=listener;}
    boolean required(){return minimum>BUILD;}
    boolean available(){return release!=null&&release.build>BUILD;}
    boolean busy(){return busy.get();}
    String status(){return status;}
    private File installerFile(UpdateFeed.Release r){return folder.resolve(r.url.endsWith(".msi")?"MarlicoBotPC-Update.msi":"MarlicoBotPC-Update.exe").toFile();}
    private void emit(){listener.changed(status,available(),required(),busy.get());}
    void start(){
        worker.execute(()->{try{Files.createDirectories(folder);Path cache=folder.resolve("manifest.json");if(Files.exists(cache)&&Files.size(cache)<=262144){UpdateFeed.Result r=UpdateFeed.parse(Files.readString(cache),"windows");release=r.release;if(r.fresh&&release!=null)minimum=release.minimumBuild;}}catch(Exception ignored){}emit();});
        worker.scheduleWithFixedDelay(this::check,2,6*3600,TimeUnit.SECONDS);
    }
    void check(){
        if(!busy.compareAndSet(false,true))return;status="Consultando versões oficiais…";emit();
        worker.execute(()->{try{
            UpdateFeed.Result result=UpdateFeed.check("windows",BUILD);if(!result.fresh)throw new IOException("Catálogo temporariamente desatualizado.");
            // Keep a known required policy if the server temporarily returns an empty catalog.
            if(result.release!=null){Path part=folder.resolve("manifest.json.part");Files.writeString(part,result.raw,StandardCharsets.UTF_8);Files.move(part,folder.resolve("manifest.json"),StandardCopyOption.REPLACE_EXISTING);release=result.release;minimum=release.minimumBuild;}
            status=available()?"Versão "+release.version+" disponível":result.release==null?"Nenhuma versão publicada no catálogo":"Versão 1.3.7 atualizada";
            if(available()&&notified!=release.build){notified=release.build;listener.discovered(release.version,required());}
        }catch(Exception e){status="Sem conexão com o catálogo. Tente novamente.";}finally{busy.set(false);emit();}});
    }
    void download(){
        UpdateFeed.Release r=release;if(r==null||r.build<=BUILD){check();return;}if(!busy.compareAndSet(false,true))return;
        status="Preparando atualização…";emit();worker.execute(()->{try{
            Files.createDirectories(folder);File installer=installerFile(r);
            boolean valid=false;try{UpdateFeed.verify(r,installer);valid=true;}catch(Exception ignored){}
            if(!valid)UpdateFeed.download(r,installer,p->{status="Baixando atualização: "+p+"%";emit();});
            UpdateFeed.verify(r,installer);status="Download verificado. Pronto para instalar.";listener.installerReady();
        }catch(Exception e){status="Falha no download: "+e.getMessage();}finally{busy.set(false);emit();}});
    }
    void install(){
        UpdateFeed.Release r=release;if(r==null||r.build<=BUILD||!busy.compareAndSet(false,true))return;
        worker.execute(()->{try{
            File file=installerFile(r);UpdateFeed.verify(r,file);
            launchInstaller(file);
            status="Atualização MSI iniciada. O agente será reiniciado.";
        }catch(Exception e){status="Não foi possível abrir o instalador. Tente atualizar novamente.";}finally{busy.set(false);emit();}});
    }
    /** Starts a fresh check and, if newer, installs the verified per-user MSI without waiting on HTTP. */
    boolean updateNow(){
        if(!busy.compareAndSet(false,true))return false;
        status="Consultando atualização oficial…";emit();
        worker.execute(()->{try{
            UpdateFeed.Result result=UpdateFeed.check("windows",BUILD);
            if(!result.fresh)throw new IOException("Catálogo temporariamente desatualizado.");
            if(result.release==null||result.release.build<=BUILD){status="Agente Windows já está atualizado.";return;}
            Path part=folder.resolve("manifest.json.part");Files.createDirectories(folder);
            Files.writeString(part,result.raw,StandardCharsets.UTF_8);
            Files.move(part,folder.resolve("manifest.json"),StandardCopyOption.REPLACE_EXISTING);
            release=result.release;minimum=release.minimumBuild;
            File installer=installerFile(release);
            boolean valid=false;try{UpdateFeed.verify(release,installer);valid=true;}catch(Exception ignored){}
            if(!valid)UpdateFeed.download(release,installer,p->{status="Baixando atualização: "+p+"%";emit();});
            UpdateFeed.verify(release,installer);
            launchInstaller(installer);
            status="Atualização MSI iniciada. O agente será reiniciado.";
        }catch(Exception e){status="Falha na atualização: "+e.getMessage();}finally{busy.set(false);emit();}});
        return true;
    }
    private void launchInstaller(File installer)throws Exception{
        if(!installer.getName().endsWith(".msi")){
            new ProcessBuilder(installer.getAbsolutePath()).directory(folder.toFile()).start();
            return;
        }
        long pid=ProcessHandle.current().pid();
        String original=ProcessHandle.current().info().command().orElse("");
        String msi=installer.getAbsolutePath().replace("'","''");
        String oldExe=original.replace("'","''");
        String script="$ErrorActionPreference='Stop';"+
            "$msi='"+msi+"';$old='"+oldExe+"';"+
            "Start-Sleep -Seconds 2;Wait-Process -Id "+pid+" -Timeout 90 -ErrorAction SilentlyContinue;"+
            "$q=[char]34;$installArgs=@('/i',(\"$q$msi$q\"),'/qn','/norestart','ALLUSERS=2','MSIINSTALLPERUSER=1');"+
            "$p=Start-Process -FilePath 'msiexec.exe' -ArgumentList $installArgs -WindowStyle Hidden -PassThru -Wait;"+
            "$new=Join-Path $env:LOCALAPPDATA 'MarlicoBotPC\\MarlicoBotPC.exe';"+
            "if($p.ExitCode -in @(0,3010) -and (Test-Path -LiteralPath $new)){Start-Process -FilePath $new -WindowStyle Hidden}"+
            "elseif(Test-Path -LiteralPath $old){Start-Process -FilePath $old -WindowStyle Hidden};"+
            "Set-Content -LiteralPath (Join-Path (Split-Path $msi -Parent) 'install-result.txt') -Value $p.ExitCode";
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        new ProcessBuilder("powershell.exe","-NoLogo","-NoProfile","-WindowStyle","Hidden","-EncodedCommand",encoded)
            .directory(folder.toFile()).start();
        worker.schedule(()->System.exit(0),1500,TimeUnit.MILLISECONDS);
    }
}
