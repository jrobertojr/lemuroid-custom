# Revisão dos modelos por referências reais

O usuário rejeitou os modelos anteriores por proporções genéricas e pediu nova inspeção de todos. As seis referências iniciais em pixel art e quatro colagens adicionais orientam a linguagem visual. Fotografias dos aparelhos foram usadas para conferir os objetos, sem incorporar essas fotos no aplicativo. O Arcade foi aprovado expressamente e seu gabinete e materiais foram preservados.

As geometrias são construídas no Blender 5.2.2 LTS com contornos próprios e faces planas. Curvas existentes nos aparelhos são aproximadas por poucos segmentos; não são substituídas por caixas, nem recebem arredondamento genérico. São representações lowpoly, não CAD dimensional. Não afirmar que são réplicas idênticas ou que o número de triângulos comprova fluidez.

## Variantes conferidas

| Família | Variante de referência e principais pontos |
| --- | --- |
| NES | NES-001 frontal: altura das duas metades, porta frontal e sua inscrição, faixa/ventilação direita e conectores verticais |
| SNES | SNS-001 americano com teclas roxas, trilhos frontais, EJECT central comprido e dois conectores |
| N64 | NUS-001 carvão: lombo integrado, quatro conectores frontais, slot, ventilação e tampa de expansão |
| Mega Drive | Mega Drive I ocidental: escolha explícita do usuário também para o alias Genesis; disco deslocado, volume/POWER/RESET à esquerda, 16-BIT dourado, marca inferior direita |
| Sega CD | Base frontal modelo 1 sob o Mega Drive; não modelo 2 lateral |
| Master System | Modelo I ocidental: wedge e painel vermelho na metade direita, leitor de cartão e cartucho |
| Atari 2600 | Heavy sixer de seis chaves, painel inclinado frisado e madeira frontal |
| Atari 7800 | CX7800: quatro teclas e painel metálico multicolorido |
| PC Engine | PI-TG001 branco: berço aberto HuCard, POWER verde esquerdo e PAD direito |
| PlayStation | SCPH-1001 cinza: proporção da caixa, tampa circular, POWER/RESET/OPEN, slots e portas |
| DOS | IBM PC 5150 com duas unidades de 5,25 lado a lado e monitor CRT de época; mídia disquete representa o acervo, não o formato de cada jogo |
| Game Boy / Color | DMG-01 e CGB-001: lentes, contornos, alto-falantes, controles e LCD distintos |
| GBA | AGB-001 índigo: contorno frontal, lente e tela 3:2, botões cinzas, Start/Select esquerdos, speaker direito e ombros integrados |
| DS / 3DS | NTR-001 e CTR-001 originais: clamshell, telas próprias, alto-falantes e teclas específicas |
| Game Gear | 2110 original: proporções largas, lente central, speaker esquerdo e controles direitos |
| Lynx | Lynx II cinza: quatro botões de ação e teclas de opções |
| Neo Geo Pocket / Color | NGP e NGPC: click-stick exclusivo e LCD quase quadrado; `ngc` não é GameCube |
| WonderSwan / Color | SW-001 e Color: dois conjuntos de quatro teclas separadas e comandos inferiores |
| PSP | PSP-1000: tela 480:272, contorno, controles, ombros e UMD traseiro |
| Arcade | Gabinete vermelho e laranja inspirado na referência do usuário, aprovado e mantido |

As 24 mídias têm revisão própria em [media-reference-audit.md](../art-source/faithful-hardware/media-reference-audit.md), incluindo as fontes consultadas e os limites das representações.

## Fontes fotográficas

