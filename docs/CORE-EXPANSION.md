# Expansão de núcleos — 19/11/2026

## Escolha persistente e restauração

A escolha manual fica salva por sistema. Leituras não escrevem mais padrões sobre preferências concorrentes; a TV confirma a gravação antes de agendar a atualização do núcleo, e a seleção móvel acompanha o valor persistido. Núcleos removidos ou inválidos têm fallback seguro.

O botão **Restaurar núcleos padrão**, no início da tela de seleção de núcleos da TV e do celular, restaura os padrões originais anteriores à expansão. A tabela está fixada em `CoreSelectionDefaults.kt`, sem depender da ordem da lista ou dos novos núcleos. O tratamento existente de migração NDS foi preservado: MelonDS normalmente, DeSmuME se há saves antigos pendentes. O botão não apaga saves nem altera moldura, modo de desempenho ou opções manuais de cada núcleo. A escolha DSperate observada na TV é preservada durante atualização; somente o botão de restauração muda escolhas deliberadamente.

## Catálogo

Foram incluídos **18 núcleos novos para 18 sistemas**, além dos cinco núcleos NDS já existentes. Os nomes indicam foco em desempenho, equilíbrio, precisão ou compatibilidade quando pertinente. Isso indica uma característica do emulador, não um resultado medido nem uma promessa de FPS.

- Arcade: FBA 2012, MAME 2000 e MAME 2003, além de permitir escolher FBNeo/MAME 2003-Plus nas duas categorias.
- NES: QuickNES e Nestopia; SNES: Snes9x 2005 Plus e 2010.
- Atari 2600: Stella 2014; Mega Drive/Sega CD: PicoDrive.
- Master System: PicoDrive/Gearsystem; Game Gear: Gearsystem.
- Game Boy/Color: SameBoy; GBA: gpSP/VBA Next.
- N64: ParaLLEl N64; PlayStation: SwanStation.
- Lynx: Beetle Lynx; PC Engine: Beetle PCE; Neo Geo Pocket/Color: RACE.

PSP, 3DS, Atari 7800, WonderSwan/Color e DOS continuam com seus núcleos atuais. Não foi admitida uma alternativa sem binário/integração/controles verificados. MesenCE foi pesquisado para WonderSwan e rejeitado após verificar suporte incompleto ao segundo direcional e à orientação vertical. Citra continua incompatível com esta TV ARM de 32 bits.

As 72 bibliotecas para quatro ABIs vêm do buildbot oficial Libretro. Os hashes, origem, versões, licenças, cobertura e ferramentas de restauração estão em `lemuroid-core-alternatives/README.md`, `manifest.json` e `SOURCES.md`. Não há URL de download inventada. As bibliotecas são incluídas nos APKs Free e Play e detectadas pelo caminho de bibliotecas nativas já existente.

## Arcade, controles e perfis

FBA 2012 exige ROMset 0.2.97.29; MAME 2000 exige 0.37b5; MAME 2003 exige 0.78. FBNeo e MAME 2003-Plus têm conjuntos próprios. O nome do ZIP não basta para garantir compatibilidade. Não há troca automática, conversão nem download de ROMs. Um núcleo mais antigo pode ser mais leve e suportar menos jogos.

SwanStation usa IDs/descritores próprios de controle digital/DualShock, em vez dos descritores PCSX. PicoDrive recebe opções de seis botões nos sistemas Mega Drive/Sega CD, pois seu controle não é escolhido apenas pelo dispositivo Libretro.

Os três modos e sua seleção automática permanecem. gpSP usa JIT e saída sem frameskip; ParaLLEl usa Rice/JIT e resolução conforme o modo; SwanStation usa software/nativo por compatibilidade; DOSBox Pure usa ciclos automáticos. Os demais núcleos mantêm opções próprias, com apresentação do frontend seguindo o modo. Preferências manuais e a moldura continuam sendo respeitadas. Detalhes em `ALTERNATIVE-CORE-PROFILES.md`.

## DOS no pendrive

Somente os cinco jogos solicitados foram copiados para `/storage/620F-B493/DOS`: Dave Goes Nutz, Dangerous Daves Risky Rescue, Dangerous Dave in the Haunted Mansion, Bio Menace e Ecstatica. Os ZIPs completos totalizam 136845500 bytes e tiveram SHA-256 verificado após a cópia. Nenhum arquivo existente foi apagado. O scanner identifica o diretório DOS e mantém o pacote completo para DOSBox Pure. Detalhes em `DOS-LIBRARY.md`.

## Verificação técnica

