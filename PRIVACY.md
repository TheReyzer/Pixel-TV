# Privacidade

Perfis, cores, avaliações, assistidos, preferências e listas ficam no armazenamento privado do aplicativo na TV. Quando o usuário vincula um perfil a uma conta Google, o app também sincroniza os dados desse perfil em um arquivo `perfil.json` dentro da pasta `TV VibeCheck` do Drive da própria conta. Não há servidor próprio nem analytics próprios.

O vínculo é feito pelo fluxo OAuth para TVs, com código exibido na tela e autorização em outro dispositivo. O app recebe apenas os tokens autorizados pelo usuário; o token de atualização fica cifrado no Android Keystore. O escopo usado é `drive.file`, limitado aos arquivos criados pelo app. Desvincular interrompe novos acessos e conserva os dados locais; o arquivo no Drive não é apagado automaticamente.

O app consulta serviços externos para catálogos, títulos/sinopses PT-BR, notas públicas, capas, disponibilidade e trailers. São usados serviços como Cinemeta, JustWatch, Wikidata, CDNs, Apple e YouTube. Eles recebem consultas/identificadores de títulos e dados normais da conexão, incluindo IP, e têm suas próprias condições. Imagens e metadados ficam em cache local.

Nos detalhes, o identificador IMDb também é consultado no Wikidata para encontrar continuações, derivados e títulos da mesma franquia. As relações e os resultados ficam em cache local.

O ranking é calculado localmente. Nomes dos perfis e notas individuais não fazem parte das consultas de catálogo.

Para evitar repetições na Home, o app também guarda localmente os identificadores dos últimos 12 títulos exibidos por perfil e dos quatro cards da última tela, incluindo a prévia lateral. Esse histórico de exposição não é sincronizado com o Drive; apenas os dados de perfil necessários para histórico, avaliações, gostos e listas são enviados para a conta Google vinculada.

Clima é opcional e consulta Open-Meteo com coordenadas configuradas em device-config.json. Não há cidade/endereço pessoal no código, nem descoberta automática de GPS. Localização pode ser exigida pelo Android para ler o SSID quando o usuário escolher essa função.

Voz usa o reconhecedor instalado na TV. O processamento pode depender de Google/fabricante e não é um reconhecedor offline próprio.

A automação opcional usa acessibilidade para eventos/interface e acesso a notificações/sessões de mídia para detectar reprodução. Esses acessos podem revelar o app/título visível; são usados para abertura de conteúdo e convite de avaliação após 90 segundos observados. Quando o app oculta dados, o usuário informa o que assistiu. O instalador não exige senha das plataformas.

Backups em backup/ ficam no computador e podem conter identificadores, IP, redes e configurações da TV. Não publique esses arquivos ou chaves ADB/assinatura. A exportação de fontes exclui backups e histórico Git.

Restaurar o launcher não apaga perfis. Desinstalar ou limpar dados pelo Android remove os dados locais; não faça isso como solução para erro de atualização.
