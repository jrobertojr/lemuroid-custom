# Diagnóstico NDS na TV TCL — 19/11/2026

## Escopo

O usuário relatou travamentos em todos os jogos de NDS e deixou um jogo aberto para coleta. A investigação consultou ADB, preferências Harmony do próprio app, logs, descritores de arquivos, SurfaceFlinger, threads e estado térmico. Não foram alteradas preferências, arquivos do aplicativo instalado, firmware ou saves. Não houve instalação de APK.

## Evidências

- Pacote: com.swordfish.lemuroid.debug, processo de jogo PID 28348 na coleta.
- Perfil ativo confirmado na preferência Harmony: performance.
- Núcleo selecionado: desmume. A biblioteca libdesmume_libretro_android.so também apareceu no mapeamento do processo.
- ROM aberta: New Super Mario Bros.nds, em cache interno `/data/data/com.swordfish.lemuroid.debug/cache/storage-framework-games/nds/`. Nome obtido do descritor de arquivo aberto, sem leitura do conteúdo da ROM.
- Origem configurada: pendrive `/storage/620F-B493`, com aproximadamente 15 GiB, 96% ocupado e 653 MiB livres.
- `custom_cache_enabled=false`; portanto não está usando cache personalizado no pendrive nesta execução.
- `allow_direct_game_load=true` não implica carregamento direto em todos os núcleos. O carregador exige também `supportsLibretroVFS`; o descritor aberto confirma o arquivo interno nesta sessão.
- Opções NDS aplicadas/retornadas em logs: JIT, block size 12, 2 threads de renderização solicitadas por `desmume_num_cores`, resolução 256x192, frameskip 2. A biblioteca anuncia ARM-JIT. Não confundir a opção num_cores com paralelização automática de toda a emulação.
- Log de shader: screenFilter=default, hdMode=false. O código resolve default para o shader por sistema; no NDS isso é LCD.
- Três amostras da superfície BLAST do jogo: 20,16; 19,95; 19,89 FPS apresentados. Amostras de 126/127 timestamps com janela móvel sobreposta de aproximadamente 6 segundos; não são três execuções independentes.
- Mediana dos intervalos: 50 ms; percentil 95: 66,67 ms; máximo: 83,33 ms nas três amostras.
- Uma coleta anterior continha um intervalo de 9,9 segundos e média de 7,76 FPS; foi excluída da avaliação contínua por poder incluir pausa/menu/interação. Não usá-la como benchmark do jogo.
- Thread GLThread do jogo: aproximadamente 84% de uma CPU na amostra; threads auxiliares em cerca de 6–10% cada. CPU 0 observada a 1.908 MHz.
- Processo do jogo: aproximadamente 334–341 MiB residentes. MemAvailable em uma coleta: 529.596 KiB. Swap em uso, sem demonstração de paginação como causa principal.
- Temperatura do SoC: aproximadamente 63–64 °C durante o jogo; Thermal Status 0 e cooling devices em 0. Não houve indicação de throttling térmico pelo HAL nessas amostras, sem excluir limitações em sessões mais longas.

## Problemas confirmados no código

1. O perfil PERFORMANCE força `desmume_frameskip=2` em PerformanceProfile.kt. A documentação do DeSmuME explica que pular quadros melhora velocidade ao custo da suavidade. Os aproximadamente 20 FPS apresentados são compatíveis com desenhar um a cada três quadros de uma execução próxima de 60 passos/s. Isso não prova velocidade emulada de 60 FPS: não houve contador interno de retro_run/velocidade emulada.
2. GameViewModelRetroGameView.kt tenta simplificar filtros em PERFORMANCE atribuindo `default`, mas ShaderChooser.kt não trata essa chave como shader simples. Ela cai no shader padrão do sistema, LCD para NDS e CRT para vários consoles. A opção existente `smooth` resolve ShaderConfig.Default. Portanto o modo desempenho não elimina os filtros como aparenta pretender.
3. CoreVariablesManager.kt mescla defaults + perfil + opções personalizadas. Uma opção personalizada com a mesma chave vence o perfil. Não houve override personalizado de frameskip NDS nesta coleta; entretanto isso importa para definir claramente o comportamento de cada modo em todos os núcleos. Não inverter globalmente essa prioridade sem definir a semântica das opções avançadas.
4. ConsoleBezelOverlay é composto também na tela de jogo e a preferência atual é auto; convém comparar sua remoção somente no perfil desempenho. Nenhum custo em milissegundos foi atribuído à moldura nesta análise.
5. Os perfis enviam `melonds_jit_enable=enabled`, mas a busca de strings na biblioteca melonDS ARM32 instalada encontrou threaded_renderer e não encontrou melonds_jit_enable. Isso indica ausência dessa opção nessa compilação; não afirmar que escrever a preferência habilita um JIT inexistente. A confirmação final deve usar as opções anunciadas pelo núcleo ao ser carregado. Não trocar automaticamente o núcleo, porque saves e save states exigem tratamento próprio.

## Trabalho proposto, respeitando os perfis

