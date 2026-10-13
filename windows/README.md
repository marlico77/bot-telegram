# MarlicoBot PC para Windows 11

Use `../dist/MarlicoBotPC-Setup.msi` para instalar o agente Windows no perfil do usuário. O MSI registra o programa em **Aplicativos instalados** e cria atalhos no menu Iniciar e na área de trabalho. Abra o aplicativo uma vez para ativar a inicialização com o Windows; ele ficará na área de notificação. Fechar a janela a oculta; use o menu do ícone para abrir ou sair. Não precisa instalar Java ou Python. O arquivo de pareamento e as preferências ficam em `%APPDATA%\MarlicoBot`, fora da pasta de instalação.

Se você instalou uma versão antiga por `MarlicoBotPC-Setup.exe`, desinstale essa versão em **Aplicativos instalados** antes de instalar o MSI. O desinstalador antigo preserva `%APPDATA%\MarlicoBot`, incluindo pareamento e preferências. A versão 1.3.3 ainda procura um EXE no catálogo de atualizações e não consegue migrar sozinha para MSI; esta troca inicial exige a instalação manual.

O instalador ainda não tem assinatura Authenticode de um publicador verificado, então o Windows pode exibir avisos de reputação. Não desative a proteção do Windows para instalar. Para distribuir sem o aviso de publicador desconhecido, o MSI precisa ser assinado com um certificado de assinatura de código confiável e ganhar reputação junto ao SmartScreen.

## Conectar à TV Box

1. No Windows, abra o agente pelo ícone na área de notificação e confirme que ele mostra **Agente ativo** e o IP local.
2. Use **Permitir rede privada**. O UAC solicita autorização para criar ou atualizar uma regra do Firewall do Windows, TCP 8765, limitada à sub-rede local e aos endereços do Tailscale.
3. No Android/TV Box, abra **Monitorar PC → Parear pela rede local**. Confira o IP do Windows preenchido no app.
4. Aprove na tela do Windows o pedido iniciado pela TV Box/celular. A chave fica guardada no Android; não é preciso exportar ou importar um arquivo de pareamento.
5. Deixe o programa aberto. Consultas do Telegram são encaminhadas pela TV Box; o painel Android usa a mesma LAN ou um túnel Tailscale ativo nos dois aparelhos.

Para usar o painel e a tela ao vivo fora de casa, instale [Tailscale para Windows](https://tailscale.com/docs/install/windows) e [Tailscale no Android](https://tailscale.com/docs/install/android), conecte os aparelhos à mesma rede privada e informe no app o IP `100.x.y.z` do PC. Faça o pareamento inicial pela LAN antes de trocar o IP. Se instalou o Tailscale depois de abrir o agente, reinicie o agente para que o endereço apareça na lista. Clique novamente em **Permitir rede privada** para atualizar uma regra antiga de firewall. Não encaminhe a porta 8765 no roteador.

O agente guarda uma chave aleatória de acesso em `%APPDATA%\MarlicoBot\agent.properties`. O pareamento só entrega essa chave depois de você aprovar o pedido localmente. Se suspeitar que ela foi exposta, remova esse arquivo e reinicie o agente; será gerada uma chave nova e os dispositivos precisarão ser pareados novamente.

## Dados e privacidade

O painel retorna CPU, memória, armazenamento, tempo ligado, processos ativos e título da janela em primeiro plano. A aba **Hardware** reúne versão/edição/build do Windows, fabricante/modelo do PC, processador, núcleos e threads, módulos de RAM, placas de vídeo, placa-mãe, BIOS, discos, volumes e adaptadores de rede. A aba **Programas instalados** lista nome, versão e publicador encontrados nos registros padrão de instalação do Windows. O inventário também pode ser consultado no aplicativo Android e no menu do Telegram. Números de série não são coletados. Não controla mouse ou teclado. A função de energia agenda o desligamento forçado após confirmação no aparelho pareado.

Por padrão, cada foto única e cada sessão ao vivo traz o agente para a frente e pede confirmação no Windows. A pessoa no PC pode ativar **Permitir acesso à tela sem supervisão** na aba **Privacidade e conexão** ou no pedido de acesso. Após a confirmação local inicial, futuros pedidos autenticados pelo app ou pelo Telegram não repetem a autorização; a preferência pode ser revogada na mesma aba. Quando a tela está sendo compartilhada, o agente mostra **TELA COMPARTILHADA AO VIVO** e a bandeja exibe um aviso; **Parar transmissão agora** encerra o compartilhamento localmente. O app recebe quadros JPEG de até 960×540 para reduzir o atraso, pode trocar entre monitores e ajustar o zoom. Fotos únicas mantêm até 1280×720. A permissão não habilita teclado, mouse ou controle remoto. Bloqueio da sessão, desktop seguro ou políticas do sistema podem impedir as imagens.

A API usa HTTP com token aleatório e só atende clientes da LAN ou conexões recebidas na interface Tailscale com endereços `100.64.0.0/10`. O firewall aceita apenas a sub-rede local e esse intervalo; não encaminhe a porta 8765 no roteador. HTTP não cifra dados dentro da LAN, então use apenas sua rede confiável. O Tailscale cifra o tráfego entre os aparelhos fora da LAN. O token do bot Telegram não faz parte deste aplicativo nem do arquivo de pareamento.

## Compilar

No repositório, com Microsoft OpenJDK 17 na pasta `.tools/jdk/`:

```powershell
.\windows\build.ps1
```

O script compila o agente, confere o inventário real do Windows, cria um runtime próprio, gera um ícone Windows e produz `dist/MarlicoBotPC-Setup.msi` com `jpackage`. Instale [WiX Toolset 3.14.1](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm) e deixe `candle.exe` e `light.exe` no PATH, em `.tools/wix` ou no diretório informado por `WIX_HOME`. O WiX é necessário apenas para compilar, não para instalar. Para gerar o pacote sem executar as verificações locais do agente, use `./windows/build.ps1 -SkipChecks`.

## Versão 1.3.0

Tema rosa, logo transparente e sessão de tela com identificador próprio. O Android aguarda a autorização por até 110 segundos; a confirmação no Windows expira em 90 segundos. O estado ao vivo aparece após o primeiro quadro. Falhas de captura ficam em capture-errors.log, na pasta AppData/MarlicoBot.

Desligamento forçado: confirmação válida por 60 segundos, seguida de 30 segundos para cancelar pelo Android, Telegram ou agente. Programas serão fechados com perda de alterações não salvas. O Windows pode recusar por política/permissão; o agente informa o erro. Não há teste de desligamento automático durante o build.

