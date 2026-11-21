# Referências e origem — 21/11/2026

Artes originais produzidas pela ferramenta nativa `image_gen`. O identificador do modelo não é exposto; não se afirma uma versão específica de DALL-E. Nenhum bitmap das páginas abaixo foi empacotado no aplicativo. São referências de pesquisa visual; a composição e as cenas geradas são interpretações originais.

| Tema/material | Direção adotada | Referências online consultadas |
|---|---|---|
| Essencial e Vitrine 3D | Arquivo/museu de jogos, composição silenciosa, expositores e luz fria | [V&A — Videogames](https://www.vam.ac.uk/exhibitions/videogames) |
| Locadora | Prateleiras, caixas VHS, balcão e iluminação âmbar de comércio de bairro | [História e documentação das locadoras](https://thevhsfiles.com/the-video-rental-era), [registro fotográfico de locadora recriada](https://www.houstonchronicle.com/life/article/Hitting-rewind-Collector-looks-to-share-VHS-11273751.php) |
| CineArcade | Contraste escuro, vinho, projeção e materiais de cinema; sem logos de serviço | [Referência de contraste/identidade cinematográfica](https://brand.netflix.com/en/assets/logos/) — nenhum logo copiado |
| Sala de Consoles, Canal Retrô e ambiente UV | Madeira, tecidos, aparelhos analógicos e luz doméstica quente | [Museum of the Home — TV c.1980](https://collections.museumofthehome.org.uk/object17703), [Science Museum — Trinitron](https://collection.sciencemuseumgroup.org.uk/objects/co8066774/sony-trinitron-colour-television-1969-1970) |
| Noite Neon | Interpretação original de retrowave: horizonte, montanhas, magenta/ciano | [WIRED — história visual de Out Run](https://www.wired.com/story/out-run-video-game-design/) |
| Lado B | Couro, jeans, bandana, cabelos volumosos, pôster fictício e luz de camarim | [Pesquisa e prompt da arte preservada](../hard-rock-vinyl/PROVENANCE.md) |
| Palco de Aço | Caixas de som, metal marcado, treliças e iluminação vermelha; sem bandas | [Arquivo de design gráfico de capas do período](https://peoplesgdarchive.org/item/20906/megadeth-peace-sells-but-whos-buying), [análise visual do gênero](https://kaosmetal.com/magazine/essentials/best-thrash-album-covers) — sem copiar capas/personagens |
| Acabamento plástico e fidelidade do hardware | ABS sutil, cores originais preservadas pelo tint; sem alterar contornos | [SEGA — Mega Drive](https://www.sega.jp/history/hard/megadrive/), [Science Museum — GBA AGB-001](https://collection.sciencemuseumgroup.org.uk/objects/co8184138/nintendo-game-boy-advance-model-agb-001), [Nintendo — história do hardware](https://www.nintendo.com/en-gb/Hardware/Nintendo-History/Nintendo-History-625945.html) |

`theme-prompts.json` e `prompts.json` contêm os prompts exatos; `asset-register.csv` registra IDs de geração e originais. As fontes PNG permanecem aqui, fora do APK. O manifesto identifica derivados por SHA256.

Os oito novos fundos são WebP estáticos, 1280 × 853, total de 641.814 bytes. Lado B reutiliza o fundo gerado e já integrado. A textura de ambiente é reduzida para 512²/RGB565; plástico ocupa a célula neutra existente de 128² no atlas 1024 × 512. Marcas/faceplates e as quatro células de arte Arcade permanecem iguais às aprovadas.

Reprodução técnica: `python tools/art/prepare_theme_art.py`, seguido dos scripts Blender `pack_hardware_atlas.py`, `build_faithful_hardware.py` e `render_hardware_previews.py`. Não há chamadas pagas de API ou geração durante a execução do app. A geometria é modelada/exportada no Blender; a IA produziu bitmaps, não malhas 3D.
