# Biblioteca DOS no pendrive

O projeto já possui o sistema `DOS`, o core `DOSBox Pure`, controles DOS e o item de console na interface. A integração de biblioteca foi reforçada em 19/11/2026 para preservar jogos com vários arquivos.

## Formato recomendado

Cada jogo deve ficar em um único arquivo `.dosz`. Esse formato é o próprio ZIP com outra extensão: a cópia pode ser byte a byte, sem extrair nem recomprimir. Pasta interna, executáveis, dados, configurações e imagens de CD permanecem juntos. O DOSBox Pure monta o pacote inteiro como disco virtual e apresenta o seletor de programas quando necessário. Não é necessário transformar cada `.exe` em um jogo separado.

O scanner reconhece `.dosz` em qualquer diretório. Também reconhece um `.zip` quando existe um componente de diretório `DOS`, `MS-DOS` ou `MSDOS`, inclusive nos caminhos SAF do Android. Nesse caso, pula a heurística genérica de ROM única e o cálculo de CRC do pacote: uma imagem de CD grande não pode ser extraída e confundida com outro sistema. A classificação explícita DOS tem precedência sobre coincidências de nome no banco de ROMs.

Exemplo: `DOS/Bio Menace (1993).dosz`. A extensão `.dosz` é preferível quando a pasta pode ser renomeada depois.

## Abertura e gravações

O core recebe um caminho de arquivo real. Com o seletor de documentos do Android, o provedor copia o pacote completo para o cache interno antes de iniciar; a extensão e o nome do arquivo são preservados. Não habilitar VFS para esse core sem validar todos os acessos internos aos arquivos.

O DOSBox Pure grava as alterações do disco virtual no diretório de saves do frontend, em arquivos próprios separados do pacote original. Preservar esses arquivos. Estados rápidos continuam desativados na configuração existente do projeto: o suporte anunciado pelo core não demonstra compatibilidade de estados entre versões/configurações.

Arquivos `.conf` soltos e executáveis individuais não são novos formatos de entrada nesta mudança. Eles podem depender de arquivos irmãos que não seriam copiados pelo SAF. Para estes jogos, manter os arquivos completos dentro do pacote.

## Limites

Os 16 ZIPs da pasta de origem ocupam 2.629.342.344 bytes (aproximadamente 2,63 GB). Conferir espaço real no pendrive e no cache interno antes de copiar/abrir os jogos maiores. Não remover ROMs, saves ou outros arquivos para liberar espaço sem autorização específica.

Reconhecer o pacote não comprova que todo jogo inicia automaticamente, que seu mapeamento de controle está correto ou que roda em velocidade integral na TV. Alguns títulos exigem selecionar o executável correto, configurar áudio ou instalar/montar CD pelo menu do core.

Testes de regressão em `DosArchiveRulesTest`: extensão explícita, caminhos locais/SAF, extensão maiúscula e rejeição de falsos positivos em nomes e arquivos soltos. Testes de execução na TV devem ser registrados separadamente dos testes do scanner.

Referência primária: [DOSBox Pure — documentação Libretro](https://docs.libretro.com/library/dosbox_pure/), seções de ZIP/DOSZ, inicialização e gravações.

## Cópia autorizada nesta sessão

O usuário limitou a cópia a cinco jogos: Dave Goes Nutz (1993), Dangerous Daves Risky Rescue (1993), Dangerous Dave in the Haunted Mansion (1991), Bio Menace (1993) e Ecstatica (1994). Foram copiados como ZIPs completos para `/storage/620F-B493/DOS`, sem extração, sem recompressão e sem apagar arquivos existentes. SHA-256 de cada cópia foi comparado ao original e coincidiu. Ficaram 535296 KiB livres após a cópia. Manifesto local de verificação: `build/core-expansion/dos-usb-copy.json`.
