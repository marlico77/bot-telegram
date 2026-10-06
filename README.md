# MarlicoBot

Projeto pessoal para ligar e monitorar um PC Windows 11 pela TV Box, aplicativo Android e Telegram.

## Downloads

Os instaladores serão publicados em [Releases](https://github.com/marlico77/bot-telegram/releases). Este repositório contém o código dos programas; o site de downloads fica em [Site-bot-telegram](https://github.com/marlico77/Site-bot-telegram).

A versão 1.3.2 retira os dados pessoais usados como padrão e permite definir o nome da saudação. Os pacotes são gerados em dist; a publicação em Releases é separada do commit de código.

## Atualizações pelo aplicativo

Instale a versão 1.3.1 manualmente uma vez. As versões anteriores não têm o atualizador.

Depois disso, Android e Windows consultam `https://botmy.netlify.app/api/v1/update` ao iniciar e a cada seis horas enquanto estiverem rodando. O Android pode adiar a consulta quando estiver suspenso pelo sistema. Também existe um botão para verificar manualmente. O site lê os Releases públicos do repositório dos programas.

Quando houver uma versão nova, o app avisa e oferece o download. O arquivo é conferido pelo tamanho e SHA-256 antes de abrir o instalador. O APK também precisa ter o mesmo pacote e assinatura do app instalado. A instalação depende da confirmação do usuário no Android ou do UAC no Windows. No Android 8 ou superior, autorize instalar apps desta fonte e volte para **Instalar download**. Se cancelar a instalação, pode tentar novamente pelo mesmo botão. Os dados de configuração são preservados.

Para publicar: crie um Release com o APK, o instalador EXE e `updates.json`. Aumente o `build` de cada plataforma e mantenha os nomes e hashes do manifesto iguais aos anexos. Na versão 1.3.2, Android usa build 14 e Windows usa build 4. O campo `minimumBuild` define a menor versão permitida, por plataforma; o padrão 0 não obriga ninguém a atualizar. Para uma próxima atualização obrigatória, defina o mínimo como o build exigido, nunca maior que o build publicado. Os clientes com atualizador bloqueiam as funções de monitoramento até atualizar. Clientes anteriores à 1.3.1 não obedecem a esse campo.

Uma falha de internet não cria uma obrigação de atualizar. Uma obrigação já recebida permanece guardada até instalar uma versão compatível ou receber um manifesto válido com outra política. Downloads vêm somente do repositório oficial via HTTPS. O instalador Windows continua sem assinatura Authenticode; a verificação do download não substitui a assinatura do publicador.

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
