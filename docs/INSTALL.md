# Instalação e restauração

## Preparação

Instale Python 3.10+, JDK 17 e o SDK Android oficial: platform-tools, plataforma 34 e build-tools 34.0.0. Defina JAVA_HOME e ANDROID_HOME. Windows, macOS e Linux usam tools/install.py; os wrappers em launcher/ encaminham para ele.

Na TV, habilite opções de desenvolvedor/depuração. Use a modalidade oferecida pelo dispositivo: cabo/ADB USB, TCP na porta informada ou depuração sem fio com pareamento. Em versões novas, execute `adb pair IP:PORTA_DE_PAREAMENTO` e depois `adb connect IP:PORTA_DE_CONEXAO`. Não suponha que sejam a mesma porta ou sempre 5555. Confirme a autorização do computador na TV.

```sh
adb devices -l
python tools/install.py --device SERIAL --probe-only
python tools/install.py --device SERIAL --dry-run
python tools/install.py --device SERIAL --set-home
```

Probe-only é somente leitura. Dry-run não se conecta e exibe o plano. Sem --set-home, o Home padrão permanece e o app abre para prévia; ele também aparece na lista de apps da TV.

## Opções

- --enable-assist: permite acessibilidade e solicita acesso às notificações para automação/detecção. Conserva os outros serviços. Se a TV bloquear, use o menu nativo.
- --wifi-names: concede localização para leitura do SSID quando exigido pelo Android.
- --config CAMINHO/device-config.local.json: configuração privada por TV.
- --apk CAMINHO: instala APK já construído. Sem essa opção, compila e executa os testes primeiro.
- --adb CAMINHO e --sdk CAMINHO: caminhos explícitos quando não estiverem no ambiente.

Microfone é permitido pelo diálogo Android quando a busca por voz é usada. Não são necessários usuário/senha das plataformas no instalador.

## Configuração privada

Copie docs/device-config.example.json para um arquivo device-config.local.json fora do conteúdo público. Campos opcionais:

- liveTvPackage: pacote realmente instalado da TV ao vivo; vazio usa detecção.
- dockApps: preferências iniciais; apps ausentes são omitidos e a ordem personalizada existente é preservada.
- hiddenApps: pacotes que não devem aparecer nos tiles.
- weather: objeto latitude/longitude válidas. Sem ele o clima fica oculto; o fuso da previsão é detectado pelas coordenadas.

O arquivo não tem dados de perfis e é escrito no armazenamento privado do app. Configuração inválida é rejeitada pelo instalador. No aplicativo, arquivo inválido usa defaults genéricos.

## Sincronização Google por perfil

Cada perfil individual pode ser vinculado a uma conta Google existente. O perfil `Geral` não é vinculado. Na TV, abra `Perfil → Contas Google e sincronização`; a autorização usa um código exibido na TV e é concluída pelo navegador do celular ou computador.

Antes de compilar uma versão com sincronização, crie no Google Cloud um cliente OAuth do tipo `TVs e dispositivos com entrada limitada`, ative a Drive API e configure as propriedades locais do Gradle:

```properties
tvVibehackGoogleClientId=SEU_CLIENT_ID
tvVibehackGoogleClientSecret=SEU_CLIENT_SECRET_OPCIONAL
```

Essas propriedades podem ficar no `gradle.properties` do usuário ou no arquivo local de Gradle que não será publicado. Sem o client ID, a interface continua disponível, mas informa que a sincronização ainda não foi configurada. O app usa o escopo estreito `drive.file` e cria uma pasta visível `TV VibeCheck` com um arquivo `perfil.json` por conta. Tokens de atualização ficam cifrados no Android Keystore.

O vínculo importa e mescla dados de perfis já existentes; não substitui silenciosamente avaliações, histórico ou listas locais. Depois do vínculo, a inicialização, avaliações, registros de assistidos, alterações de lista e edição de perfil acionam a sincronização de todos os perfis vinculados. Sem internet, as alterações ficam locais e são reenviadas quando houver conexão.

## Atualização e restauração

O instalador cria backup/install-DATA/ antes das alterações, com inventário, Home anterior e diário. Use a mesma chave de assinatura nas atualizações. Uma chave diferente causa erro; nunca desinstale ou limpe dados para contornar. Builds de CI não estabelecem um canal permanente de atualização assinado.

```sh
python tools/install.py --device SERIAL --restore backup/install-DATA
```

A restauração usa o dispositivo do backup, repõe o Home anterior quando conhecido e desfaz permissões/configuração adicionadas pelo instalador. Mantém app e perfis. Configurações alteradas depois pelo usuário são preservadas. Se o Home anterior era um seletor do sistema, escolha o launcher no menu nativo.

O fluxo recomendado não desabilita pacotes, não faz debloat e não muda brilho, áudio ou DNS.

## Conferência

Verifique a saudação de inicialização, Home, criação inicial de perfil, filtros, navegação, busca/voz, volta dos detalhes e gaveta. Confirme cards de mesma altura, capas horizontais/verticais preenchidas sem deformação e melhoria de qualidade. Se uma fonte falhar ou não fornecer imagem maior, a imagem existente é conservada e novas tentativas são feitas; não há como gerar pixels inexistentes na fonte.

Não infira sucesso de funções nativas só porque am start retornou OK. Teste no controle, sem iniciar reprodução paga automaticamente. Revogue ADB quando terminar se não precisar manter acesso.

Confira também que reiniciar/despertar ou retornar de um app renova os cards da Home, enquanto voltar dos detalhes conserva os mesmos cards. Na grade, Voltar deve focalizar o botão de origem; pressionar novamente deve selecionar Novidades ou Em alta, conforme o filtro anterior. A avaliação deve encerrar sem perguntar se deseja avaliar outro título.

Na grade, teste subir/descer, manter a seta pressionada, inverter a direção e usar Voltar durante o movimento. O foco deve alcançar o último destino pedido, manter o card inteiro visível e parar de acompanhar a grade ao voltar ao botão de origem.
