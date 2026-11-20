# DeSmuME 2015 — integração e testes na TV (19/11/2026)

Core adicional compilado do código de `libretro/desmume2015`, commit
`422b688009ccb9f2917c086a8fe79c8cecb030ae`. Fonte e adaptações documentadas em
`lemuroid-core-desmume2015/README.md`. Biblioteca empacotada nos APKs, sem depender
de um download de core inexistente no servidor antigo.

## Perfis e preferências

| Ajuste | Qualidade | Equilibrado | Desempenho |
| --- | --- | --- | --- |
| Resolução | 256×192 | 256×192 | 256×192 |
| JIT ARMv7 / bloco | Ativo / 12 | Ativo / 12 | Ativo / 12 |
| Trabalhadores | 2 | 2 | 2 |
| Frameskip | 0 | 1 | 2 |
| Edgemarking | Padrão | Padrão | Desativado |
| Hacks de linha/TXT | Padrão | Padrão | Desativados |

As chaves do port foram renomeadas para `desmume2015_*`. Os ajustes manuais do
DeSmuME antigo (por exemplo, frameskip 0 e tela superior na TV) não contaminam
o novo core. Configurações manuais do novo core têm a precedência habitual.
O caminho existente de HD/filtro, escolha automática inicial do perfil e moldura
configurável permanece ativo. Nenhuma preferência de moldura foi alterada.

Foram compiladas as quatro ABIs do aplicativo. O JIT deste módulo está habilitado
em armeabi-v7a, a ABI efetiva da TV; as outras três usam interpretação.

## Teste repetível do core na própria TV

Harness ARM de 32 bits, sem root, em diretórios isolados do cache. Mesma ROM de
Mario, mesmas entradas, 720 quadros de aquecimento e 600 medidos sem limitador.
O trecho é a abertura animada, e não um teste de todas as fases do jogo.

| Core/configuração | Quadros emulados/s | Imagens novas/s |
| --- | ---: | ---: |
| DeSmuME antigo, referência sem frameskip | 22,72 | 22,72 |
| DeSmuME 2015, referência sem frameskip | 27,04 | 27,04 |
| DeSmuME 2015, Qualidade | 27,26 | 27,26 |
| DeSmuME 2015, Equilibrado | 32,87 | 16,43 |
| DeSmuME antigo, Desempenho, frameskip 2 | 27,60 | 9,20 |
| DeSmuME 2015, Desempenho, frameskip 2 | 34,96 | 11,65 |
| NooDS, Desempenho, sem frameskip | 24,90 | 24,90 |

Cada rodada de 600 quadros do DeSmuME 2015 produziu 441.000 quadros de áudio e
723.480 amostras não nulas. Todos os modos passaram por salvar/restaurar estado
e comparação da RAM após uma alteração deliberada. Buffer de estado: 12 MiB.

Em Castlevania Order of Ecclesia, Desempenho marcou 51,38 quadros emulados/s e
17,13 imagens novas/s, mas o trecho permaneceu silencioso: não usar essa rodada
para alegar jogabilidade ou áudio validados nesse jogo.

## Arquivos e teste no aplicativo

O core usa o formato nativo `.dsv`, compatível com o fluxo do DeSmuME antigo.
Os estados ficam em `states/desmume2015`, separados dos demais cores. A API de
SRAM do upstream não é exposta; o aplicativo não sobrescreve `.srm` com dados
vazios. O adaptador importa `.srm` válido mais recente usando o importador nativo,
permitindo voltar de NooDS/melonDS sem preferir um `.dsv` antigo.

Teste isolado de importação: os 8.192 bytes de um `.srm` real foram preservados
exatamente no `.dsv` resultante de 8.314 bytes. Nesse teste adicional foram
medidos somente 120 quadros, para conferir arquivos e estado; sua taxa não é
comparável às janelas de 600 quadros da tabela.

APK instalado sem apagar dados. A opção DeSmuME 2015 apareceu em Alterar Núcleos.
Mario abriu no modo Desempenho; logs confirmaram JIT, resolução nativa, frameskip
2 e os ajustes do perfil. Saída AAudio iniciada e moldura Automático visível.
SurfaceFlinger registrou 19,09 apresentações/s em 127 amostras, que podem incluir
imagens repetidas. Esse número não substitui a contagem de imagens novas do core.
Saída normal gravou estado automático separado e preservou os demais estados.
A reabertura recuperou o estado com imagem válida, sem erro de restauração.
Os hashes SHA-256 de Mario `.dsv`/`.srm` e Castlevania `.dsv` continuaram iguais
aos backups após os testes pelo aplicativo.

## Decisão sobre o padrão de Desempenho

O 2015 ganhou aproximadamente 27% de velocidade emulada sobre o DeSmuME antigo
na configuração Desempenho. Porém, a velocidade ficou em aproximadamente 58%
da necessária e a imagem recebeu apenas cerca de 12 quadros novos/s. O NooDS
entregou aproximadamente 25 imagens novas/s na mesma sequência.

**Não promover automaticamente o 2015 a padrão:** o ganho interno não demonstrou
melhora geral de fluidez e áudio em relação às opções disponíveis. Manter como
opção adicional. Preservar a escolha NooDS, perfil Desempenho e moldura Automático
observados no início desta etapa. Nenhuma regra de escolha automática foi alterada.

## Verificações

- APK com as quatro bibliotecas nativas compiladas.
- Cinco testes de perfis sem falhas, incluindo isolamento das chaves de 2015.
- Compilação do aplicativo Free Dynamic Debug e do módulo Play aprovada.
- Logs, capturas e backups em `build/tv-performance`, fora do versionamento.
