# Polimento dos menus — 20/11/2026

## Capas, consultas e relógio 3D

Alterações de implementação; ainda não representam uma medição de FPS na TV.

- `Game3DObject`: a decodificação Coil recebe um limite de 256×256 no perfil Desempenho e 512×512 nos demais. A seleção espera 140 ms antes de carregar a próxima imagem. O objeto e a capa anteriores permanecem até a substituição ficar pronta. As alternativas de imagem e o fallback desenhado foram preservados; cancelamento de carregamento é propagado.
- Após preparar a nova capa, solicita-se explicitamente um frame para o upload pela thread GL. Bitmaps temporários continuam sendo reciclados; a cópia preparada pertence ao renderizador. A mídia recebe o identificador do sistema para usar os novos modelos específicos.
- `ThreeDFrameClock`: `Bind3DFrameClock(view, invalidationKey)` solicita renderização somente enquanto o lifecycle está RESUMED, em modo WHEN_DIRTY. O relógio usa Choreographer nativo, sem invalidar a árvore Compose a cada frame. O orçamento nominal é 60 pedidos/s em Qualidade, 30 em Equilibrado e, em Desempenho, 30 durante aproximadamente 900 ms após mudança da seleção, depois 4 pedidos/s. São cadências de solicitação aproximadas, não FPS efetivamente medidos. A rotação do objeto é desativada em Desempenho para evitar movimento aos saltos durante o período ocioso.
- `CoverUtils`: o cache de memória de capas em TVs ou processos ARM32 tem teto de 32 MiB, limitado também a 20% do heap. O cache em disco existente foi preservado. O carregador e as chamadas legadas desativam crossfade em Desempenho; `shouldCrossfade(context)` permite a mesma política nos pedidos individuais. O perfil global continua governando a biblioteca; ajustes por jogo governam apenas sua sessão de emulação.
- `TVGameMetadataHelper`: as consultas HTTP agora usam callbacks canceláveis. Trocar de seleção cancela imediatamente a chamada OkHttp, inclusive durante a leitura do corpo; respostas são fechadas em todos os caminhos. O cache LRU existente e os fallbacks português/inglês permanecem.

Não foram alterados os perfis de emulação, a seleção automática do perfil, as preferências de moldura, os estados de retorno à biblioteca, os comandos X ou os saves. As mudanças de renderização devem ser verificadas na TV com navegação rápida, pausas/retorno, entrada/saída de jogo e alternância de perfis. Nenhum ganho numérico deve ser anunciado antes dessa comparação.
