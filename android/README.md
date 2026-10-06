# MarlicoBot para Android TV

Aplicativo Android para TV Box e celular. Recebe comandos do Telegram na TV Box, envia Wake-on-LAN e consulta o agente de leitura do Windows 11. Compatível a partir do Android 6.0 (API 23). A interface funciona com controle direcional, teclado ou toque. O bot Android é Java nativo e não usa Termux.

## Instalação

1. Copie `dist/MarlicoBot-1.2.3.apk` para o pendrive. Instale por cima da versão anterior sem desinstalá-la, para manter as configurações.
2. Na TV Box, abra o APK com o gerenciador de arquivos e permita a instalação por essa fonte se o Android solicitar.
3. Se ainda não configurou o bot, importe o `.env` já usado. Para parear com o PC, abra **Monitorar PC → Parear pela rede local** e aprove o pedido na tela do Windows.
4. Pare o bot antigo no Termux com **Ctrl+C** e encerre qualquer cópia no PC.
5. Toque em **Conectar bot** e permita as notificações, se solicitado.
6. Quando aparecer **Bot conectado**, envie uma mensagem ao bot e toque em **Menu**. As opções permitem ligar o PC, consultar o status, listar processos e pedir uma captura da tela.

O IP privado do PC precisa estar correto nas configurações do app. A chave do agente é recebida pela rede somente após aprovação na tela do Windows e armazenada com Android Keystore. O agente só é consultado por um IP privado; no celular, o painel de monitoramento funciona quando ele está na mesma rede do PC. Para evitar conflito de long polling, conecte a conta do bot apenas na TV Box; no celular, pareie pela rede e use o painel sem tocar em **Conectar bot**.

## Uso e comportamento

- Toda mensagem privada de um usuário autorizado recebe uma saudação conforme o horário de Brasília, seguida de **Menu**. Quando o agente autenticado responde, o Telegram mostra **Computador ligado** e remove **Ligar PC**; caso contrário, o menu oferece Wake-on-LAN. O menu também tem **Métricas**, **Programas abertos** e **Print da tela**. Os comandos `/metricas`, `/programas`, `/print` e `/ligar` funcionam pelo Telegram de fora da rede, desde que a TV Box esteja ligada, na rede do PC e com o bot conectado. Grupos e usuários não autorizados são ignorados.
- No Telegram e no botão local, o app tenta confirmar por até 45 segundos que o PC responde ao ping ou à porta TCP configurada. Só informa que está ligado quando recebe uma resposta. Se nenhum teste responder, informa que o sinal foi enviado, mas o estado não pôde ser confirmado; firewall e regras de rede podem bloquear esses testes.
- O botão local **Ligar PC** envia o sinal e verifica o IP configurado. Há uma espera de 10 segundos entre envios para evitar cliques repetidos.
- Fechar a tela do app mantém o serviço ativo. **Pausar** encerra o serviço e impede a retomada automática até uma nova conexão.
- **Iniciar com a TV Box** retoma a conexão após reiniciar, se o bot estava habilitado antes. O app precisa ter sido aberto e conectado ao menos uma vez.
- A notificação persistente permite retornar ao app ou pausar. Um bloqueio parcial de suspensão mantém o processamento enquanto o serviço estiver ativo.
- Internet indisponível: novas tentativas com espera progressiva, até 60 segundos. HTTP 429 respeita `retry_after`; 401/404 pausa com orientação sobre o token; 409 aguarda a outra sessão encerrar e para após três conflitos. Webhooks existentes são detectados e informados, sem apagá-los.
- Comandos enfileirados antes de iniciar o serviço são descartados. O relógio da Box deve estar correto para a saudação. O fuso utilizado é `America/Sao_Paulo`, independentemente do fuso selecionado no Android. Botões de sessões anteriores ou de antes de uma interrupção expiram e apresentam um novo menu.
- O envio da resposta ao Telegram não repete um pacote WOL já enviado quando a resposta falha.
- O histórico contém até 30 eventos locais e nunca registra o token.

## Monitoramento e agente Windows

Instale `dist/MarlicoBotPC-Setup.exe` no Windows 11. O agente consulta CPU, memória, armazenamento, tempo ligado, processos, título da janela ativa, versão do Windows e inventário de hardware e programas. Não tem comandos para mover o mouse, digitar, executar programas, ler arquivos ou alterar configurações.

No Windows, escolha **Permitir conexão da rede local** e aprove o pedido do UAC para liberar apenas a sub-rede local na porta TCP 8765. No app, inicie **Parear pela rede local**; aceite no Windows o pedido que você iniciou. O agente precisa continuar aberto.

Para ver a tela ao vivo, no painel Android abra **Monitorar PC**, escolha o monitor e toque em **Acesso remoto**. Na primeira solicitação, o Windows exibe uma confirmação para aquela sessão; marque a permissão persistente no agente se quiser permitir solicitações futuras sem confirmação. Um aviso fica visível no PC e a pessoa pode parar pelo agente ou pelo app. O app Android precisa estar na mesma rede local que o PC; fora dela, use **Print da tela** no Telegram para uma captura única com aprovação no Windows. O vídeo é atualizado em quadros, com zoom e seleção de monitor; não há controle de mouse/teclado.

