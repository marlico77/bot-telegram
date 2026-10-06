# MarlicoBot

Projeto pessoal para ligar e monitorar um PC Windows 11 pela TV Box, aplicativo Android e Telegram.

## Downloads

Os instaladores serão publicados em [Releases](https://github.com/marlico77/bot-telegram/releases). Este repositório contém o código dos programas; o site de downloads fica em [Site-bot-telegram](https://github.com/marlico77/Site-bot-telegram).

A versão 1.3.0 atualiza a tela ao vivo, o tema rosa e a interface para celular. Os pacotes são gerados em dist; a publicação em Releases é separada do commit de código.

O instalador pede confirmação do UAC e instala o agente em Arquivos de Programas. O agente roda sem elevação na sessão do usuário e inicia com o Windows, minimizado na área de notificação. Fechar a janela a oculta; o menu do ícone pode abrir o painel ou parar o agente. O painel exibe métricas, processos ativos, hardware e programas instalados. O inventário também fica disponível no painel Android e no menu do Telegram. A visualização é somente leitura, sem teclado, mouse ou shell. A função de energia permite desligar o PC após confirmação. A foto única pede autorização em cada solicitação. A tela ao vivo começa após autorizar a sessão no Windows ou ativar a permissão persistente e iniciar pelo app; o ícone indica quando está ativa e permite parar localmente.

O instalador atual não tem assinatura Authenticode de um publicador verificado. O UAC não remove avisos do Microsoft Defender SmartScreen. Não desative a proteção do Windows; para distribuição confiável, é necessário assinar o pacote com um certificado de assinatura de código reconhecido.

## Parear

1. Abra MarlicoBot PC pelo ícone da bandeja e selecione **Permitir conexão da rede local**. O Windows pode pedir autorização de administrador para criar uma regra de firewall limitada à sub-rede local.
2. No app Android, abra **Monitorar PC → Parear pela rede local** e confirme o IP privado do computador.
3. Aprove o pedido de pareamento que aparecerá na tela do Windows. A chave é guardada no Android sem transferir arquivo.
4. Para consultas pelo Telegram fora de casa, deixe o MarlicoBot conectado somente na TV Box. Envie `/metricas`, `/programas` ou `/print` ao bot, ou toque nas opções do **Menu**. A captura única de tela ainda pede aprovação no Windows.

O painel Android consulta o PC diretamente quando o celular está na mesma rede local. Fora de casa, use os comandos do Telegram; o painel não cria uma VPN. O pareamento é aprovado no Windows e funciona apenas pela rede local. Não encaminhe a porta do agente para a internet.

## Limites

O processo Windows precisa permanecer aberto e o PC precisa estar ligado para fornecer telemetria. A TV Box precisa permanecer ligada e na rede local para encaminhar consultas do Telegram e Wake-on-LAN. Firewall, isolamento Wi-Fi, bloqueio de tela ou políticas do Windows podem impedir uma consulta/captura. A visualização ao vivo atualiza imagens em quadros e exige autorização local por sessão ou permissão persistente. A implantação ainda precisa ser conferida na TV Box e no computador do usuário.

Consulte [android/README.md](android/README.md) e [windows/README.md](windows/README.md) para os detalhes e passos de instalação.

## Desligar o PC

No Android, abra Monitorar PC e toque em Desligar PC. No Telegram, use /desligar ou a opção do menu. A confirmação informa que alterações não salvas serão perdidas. Após confirmar, o Windows agenda o desligamento forçado em 30 segundos. É possível cancelar pelo aplicativo, por /cancelar_desligamento ou pelo agente Windows. Só aceita aparelhos pareados e usuários privados autorizados no Telegram; confirmações expiram em 60 segundos e não podem ser reutilizadas.

## Tela ao vivo na versão 1.3.0

Atualize Windows e Android juntos. Toque em Acesso remoto e aprove a solicitação no Windows. A imagem aparece acima das métricas; o seletor muda o monitor durante a sessão, e os botões ajustam o zoom e ampliam a área de visualização. Ao sair do app, a transmissão é encerrada. Trata-se de quadros JPEG periódicos, não do protocolo de vídeo do AnyDesk. O acesso direto requer a mesma rede local. Não há relay pela internet nem controle de mouse/teclado.

Compilação não confirma o funcionamento nos aparelhos. Não foi realizado teste de captura em um PC remoto nem executado desligamento real nesta máquina de desenvolvimento.
