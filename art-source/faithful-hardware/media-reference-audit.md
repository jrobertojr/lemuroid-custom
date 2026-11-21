# Revisão das 24 mídias — 20/11/2026

Revisão do gerador `tools/art/build_faithful_hardware.py`, função `media`, com
fotografias de objetos reais como referência. As proporções são normalizadas
para apresentação; não constituem CAD de fabricação nem prova de identidade
dimensional. Fotos externas ficam apenas como referência de consulta: nenhuma
arte de jogo dessas páginas foi copiada para os arquivos distribuídos.

As capas são preenchidas em execução pela biblioteca do usuário. A superfície
de capa continua sendo a última parte do arquivo L3D2. Não há bevel suavizado.

| Sistema | Variante e inspeção | Correção nesta revisão |
| --- | --- | --- |
| NES | Cartucho oficial NES ocidental, não Famicom | Relação largura/altura, sete canais verticais à esquerda, etiqueta deslocada à direita, linha inferior e abertura do conector |
| SNES | SNS-006 americano, correspondente ao console US | Etiqueta na parte superior; cinco sulcos horizontais em cada asa, parafusos inferiores, painel central inferior; removidos falsos sulcos verticais |
| N64 | NUS-006 cinza | Perfil superior arqueado por segmentos, etiqueta central alta e estreita, duas divisões verticais das asas |
| Mega Drive / Genesis | Cartucho padrão Sega, não EA nem Sonic & Knuckles | Corpo largo e baixo, etiqueta e sulcos laterais horizontais; abertura inferior |
| Sega CD | CD de 120 mm | Anéis e espessura, abertura central passante, frente impressa e verso prateado |
| Master System | Cartucho padrão Sega ocidental | Corpo horizontal; etiqueta na faixa superior, frisos logo abaixo; removida etiqueta quadrada genérica |
| Game Boy | Cartucho DMG cinza | Entalhe da trava de alimentação, área da etiqueta, frisos laterais e abertura inferior |
| Game Boy Color | Cartucho exclusivo CGB, representação opaca do casco fumê | Silhueta superior própria sem entalhe DMG; representação leve sem transparência em tempo real |
| Game Boy Advance | AGB-002 padrão | Proporção curta e larga, aba superior, etiqueta baixa, guias e conector traseiro |
| Nintendo DS | NTR-005 | Canto superior chanfrado, etiqueta e 17 contatos na face traseira |
| Nintendo 3DS | CTR-005 | Aba lateral para impedir inserção em DS, corpo branco, 17 contatos traseiros |
| PSP | UMD, não caixa de varejo | Disco circular quase do tamanho do caddy, aro branco, cubo metálico e janela traseira; removida antiga etiqueta retangular |
| Game Gear | Cartucho padrão Sega | Corpo vertical, ombros/aba superior, etiqueta vertical, abertura inferior |
| Atari 2600 | Casco padrão Atari de primeira parte | Corpo mais alto que largo, espessura e tampa inferior, dobra superior de etiqueta e frisos traseiros |
| Atari 7800 | Casco padrão Atari de primeira parte | Mesma família física compatível, área de etiqueta e tampa inferior próprias; não pretende cobrir cascos de todas as editoras |
| PC Engine | HuCard branca, não cartucho volumoso | Espessura de cartão; etiqueta abaixo da área de 38 contatos frontais superiores |
| Atari Lynx | Cartão com lábio de extração | Corpo fino e vertical, 34 contatos frontais inferiores e lábio; removido trapézio horizontal genérico |
| Neo Geo Pocket | Cartucho SNK padrão | Corpo largo/baixo, etiqueta, seis sulcos superiores e área inferior de marcação |
| Neo Geo Pocket Color | Mesmo formato SNK da família Pocket | Conservada compatibilidade da silhueta; não é cartucho GameCube |
| WonderSwan | Cartucho Bandai monocromático | Corpo largo/baixo, PCB e 48 contatos frontais inferiores; removida silhueta quadrada com entalhe |
| WonderSwan Color | Cartucho Bandai da mesma família física | Proporção e conector próprios preservados; não é cartucho Game Boy |
| DOS | Disquete de 3½ polegadas | Espessura reduzida, shutter, etiqueta, janelas inferiores e cubo metálico traseiro |
| PlayStation | CD preto de PlayStation | Abertura central real, verso preto, frente impressa, aro físico; frente não é o verso escuro |
| Arcade | Gabinete já aprovado pelo usuário | Fluxo de mídia mantido: mesmo gabinete, capa dinâmica somente na tela; não alterado nesta revisão |

## Fontes visuais e técnicas

- Fotografias e catálogo de objetos da coleção do [Museum of Obsolete Media](https://obsoletemedia.org/data/rom-cartridges-and-cards/), incluindo [SNES americano](https://obsoletemedia.org/snes-north-american/), [Mega Drive](https://obsoletemedia.org/sega-mega-drive/), [GBC](https://obsoletemedia.org/nintendo-game-boy-color/), [NGP](https://obsoletemedia.org/neo-geo-pocket-pocket-color/), [WonderSwan](https://obsoletemedia.org/bandai-wonderswan/), [Lynx](https://obsoletemedia.org/atari-lynx/), [HuCard](https://obsoletemedia.org/pc-engine-hucard/), [DS](https://obsoletemedia.org/nintendo-ds-ds-lite-dsi/) e [3DS](https://obsoletemedia.org/nintendo-3ds/).
- Foto frontal efetivamente inspecionada de [N64 na iFixit](https://www.ifixit.com/Device/Nintendo_64_Cartridges): central estreita, etiqueta alta, asas largas.
- Foto frontal efetivamente inspecionada de [SNES americano, Lamborghini American Challenge](https://theexchange.com/products/9149304088): frisos das asas e painel inferior. Referência do casco, sem reutilização da arte da etiqueta.
- Foto frontal efetivamente inspecionada de [WonderSwan, Clock Tower](https://gamesdb.launchbox-app.com/games/images/27814-clock-tower-for-wonderswan): relação largura/altura e PCB exposto. Referência do casco, sem reutilização da arte da etiqueta.
- Foto frontal efetivamente inspecionada de [Lynx, Todd's Adventure in Slime World](https://retromtl.com/en/products/todds-adventure-in-slime-world-atari-lynx): cartão fino e contatos expostos. Referência do casco, sem reutilização da arte da etiqueta.
- Foto de objeto do fotógrafo [Evan-Amos, Master System](https://commons.wikimedia.org/wiki/File:Sega-Master-System-Game-Cartridge-1.jpg).
- [UMD fotografado pela TechnoEdge](https://www.techno-edge.net/article/2023/01/07/691.html): disco circular dentro do caddy branco, não caixa de varejo.
- [iFixit, famílias Nintendo](https://www.ifixit.com/Device/Nintendo_Game_Cartridge): distinções de formato entre NES, SNES, N64 e portáteis.
- Contatos, conferidos para não inventar desenho eletrônico: [DS — 17](https://www.hardwarebook.info/DS_Game_Card), [3DS — 17](https://3dbrew.org/wiki/Gamecards), [WonderSwan — 48](https://blog.tauwasser.eu/wonderswan-cartridges/), [Lynx — 34](https://atarilynxvault.com/pages/atari-lynx-cartridge-pinout), [HuCard — 38](https://pce.nesdev.org/wiki/HuCard_pinout).

## Limites da validação desta revisão

O código passou por compilação sintática Python. A exportação Blender, a contagem
real de triângulos/partes e a validação no renderizador são registradas pela etapa
integrada; esta nota não substitui esses resultados. O gabinete Arcade permanece
conforme a aprovação do usuário.
