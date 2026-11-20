# TV do usuário — referência de hardware

Consulta realizada em 19/11/2026, por descoberta SSDP, endpoints locais do Cast e ADB. Dados reportados pelo aparelho, sem alteração de configurações, instalação de apps ou execução de benchmarks. Valores de RAM disponível, espaço livre, rede e modo de tela são fotografias do momento.

## Identificação e acesso

- Nome: TV Quarto principal.
- Fabricante: TCL.
- Modelo informado pelo Android e Cast: Smart TV Pro. O modelo comercial exato e o tamanho em polegadas não foram confirmados; não deduzir de identificadores genéricos ou DPI.
- Device: G09; produto: G09_4K_GB; placa: TC8000; plataforma: t5m; hardware: amlogic.
- IP observado: 192.168.15.78, na rede local 192.168.15.0/24. Pode mudar por DHCP.
- ADB: 192.168.15.78:5555, conexão autorizada e funcional nesta consulta.
- ADB no computador: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Cast/DIAL: porta 8008; descrição em `http://192.168.15.78:8008/ssdp/device-desc.xml`.
- Diagnóstico Cast: `http://192.168.15.78:8008/setup/eureka_info?options=detail`.
- DLNA/UPnP MediaRenderer: porta 16824. O serviço anuncia Windows Media Player/Microsoft, mas isso é identificação do serviço, não do hardware da TV; a TV é TCL.

## Processador e gráficos

- Fabricante do SoC: Amlogic.
- SoC reportado: AMLT963D4.
- 4 CPUs online, índices 0–3.
- CPU: ARMv8, implementer 0x41, part 0xd05, variant 0x2, revision 0.
- Frequência mínima/máxima exposta para CPU 0: 100 MHz / 1.908 MHz (1,908 GHz). Não representa frequência sustentada medida.
- Governor: schedutil.
- Extensões anunciadas: NEON, VFP/VFPv3/VFPv4, AES, PMULL, SHA1/SHA2, CRC32, divisão inteira, entre outras.
- ABI de aplicativos: `armeabi-v7a,armeabi`. Lista de ABIs de 64 bits vazia.
- **Usar binários ARM de 32 bits. A capacidade ARMv8 da CPU não implica suporte a APKs ou núcleos arm64 neste Android.**
- GPU: ARM Mali-G57.
- OpenGL ES: 3.2.
- Driver GL: `v1.r44p0-01eac0.c46fdb8d6d694ec3d0246d3de65b6593`.
- Vulkan anunciado pelo Package Manager: version 4198400 (1.1.0), level 1 e compute. Capacidade anunciada; nenhum teste Vulkan foi executado.
- Recursos OpenGL ES AEP anunciados.

## Memória e armazenamento

- RAM total visível ao kernel: 1.957.140 KiB, aproximadamente 1,87 GiB, compatível com a classe de 2 GB. Não é uma medição da capacidade física dos chips.
- MemAvailable na primeira coleta: 670.204 KiB, aproximadamente 654 MiB.
- MemFree: 218.016 KiB; cache: 518.128 KiB.
- Swap total: 877.020 KiB; livre: 365.212 KiB. Tipo de swap não verificado.
- CMA total: 708.608 KiB; CMA livre: 115.836 KiB. Esses valores fazem parte da contabilidade de memória e não devem ser somados à RAM total.
- Heap growth limit Android: 256m; heap size: 384m.
- `ro.config.low_ram` não apresentou valor; o sistema anuncia `android.hardware.ram.normal`.
- Volume de dados (`df -k /data`): total 8.764.416 KiB (~8,36 GiB); usado 7.118.748 KiB (~6,79 GiB); disponível 1.514.596 KiB (~1,44 GiB); ocupação reportada 83%.
- Armazenamento compartilhado em `/storage/emulated/0`, mesmo volume de dados observado.
- Capacidade física total do armazenamento não confirmada. Leitura de `/sys/block/mmcblk0/size` negada pelo sistema; não foi tentada elevação de privilégios.

## Tela

- Resolução física informada: 3840 × 2160.
- Override Android observado: 1920 × 1080. Preservar como configuração existente, não como capacidade máxima do painel.
- Densidade: 320 dpi.
- Modo ativo reportado: ID 1, 3840 × 2160, aproximadamente 60 Hz.
- Modos anunciados pelo Android em 4K: 48, 50, 59,94, 60, 100, 119,88 e 120 Hz. São anúncios do firmware, sem comprovação por teste físico do painel/HDMI.
- Tipos HDR anunciados: IDs 1, 2, 3 e 4.
- Luminância HDR anunciada: máxima/média máxima 500; mínima 0. Dados do firmware, não medidos com instrumento.
- ALLM anunciado: suportado. `gameContentTypeSupported`: false.
- `DeviceProductInfo` também apresenta campos genéricos como Linux FHD e ano 2012; não utilizar esses campos para identificar o modelo comercial ou ano real da TV.