- [SNES SNS-001](https://www.ebay.com/itm/405954071835), [N64 NUS-001](https://gametrog.com/nintendo-64-n64-information-specs/), [Mega Drive I](https://retrosales.com.au/products/console-sega-mega-drive-genesis-1-60hz), [Sega CD 1](https://consolemods.org/wiki/Genesis:Sega_CD_Model_Differences).
- [Master System I](https://full-set.net/console/mastersystem.html), [Atari 2600](https://www.ebay.com/itm/177386551877), [Atari 7800](https://videogamecritic.com/7800.htm), [PC Engine](https://www.juicygamereviews.com/retro-game-reviews/pc-engine-a-forgotten-gem).
- [NES frontal](https://judge.me/reviews/stores/bitjumpgaming.com/products/upgraded-nes-nintendo-entertainment-system), [PlayStation SCPH-1001](https://securis.ws/products/sony-playstation-1-scph-1001-console-w-power-cord-1), [IBM PC 5150](https://computers.popcorn.cx/ibm/pc/).
- [Foto oficial de lançamento do GBA](https://game.watch.impress.co.jp/docs/20010116/nintendo.htm), [Game Boy](https://www.geekinventory.co.nz/products/original-gameboy-dmg-01-fat-gameboy), [Game Boy Color](https://collection.sciencemuseumgroup.org.uk/objects/co8184137/nintendo-game-boy-color-model-cgb-001).
- [Game Gear](https://www.gamejenie.com/product-page/sega-game-gear-system-recapped), [Lynx II frontal](https://commons.wikimedia.org/wiki/File:Atari-Lynx-II-Front-Flat.png), [NGP frontal](https://commons.wikimedia.org/wiki/File:Neo_Geo_Pocket_Flat_Front.jpg), [NGPC](https://www.lukiegames.com/Blue-NeoGeo-Pocket-Color-System).
- [WonderSwan](https://consoledictionary.com/page.php?pid=33&t=console), [WonderSwan Color](https://commons.wikimedia.org/wiki/File:Wonderswan_color-JD.jpg), [PSP-1000](https://superretro.com.au/products/06c2f1bf-e97e-11e9-efcf-9650e5e6bfa3), [DS NTR-001](https://ec.treasure-f.com/item/1005619844051005), [3DS CTR-001](https://ec.treasure-f.com/item/1000391488905000).

## Texturas e reprodução

Atlas nativo image_gen com originais e prompts preservados. O modelo de geração não é exposto pela ferramenta; não foi alegada versão específica. Os faceplates foram corrigidos após comparar as fotos. O empacotador admite apenas as células modificadas, conservando as células aprovadas do Arcade. O atlas do ambiente permanece separado.

Comandos, contrato L3D2, orçamento e carregamento estão em [3D-ASSET-PIPELINE.md](3D-ASSET-PIPELINE.md). O manifesto contém hashes, partes, triângulos e limites reais de cada uma das 48 malhas. `inspection/` contém vistas frontal e de três quartos para conferência, não texturas de execução. Miniaturas estáticas 256×256 usam os mesmos modelos e mantêm as capas dinâmicas sem abrir contextos GL por cartão.

Perfis de desempenho, seleção de núcleo, saves e molduras laterais não são alterados por esta revisão de arte. Compilação, validação e inspeção na TV precisam ser registradas após a última regeneração.

## Correções durante a conferência na TV

O primeiro teste revelou faces de etiquetas e detalhes invertidas pelo recálculo de normais de superfícies abertas. O Blender as mostrava dos dois lados, mas o OpenGL com GL_BACK ocultava a frente. O gerador agora recalcula volume apenas em sólidos fechados e conserva o winding explícito de placas/anéis. O validador exige normais frontais na parte de capa, evitando repetir o cartucho sem etiqueta.

Em 21/11 o usuário determinou que cartuchos não parem de girar: a vitrine mantém rotação em todos os perfis, com cadência limitada a 60/30/20 Hz; o lifecycle continua suspendendo desenhos em segundo plano. Nenhuma regra de emulação ou moldura é alterada.

A inspeção também reproduziu uma pausa indevida na criação da GLSurfaceView: o descarte do efeito anterior lia a referência mutável já apontando para a superfície nova. Abrir/fechar Configurações retomava o giro, confirmando o vínculo com lifecycle. O novo vínculo captura cada vista, a retoma imediatamente se a Activity já está RESUMED e pausa somente a instância correspondente no descarte; aplica-se às cinco vistas 3D compartilhadas.

O teste de rotação na TV passou na entrada inicial e em Desempenho/Equilibrado/Qualidade, por comparação de duas capturas da região exclusiva da mídia após repouso: 29.394/21.239/25.653/47.529 pixels alterados acima do limiar8 no ensaio de21/11. Isso comprova movimento, não mede FPS. O shader da vitrine também foi corrigido para multiplicar textura pela cor do material, como o renderer dos consoles; antes, o atlas neutro apagava a distinção de frisos/carcasas. O teste visual dessa última correção é registrado ao concluir.

Validação final: `assembleFreeDynamicDebug` ARMv7 passou (29s no último build); 48 malhas e24 miniaturas passaram no validador independente. O APK final instalado e o artefato local têm SHA256 `6d4a29d0c81c689e2294a9606838067c834ed5119227d012ed19856718f8d4fe`. Conferência do shader na TV confirmou frente com capa e frisos escuros visíveis, com rotação após entrada inicial. Preferências observadas/restauradas: Sala de Consoles, Qualidade, molduras desativadas; sem alterar núcleo, saves ou preferências por jogo. Não foi medido ganho de FPS; os testes de movimento e navegação não comprovam60FPS sustentados.

A conferência dos quatro consoles prioritários foi feita no tema Sala de Consoles. A grelha N64 e a borda das entradas DE-9 do Mega Drive receberam winding explícito e guardas de exportação para sobreviver ao culling. As bordas internas do furo do CD também apontam para a cavidade. Arcade mantém silhueta e materiais aprovados. A TV voltou a DOS01/11 no tema original ao fim da navegação.
