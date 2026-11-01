# Compatibilidade por capacidade

O aplicativo requer Android TV ou Google TV com Android 8/API 26 ou superior, instalação de APK permitida e ADB autorizado. A validação física desta versão foi feita em TCL BeyondTV, Android 9/API 28, 1920×1080 com densidade 2.

| Função | Base comum | Verificação necessária |
|---|---|---|
| Home padrão | Intents HOME | O fabricante pode exigir seleção no menu ou bloquear a troca |
| Layout e D-pad | Dimensões/densidade reais | Colunas adaptáveis; verificar resolução e overscan |
| Entradas e canais | TvInputManager/intents TV | Alguns fabricantes exigem permissão privilegiada |
| Volume/menu | Eventos do controle | Algumas teclas passam diretamente pelo firmware |
| Voz | SpeechRecognizer e microfone | Serviço instalado, idioma e áudio Bluetooth |
| Abrir conteúdo em outro app | Deep links/automação opcional | Depende da versão, telas e perfis do aplicativo |
| Convite de avaliação | Acessibilidade e sessões/notificações | Metadados podem estar ocultos; limiar de 90 s observados |
| Capas e catálogo | Serviços externos/cache | Disponibilidade, rede e existência de uma imagem maior |

Testes automatizados não equivalem a validação de firmware de todas as marcas. Samsung Tizen e LG webOS não executam este APK. Fire TV, Roku e aparelhos com Home/instalação bloqueados não fazem parte do suporte verificado. Não modifique firmware nem desative serviços essenciais para forçar compatibilidade.

A interface, sinopses e disponibilidade de streaming priorizam português brasileiro e o mercado brasileiro.
