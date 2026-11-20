# Polimento dos temas — 20/11/2026

Esta etapa sucede a release `v1.17.0-custom.20261120.1` (`507ec7a`). O objetivo é dar personalidade própria aos nove temas, melhorar leitura na TV e conter decoração conforme os três modos existentes. A seleção de núcleo, configurações por jogo/sistema, moldura, atalhos X/B, grupos de CDs e retorno à mesma seleção continuam sob seus contratos existentes.

## Pilares visuais

| Chave estável | Nome em português | Identidade e hierarquia |
| --- | --- | --- |
| `default` | Essencial | Interface Material limpa, com a paleta dinâmica já disponível no dispositivo. Mantém o frontend clássico Leanback na TV. |
| `locadora` | Locadora | Madeira, capas físicas e âmbar; a obra selecionada sai da prateleira e os títulos/legendas ganham leitura a distância. |
| `netflix` | CineArcade | Cinema doméstico, azul escuro e coral. Emblema original de arco/play substitui a fita em N; pôster completo quando não há screenshot panorâmico. |
| `console` | Sala de Consoles | Azul luminoso e organização por console, preservando seus dashboards e objetos físicos. Ondas e pulso decorativos são discretos e opcionais pelo perfil. |
| `tv` | Canal Retrô | CRT com fósforo verde mais suave, vidro e madeira. A mídia muda sem misturar dados do jogo anterior com o atual. |
| `retro_80_90` | Noite Neon | Horizonte violeta, ciano e magenta, com título em primeiro plano e mídia central; o fundo fica atrás do conteúdo legível. |
| `rockstar` | Distrito Arcade | Editorial urbano próprio, carvão, âmbar e verde-água. Contagem real de jogos e posição da seleção substituem dinheiro e classificação decorativos. |
| `thrash_metal` | Palco de Aço | Aço, vermelho queimado e cobre; capas como álbuns. O vinil gira somente quando o perfil permite decoração contínua. |
| `three_d` | Vitrine 3D | Azul/ciano, objeto em destaque e palco escuro; os títulos e indicadores mantêm hierarquia clara. Modelagem/renderização dos objetos é tratada por componentes próprios. |

As chaves persistidas e os valores do enum não foram renomeados. Apenas os rótulos de apresentação em inglês/português e o cabeçalho móvel usam os nomes novos. O tema já escolhido continua selecionado após a atualização.

## Mudanças verificáveis

- `FrontendTheme.kt` centraliza `FrontendMotion`, o acompanhamento do perfil global/lifecycle e a exibição dos nomes. Uma inscrição de preferências por frontend evita um listener por card. Os perfis de jogo/sistema não alteram indevidamente a decoração da biblioteca.
- `TVThemedFrontend.kt` e `MobileThemedFrontend.kt` fornecem a política de movimento aos componentes. Flows DAO de sistemas, recentes e favoritos são memorizados, evitando recriar a inscrição a cada recomposição.
- O slideshow compartilhado não fica rodando em paralelo ao slideshow próprio da Vitrine 3D. Os ciclos são suspensos fora do estado `RESUMED` e no modo Desempenho.
- Os layouts usam uma única transição de mídia pelo Compose. O fade do Coil está desligado nesses mesmos pedidos de imagem; isso elimina duas camadas de transição simultâneas.
- CineArcade e Canal Retrô passam uma representação imutável `(Game, URL)` à transição. Cada conteúdo usa o próprio argumento, incluindo a capa alternativa, em vez de usar a URL externa mais recente nas duas camadas.
- O percentual de relevância gerado pelo hash do ID e o selo genérico HD foram removidos. Os cinco/três indicadores de estrelas do Distrito e do painel compartilhado deram lugar a estado real de favorito. A faixa CineArcade se chama “Sua seleção”, sem alegar popularidade diária medida.
- Textos secundários explícitos de TV antes com 8–11,5 sp passaram para 12 sp; títulos continuam maiores. Foco mantém borda/realce e transição curta, com menos salto vertical. Rolagem animada e a saída elástica da caixa são evitadas em Desempenho.
- O cabeçalho móvel consulta o mesmo array de nomes da configuração, removendo rótulos fixos Netflix/Rockstar que ficariam divergentes do seletor.

## Decoração por modo

| Modo global | Foco | Transição de mídia | Troca automática de screenshot | Vinil / ondas / pulso |
| --- | ---: | ---: | ---: | --- |
| Qualidade | 180 ms | 320 ms | 4,5 s | Somente enquanto a biblioteca está visível |
| Equilibrado | 140 ms | 180 ms | 6 s | Estáticos |
| Desempenho | 80 ms | Imediata | Desativada | Estáticos |

Nenhum desses ajustes modifica a moldura lateral, o núcleo escolhido ou as opções de emulação. Não foi introduzido contador de FPS fictício. O relógio/renderização 3D possui política própria nos componentes de renderização; não confundir essa política com os fades e ciclos 2D acima.

## Verificação nesta etapa

Verificações de fonte concluídas: XML dos dois arrays válido, nove nomes em cada idioma, valores persistidos idênticos aos da release, ausência de `.crossfade(true)` nos layouts com transição Compose e remoção dos relógios infinitos locais de vinil/ondas/pulso. `git diff --check` não apontou erros nos arquivos desta etapa.

A compilação, inspeção visual dos nove temas na TV e medições ficam na verificação integrada da branch. A revisão de fonte não demonstra uma taxa de quadros sustentada nem substitui as capturas reais. Conferir especialmente títulos longos com a tipografia maior, capas faltantes, navegação rápida, grupos de CDs, retorno de jogo, mudança de perfil, mobile retrato/paisagem e suspensão/retomada.

Sugestão para evolução: manter mídia completa nos objetos, usar arte panorâmica somente como fundo, preservar o foco visível em qualquer paleta e adicionar decoração nova apenas quando sua leitura e custo puderem ser verificados na TV de referência.
