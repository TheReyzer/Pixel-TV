# Instalação guiada por IA

Copie o roteiro abaixo para uma IA com terminal e acesso ao repositório.

---

Instale este projeto na minha Android TV/Google TV. Leia README.md, AGENTS.md, docs/INSTALL.md, docs/COMPATIBILITY.md e docs/RECOMENDACOES.md antes de agir.

1. Reutilize o aplicativo existente; não substitua seus recursos por um launcher simplificado. Não copie perfis, cidades ou configurações de outro usuário.
2. Verifique Python 3.10+, JDK 17, SDK Android 34/build-tools 34.0.0 e platform-tools. Prepare requisitos usando distribuidores oficiais, conforme as permissões do ambiente, sem supor caminhos de outro computador.
3. Identifique plataforma e dispositivo. Prossiga somente com Android TV/Google TV API 26+. Peça o IP/porta ou escolha explícita entre dispositivos encontrados; não escolha silenciosamente entre várias TVs. Oriente o usuário a habilitar depuração, parear quando necessário e autorizar o computador na tela.
4. Execute tools/install.py com --probe-only e confira modelo, Android e capacidades. Não execute debloat, não desative o launcher original e não altere brilho, áudio, DNS ou serviços do fabricante neste fluxo.
5. Pergunte se o usuário quer tornar o app o Home padrão e se deseja automação/detecção de reprodução. Explique acessibilidade/notificações antes de --enable-assist. --wifi-names só quando a leitura de SSID for desejada. Cidade/coordenadas para clima são opcionais e pertencem ao arquivo local.
6. Se necessário, produza device-config.local.json usando o exemplo. Valide os pacotes instalados e a TV ao vivo detectada. Não coloque nome/endereço pessoal no código; preserve a ordem de favoritos em atualizações.
7. Execute testes e instalador com o SERIAL exato e somente as opções escolhidas. O instalador guarda backup antes das alterações. Se a assinatura for incompatível, pare preservando dados; não desinstale ou limpe perfis para contornar. Se o fabricante bloquear o Home, oriente a seleção pelo menu nativo.
8. Na primeira abertura, deixe o próprio usuário criar o perfil individual. Uma instalação nova possui apenas Geral. Não invente pessoas nem preencha perfis automaticamente. Atualizações com perfis existentes não devem mostrar onboarding.
9. Verifique por screenshot e D-pad a Home, cards, rolagem, busca, perfis, favoritos e gaveta. Não deixe ferramentas de inspeção suspendendo acessibilidade. Confira a melhora de qualidade e as imagens inteiras: horizontal à esquerda e verticais à direita. Não inicie reprodução paga automaticamente; peça ao usuário o teste quando necessário.
10. Entregue o caminho do backup e o comando de restauração. Informe quais recursos ficaram dependentes do fabricante/aplicativo e o hardware realmente testado. Não publique arquivos no GitHub nem envie mensagens externas sem solicitação explícita.

---

O computador precisa alcançar a TV. A IA faz o trabalho técnico; autorizações na TV, pareamento do controle e perfis privados dos aplicativos dependem do usuário.
