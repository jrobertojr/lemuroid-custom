# Probe de rolagem da descrição

Requer JDK 17, SDK Android local com platform 35/build-tools 35.0.0, app debug instalado e TV autorizada via ADB. Sem bibliotecas de teste adicionais ou downloads.

1. Abra a biblioteca de um tema personalizado (não o seletor de consoles), com descrições carregadas. Para este teste, use CineArcade: o probe verifica que há um único painel.
2. Execute `python tools/validation/description-scroll/build_probe.py` na raiz do projeto.
3. Instale `build/description-validation/probe/probe.apk` com `adb install -r`.
4. Execute `adb shell am instrument -w org.lemuroid.validation.description/org.lemuroid.validation.DescriptionScrollProbe`.
5. Resultado esperado: `INSTRUMENTATION_CODE: 0` e `result=PASS`. Um erro é devolvido no campo `error`, com código 1.
6. Remova apenas o pacote de teste: `adb uninstall org.lemuroid.validation.description`.

A instrumentação reinicia o processo do app. Não execute com um jogo aberto. Não lança jogos: direita/esquerda somente alternam a seleção da biblioteca. O cache de sinopses recebe texto sintético de 4.666 caracteres, é restaurado e não é gravado em disco. O teste exercita entrada simulada RZ pela Activity, eventos frequentes, neutralização, zona morta, subida, preservação do eixo esquerdo e a parada utilizada pelo lifecycle. Não comprova o mapeamento de um controle físico específico nem gameplay. Verifique os botões e seus limites nas capturas da TV separadamente.
