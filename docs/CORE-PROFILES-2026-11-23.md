# Auditoria de perfis de emulação — 23/11/2026

## Problemas encontrados

O perfil Qualidade retornava uma lista vazia para os núcleos originais. Isso herdava `pcsx_rearmed_drc=disabled` e, em Android ARM32, `mupen64plus-cpucore=cached_interpreter`, sem aumentar a resolução de 320×240 do N64. Desabilitar o recompilador consumia CPU sem melhorar a imagem. Vários perfis alternativos eram intencionalmente idênticos por restrições reais dos emuladores; decoração do menu não representa desempenho do jogo.

Duas configurações anteriores eram ignoradas: `mupen64plus-EnableHybridFilter` não existe no núcleo distribuído (a chave é `mupen64plus-HybridFilter`); a interpolação FM FBNeo não aceita `2-point`, apenas `disabled` ou `4-point 3rd order`. O filtro de amostras, separadamente, aceita `2-point 1st order`. O valor PSP `ppsspp_texture_scaling_level=1` também não pertence às opções declaradas; desativação usa `disabled`. A chave antiga `ppsspp_vertex_cache` não existe no binário 1.17.0 e foi removida da política.

## Políticas implementadas

`CorePerformancePolicies.kt` define ajustes explícitos para oito núcleos. O perfil Qualidade mantém recompilação onde disponível e investe processamento em resolução ou fidelidade. Os outros dois diminuem o trabalho efetivo do emulador.

| Núcleo | Qualidade | Equilibrado | Desempenho |
| --- | --- | --- | --- |
| PCSX ReARMed | Recompilador, resolução NEON 2×, Gaussian, dithering, reverb | Recompilador, resolução nativa, Gaussian, dithering, reverb | Recompilador, nativa, interpolação simples, sem dithering/reverb |
| Mupen64Plus Next | Recompilador, 640×480, filtro de 3 pontos, híbrido, LOD | Recompilador, 320×240, 3 pontos e LOD | Recompilador, 320×240, filtro padrão, sem híbrido/LOD |
| PPSSPP | JIT, 960×544, curvas High | JIT, 480×272, curvas Medium | JIT, 480×272, curvas Low |
| Genesis Plus GX | FM Nuked, filtro de áudio | FM MAME, filtro de áudio | FM MAME, sem filtro de áudio |
| Snes9x | Interpolação Gaussian | Interpolação linear | Sem interpolação |
| FBNeo | FM e amostras com 4 pontos/3ª ordem | FM 4 pontos e amostras 2 pontos/1ª ordem | Interpolações desativadas |
| MAME2003 Plus / MAME2003 | Áudio 48 kHz | Áudio 44,1 kHz | Áudio 22,05 kHz |

A escala de resolução do PCSX usa o renderizador NEON existente; não seleciona um novo contexto gráfico. As opções framebuffer/cópia de memória N64 específicas do dispositivo permanecem. Não há overclock, alteração de região, remoção de efeitos PSP ou novos frameskips compulsórios. Alto desempenho em sistemas 2D que já atingem a velocidade original pode aparecer como menor consumo e mais margem de CPU, não como FPS superior ao console.

NDS mantém as políticas validadas anteriores, inclusive DSperate com JIT e todos os quadros, sem desligar precisão de áudio nem usar HLE que já falhou. SwanStation conserva software/nativo: aumentar a escala sem validar renderização por hardware seria uma configuração sem efeito. DOS continua com ciclos automáticos: fixar ciclos só para diferenciar nomes prejudicaria jogos dependentes da velocidade. Os demais núcleos que não expõem um orçamento seguro conservam seus padrões.

`CoreVariablesManager.mergeCoreOptions` torna testável a precedência já existente: padrões → perfil → escolhas manuais. Escolhas por jogo/sistema continuam sendo resolvidas antes desse estágio. A detecção automática, preferências de núcleos, saves e molduras não foram alterados.

## Verificação

Foram baixados **apenas para inspeção**, em `build/profile-audit`, os binários ARMv7 originais de LemuroidCores/tag 1.17.0. Não substituem os núcleos do aplicativo. Conferência de 31 chaves e respectivos valores declarados nos seis núcleos principais passou; recibo local `validated-policy-options.json` contém SHA256 dos binários inspecionados. Essa verificação identifica chaves/enumerações inválidas, mas não equivale a um benchmark ou comprovação visual. Comparações de velocidade emulada precisam da mesma ROM, cena, perfil efetivo, opções manuais e aquecimento; FPS de apresentação sozinho é insuficiente.

Testes adicionados em `PerformanceProfileTest`: três orçamentos diferentes para os oito núcleos, JIT e resolução em Qualidade, valores exatos Arcade, chave híbrida válida, precedência manual e preservação da moldura. Comando de validação: `:retrograde-app-shared:testDebugUnitTest --tests '*PerformanceProfileTest'`. A execução Gradle e os testes físicos são coordenados pela tarefa principal; não declarar resultados antes da execução.

## Alternativas pesquisadas

O [índice ARMv7 oficial Libretro](https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/) consultado em 23/11/2026 oferece candidatos adicionais. Sua existência **não comprova** suporte no frontend, saves, controles, BIOS, quatro ABIs ou funcionamento nesta TV.

- GB/GBC: Gearboy, TGBDual, FixGB; NES: FixNES, RustyNES, Mesen2.
- SNES: Snes9x 2002/2005 e variantes bsnes 2014/mercury; GBA: VBA-M, Beetle GBA.
- Master System/Game Gear: SMS Plus; Mega Drive: BlastEm, Genesis Plus GX Wide.
- Lynx: GearLynx; PC Engine: Geargrafx.
- DOS: DOSBox, DOSBox SVN, DOSBox Core.
- Arcade: FBAlpha, MAME2010/2015/2016 e recortes FBA2012 CPS/NeoGeo. Cada núcleo exige ROMset correspondente; não trocar automaticamente.
- N64: Mupen64Plus Next GLES2; PS1: Beetle PSX software/hardware; Atari2600: Stella2023.

PSP não tem outra implementação Libretro independente pronta no catálogo analisado. O suporte 3DS ARM64 não torna esta TV Android ARM32 compatível. WonderSwan e NGP não devem receber núcleos sem adaptação dos controles/orientação já identificada no histórico. Não admitir todos os arquivos do buildbot indiscriminadamente: emuladores de outros sistemas, jogos standalone e módulos que dependem de recursos ausentes não são alternativas para um console suportado.

## Fontes primárias

- [LemuroidCores, versão distribuída 1.17.0](https://github.com/Swordfish90/LemuroidCores/tree/1.17.0)
- [PCSX ReARMed — opções](https://github.com/libretro/pcsx_rearmed/blob/master/frontend/libretro_core_options.h)
- [Mupen64Plus Next — opções](https://github.com/libretro/mupen64plus-libretro-nx/blob/develop/libretro/libretro_core_options.h)
- [PPSSPP 1.17.1 — opções](https://github.com/hrydgard/ppsspp/blob/v1.17.1/libretro/libretro_core_options.h)
- [Genesis Plus GX — FM e impacto de CPU](https://docs.libretro.com/library/genesis_plus_gx/)
- [Snes9x — opções](https://github.com/snes9xgit/snes9x/blob/master/libretro/libretro_core_options.h)
- [FBNeo — definições exatas de interpolação](https://github.com/libretro/FBNeo/blob/master/src/burner/libretro/retro_common.cpp)
