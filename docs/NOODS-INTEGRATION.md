# NooDS para Nintendo DS — 19/11/2026

Core adicional compilado e distribuído junto do aplicativo, a partir do port
Libretro `jonian/libretro-noods`, revisão
`9a2665306927ede64f4b5c109df79f4015607dbc`. Fonte, licença e adaptações em
`lemuroid-core-noods/README.md`. ABIs: armeabi-v7a, arm64-v8a, x86 e x86_64.

## Perfis

| Ajuste do core | Qualidade | Equilibrado | Desempenho |
| --- | --- | --- | --- |
| Resolução 3D | 2× | Nativa | Nativa |
| Filtro do core | Linear | Nearest | Nearest |
| Renderização 2D em trabalhador | Ativa | Ativa | Ativa |
| Trabalhadores 3D | 2 | 2 | 2 |
| ARM7 HLE | Desativado | Desativado | Desativado |
| Frameskip / limitador interno | Desativados | Desativados | Desativados |
| Áudio | Ativo | Ativo | Ativo |

O aplicativo continua responsável pelo ritmo dos quadros. Equilibrado e
Desempenho compartilham os ajustes nativos que tiveram melhor resultado; as
diferenças gerais de HD/filtro dos três perfis continuam valendo. A detecção
automática inicial e a precedência dos ajustes manuais foram preservadas.
Nenhum perfil altera a preferência da moldura lateral.

## Medições na TV de referência

Executável ARM de 32 bits `tools/nds_benchmark.cpp`, rodado na própria TV via
ADB/run-as, sem root. Sequência repetível de entradas, 720 quadros de aquecimento
e 600 quadros medidos; sem espera de sincronização ou frameskip. As gravações
do teste usam diretórios separados no cache, sem importar o progresso do usuário.
São taxas de emulação do core, sem apresentação pela interface Android.

| Jogo/cena | Qualidade | Equilibrado | Desempenho |
| --- | ---: | ---: | ---: |
| New Super Mario Bros, abertura animada com imagem e áudio válidos | 22,80 FPS | 25,04 FPS | 25,16 FPS |
| Castlevania Order of Ecclesia, abertura/logo do codec | 16,18 FPS | 38,80 FPS | 38,71 FPS |

O trecho de Castlevania estava silencioso e ainda na abertura: esses números
**não validam jogabilidade nem áudio nesse jogo**. Mario produziu 540.147
amostras não nulas e 328.200 quadros de áudio em cada medição. Todos os seis
testes expuseram 8 KiB de SRAM e passaram por salvar/restaurar estado, incluindo
alteração e comparação da RAM após restaurar.

Uma rodada anterior de Mario marcou 27,41 FPS em Qualidade e 29,73 FPS em
Equilibrado. DeSmuME, sem pular quadros e com JIT/bloco 12/dois trabalhadores,
marcou 23,23 FPS na mesma sequência. São rodadas sequenciais sujeitas à carga e
ao aquecimento da TV; o ganho não é estável nem suficiente para cerca de 60 FPS.

Experimentos rejeitados:

- ARM7 HLE: resultado aparentemente maior, mas Mario ficou preto e silencioso.
  Desativado em todos os perfis; opção avançada continua disponível com aviso.
- Um trabalhador 3D e renderização 2D sem trabalhador: Mario caiu para 21,35 FPS.

**Decisão: não promover NooDS a padrão**, pois a condição de desempenho fluido
não foi atingida. As regras anteriores de escolha e as preferências existentes
continuam válidas; o core pode ser escolhido nas configurações de NDS.

## Integração de arquivos e controles

- ROMs do pendrive continuam pelo fluxo existente; o core não implementa VFS e
  utiliza o arquivo disponibilizado pelo cache do aplicativo.
- SRAM exposta pela API Libretro para o fluxo normal de `.srm`; conversão de
  `.dsv` para dados brutos reaproveita a migração existente, preservando o `.dsv`.
- Escritas nativas `.sav` ficam no subdiretório `saves/noods`.
- Estados são separados por core. Estados de DeSmuME/melonDS não são estados de
  NooDS. Buffer de serialização de 32 MiB, com verificação de tamanho na escrita.
- Layouts vertical, horizontal, tela superior e inferior; integração com a
  alternância temporária de tela existente. Controles padrão de Nintendo DS.

## Verificações de compilação

- `:retrograde-app-shared:testDebugUnitTest`: quatro testes, sem falhas.
- `:lemuroid-app:assembleFreeDynamicDebug`: sucesso, com bibliotecas das quatro ABIs.
- `:lemuroid-app-ext-play:compileDebugKotlin`: sucesso.

## Verificação pelo aplicativo

APK instalado na TV sem apagar dados. NooDS apareceu em Configurações → Alterar
Núcleos → Nintendo DS. Mario abriu nos três modos; os logs confirmaram resolução
3D maior/filtro Linear em Qualidade e resolução nativa/Nearest nos outros dois.
Todos mantiveram áudio ativo, ARM7 HLE desativado e dois trabalhadores 3D.
No modo Desempenho, SurfaceFlinger registrou 26,41 FPS apresentados em uma janela
de 126 amostras. A saída AAudio foi iniciada pelo processo do jogo.

Saída normal gravou SRAM e estado automático; a reabertura recuperou o estado
sem erro, com imagem válida. A preferência de moldura permaneceu `auto`, conforme
observada imediatamente antes destes testes. A seleção anterior `desmume` e o
perfil `performance` são restaurados após os testes, pois NooDS não passou no
critério para se tornar padrão.

Restauração confirmada pelo diário do Harmony (`pref_key_core_selection_nds =
desmume`, `pref_key_performance_profile = performance`) e pela tela de ajustes.
O arquivo `New Super Mario Bros.srm` permaneceu idêntico ao backup por SHA-256;
os arquivos `.dsv` e os estados dos outros cores foram preservados. O Harmony
mantém alterações recentes no diário antes de consolidar `prefs.data`, portanto
ler somente esse JSON imediatamente depois de uma mudança mostra valores antigos.

Logs e capturas locais da sessão em `build/tv-performance` (não versionados).