Todos os 18 novos binários ARMv7 carregaram na TV com API/símbolos Libretro e metadados válidos, em processos isolados. Isso verifica carregamento/dependências, não todos os jogos. Binários dos outros três ABIs passaram por validação ELF e SHA-256; não foram executados em outros dispositivos.

Backup anterior aos testes: `build/core-expansion/saves-before-expansion`. A primeira instalação foi recusada por espaço interno insuficiente. Apenas cópias temporárias dos benchmarks criadas nesta sessão e nas sessões NDS foram removidas; ROMs do pendrive, saves e preferências foram preservados.

## Resultado final na TV

- Compilação completa para quatro ABIs, compilação Play Kotlin e 14 testes unitários aprovados (8 de perfis, 3 de padrões/persistência e 3 de reconhecimento DOS).
- O APK universal de 212 MB foi recusado por falta de espaço temporário. Foi adicionado o parâmetro opcional `-PlemuroidAbis=armeabi-v7a` para gerar APK específico de cerca de 74,6 MB. Sem esse parâmetro, o build conserva as quatro arquiteturas. Esse APK foi instalado com sucesso, preservando dados.
- Verificação real encontrou inicialização de ListPreference que anulava apenas a apresentação do valor salvo ao anexar à tela. Corrigida aplicando o valor após anexar. Tela final mostra o núcleo persistido, sem “Não definido”.
- QuickNES escolhido pela UI, 1943 iniciado com imagem correta; após encerrar/reiniciar o aplicativo, a escolha permaneceu QuickNES na preferência e no resumo da TV.
- Botão Restaurar acionado na TV: resumo Nintendo voltou a FCEUmm. A preferência de Qualidade e moldura Auto que estavam ativas naquele momento foram preservadas. Leituras isoladas de prefs.data podem estar desatualizadas enquanto houver transações Harmony; verificar o valor pelo aplicativo/arquivo de transações.
- Após a verificação, DSperate foi novamente escolhido para NDS e FBNeo para a categoria original FBNeo; Nintendo ficou com FCEUmm, como antes do teste.
- Todos os cinco jogos DOS foram cadastrados no banco com systemId `dos` (IDs observados 830–834). Dave Goes Nutz abriu o menu DOSBox Pure e enumerou DAVE.EXE, DAVEGAME.EXE, DOCSHELL.EXE e START.BAT de dentro do ZIP. Isso comprova montagem do pacote; não foi realizada uma partida completa dos cinco títulos.
- Double Dragon II (World), id 416, abriu no FBA 2012 com imagem correta e moldura Auto. SurfaceFlinger registrou 127 apresentações em uma amostra a 60,00 FPS na sequência de abertura/atração. Não é benchmark de todos os jogos, nem prova de ganho relativo ao FBNeo.
- Evidências: `build/core-expansion/*`, inclusive cópias de telas/medidas e manifesto de transferência. Nenhuma ROM ou BIOS foi baixada.

## Submenu Arcade

Em atualização posterior no mesmo dia, as opções passaram a agrupar os sistemas arcade em **Alterar Núcleos → Arcade → FBNeo / MAME2003 Plus**, na TV e no celular. Cada grupo conserva sua preferência independente (`pref_key_core_selection_fbneo` e `pref_key_core_selection_mame2003plus`). A identificação da biblioteca permanece no `Game.systemId`, e `GameLauncher` usa esse ID para consultar o núcleo ao abrir o jogo; a categoria visual Arcade não substitui a identificação individual.

Teste real na TV: escolher FBA 2012 sob FBNeo atualizou apenas esse resumo; MAME2003 Plus continuou em MAME2003 Plus. O seletor retornou ao próprio submenu Arcade após a escolha. As escolhas anteriores FBNeo e MAME2003 Plus foram restauradas depois do teste. Os rótulos continuam informando o ROMset exigido. Essa divisão segue os dois sistemas atualmente identificados pelo banco do projeto; não identifica novas placas CPS/Neo Geo nem converte ROMsets.

O botão Voltar do submenu retorna à seleção de núcleos; no celular há também uma opção Voltar. Padrões, modos, moldura e arquivos de jogos/saves não foram alterados por essa organização.

Validação final do submenu: APK ARMv7 compilado e instalado; entrada Arcade mostrou os dois grupos com suas escolhas originais. Voltar retornou a Alterar Núcleos, e o próximo Voltar retornou à biblioteca. Evidências locais `build/core-expansion/arcade-final-child.xml`, `arcade-final-back-test.xml`, `arcade-independent-choices.xml` e `build-arcade-navigation.log`. A interface móvel foi compilada; não houve teste visual em telefone.
