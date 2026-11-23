# Descrições e ações da biblioteca — 21/11/2026

Correção solicitada: descrições longas não podem esconder Jogar/Minha Lista; permitir ler todo o texto e usar o analógico direito para rolar.

## Implementação

- A busca agora avalia até cinco resultados e exige correspondência do título (ignorando pontuação, acentos e qualificadores). Evita listas de outro assunto e a série inteira no lugar de um jogo numerado; tenta inglês se o resultado em português não corresponde. A [API Search](https://www.mediawiki.org/wiki/API:Search) faz busca por título ou conteúdo, portanto o primeiro resultado sozinho não comprova identidade.
- Retirado o corte de 380 caracteres no resumo recebido de Wikipedia. A descrição mostra todo o resumo disponível, combinado aos metadados locais; isso não equivale ao artigo inteiro nem garante que exista uma sinopse online para todo jogo.
- `GameDescriptionText` mantém o texto sem `maxLines`/reticências em uma área limitada e rolável, também por toque. Aplicado aos oito temas personalizados de TV, cinco variações do dashboard de consoles e painéis móveis correspondentes. Essencial conserva a biblioteca nativa, sem um painel temático de sinopse.
- CineArcade reserva altura para os botões, com descrição ponderada separada da linha de ações; o título tem duas linhas e o painel ganha espaço. O painel Nintendo e a apresentação móvel também reservam a linha de ações fora da área flexível.
- O analógico direito vertical usa RZ, com alternativa RY conforme os eixos anunciados pelo dispositivo. Zona morta mínima de 18%, velocidade proporcional à inclinação e até 240 dp/s. Não intercepta Y do analógico esquerdo, direcional, A, X ou Y dos botões.
- Entrada pertence à Activity da biblioteca. Centralizar, mudar descrição, perder foco ou pausar interrompe a rolagem. Essencial na TV não intercepta o analógico pela composição temática oculta.
- Um ScrollState estável por painel evita registrar painéis anteriores ao mudar de descrição. A rotina só roda enquanto há inclinação fora da zona morta. Eventos frequentes atualizam a velocidade sem reiniciar o intervalo da rolagem a cada amostra.
- Molduras, núcleos, overrides, saves, três perfis e seleção automática não foram alterados. Rotação e relógio nativo dos modelos 3D preservados.

## Verificação

- Build ARMv7 `:lemuroid-app:assembleFreeDynamicDebug -PlemuroidAbis=armeabi-v7a`, offline: passou. Ktlint no componente novo e `git diff --check`: passaram.
- Probe por instrumentação de plataforma na TV real, sem dependências de testes novas no aplicativo. Texto temporário de **4.666 caracteres** no cache em memória, sem alterar ROMs ou dados persistentes dos jogos. Máximo de rolagem **2.087 px**; movimento para baixo chegou a **761 px**, retorno para **606 px**. Passaram: texto longo, um único painel após trocar seleção, eixo direito enviado pelo dispatch da Activity, eventos frequentes, centralização, zona morta, preservação do eixo esquerdo parada explícita de lifecycle/foco e seis casos de correspondência de títulos (incluindo acentos e jogos numerados).
- Os eventos foram simulados por instrumentação; não é uma confirmação do controle físico do usuário. O teste de parada chama o mesmo método usado pelo lifecycle; não simula desconexão física.
- Inspeção visual de TV: evidências e resultados em `build/description-validation/`. Conferência dos oito temas personalizados e ações no rodapé; dashboard de consoles conferido na biblioteca selecionada, sem alegar teste visual de todos os sistemas. Celular: compilado, sem aparelho físico validado.
- Após a correção da busca, a TV apresentou texto específico de Mario Kart 64 em português e de Dangerous Dave in the Haunted Mansion em inglês, em vez de resultados genéricos. O caso Bio Menace/lista de Star Wars foi verificado pelo teste de correspondência de títulos.
- Não abriu jogos. O probe altera apenas cache de descrições e restaura seus valores; o processo de instrumentação é encerrado ao fim.

## APK

`build/releases/Lemuroid-Custom-20261121-description-scroll-armv7.apk` — 79.984.164 bytes.

SHA256: `ac63a03ed5a9c7eac4e932e8408717266ca3776705d4acd67c251d3ca0480e31`.

Instalado na TV. Preferências observadas no início desta validação: CineArcade, Qualidade e molduras desativadas; restauradas ao concluir a conferência dos temas. O CineArcade mostrava Mario Kart 64 na primeira posição de Continuar Jogando. Não restaurar Lado B por histórico antigo.

Para repetir o probe, ver `tools/validation/description-scroll/README.md`. APK de teste separado; não faz parte do APK de usuário.