## Sistema e conectividade

- Android 12, API 31.
- Patch de segurança: 2025-12-05.
- Build: `STT2.230203.001.AS24 release-keys`.
- Kernel: Linux 5.15.180+, SMP PREEMPT, compilação de 30/12/2025, arquitetura reportada armv8l.
- Interface de TV: recursos leanback e leanback_only anunciados.
- Bluetooth, Bluetooth LE e USB host anunciados. Versões e quantidade de portas não verificadas.
- Wi-Fi ativo; Ethernet reportada desconectada.
- SSID observado: BETO VIVO.
- Wi-Fi standard 5; frequência 5745 MHz, banda de 5 GHz.
- RSSI observado: -74 dBm.
- Velocidade negociada/Tx observada: 351 Mbps; máximo de link anunciado: 866 Mbps. Não equivalem a throughput medido.
- MAC Wi-Fi observado: 0C:91:60:45:FC:5B. O endpoint Cast retornou MAC zerado; usar o valor do Android/rede quando necessário.
- Cast: build 1015735464; revisão `3.74.mediashell_clone_374_20260921.00_RC04.1015735464`; stable-channel; `has_update=false` naquele momento, sem afirmar atualização global do Android.
- Servidor de descoberta anuncia Chromecast/1.6.18; essa versão anunciada não substitui a revisão do Cast acima.

## Relação com o Lemuroid

- Aplicativo instalado: `com.swordfish.lemuroid.debug`.
- Versão instalada: 1.17.0-DEBUG; versionCode 252; minSdk 23; targetSdk 35.
- ABI instalada: armeabi-v7a.
- O pacote de produção `com.swordfish.lemuroid` não apareceu na consulta.
- Pelo algoritmo atual de `PerformanceProfileManager`, esta TV recebe inicialmente PERFORMANCE, pois não anuncia ABI de 64 bits e tem cerca de 2 GB de RAM. A preferência efetivamente salva no app não foi lida.
- Considerar orçamento de RAM/heap e armazenamento disponível ao desenvolver temas, capas e modelos 3D. A resolução física 4K não representa a resolução atual da interface Android.
- Não há benchmark de emulação nesta análise. Não assumir FPS, compatibilidade de um jogo ou desempenho de um núcleo apenas pelas especificações.

## Fontes e limites

Fontes: SSDP; descritores Cast/DLNA; `getprop`; `/proc/cpuinfo`; `/proc/meminfo`; cpufreq via sysfs; `df`; `wm`; `dumpsys SurfaceFlinger`; `dumpsys display`; `pm list features`; `dumpsys package`; `cmd wifi status`.

Não foram confirmados: modelo comercial, polegadas, capacidade física total do flash, identificação comercial da CPU, quantidade de unidades da GPU, versões de Bluetooth/HDMI, throughput de rede, temperaturas, consumo e desempenho sustentado. Não instalar ferramentas nem mudar a TV apenas para preencher esses campos sem uma tarefa que justifique isso.

Este arquivo é a memória persistente do projeto sobre a TV. Revalidar IP e dados variáveis ao reconectar. Não pressupõe disponibilidade de memória global em outros projetos/chats.

## Complemento: ROMs e diagnóstico NDS

O usuário confirmou que utiliza um pendrive para as ROMs e relatou travamentos em todos os jogos de NDS. Em 19/11/2026 foi observado o volume USB `/storage/620F-B493`, aproximadamente 15 GiB, 96% ocupado e 653 MiB livres. Esses valores variam.

Foi confirmada a preferência ativa PERFORMANCE e o núcleo NDS DeSmuME. O arquivo aberto pelo processo do jogo era a cópia interna em `cache/storage-framework-games/nds/New Super Mario Bros.nds`; o cache personalizado estava desativado. Nesta execução, não presumir leitura direta da ROM pelo USB só porque sua origem é o pendrive.

Consultar `docs/NDS-PERFORMANCE-DIAGNOSIS.md` para medições, causas identificadas e próximos testes. Não confundir FPS apresentado com velocidade da emulação.
