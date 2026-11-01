# Recomendações — regras implementadas

Atualizado em 23/10/2026. Esta versão substitui a auditoria inicial: as melhorias abaixo já estão implementadas.

## Aprendizado individual

Somente avaliações explícitas ensinam gosto: Odiei = -3; Não gostei = -1; Mais ou menos = 0; Gostei = +1; Amei = +2. Abrir detalhes, pesquisar, ver trailer e salvar para depois não atribui uma preferência. Assistir sem dar nota registra o assistido, sem inventar uma opinião.

Uma característica desconhecida não conta como uma avaliação neutra. Mais ou menos é evidência real de neutralidade. A força do sinal é suavizada pela quantidade de evidência: `soma(nota × participação) / (soma(participação) + 2)`. A confiança usa `participação / (participação + 2)` e auxilia a exploração de características pouco conhecidas.

Cada avaliação fornece os gêneros, o formato, a época e a década. Os gêneros dividem entre si a participação daquela obra. Uma rejeição isolada a um filme continua sem significar rejeitar todo o formato ou toda a época.

| Grupo de características | Peso no escore |
|---|---:|
| Gêneros | 48% |
| Filme / série | 13% |
| Época | 8% |
| Década | 5% |
| Temas e características explicitadas na sinopse | 16% |
| Duração | 4% |
| Direção | 4% |
| Elenco | 2% |

Esses pesos não são uma probabilidade de gostar. Campos ausentes não são inventados. A nota IMDb exibida também não é uma nota pessoal calculada pelo app.

Os temas usam regras locais de palavras/expressões em português e inglês: investigação, tribunais, sobrevivência, espaço, viagem no tempo, distopia, política, medicina, espionagem, sobrenatural, zumbis, amadurecimento, psicológico e humor sombrio. Também reconhece menções explícitas a ritmo contemplativo/acelerado e tom leve/sombrio. É uma heurística textual, não interpretação semântica completa da obra. Não se inventa o ritmo ou tom quando não há menção na sinopse.

Duração, direção e elenco vêm dos metadados disponíveis no catálogo: até dois nomes da direção e três do elenco. Duração de filmes: até 95 minutos, de 96 a 130, acima de 130; episódios: até 30, de 31 a 60, acima de 60. Títulos sem esses dados continuam recomendáveis usando os demais sinais.

Novas avaliações guardam essas características. Avaliações antigas continuam válidas e podem aproveitar características do mesmo IMDb/formato no catálogo carregado, sem reescrever a opinião ou o histórico.

## Evolução dos gostos

A participação temporal é `0,35 + 0,65 × 2^(-idadeDaNotaEmDias / 180)`. Uma nota recente tem mais influência; as antigas preservam parte do sinal de longo prazo. Uma preferência pode mudar conforme novas notas chegam, sem apagar o passado.

A data da opinião (`ratedAt`) é separada da última visualização (`watchedAt`). Rever algo ou manter a nota anterior não transforma uma opinião antiga em uma avaliação nova. Na migração de registros antigos que não possuíam essa separação, a data existente é usada como aproximação.

## Geral e grupos de pessoas

Cada pessoa é calculada separadamente; muitas notas não transformam uma pessoa em vários participantes. Perfis sem avaliações não entram como opiniões neutras.

O consenso considera a média, a discordância e a pior afinidade entre participantes com evidência:

`média − 0,40 × desvio-padrão + 0,25 × mínimo(0, pior afinidade)`.

O último termo só entra com mais de uma pessoa. Rejeição forte de alguém reduz a posição, sem constituir um bloqueio absoluto do catálogo.

O Geral não entra como uma pessoa adicional quando existem indivíduos com avaliações. Notas do Geral em obras que algum indivíduo já avaliou são desconsideradas no aprendizado coletivo, sem serem apagadas. Notas coletivas exclusivas contribuem como um sinal complementar limitado a 15% do seu escore, sem alterar os gostos individuais. Quando somente o Geral tem notas, ele fornece o aprendizado inicial.

O Descobrir usa as pessoas selecionadas no assistente, independentemente do perfil ativo na tela inicial. Os gráficos de gostos usam os mesmos sinais temporais e a mesma combinação de pessoas, distinguindo títulos únicos e registros de notas.

## Diversidade, novidades e catálogo

A afinidade é combinada com um bônus da ordem inicial de até 0,12. Novidades começa por títulos já lançados do ano corrente, dos mais novos aos mais antigos. Em alta começa pela popularidade. A afinidade pode fazer uma obra anterior do mesmo ano superar uma mais nova.

