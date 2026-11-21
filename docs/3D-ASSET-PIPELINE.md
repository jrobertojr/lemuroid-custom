# Biblioteca 3D e atlas em pixel art

## Fonte editável e reprodução

`tools/art/build_faithful_hardware.py` gera geometria original no Blender 5.2.2 LTS, com contornos específicos, faces planas e curvas reais aproximadas por poucos segmentos. Não usa modificador Bevel nem smooth shading. A fonte atual é `art-source/faithful-hardware/hardware-library.blend`, com cenas individuais e atlas incorporado. O ponto de entrada histórico `build_lowpoly_hardware.py` encaminha para o gerador atual. A revisão foi refeita após o usuário rejeitar os modelos anteriores; fotos reais e variantes estão registradas em `FAITHFUL-HARDWARE-2026-11-20.md`.

Executar a partir do repositório:

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/art/pack_hardware_atlas.py
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/art/build_faithful_hardware.py
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/art/render_hardware_previews.py
python tools/art/validate_l3d.py
```

Referências visuais primárias de aparência, sem copiar imagens: [história Nintendo](https://careers.nintendo.com/our-history/), [hardware Nintendo](https://www.nintendo.com/en-gb/Hardware/Nintendo-History/Nintendo-History-625945.html) e [história PlayStation/Sony](https://www.sony.com/en/SonyInfo/CorporateInfo/History/SonyHistory/2-09.html). Os modelos são representações simplificadas e originais; não reproduções de engenharia ou medidas oficiais.

## Cobertura

São 24 modelos de console e 24 modelos de mídia, atendendo os 25 sistemas do aplicativo: NES, SNES, N64, Mega Drive, Sega CD, Master System, Game Boy, Color, Advance, DS, 3DS, PSP, Game Gear, Atari 2600/7800, PC Engine, Lynx, Neo Geo Pocket/Color, WonderSwan/Color, DOS e os dois sistemas Arcade. Arcade compartilha gabinete/mídia; `scd` normaliza para `segacd`, `genesis` para `md`, e `fbneo`/`mame2003plus` para `arcade`.

Mídias distinguem cartuchos NES/SNES/N64/GB/GBA, cartões DS/3DS, UMD, CD, HuCard, disquete DOS e gabinete com capa na tela. Não inventar suporte GameCube: `ngc` significa Neo Geo Pocket Color. Capas permanecem imagens do acervo, preservando proporção com letterbox. O modelo DOS usa disquete como símbolo do acervo, não como afirmação do formato real de cada jogo.

## Contrato L3D2

- Little-endian; cabeçalho `L3D2` (`0x3244334c`) e contagem de partes uint32.
- Por parte: sete float32 (`r,g,b,a,shininess,specStrength,emissive`), contagem uint32 de vértices, seguida de triângulos sem índice. Oito float32 por vértice: posição XYZ, normal XYZ, UV.
- Coordenadas destras: X direita, Y cima, +Z frente. Consoles apoiam em Y=0. Mídias ficam centralizadas para o showcase. O showcase individual centraliza a altura por limites da malha; anel e quarto preservam a base.
- Materiais agrupados: até oito partes. Limites: 600 triângulos comuns, 800 gabinete. Os valores reais e SHA256 de cada arquivo ficam em `art-source/faithful-hardware/manifest.json`.
- Em mídia, a **última parte** recebe capa com UV completo; demais partes recebem o atlas. Esse contrato é consumido por `Hardware3DRenderer`.
- O leitor rejeita contagens inválidas, dados insuficientes, valores não finitos e bytes residuais, com fallback procedural. O validador independente confere hashes, orçamento, normais, UV e base.

## Atlas e UV

O ambiente conserva `assets/textures/pixel_atlas_v2.png`, 512×512, grade 4×2. Tiles: 0 parede nogueira; 1 piso; 2 tapete; 3 ABS neutro; 4 tela CRT; 5 pôster original; 6 janela noturna; 7 letreiro âmbar. Origem de imagem e prompt estão em `art-source/pixel-atlas`; a geometria não usa imagens geradas como referência dimensional.

Hardware usa um atlas separado `hardware_atlas_v3.png`, 1024×512, grade 8×4, criado com duas imagens nativas image_gen e empacotamento técnico nearest/RGB565. Originais e prompts estão em `art-source/faithful-hardware`. Plásticos usam UV planar por face para amostrar o grão ABS gerado muito sutil; marcas e painéis conservam suas UV por face. A multiplicação pela cor do material preserva as carcaças originais. Não pintar todas as faces com logos. O custo RGBA é 2 MiB por contexto que use hardware, além de 1 MiB do atlas de ambiente quando presente.

O carregamento usa nearest e clamp, sem mipmaps; PNG é decodificado uma vez por contexto GL, com atlas procedural de reserva. UV deixa margem de 3% por célula. Blender usa V invertido em relação ao bitmap Android: o exportador grava o UV Android e cria uma camada UV ativa separada para renderização correta no `.blend`. As UV padrão dos cubos são removidas antes disso. Telas, pôsteres e janela do ambiente mapeiam uma face inteira, evitando recortes dependentes da posição no mundo.

## Orçamento durante a navegação

Os wrappers usam `RENDERMODE_WHEN_DIRTY` com o relógio compartilhado dos três perfis; selecionados recebem atualização por interação. Por pedido explícito do usuário, a mídia selecionada continua girando nos três perfis: solicitações de desenho limitadas a 60/30/20 Hz em Qualidade/Equilibrado/Desempenho. Esses limites não comprovam FPS entregue. No modo Desempenho, os ambientes ainda reduzem balanços e atualizações ociosas; essa política não pausa mais cartuchos e CDs da vitrine. O anel não desenha consoles atrás do conjunto; o quarto pula consoles atrás da direção da câmera, mantendo o selecionado. Isso é descarte conservador, não teste completo de oclusão.

Texturas e malhas são carregadas/cacheadas por contexto, fora do caminho por frame. Os itens não selecionados usam PNGs RGBA 256×256 pré-renderizados das mesmas mídias, com abertura transparente para a capa dinâmica e metadados normalizados gerados junto. Não abrem contextos GL adicionais. CDs preservam o furo também na miniatura. Capas de origem até 256 pixels permanecem em 256, em vez de serem ampliadas para 512. O contexto do emulador, as molduras configuráveis e a liberação da biblioteca durante jogos continuam no fluxo existente.

Pranchas offline verificam geometria/UV, não comprovam FPS ou enquadramento em todos os aparelhos; a compilação e a inspeção visual da TV devem ser registradas junto à entrega.

Atualização de 21/11/2026: os originais ativos de ambiente e plástico estão em `art-source/theme-art-20261121`, com prompts, pesquisa online e registro de origem. Consultar `docs/GENERATED-ART-2026-11-21.md`. A grade UV e resoluções foram preservadas; apenas a célula 0 do hardware recebeu o novo ABS. Os pixels restantes, inclusive a arte Arcade aprovada, permaneceram idênticos. Executar `python tools/art/prepare_theme_art.py` antes do empacotamento Blender para reproduzir fundos e ambiente atuais. O source histórico em `art-source/pixel-atlas/source.png` continua preservado, com override ativo documentado no provenance.
