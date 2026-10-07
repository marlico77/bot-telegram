package br.com.marlico.agent;

import br.com.marlico.update.UpdateFeed;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdates {
    static final int BUILD=5;
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
            status=available()?"Versão "+release.version+" disponível":result.release==null?"Nenhuma versão publicada no catálogo":"Versão 1.3.3 atualizada";
            if(available()&&notified!=release.build){notified=release.build;listener.discovered(release.version,required());}
        }catch(Exception e){status="Sem conexão com o catálogo. Tente novamente.";}finally{busy.set(false);emit();}});
    }
    void download(){
        UpdateFeed.Release r=release;if(r==null||r.build<=BUILD){check();return;}if(!busy.compareAndSet(false,true))return;
        status="Preparando atualização…";emit();worker.execute(()->{try{
            Files.createDirectories(folder);File installer=folder.resolve("MarlicoBotPC-Update.exe").toFile();
            boolean valid=false;try{UpdateFeed.verify(r,installer);valid=true;}catch(Exception ignored){}
            if(!valid)UpdateFeed.download(r,installer,p->{status="Baixando atualização: "+p+"%";emit();});
            UpdateFeed.verify(r,installer);status="Download verificado. Pronto para instalar.";listener.installerReady();
        }catch(Exception e){status="Falha no download: "+e.getMessage();}finally{busy.set(false);emit();}});
    }
    void install(){
        UpdateFeed.Release r=release;if(r==null||r.build<=BUILD||!busy.compareAndSet(false,true))return;
        worker.execute(()->{try{
            File file=folder.resolve("MarlicoBotPC-Update.exe").toFile();UpdateFeed.verify(r,file);
            new ProcessBuilder(file.getAbsolutePath()).directory(folder.toFile()).start();
            status="Instalador aberto. Autorize o Windows para concluir.";
            // The installer stops the agent only after elevation and user confirmation.
        }catch(Exception e){status="Não foi possível abrir o instalador. Tente atualizar novamente.";}finally{busy.set(false);emit();}});
    }
}
