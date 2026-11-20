# Direção de arte e assets — 20/11/2026

## Regras da revisão

Três pilares: silhueta reconhecível do aparelho, materiais de pixel art da era 16 bits e leitura clara em uma TV vista à distância. Modelos originais simplificados, sem logotipos adicionados. Não misturar materiais fotográficos com os novos objetos low poly. Capas da biblioteca continuam sendo carregadas pelo pipeline existente e não fazem parte da textura gerada.

Um atlas compartilhado de 512×512 evita texturas grandes por console. O bitmap usa cores quantizadas em RGB565, armazenadas em PNG, com amostragem nearest e bordas clamp no runtime. O atlas tem quatro colunas e duas linhas: madeira de parede, piso, carpete, plástico neutro; tela CRT original, pôster original, janela noturna e painel luminoso. UVs de plástico ficam na região neutra, preservando a cor característica de cada aparelho; as outras regiões pertencem ao cenário ou a superfícies específicas.

As fontes editáveis, modelos, orçamentos e exportação Blender estão documentados em [3D-ASSET-PIPELINE.md](3D-ASSET-PIPELINE.md). Cada tema conserva sua proposta e recebe uma identidade própria descrita em [THEME-POLISH.md](THEME-POLISH.md). A geometria e as texturas são projetadas para o orçamento da TV ARM32 de referência; qualidade visual e custo devem ser verificados no aplicativo, além dos renders do Blender.

## Geração da textura

Foi usada a ferramenta nativa `image_gen` disponível nesta sessão, sem afirmar um nome de modelo que a ferramenta não informa. Fonte preservada em `art-source/pixel-atlas/source.png`; identificação e hash em `art-source/pixel-atlas/provenance.json`. Normalização técnica pelo Blender 5.2.2 LTS com `tools/art/normalize_pixel_atlas.py`, sem modificar a fonte gerada. A textura final é `lemuroid-app/src/main/assets/textures/pixel_atlas_v2.png`.

Prompt enviado:

> Create a production-ready square flat UV texture atlas for an original retro videogame collection browser. This is ONE usable bitmap texture sheet, not a concept render, no objects in perspective. Strict 4 columns by 2 rows, exactly eight equal rectangular cells, no gaps, no outer border. 16-bit console pixel-art style with crisp visibly square pixels, restrained 32-color palette, hand-placed clusters, no anti-aliasing, no gradients, no text, no letters, no logos, no copyrighted game imagery. Desired output 512x512 pixels. Upper row left to right: (0) warm dark walnut wall panels, vertical subtle seams; (1) honey oak floor planks with restrained horizontal grain; (2) dark teal and navy arcade carpet, small geometric amber motifs; (3) neutral light gray vintage ABS plastic with tiny sparse pixel grain, mostly uniform luminance so shell tint stays accurate. Lower row left to right: (4) generic original CRT screen scene with tiny turquoise mountain silhouettes and blue sky, no characters/UI/text; (5) original decorative retro pixel poster of geometric controller buttons and a bright star on navy, no lettering; (6) dark blue nighttime window with exactly four panes, a small moon and pixel stars; (7) warm amber arcade marquee light panel with tiny geometric red/cyan accents, no lettering. Fill every cell edge to edge. Material cells top row must remain readable and quiet at small scale; all eight cells align precisely at x=0,25%,50%,75%,100% and y=0,50%,100%. Pixelated SNES-era material vocabulary suitable for low-poly UV models, consistent original art direction.

O resultado original tinha 1254×1254; foi convertido para o tamanho do atlas por amostragem nearest e quantização de cores. A imagem foi inspecionada antes e depois dessa conversão. O estilo solicitado foi usado como direção artística, sem assumir que a geração entregaria automaticamente um atlas pronto para o runtime.

## Referências de forma

As páginas oficiais de [história da Nintendo](https://www.nintendo.com/en-gb/Hardware/Nintendo-History/Nintendo-History-625945.html), [acervo da Nintendo](https://careers.nintendo.com/our-history/) e [história do PlayStation](https://www.sony.com/en/SonyInfo/CorporateInfo/History/SonyHistory/2-09.html) foram consultadas como referências de aparência. Não foram incorporados seus bitmaps ou modelos. As geometrias simplificadas e a decoração foram produzidas para este projeto. A procedência não constitui uma promessa de exclusividade sobre imagens geradas.