Uma captura da tela principal nunca ocorre em segundo plano: cada pedido do Telegram ou do painel Android exibe uma caixa de confirmação no Windows. **Sim** envia uma imagem única reduzida; **Não** ou falta de resposta cancela. A captura não é uma transmissão contínua. Para comandos remotos via Telegram fora de casa, a TV Box deve estar ligada, pareada e conectada ao PC pela rede local. O painel do APK no celular só consulta o PC quando o celular está na mesma rede local; acesso pelo celular fora de casa não usa VPN.

O agente usa HTTP com autenticação aleatória apenas na rede local, protegido também pela regra de firewall sugerida. Não encaminhe a porta 8765 no roteador nem use uma rede Wi-Fi pública para parear. A implementação ainda precisa ser verificada no Windows e na TV Box físicos do usuário.

## Execução em segundo plano

Em **Configurações → Permitir segundo plano**, configure o MarlicoBot sem restrição de bateria, se o firmware oferecer essa opção. Alguns fabricantes têm uma permissão adicional de início automático. Forçar a parada nas configurações do Android impede a retomada automática até abrir o app novamente. A TV Box deve permanecer alimentada; se a alimentação vier da USB da TV, desligar a TV pode desligar a Box.

O serviço usa `specialUse`, com finalidade declarada, para aguardar comandos continuamente. Isso não remove restrições impostas por firmwares de fabricantes. Não foi publicado nem avaliado pela Play Store; a entrega é para instalação direta por APK.

## Proteção dos dados

O token é cifrado com AES-GCM e uma chave do Android Keystore. Backups do aplicativo estão desativados. O APK não contém o `.env` nem o token real. IDs autorizados e MAC são configuráveis, com os valores não secretos do projeto como padrão. A interface é carregada de recursos empacotados; navegação externa, acesso do WebView a arquivos arbitrários e conexões do JavaScript estão bloqueados. A comunicação do serviço com Telegram usa HTTPS e a validação padrão de certificados do Android.

## Código e compilação

- `src/`: serviço, comunicação Telegram, Wake-on-LAN, armazenamento e ponte para a tela.
- `assets/`: HTML/CSS/JS local da interface e avatar já existente no projeto.
- `res/`: ícones, banner de TV e tema.
- `checks/`: verificações do protocolo e da interface.
- `build.ps1`: compila, empacota, alinha e assina o APK, verificando o resultado.

Execute na raiz do projeto:

```powershell
.\android\build.ps1
```

O projeto usa ferramentas oficiais diretamente, sem Gradle ou bibliotecas Android adicionais. O JDK 17 e Android SDK 35 foram baixados para `.tools/` deste projeto. Para reconstruir em outra máquina, prepare:

| Componente | Arquivo oficial | Destino esperado |
|---|---|---|
| Microsoft OpenJDK 17 | https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip | `.tools/jdk/<pasta-do-jdk>/bin` |
| Android SDK Platform 35 | https://dl.google.com/android/repository/platform-35_r02.zip | `.tools/platform/android-35/android.jar` |
| Android SDK Build Tools 35.0.0 | https://dl.google.com/android/repository/build-tools_r35_windows.zip | `.tools/buildtools/android-15/` |

Os arquivos Android baixados foram comparados aos SHA-1 do catálogo oficial `https://dl.google.com/android/repository/repository2-1.xml`. A compilação usa minSdk 23 e targetSdk 35. O APK contém bytecode Java/DEX sem bibliotecas nativas, permitindo instalação em aparelhos ARM e x86 compatíveis.

**Preserve a pasta `android/signing/`.** A chave de assinatura pessoal e sua senha são geradas localmente e ficam fora do controle de versão. A mesma chave é necessária para futuras atualizações sem reinstalar o app. Não distribua essa pasta. A entrega pública é somente o APK; o arquivo SHA-256 permite verificar sua integridade.

## Verificação da versão 1.0.0

- Compilação completa com o SDK 35 e Java 17.
- 127 verificações de pacote WOL, MAC, broadcast IPv4, portas, IDs de 64 bits e importação `.env` com BOM/aspas.
- Interface renderizada em 960×540, 1280×720 e 1920×1080; verificações de navegação por setas, importação, configuração inicial, token não exibido e bloqueio de cliques repetidos.
- APK alinhado e assinatura v2/v3 verificada com `apksigner`.
- Não foi executado na TV Box física nem em emulador Android. A importação pelo seletor do aparelho, o Android Keystore, o serviço após reiniciar e o Wake-on-LAN real ainda exigem a instalação e uma verificação no dispositivo. As capturas em `dist/interface-*.png` mostram a interface com dados de demonstração, renderizada no navegador.

