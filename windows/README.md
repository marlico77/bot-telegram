# MarlicoBot PC para Windows 11

Use `../dist/MarlicoBotPC-Setup.exe` para instalar o agente para o computador. O instalador solicita confirmação no UAC, copia o aplicativo para **Arquivos de Programas**, cria atalhos compartilhados e registra-o em **Aplicativos instalados**. O agente abre na sessão do usuário sem privilégios administrativos e inicia com o Windows, minimizado na área de notificação. Fechar a janela a oculta; use o menu do ícone para abrir ou sair. Não precisa instalar Java ou Python. O arquivo de pareamento e as preferências ficam em `%APPDATA%\MarlicoBot`, fora da pasta de instalação.

O instalador ainda não tem assinatura Authenticode de um publicador verificado. Por isso, o UAC de administrador não elimina eventuais avisos do Microsoft Defender SmartScreen. Não desative a proteção do Windows para instalar. Para distribuir sem o aviso de publicador desconhecido, o executável precisa ser assinado com um certificado de assinatura de código confiável e ganhar reputação junto ao SmartScreen.

## Conectar à TV Box

1. No Windows, abra o agente pelo ícone na área de notificação e confirme que ele mostra **Agente ativo** e o IP local.
2. Use **Permitir conexão da rede local**. O UAC solicita autorização para criar uma regra do Firewall do Windows, TCP 8765, limitada à sub-rede local.
3. No Android/TV Box, abra **Monitorar PC → Parear pela rede local**. Confira o IP do Windows preenchido no app.
4. Aprove na tela do Windows o pedido iniciado pela TV Box/celular. A chave fica guardada no Android; não é preciso exportar ou importar um arquivo de pareamento.
5. Deixe o programa aberto. Consultas do Telegram são encaminhadas pela TV Box; o painel Android requer que o celular esteja na mesma LAN do computador.

O agente guarda uma chave aleatória de acesso em `%APPDATA%\MarlicoBot\agent.properties`. O pareamento só entrega essa chave depois de você aprovar o pedido localmente. Se suspeitar que ela foi exposta, remova esse arquivo e reinicie o agente; será gerada uma chave nova e os dispositivos precisarão ser pareados novamente.

## Dados e privacidade

O painel retorna CPU, memória, armazenamento, tempo ligado, processos ativos e título da janela em primeiro plano. A aba **Hardware** reúne versão/edição/build do Windows, fabricante/modelo do PC, processador, núcleos e threads, módulos de RAM, placas de vídeo, placa-mãe, BIOS, discos, volumes e adaptadores de rede. A aba **Programas instalados** lista nome, versão e publicador encontrados nos registros padrão de instalação do Windows. O inventário também pode ser consultado no aplicativo Android e no menu do Telegram. Números de série não são coletados. Não implementa controle remoto, execução de programas ou comandos.

Cada pedido de foto única traz o agente para a frente e pede confirmação no Windows. Para visualização ao vivo, toque em **Acesso remoto** no Android: o Windows pedirá autorização para aquela sessão. Se a pessoa no PC quiser, pode marcar **Permitir visualização contínua quando eu iniciar no app** na aba de privacidade para não confirmar cada sessão. Quando a tela está sendo compartilhada, o agente mostra **TELA COMPARTILHADA AO VIVO** e a bandeja exibe um aviso; **Parar transmissão agora** encerra o compartilhamento localmente. O app recebe quadros JPEG de até 1280×720, pode trocar entre monitores e ajustar o zoom. A permissão não habilita teclado, mouse ou controle remoto. Bloqueio da sessão, desktop seguro ou políticas do sistema podem impedir as imagens.

A API usa HTTP com token aleatório e só atende clientes de endereço privado/loopback. A autorização do firewall é limitada à sub-rede local; não encaminhe a porta 8765 no roteador. HTTP não cifra dados dentro da LAN, então use apenas sua rede confiável. O token do bot Telegram não faz parte deste aplicativo nem do arquivo de pareamento.

## Compilar

No repositório, com Microsoft OpenJDK 17 na pasta `.tools/jdk/`:

```powershell
.\windows\build.ps1
```

O script compila o agente, confere o inventário real do Windows, cria um runtime próprio, gera um ícone Windows a partir da logo e produz `dist/MarlicoBotPC-Setup.exe`. O instalador é criado com o IExpress incluído no Windows. Para gerar o pacote sem executar as verificações locais do agente, use `./windows/build.ps1 -SkipChecks`.
