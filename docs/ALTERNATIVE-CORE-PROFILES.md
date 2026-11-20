# Perfis dos cores alternativos

Os três perfis, a detecção automática e a prioridade das opções manuais continuam no fluxo existente. Nenhum ajuste de core altera a moldura. Os cores que não precisam de opções específicas conservam os padrões próprios e recebem as diferenças de apresentação do frontend conforme o perfil.

| Core | Qualidade | Equilibrado / Desempenho |
| --- | --- | --- |
| gpSP | Recompilador ligado, sem frameskip, correção de cores ligada | Recompilador ligado, sem frameskip, correção de cores desligada |
| ParaLLEl N64 | Rice, recompilador dinâmico, 640×480 | Rice, recompilador dinâmico, 320×240 |
| SwanStation | Recompilador, software, resolução nativa, PGXP desligado | Mesma configuração portátil; filtros do frontend seguem o perfil |
| DOSBox Pure | Ciclos automáticos | Ciclos automáticos |

SwanStation não recebe Vulkan, resolução multiplicada ou PGXP automaticamente: essas opções dependem de um renderizador por hardware validado no frontend. A escala interna do core é uma opção do renderizador por hardware, portanto aumentá-la em software não seria uma melhoria real de qualidade. Isso não impede uma configuração manual validada posteriormente.

No DOSBox Pure, fixar uma velocidade arbitrária para todos os jogos pode alterar a lógica e a velocidade dos títulos antigos. `auto` permite ao core identificar a necessidade do programa; o perfil não impõe overclock nem frameskip.

As chaves gpSP (`gpsp_drc`, `gpsp_frameskip`, `gpsp_color_correction`), ParaLLEl (`parallel-n64-gfxplugin`, `parallel-n64-cpucore`, `parallel-n64-screensize`) e SwanStation (`swanstation_CPU_ExecutionMode`, `swanstation_GPU_Renderer`, `swanstation_GPU_ResolutionScale`, `swanstation_GPU_PGXPEnable`) foram conferidas também nas strings dos binários ARMv7 adicionados ao projeto. Isso valida a existência das opções, não comprova desempenho em jogos reais.

Referências primárias consultadas:

- [gpSP — opções oficiais](https://github.com/libretro/gpsp/blob/master/libretro/libretro_core_options.h)
- [ParaLLEl N64 — documentação Libretro](https://docs.libretro.com/library/parallel_n64/)
- [SwanStation — opções oficiais](https://github.com/libretro/swanstation/blob/main/src/libretro/libretro_core_options.h)
- [DOSBox Pure — documentação Libretro](https://docs.libretro.com/library/dosbox_pure/)

`PerformanceProfileTest` verifica recompiladores, saída sem frameskip do gpSP, resoluções por perfil, ausência de seleção gráfica incompatível automática, ciclos DOS automáticos, ausência de chaves duplicadas e preservação da moldura para todos os cores/perfis. Medições na TV devem ser registradas à parte.