Referências oficiais: [aplicativos para Android TV](https://developer.android.com/training/tv/get-started/create), [tipos de serviço em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types), [Telegram Bot API](https://core.telegram.org/bots/api).

## Compatibilidade — versão 1.0.1

Após o relato de erro de análise do pacote, o APK 1.0.0 original foi revalidado: CRC, assinatura e recursos estavam íntegros. A causa no dispositivo ainda depende do nível de API real (`/system/bin/getprop ro.build.version.sdk`) e da integridade da cópia no pendrive.

A versão 1.0.1 reduz o mínimo de API 26 para API 23, recompila o DEX para esse mínimo e substitui `Map.getOrDefault`, indisponível no Android 6. O DEX foi recompilado para o mínimo de API 23 e a mesma chave de assinatura foi preservada. As assinaturas v1, v2 e v3 são explicitadas e verificadas a partir da API 23. O build agora deriva versão e API mínima do manifesto, evitando divergência entre o manifesto e o bytecode. Essas alterações ampliam a compatibilidade; não confirmam a causa do erro relatado nem substituem o teste no aparelho.

## Conversa no Telegram — versão 1.0.3

- 05h01–11h59: “Bom dia, Marlon! O que deseja?”
- 12h00–17h59: “Boa tarde, Marlon! O que deseja?”
- 18h00–05h00, incluindo o minuto 05h00 inteiro: “Boa noite, Marlon! O que deseja?”

Abaixo da saudação há apenas **Menu**. O clique altera a própria mensagem para “Escolha uma opção, Marlon:” e mostra apenas **Ligar PC**. Ao enviar o sinal, a mensagem vira “✅ Sinal enviado para ligar seu PC.”, com **Menu** para continuar. O aviso de cliques repetidos também foi encurtado. Um erro de edição por mensagem já idêntica é ignorado; se a mensagem não puder mais ser editada, o bot tenta enviar uma nova resposta, sem repetir o envio WOL.

O APK preserva o identificador e a chave de assinatura. Instale a 1.0.3 como atualização, sem desinstalar, para manter as configurações da TV Box (incluindo broadcast ajustado no aparelho). O fluxo foi compilado e as transições e os limites de horário foram verificados localmente, sem usar o token real para enviar mensagens ou ligar o PC durante os testes. A execução final do novo fluxo no Telegram depende de instalar esta versão na Box.

## Verificação de resposta do PC — versão 1.0.4

Depois de enviar Wake-on-LAN, o app consulta o IP do computador (padrão `192.168.0.6`) por até 45 segundos. Ele indica **“Seu PC está ligado e respondendo”** somente se um ping responder ou uma conexão TCP à porta de verificação for aceita. Quando não recebe resposta, mantém o resultado **sem confirmação**: o computador pode ter ligado e o firewall pode estar descartando os dois testes. A disponibilidade desta detecção depende das regras do PC e do roteador.

O IP, a porta de verificação (padrão TCP 445, SMB/compartilhamento de arquivos no Windows), broadcast e MAC podem ser alterados em **Configurações**. O `.env` existente continua válido: durante a atualização, o app mantém os dados anteriores e acrescenta o IP e a porta de verificação padrão. Se a Box já tinha o IP gravado, confirme que ele continua em `192.168.0.6`.

A verificação de IP, validações e respostas TCP positivas/negativas passaram em testes locais. A descoberta no Android, resposta ICMP ou SMB no PC-alvo e o tempo de inicialização ainda precisam ser conferidos na sua rede após instalar a atualização. A confirmação representa resposta naquele IP, não inspeção de tela, login nem estado elétrico confirmado por hardware.

## Monitoramento do Windows — versão 1.1.0

No menu do Telegram há **Status do PC**, **Programas abertos** e **Ver tela**, além de **Ligar PC**. O status contém CPU, memória, armazenamento agregado, uptime e janela ativa. A lista detalha nomes/PIDs e memória dos processos. A captura de tela é uma imagem única: o app Windows mostra uma janela de confirmação local e só envia depois de um clique em **Sim**. Não há vídeo contínuo nem comandos de teclado, mouse, shell ou abertura de programas.

O celular pode abrir **Monitorar PC** e atualizar status/processos enquanto estiver na mesma rede do PC. Para consulta de fora de casa, use o menu do Telegram por meio da TV Box; o painel Android não cria acesso remoto/VPN. Para evitar conflitos, mantenha o long polling do bot somente na TV Box; a instalação do APK no celular serve para o painel local e não deve ser conectada ao mesmo bot.

As verificações locais passaram: 132 validações WOL/configuração, 33 regras de horário e callbacks, verificação de navegação e layout em 390×844, 960×540, 1280×720 e 1920×1080, mais 6 verificações HTTP do agente (autorização, JSON, lista de processos, captura protegida e ausência de endpoint de controle). Os instaladores foram compilados e suas assinaturas/alinhamento conferidos. Ainda não foram executados nem pareados na TV Box e no PC físicos do usuário; o primeiro status e a aprovação real da captura dependem dessa instalação.
