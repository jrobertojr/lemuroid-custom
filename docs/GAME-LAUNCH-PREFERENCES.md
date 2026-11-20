# Escolhas por jogo e sistema

Implementação de 20/11/2026.

- `GamePreferencesManager` guarda núcleo e perfil por sistema + SHA256 da URI do arquivo. Reindexar/renomear o jogo não perde a escolha; mover o arquivo muda sua identidade. CDs e versões têm configurações independentes.
- Núcleo: escolha do jogo → escolha do sistema → padrão existente. Só são oferecidos os núcleos declarados para o sistema detectado. Remover override volta a herdar sem sobrescrever a preferência do sistema.
- Desempenho: jogo → sistema → perfil geral. A detecção automática e os três perfis anteriores são mantidos. O perfil resolvido na abertura é usado pelo carregador, opções do core, shader/HD e áudio, inclusive ao retomar do menu. Preferências manuais de variáveis do core continuam com precedência. Moldura permanece independente.
- TV: X em jogo individual abre o editor antes de iniciar. Em agrupamentos X no item pai não inicia/configura um representante; é necessário abrir o grupo e usar X no CD/versão desejado. A também continua iniciando normalmente.
- Editor compartilhado salva escolhas imediatamente, permite herdar/restaurar e iniciar/testar o núcleo. O jogo abre em sua atividade/processo habitual, com estados separados por core. Ao sair normalmente há salvamento e retorno à biblioteca. Não há troca de core dentro da sessão ativa.
- Menu durante o jogo e contexto móvel dão acesso ao mesmo editor, com alterações aplicadas na próxima abertura. Configuração geral também oferece lista de perfis por sistema.
- Teste é comparação manual da mesma cena/imagem/áudio. Nenhum contador de interface é apresentado como FPS emulado e não se promete que um núcleo seja sempre mais rápido.
- Preparação antes de iniciar assegura disponibilidade do núcleo escolhido por jogo, incluindo núcleos baixados sob demanda.
- B/Voltar só pede confirmação ao sair da biblioteca principal para a TV. Cancelar mantém o aplicativo; navegação interna e diálogos conservam seu próprio Voltar.

## Núcleos adicionais

Mesen 0.9.9 (NES) e Mesen-S 0.4.0 (SNES) são alternativas de precisão/compatibilidade, sem mudar padrões. Quatro ABIs oficiais, SHA256 em `lemuroid-core-alternatives/manifest.json`, GPLv3 e metadados incluídos. Todos os 80 arquivos do módulo tiveram SHA256 verificado. Os dois ARMv7 carregaram via dlopen na TV Android 12, API Libretro 1 e símbolos necessários presentes. Não confundir esse probe com teste completo de jogos ou garantia de FPS; Mesen pode custar mais CPU nesta TV.

Referências primárias: https://docs.libretro.com/library/mesen/ e https://docs.libretro.com/library/mesen-s/ . A biblioteca não informa commit de origem; os binários exatos estão fixados por SHA256, sem alegar um commit não verificado.

## Compatibilidade de inicialização Mesen

O probe inicial dlopen não detectou uma falha de ordem de callbacks: os ports Mesen/Mesen-S antigos exigem que renderer/áudio/input existam quando seus setters são chamados, mas o frontend registra esses callbacks antes de retro_init (ordem permitida pela API). Mesen falhou ao abrir 1943. A camada `compat/mesen_callbacks.cpp` guarda os cinco callbacks, chama a inicialização original e então os aplica; o restante da API é delegado. Os binários originais permanecem intactos. Foram compilados oito adaptadores (quatro ABIs), com hashes próprios, fonte e script de reprodução. O probe independente callback-before-init → init → info → deinit passou nos dois ARMv7.

## Validação na TV

- X abriu o editor em 1943; núcleo Mesen e perfil Desempenho persistiram após encerrar/reabrir o app. Log da sessão: game=349, core=mesen, profile=performance; o geral continuou Qualidade. Abertura e tela inicial de 1943 renderizadas, com a moldura configurada. Não é medição de FPS sustentado.
- Perfil Nintendo Equilibrado foi herdado no editor; override do jogo Desempenho teve precedência. Restaurar retirou núcleo/perfil do jogo e voltou a herdar. O modo do sistema foi restaurado para geral.
- 3 Ninjas (game=154) abriu por X com Mesen-S e perfil Qualidade herdado, exibiu a abertura e voltou normalmente ao mesmo jogo da biblioteca. Override de teste removido para herdar Snes9x novamente.
- X no agrupamento Alone in the Dark não abriu o editor; A abriu os discos, D-pad no segundo + X abriu explicitamente Disc 2. B voltou sem pedir saída do app.
- B na biblioteca mostrou a pergunta; outro B cancelou. Voltar Android também mostrou a pergunta; escolher Sair retornou ao launcher da TV.
- Configurações → Desempenho por sistema apresentou os 25 sistemas com Qualidade/Equilibrado/Desempenho e herança do geral. Escolhas Arcade permanecem separadas.
- Suite compartilhada: 17 testes sem falhas; Free ARM32 compilado e instalado, extensão Play compilada. SHA256 dos 80 binários originais e oito adaptadores verificado.

Capturas e logs de execução ficam em `build/per-game-validation`; são evidências locais, não parte dos saves do jogador. Não houve teste completo de todos os jogos/núcleos, controle físico do jogador ou avaliação auditiva.

A compilação final também foi instalada e 1943/Mesen abriu em Qualidade (além do teste anterior em Desempenho). Na confirmação final de saída, o foco foi verificado em Continuar no aplicativo e o botão de confirmação manteve o app aberto. Comparação após reiniciar: as 28 preferências de núcleo por sistema/perfil geral/tema/moldura foram preservadas, sem overrides temporários remanescentes. Apenas os arquivos de probe gerados por esta tarefa foram limpos da TV.
