# DSperate para Lemuroid

Core adicional Nintendo DS, sem alterar o core escolhido nem as regras automáticas dos três perfis.

## Origem

- Wrapper: https://github.com/thwonp/dsperate-lr — `27b9d76fb4c900d29f9629806f316527b5da90c2`.
- DSperate v3.3.0: https://github.com/beebono/DSperate — `99618eb956c66083618395dbae9bb5cbf0241d16`.
- Licença GPL-3.0, cópia em LICENSE.

## Android

Compilação nativa pelo NDK 27.2, C++17 e ThinLTO com LLD, quatro ABIs. ARMv7 usa JIT e NEON com a convenção de chamada Android softfp; ARM64 usa JIT e NEON. x86/x86_64 usam o interpretador. Renderização por software; Vulkan/GLES do upstream não são habilitados nesta integração. Os perfis PGO GCC do upstream não são usados com Clang.

Adaptações locais: nome GUEST_PAGE_SIZE para evitar macro Bionic; alocação por posix_memalign compatível com API 23; correções de compilação Clang para literais NEON e conversão do ponteiro de função; gravação de máscaras de pixels via memcpy para endereços sem alinhamento ARMv7. A primeira versão compilada falhava com SIGBUS nessa gravação na TV; não distribuir o binário anterior.

O wrapper oferece telas superior, inferior, vertical e horizontal, com atualização de geometria e toque. O atalho de tela temporária existente também reconhece o core. A moldura continua controlada pela preferência do aplicativo.

Todos os três perfis habilitam JIT, desabilitam frameskip e deixam Slot 2 vazio. Os ajustes gerais de resolução/filtro do aplicativo continuam obedecendo ao perfil. Não se força descarte de imagens para inflar velocidade emulada. Estados são isolados por core; SRAM usa a integração Libretro e a migração NDS de saves crus.

## Validação

Resultados e limitações na TV de referência: ver docs/DSPERATE-INTEGRATION.md na raiz do projeto. Não declarar velocidade integral apenas pelo benchmark nativo: validar imagem, áudio e execução no aplicativo.