O ranking reduz a repetição de gêneros em relação aos três itens anteriores. A cada cinco posições, uma posição dá mais espaço a exploração e novidade dentro de uma faixa de afinidade aceitável. A seleção de exploração evita rejeições fortes quando há alternativas seguras. Em alta / Todos conserva a mistura de formatos quando possível. Não é uma promessa de que exatamente 20% dos títulos serão de um gênero novo.

A carga aproveita até 100 títulos por resposta do catálogo em cada formato, além da consulta do ano corrente e da reserva local. Metadados novos prevalecem sobre dados antigos da reserva; nomes brasileiros conhecidos são preservados.

Os detalhes exibem uma explicação baseada em gêneros/temas apoiados pelas avaliações, ou identificam a opção como exploração quando não há sinal positivo suficiente.

## Reposição: não deixar as recomendações vazias

Na tela inicial normal:

1. Procura opções da categoria/formato, respeitando os assistidos dos últimos 21 dias.
2. Se houver menos de três, mantém as existentes primeiro e completa com o catálogo e a reserva local, mantendo o formato e priorizando inéditos. Em Novidades, avisa quando precisa oferecer outros anos.
3. Somente se todas essas opções também tiverem sido assistidas recentemente, reapresenta obras com o aviso **Para rever**. Essa exceção atende ao pedido mais recente de sempre haver recomendações.

A penalização de uma obra já assistida começa em 0,6 após o prazo normal e diminui exponencialmente com o tempo, em vez de permanecer em -1,8 para sempre. O histórico não é apagado.

No Descobrir, tenta primeiro os critérios exatos, depois a reserva com os mesmos critérios. Se necessário amplia a época e depois os gêneros, preservando o formato. Mantém os assistidos excluídos até esgotar as alternativas; só no último recurso os reapresenta com **Para rever**. O aviso na tela informa a ampliação. As escolhas originais permanecem no assistente para edição.

A reposição usa obras reais e IDs do catálogo, sem criar filmes ou séries fictícios. Funciona com a reserva local quando a rede estiver indisponível; carregar capas ainda depende do cache/rede. A lista pessoal Assistir mais tarde pode continuar vazia se a pessoa não salvou nada: ela não é preenchida com recomendações que o usuário não escolheu.

Descobrir continua tentando obter pelo menos 100 resultados exatos, com limite de tempo e requisições. Filtros muito restritos ou indisponibilidade de rede podem produzir menos; a reposição impede um conjunto vazio, sem inventar correspondências exatas.

## Cache e preservação

Ao trocar de perfil ou salvar/alterar/remover uma avaliação, a tela aguarda o novo ranking personalizado e aplica outra página uma única vez. Não mostra uma lista genérica intermediária. Evita primeiro os cards da tela imediatamente anterior e a última página daquele perfil; depois considera os últimos 12 títulos distintos vistos no perfil. Entre páginas com a menor repetição possível, as mais bem colocadas têm mais chance (peso 1 / posição da página). A seleção é consumida quando o resultado pronto aparece, sem sortear novamente a cada atualização do catálogo. Não altera notas nem históricos. Essa memória de exposição vale durante o processo do launcher; com poucas opções, minimiza repetições. Editar nome/avatar/cor não reinicia recomendações. Descobrir e Assistir mais tarde mantêm seus fluxos próprios.

As páginas normais exibem três títulos distintos quando há pelo menos três elegíveis; a última página pode completar com títulos do início. Se a lista crescer durante o carregamento, os cards faltantes são atualizados mesmo com um card focado, preservando páginas completas sob foco. Mudanças de data/hora/fuso também revalidam o ranking e o relógio visível. A navegação horizontal contínua reaproveita o ranking carregado. Abrir detalhes e voltar, ou abrir e fechar a lista interna de aplicativos, conserva a página. O avanço automático ocorre somente após sair para outro aplicativo ou para a TV ao vivo e retornar. Se o retorno ainda mostrar os detalhes ou a lista interna, o avanço fica pendente até voltar à tela inicial e é consumido uma única vez. Retomadas repetidas da Activity, menus internos e diálogos não provocam avanço por si só. Nova avaliação, troca de perfil, virada de dia ou atualização de metadados relevantes invalidam o cálculo correspondente. Na primeira carga, as opções disponíveis aparecem imediatamente; o cálculo personalizado roda em segundo plano sobre uma cópia dos perfis e pré-carrega as seis combinações de categoria/formato. Uma geração antiga é descartada ao mudar de perfil/dados. Em atualizações automáticas, uma página completa focada é preservada. Após uma avaliação ou troca explícita de perfil, o resultado novo pronto é aplicado mesmo sob foco; ao voltar de detalhes sem avaliar, conserva a página. Não exige uma nova busca completa na rede.

O formato local dos perfis foi migrado para a versão 5, preservando identidades, notas, datas existentes, avatares, cores e listas. Importação de notas de Netflix, UniTV, Prime ou Apple TV continua adiada pelo usuário e não foi investigada.