- Corrigir a seleção do shader simples exclusivamente em PERFORMANCE, mantendo QUALITY e BALANCED com as escolhas atuais do usuário.
- Retirar frameskip 2 fixo como suposta garantia de fluidez. Comparar frameskip 0 e 1 no mesmo trecho e núcleo, distinguindo quadros apresentados, velocidade emulada e áudio. Frameskip 1 também reduz suavidade; não é solução equivalente a 60 quadros desenhados.
- Molduras devem seguir a configuração do usuário em todos os perfis. O usuário explicitou que o perfil de desempenho não deve desativá-las automaticamente.
- Validar opções contra as capacidades reais de cada biblioteca/arquitetura; registrar perfil e opções efetivas em diagnóstico. Não aplicar nomes/valores de outro fork do melonDS.
- Avaliar ajustes de threads/JIT do DeSmuME em teste controlado. Não aumentar arbitrariamente block size nem desativar timing/precisão globalmente: esses ajustes podem afetar compatibilidade.
- Comparar build otimizada com a DEBUG atual, sem atribuir ganho percentual sem teste.
- Manter ROMs no pendrive e usar cache interno quando apropriado e houver espaço. O jogo observado já usa esse caminho, de modo que mover sua origem não explica nem resolve sozinho o FPS baixo desta sessão.
- Medir por núcleo e jogo em cena repetível, incluindo sessão longa/temperatura. A TV suporta apenas APKs/núcleos armeabi-v7a, cerca de 2 GB de RAM, e não há como prometer velocidade plena de qualquer core por uma configuração genérica.

## Fontes técnicas externas

- https://docs.libretro.com/library/desmume/ — significado e custo de frameskip, opções de CPU/JIT e renderização.
- https://github.com/libretro/melonDS/blob/master/src/libretro/libretro_core_options.h — opções dependentes de flags de compilação; upstream atual pode diferir do fork instalado.
- https://github.com/libretro/melonDS/blob/master/Makefile — configuração de arquitetura no upstream; não substitui inspeção da biblioteca instalada.

Na etapa inicial acima, não foi aplicado patch no código do app nem comprovado ganho de FPS.

## Correção e validação subsequentes — 19/11/2026

O usuário autorizou as correções e reiterou a preservação dos três perfis e da seleção automática. Também exigiu preservar a configuração das molduras laterais, independentemente do perfil.

Alterações mantidas:

- PERFORMANCE passa a solicitar `smooth`, que ShaderChooser resolve para ShaderConfig.Default, eliminando o fallback indevido para LCD/CRT. QUALITY e BALANCED mantêm o filtro escolhido pelo usuário.
- A tentativa de PERFORMANCE solicitar frameskip 0 não resolveu a lentidão. O ajuste original 2 foi restaurado, preservando o compromisso original de velocidade do perfil. Uma escolha manual de frameskip continua tendo precedência, como antes. JIT, bloco 12, resolução nativa e dois trabalhadores continuam como antes.
- Critérios de detecção automática, persistência da escolha manual, precedência das opções personalizadas e configuração da moldura não foram alterados.
- Foram adicionados três testes de regressão para preservar os contratos dos perfis: desempenho com os ajustes originais; equilíbrio com os ajustes anteriores; qualidade sem overrides de núcleo.

Validação na TV:

- A atualização foi instalada via ADB com `install -r`, sem limpeza de dados. O mesmo jogo, ID 482, foi reaberto pelo launcher existente.
- Logs confirmaram `screenFilter=smooth`, HD desligado e `desmume_frameskip=0` durante os testes. A preferência manual 0 observada na TV continua prevalecendo sobre o perfil; ela não foi apagada nem sobrescrita.
- As preferências passaram a apresentar top only, frameskip personalizado 0 e moldura disabled durante a sessão interativa do usuário. Nenhum comando desta correção escreveu essas preferências; não atribuir essas mudanças à atualização. A configuração da moldura permanece sob controle do usuário.
- Com top only e frameskip 0: aproximadamente 14,72–15,72 FPS apresentados nas primeiras amostras; intervalos medianos de 66,67 ms. **Não foi demonstrada fluidez de 60 FPS.** O usuário confirmou lentidão tanto de imagem quanto de áudio.
- A comparação com os aproximadamente 20 FPS anteriores não é um benchmark de velocidade equivalente: frameskip e layout eram diferentes. Não alegar ganho ou regressão percentual com base nesses dois conjuntos.

Experimentos temporários, retirados do código final:

- Timing avançado desligado: aproximadamente 15,65–15,72 FPS, sem ganho relevante; descartado para evitar redução de compatibilidade sem benefício demonstrado.
- Bloco JIT 100 e mais trabalhadores (a TV recebeu 4): aproximadamente 16,15–16,19 FPS. Ganho pequeno, insuficiente para justificar a alteração de compatibilidade/consumo. Retornou a bloco 12 e 2 trabalhadores.
- Carregamento da ROM inteira na memória: opção confirmada ativa, ROM de 32 MiB, amostra inicial de aproximadamente 15,09 FPS. Sem benefício relevante; retirado para não ampliar uso de RAM nem introduzir risco com ROMs maiores.
- Simpleperf do sistema recusou a coleta solicitando mudança da propriedade security.perf_harden. Essa propriedade não foi alterada, nem se tentou root. Não há perfil nativo de símbolos para atribuir percentuais a funções internas do núcleo.

Os três testes passaram e a compilação final FreeDynamicDebug foi concluída com sucesso. A correção mantida na camada do aplicativo elimina o erro de seleção do shader, mas **a lentidão de NDS permanece nesta TV nos trechos medidos**. A próxima investigação deve tratar o núcleo nativo ARM32 e seu processamento, com uma cena repetível e instrumentação adequada, em vez de insistir em filtros ou em salto de quadros como garantia de velocidade. O submódulo de cores desta cópia armazena/distribui bibliotecas; o código Kotlin examinado não contém a implementação nativa do DeSmuME.
