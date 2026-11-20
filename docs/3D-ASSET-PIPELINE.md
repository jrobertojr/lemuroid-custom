# Biblioteca 3D e atlas em pixel art

## Fonte editável e reprodução

`tools/art/build_lowpoly_hardware.py` gera geometria original com primitivas, biséis de um segmento e cilindros de poucos lados no Blender 5.2.2 LTS. Não importa malhas, logotipos ou fotografias de terceiros. O script salva `art-source/lowpoly-hardware/hardware-library.blend`, com cenas individuais editáveis e atlas incorporado, além de pranchas PNG renderizadas. Cada console tem uma silhueta própria; versões de hardware relacionadas compartilham partes, preservando telas e controles característicos.

Executar a partir do repositório:

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/art/build_lowpoly_hardware.py
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
- Materiais agrupados: duas a quatro partes nos modelos comuns; gabinete permite cinco. Limites: 600 triângulos comuns, 1.200 gabinete. Os valores reais e SHA256 de cada arquivo ficam em `art-source/lowpoly-hardware/manifest.json`.
- Em mídia, a **última parte** recebe capa com UV completo; demais partes recebem o atlas. Esse contrato é consumido por `Hardware3DRenderer`.
- O leitor rejeita contagens inválidas, dados insuficientes, valores não finitos e bytes residuais, com fallback procedural. O validador independente confere hashes, orçamento, normais, UV e base.

## Atlas e UV

`assets/textures/pixel_atlas_v2.png` tem 512×512 pixels e grade 4×2. Tiles: 0 parede nogueira; 1 piso; 2 tapete; 3 ABS neutro; 4 tela CRT; 5 pôster original; 6 janela noturna; 7 letreiro âmbar. Origem de imagem e prompt estão em `art-source/pixel-atlas`; a geometria não usa imagens geradas como referência dimensional.

O carregamento usa nearest e clamp, sem mipmaps; PNG é decodificado uma vez por contexto GL, com atlas procedural de reserva. UV deixa margem de 3% por célula. Blender usa V invertido em relação ao bitmap Android: o exportador grava o UV Android e cria uma camada UV ativa separada para renderização correta no `.blend`. As UV padrão dos cubos são removidas antes disso. Telas, pôsteres e janela do ambiente mapeiam uma face inteira, evitando recortes dependentes da posição no mundo.

## Orçamento durante a navegação

Os wrappers usam `RENDERMODE_WHEN_DIRTY` com o relógio compartilhado dos três perfis; selecionados recebem atualização por interação. No modo Desempenho, rotação e balanço decorativos param durante o repouso. O anel não desenha consoles atrás do conjunto; o quarto pula consoles atrás da direção da câmera, mantendo o selecionado. Isso é descarte conservador, não teste completo de oclusão.

Texturas e malhas são carregadas/cacheadas por contexto, fora do caminho por frame. Capas de origem até 256 pixels permanecem em 256, em vez de serem ampliadas para 512. O contexto do emulador, as molduras configuráveis e a liberação da biblioteca durante jogos continuam no fluxo existente.

Pranchas offline verificam geometria/UV, não comprovam FPS ou enquadramento em todos os aparelhos; a compilação e a inspeção visual da TV devem ser registradas junto à entrega.
