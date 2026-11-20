# Validação integrada do polimento — 20/11/2026

TV TCL Android 12/API 31, ARM32, Mali-G57, biblioteca do pendrive. APK FreeDynamicDebug armeabi-v7a, mantendo pacote e assinatura de desenvolvimento existentes. Não é uma build de produção assinada.

## Resultado e limites

Os nove temas foram abertos na TV. Essencial mantém o frontend Leanback limpo; os oito temas personalizados receberam revisão visual. Netflix agora aparece como CineArcade e Rockstar como Distrito Arcade; chaves persistidas permanecem iguais. Capturas locais em `build/polish-validation/after`. Não distribuir capturas com capas de jogos como arte do projeto.

A correção de enquadramento foi instalada e conferida em Favoritos/Elevator Action Returns: gabinete inteiro, com base e letreiro dentro do quadro. Uma inicialização pós-atualização levou 99,5 s no am start e permaneceu no launcher até focalizar a tarefa. Outras inicializações deste ensaio levaram aproximadamente 6–7 s. Essa ocorrência não foi resolvida nem demonstra um tempo típico; merece rastreamento próprio.

A conferência final em Favoritos revelou o gabinete Arcade cortado no showcase: sua origem estava na base. O loader do showcase agora centraliza a altura real em buffers próprios; os buffers e a base do cenário não são modificados.

48 assets L3D2 originais (24 consoles e 24 mídias), 7.372 triângulos no conjunto, 712.640 bytes de geometria. Validador independente confirmou SHA256, UV, normais, partes e limites. Atlas original gerado por image_gen, 512×512 RGB565 com filtro nearest; biblioteca Blender editável com atlas incorporado. O APK final ocupa 77.964.277 bytes: o polimento adicionou cerca de 635 KiB ao APK anterior, portanto não se afirma redução do tamanho do instalador.

Compilação ARMv7 passou, extensão Play compilou e 17 testes compartilhados passaram sem falhas. `git diff --check` e o validador de assets passaram. Sem ensaio visual em celular ou outras GPUs/ABIs.

## Navegação medida

Mesmo roteiro: tema Vitrine 3D, Todos, primeira entrada; 30 comandos Direita para aquecer, limpar gfxinfo e 30 Esquerda com pausa solicitada de 150 ms entre comandos. Cada chamada Android input tem custo adicional. Captura gfxinfo do processo principal, sem converter esses números em FPS de emulação. Amostras únicas; cache, compilação JIT, temperatura, rede e carga do firmware não foram controlados.

| Amostra | Frames Android | Janky frames | Percentil 95 |
| --- | ---: | ---: | ---: |
| Antes, Qualidade aquecido | 179 | 22 (12,29%) | 200 ms |
| Final, Qualidade | 186 | 29 (15,59%) | 200 ms |
| Final, Equilibrado | 187 | 33 (17,65%) | 200 ms |
| Final, Desempenho | 123 | 20 (16,26%) | 200 ms |

A correção posterior de centralização foi verificada visualmente, sem repetir a medição; não altera a política de animação ou perfis.

Essas amostras **não demonstram ganho de fluidez sustentada**. O percentil 95 permaneceu igual e a proporção de frames atrasados subiu na amostra Qualidade. Não anunciar 60 FPS constantes, nem menor latência comprovada. As mudanças reduzem trabalho desnecessário por construção: relógio GL nativo por vsync e lifecycle, cadência ociosa reduzida em Desempenho, decodificação limitada, HTTP cancelável, cache limitado e remoção de fades/relógios duplicados. Continua havendo engasgos que exigem perfilamento mais profundo.

PSS observado: antes 292.440 KiB e final 267.317 KiB; RSS 304.336 e 324.608 KiB respectivamente. Momentos de coleta diferentes e swap anterior elevado impedem tratar a diferença de PSS como ganho isolado da implementação.

Uma tentativa intermediária com withFrameNanos foi substituída por Choreographer nativo; não mantém trabalho de frame Compose para agendar apenas GL. Timer de 17 ms também foi removido. Amostra intermediária iniciada antes da UI pronta não foi usada na tabela final.

## Fluxos e preferências

B na raiz abriu confirmação com Continuar como opção inicial, e confirmar manteve o app aberto. X abriu o editor de 1943 com herança FCEUmm/Qualidade e texto de precedência. A abertura pelo editor renderizou o jogo. O comando Back injetado pelo ADB não concluiu a saída; a validação precisou invocar requestFinish pelo depurador, usando o pipeline existente de captura/gravação e retorno. Portanto este ensaio não comprova a saída por controle físico. O retorno preservou a seleção de 1943. Nenhuma preferência temporária de núcleo/jogo foi criada nesse ensaio.

Configurações de núcleo, precedência de perfis, discos agrupados, retorno à seleção e molduras não foram alteradas pelo polimento. No painel desta validação a moldura estava Desativada; não foi modificada. O snapshot antigo prefs.data indicava Suave, mas Harmony mantém transações separadas, então esse arquivo isolado não prova o valor vivo da preferência. O tema Vitrine 3D e o perfil Qualidade foram restabelecidos ao final.

## Próximas melhorias sugeridas

1. Fazer trace Perfetto em uma variante otimizada que mantenha os dados existentes, separando trabalho de composição, texto, leitura de capas, GPU e coleta de lixo. Os dados atuais não justificam prometer FPS.
2. Mover agrupamento e filtragem de bibliotecas grandes para o ViewModel em trabalhador e publicar listas imutáveis/paginadas; medir navegação com 1.000+ entradas.
3. Persistir metadados e prévias já consultados, com orçamento de disco e cancelamento, para evitar consultas repetidas após reiniciar.
4. Gerar miniaturas de mídias por sistema em segundo plano, reutilizando os modelos 3D e UV. Hoje os cartões não selecionados continuam usando a representação 2D leve.
5. Manter catálogo de cores e downloads sob demanda: binários dos emuladores dominam o APK; reduzir apenas triângulos não muda substancialmente o instalador.

6. Melhorar a resolução de metadados por título, sistema e ano: a captura de Elevator Action Returns mostrou resumo do Elevator Action de 1983. Evitar aceitar automaticamente uma correspondência de franquia como descrição da versão selecionada.
