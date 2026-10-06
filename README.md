# MarlicoBot

Projeto pessoal para ligar e monitorar um PC Windows 11 pela TV Box, aplicativo Android e Telegram.

## Downloads

Os instaladores serão publicados em [Releases](https://github.com/marlico77/bot-telegram/releases). Este repositório contém o código dos programas; o site de downloads fica em [Site-bot-telegram](https://github.com/marlico77/Site-bot-telegram).

O commit inicial não inclui os instaladores. Há problemas conhecidos de timeout na visualização ao vivo e falhas de captura relatadas no Telegram, ainda pendentes de correção.

O instalador pede confirmação do UAC e instala o agente em Arquivos de Programas. O agente roda sem elevação na sessão do usuário e inicia com o Windows, minimizado na área de notificação. Fechar a janela a oculta; o menu do ícone pode abrir o painel ou parar o agente. O painel exibe métricas, processos ativos, hardware e programas instalados. O inventário também fica disponível no painel Android e no menu do Telegram. O agente é somente leitura; não oferece teclado, mouse, shell ou controle remoto. A foto única pede autorização em cada solicitação. A tela ao vivo só começa após ativar a permissão persistente no Windows e iniciar pelo app; o ícone indica quando está ativa e permite parar localmente.

O instalador atual não tem assinatura Authenticode de um publicador verificado. O UAC não remove avisos do Microsoft Defender SmartScreen. Não desative a proteção do Windows; para distribuição confiável, é necessário assinar o pacote com um certificado de assinatura de código reconhecido.

## Parear

1. Abra MarlicoBot PC pelo ícone da bandeja e selecione **Permitir conexão da rede local**. O Windows pode pedir autorização de administrador para criar uma regra de firewall limitada à sub-rede local.
2. No app Android, abra **Monitorar PC → Parear pela rede local** e confirme o IP privado do computador.
3. Aprove o pedido de pareamento que aparecerá na tela do Windows. A chave é guardada no Android sem transferir arquivo.
4. Para consultas pelo Telegram fora de casa, deixe o MarlicoBot conectado somente na TV Box. Envie `/metricas`, `/programas` ou `/print` ao bot, ou toque nas opções do **Menu**. A captura única de tela ainda pede aprovação no Windows.

O painel Android consulta o PC diretamente quando o celular está na mesma rede local. Fora de casa, use os comandos do Telegram; o painel não cria uma VPN. O pareamento é aprovado no Windows e funciona apenas pela rede local. Não encaminhe a porta do agente para a internet.

## Limites

O processo Windows precisa permanecer aberto e o PC precisa estar ligado para fornecer telemetria. A TV Box precisa permanecer ligada e na rede local para encaminhar consultas do Telegram e Wake-on-LAN. Firewall, isolamento Wi-Fi, bloqueio de tela ou políticas do Windows podem impedir uma consulta/captura. A visualização ao vivo atualiza imagens em quadros e exige a permissão persistente local. A implantação ainda precisa ser conferida na TV Box e no computador do usuário.

Consulte [android/README.md](android/README.md) e [windows/README.md](windows/README.md) para os detalhes e passos de instalação.
