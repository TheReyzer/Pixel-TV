# Trabalho neste repositório

Leia README.md e docs/INSTALL.md. Preserve recursos, D-pad, acessibilidade, persistência e limites de memória. Aplique Ponytail full se disponível, sem exigir caminhos de usuário específicos.

- Fontes Android: launcher/app/src/main; testes Java: launcher/app/src/test.
- Instalação canônica: tools/install.py. Não execute debloat nem desative o Home do fabricante no fluxo de instalação.
- Use somente o dispositivo explicitamente escolhido. Preserve perfis, avaliações, listas, favoritos e serviços de acessibilidade existentes.
- Primeira instalação: apenas Geral, seguido da criação obrigatória de um perfil individual. Não inclua contas pessoais nos defaults.
- Marca, cidade e aplicativos preferidos pertencem ao arquivo privado device-config.json.
- Mantenha validação TLS e identidade IMDb das imagens/metadados.
- Não inicie reprodução paga, limpe dados ou troque assinatura como atalho de teste.
- Valide tools/tests com unittest e Android com testDebugUnitTest/assembleDebug. Informe o hardware realmente testado.
- Prepare publicação com tools/export_source.py. Não publique no remote herdado sem pedido explícito.
