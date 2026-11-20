# DSperate e retorno à biblioteca — 19/11/2026

## Integração

DSperate foi adicionado como quinto core NDS, compilado do fonte para armeabi-v7a, arm64-v8a, x86 e x86_64. Origem, commits e adaptações estão em `lemuroid-core-dsperate/README.md`. Na TV de referência usa ARMv7, JIT e NEON, renderização por software e ThinLTO. Os perfis PGO GCC do upstream não são usados.

Qualidade, Equilibrado e Desempenho mantêm JIT ativo, frameskip desativado e Slot 2 vazio. Os ajustes gerais do aplicativo continuam seguindo os três perfis, inclusive sua seleção automática existente. A preferência de moldura não é substituída. O core suporta tela superior, inferior, ambas na vertical e ambas na horizontal, inclusive atualização em execução e o atalho R2 existente. Saves crus são migrados pela integração NDS; estados ficam separados por core.

A primeira execução experimental falhou com SIGBUS no renderizador NEON: a máscara de pixels podia começar em um endereço sem alinhamento de 4 bytes, mas era escrita como u32 alinhado. Duas gravações foram substituídas por memcpy de 4 bytes, preservando o NEON. Os resultados abaixo são posteriores à correção.

## Testes nativos na TV

Mesmo programa comparativo dos cores anteriores, 720 frames de aquecimento e 600 medidos, sem limitador, sem interface Android e em diretórios de save isolados. ROM original no pendrive, cópia temporária para leitura pelo processo de teste. Estes valores medem capacidade do core e não representam FPS limitado do aplicativo.

| Jogo | Perfil | Quadros emulados/s | Imagens novas/s | Áudio não nulo | Estado salva/restaura |
|---|---|---:|---:|---|---|
| New Super Mario Bros | Qualidade | 72,27 | 72,27 | Sim | Sim/Sim |
| New Super Mario Bros | Equilibrado | 67,04 | 67,04 | Sim | Sim/Sim |
| New Super Mario Bros | Desempenho | 72,29 | 72,29 | Sim | Sim/Sim |
| Castlevania Order of Eclesia | Desempenho | 92,35 | 92,35 | Não, sequência de inicialização | Sim/Sim |

Alvo Libretro reportado: 59,826 FPS. SRAM de 8192 bytes exposta em ambos os jogos. Captura de Mario conferida visualmente. O resultado de Castlevania não comprova desempenho ou áudio durante uma fase: a sequência automática permaneceu em inicialização silenciosa.

Comparação anterior de Mario no mesmo teste: NooDS 24,90 imagens/s; DeSmuME 2015 Desempenho 34,96 quadros emulados/s e 11,65 imagens/s. A melhora do DSperate é substancial sem descarte forçado de imagens. Variações entre os três testes DSperate não indicam ajustes diferentes do core: as opções são iguais e há variação de carga/temperatura do aparelho.

## Teste no aplicativo instalado

APK freeDynamicDebug compilado e instalado na TV com os quatro ABIs incluídos. Logs confirmam core DSperate, JIT enabled, frameskip disabled, slot2 empty e inicialização do áudio. Capturas confirmam imagem correta e moldura lateral Auto. Layout vertical e superior foram observados, com mudança em execução.

SurfaceFlinger: amostra inicial de 126 apresentações a 60,00 FPS; cenas animadas de abertura tiveram amostras de 43,45 e 51,78 FPS. Uma amostra posterior durante uma fase do Mario registrou 126 apresentações a 60,00 FPS, com imagem de gameplay conferida. Quatro janelas adicionais durante gameplay registraram 60,00 FPS, com 126–127 amostras por janela e intervalo máximo de apresentação de 16,67 ms. Essas janelas curtas não excluem travamentos fora delas. Apresentações SurfaceFlinger não demonstram por si só que todos os quadros são únicos.

O usuário confirmou na TV: "Esta jogavel, funcionando muito bem comparado aos outros mas ainda tem uns travamentos pontuais". Portanto, é jogável e significativamente melhor, mas não se deve prometer 60 FPS constantes, áudio perfeito em todo jogo nem fluidez em qualquer core. DSperate ficou selecionado na TV em Desempenho; não houve alteração global das regras de seleção de cores ou dos perfis automáticos.

Backup de saves antes do teste: `build/tv-performance/saves-before-dsperate`, 11 arquivos, 911765 bytes. Saída normal de Mario registrou `game=482 core=DSPERATE writingSram=true writingAutoSave=true`.

## Retorno à seleção

Em Equilibrado/Desempenho, MainTVActivity removia a composição da biblioteca em segundo plano para liberar RAM. Estados locais de navegação eram perdidos ao sair do jogo. Agora um SaveableStateHolder conserva os estados de navegação enquanto a interface continua sendo liberada. Filtro, console, posição linear e linha/coluna Netflix usam rememberSaveable. A identidade do filtro é estável mesmo quando Recentes aparece na lista de categorias. O jogo lançado é guardado pelo ID para reencontrá-lo quando Recentes muda de ordem; uma navegação deliberada limpa esse marcador.

A interface temática móvel também conserva categoria, console e seleção. Atalhos externos retornam à biblioteca existente com CLEAR_TOP/SINGLE_TOP, com fallback da detecção de TV quando o resultado não traz esse dado.

Verificação real na TV, tema TV e modo Desempenho: categoria Nintendo DS, Mario na posição 3/4; iniciar pela lista e sair com Voltar devolveu à categoria Nintendo DS e Mario 3/4, sem voltar a Todos. Save automático foi confirmado no log. Outros temas e telefone foram compilados, sem teste visual individual nesta sessão. A volta via atalho externo foi implementada, mas não foi exercitada nesta etapa.

## Verificações de compilação

- `:retrograde-app-shared:testDebugUnitTest`: 6 testes, zero falhas/erros, incluindo os três perfis DSperate.
- `:lemuroid-app:assembleFreeDynamicDebug`: sucesso, quatro ABIs.
- `:lemuroid-app-ext-play:compileDebugKotlin`: sucesso.

Evidências locais de logs, PNGs e medidas: `build/tv-performance/dsperate-*`; esse diretório de build não faz parte dos fontes.