Fontes: ViewerProfiles.java, RecommendationFeatures.java, MediaRepository.java, DiscoveryController.java e MainActivity.java em launcher/app/src/main/java/com/gathin/tvhome/.

## Navegação contínua de Descobrir — atualização de 28/10/2026

Descobrir usa agora uma grade contínua de seis colunas, com cards reciclados e indicador de posição. A rolagem acompanha os próximos grupos para pré-carregar imagens. No fim, Descobrir mais resultados reaplica as escolhas e a afinidade, exclui as identidades já carregadas e acrescenta os novos títulos, conservando a ordem anterior. Isso não apaga avaliações nem altera os critérios pessoais. A tela inicial normal usa a faixa horizontal contínua e mantém as regras de atualização ao voltar de apps.

Explorar catálogo IMDb, disponível na busca, é uma navegação livre pelo catálogo conectado ao IMDb já usado no projeto, respeitando Tudo/Filmes/Séries; não importa conta ou notas do IMDb. Assistir mais tarde continua exclusivamente pessoal. As fontes podem esgotar títulos ou ficar indisponíveis; os resultados anteriores são preservados e o usuário pode tentar novamente.


## Pesquisa e histórico em grade — 28/10/2026

A pesquisa por título usa agora a grade contínua, com carregamento automático das próximas páginas perto do final e deduplicação por IMDb. O modal edita a consulta/voz/formato e conserva o último rascunho; os resultados só são substituídos quando a nova busca termina. Selecionar um título abre os detalhes, com Assistir, Avaliar e Assistir mais tarde. Voltar retorna à mesma grade/card; outro Voltar foca a busca, sem clicar nem abrir o editor. Avaliar outro retorna à grade e mantém a pesquisa e suas páginas durante a sessão. Esse catálogo pesquisado não é reordenado pelo ranking de recomendações ao salvar uma nota.

Assistidos e avaliações usa a mesma grade, filtrada pelo perfil ativo. No Geral, um título aparece uma vez, preservando as opiniões de cada pessoa. Os detalhes mostram as notas existentes e os botões Na lista/Avaliado marcados para o perfil ativo (união no Geral). Consultar essas telas não apaga registros nem muda a regra de exclusão de 21 dias. O histórico não é preenchido com recomendações quando não há registros.


## Foco e retorno ao topo — 28/10/2026

Voltar enquanto a grade está visível mantém seus resultados/posição e focaliza o atalho correspondente, sem abrir outro modal. Isso vale para Descobrir, Busca, Assistir mais tarde e Assistidos e avaliações; o catálogo livre retorna ao atalho da busca. O retorno dos detalhes preserva a consulta anterior e mostra a grade; clicar explicitamente no botão de busca reabre o editor com a consulta. Junto ao botão que carrega mais títulos, Voltar ao topo leva ao primeiro card sem refazer a pesquisa. O indicador de posição tem largura ajustada ao texto.


## Apresentação dos resultados — 28/10/2026

As grades usam seis colunas de pôsteres. Ao focar, a capa cresce sem faixa separada e os nomes brasileiro/original aparecem sobre a parte inferior da imagem, cada um em uma linha com marquee infinito. A nota do catálogo fica pequena no topo, sem a marca IMDb. Capas do catálogo brasileiro têm prioridade; a alternativa é sempre da mesma obra. A mudança de apresentação não muda afinidades, exclusões ou avaliações.


## Estabilidade e fundo dos cards — 28/10/2026

A grade reserva espaço para a escala do foco e acomoda duas linhas completas; as linhas não mudam de altura ao focar. A consulta aparece só no botão de busca. Card sem nota não mostra a pílula. O fundo e a cor ambiente acompanham a seleção estabilizada com cache e transição; isso não altera o ranking. Imagens falhas repetem a carga com intervalos crescentes, limitados a uma tentativa por minuto enquanto a mesma imagem estiver visível, e também são recuperadas no retorno/reconexão. A indisponibilidade de todas as fontes ainda pode impedir a arte até que uma delas responda.


Atualização de navegação — 28/10/2026: na Home normal, as sugestões agora formam uma faixa horizontal contínua, com três cards maiores visíveis e reaproveitamento dos próximos cards. Isso altera a apresentação, sem trocar o cálculo de afinidade, o cruzamento do Geral ou as exclusões de assistidos. Entrar e voltar dos detalhes mantém a posição exata. O catálogo salvo é preparado em segundo plano ao iniciar; os demais filtros são classificados fora da thread da interface. Voltar da TV ao vivo ou de outro app continua avançando as sugestões já disponíveis, sem exigir nova busca a cada retorno.
