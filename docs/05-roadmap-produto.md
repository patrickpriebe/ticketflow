# Roadmap — produto e frontend

O que existe hoje cobre o caminho inteiro: descobrir, escolher, pagar, receber o
ingresso e acompanhar o pedido. O que está aqui é o que **ainda não existe**,
organizado por quanto muda o sistema — não por quanto tempo leva.

Cada item diz o que exige do backend. Isso importa: metade das ideias bonitas de
frontend morre quando se descobre que precisam de uma tabela nova.

---

## Nível 1 — Fecham lacunas do que já foi construído

Coisas que o sistema quase faz, e que ficaram de fora por escolha consciente.

### Paginação e ordenação no catálogo
O endpoint aceita `page` e `size`; o front pede uma página e pronto. Com nove
eventos não faz diferença. Passa a fazer no primeiro momento em que o catálogo
crescer — e o sintoma é traiçoeiro, porque a tela não quebra: ela só deixa de
mostrar coisas.
**Backend:** nada. **Front:** paginação ou rolagem infinita, e ordenar por data
ou preço.

### Busca no servidor
Hoje o texto filtra a página já carregada. É honesto enquanto tudo cabe numa
página; deixa de ser no minuto seguinte.
**Backend:** parâmetro `q` no `GET /events`, no mesmo formato do filtro de
cidade. Para valer a pena de verdade, índice `pg_trgm` no Postgres.

### Gênero e imagem do evento
Filtrar por "show", "teatro" ou "esporte" é a primeira coisa que qualquer pessoa
tenta. E o pôster gerado resolve o problema de não ter imagem, mas não substitui
a foto do artista.
**Backend:** colunas `category` e `image_url` em `events`, campos novos no
contrato. **Front:** filtro por gênero e `<img>` com o pôster como reserva.

### Erros por campo
O `ProblemDetail` já carrega `errors[]` quando a validação falha. O front mostra
só o texto geral. Com dois campos no formulário ninguém sente; com um formulário
de verdade, sente muito.

### Cancelar um pedido
Existe o status `CANCELLED` e nenhuma forma de chegar nele pela tela. É o botão
mais pedido em qualquer sistema de compra.
**Backend:** `POST /orders/{id}/cancel`, liberando o estoque na mesma transação
e publicando o evento.

---

## Nível 2 — Mudam o que o produto sabe fazer

### Carrinho entre eventos
Hoje o carrinho é de um evento só, porque o `POST /orders` é de um evento só.
Comprar ingresso de dois shows na mesma compra significa vários pedidos numa
transação — ou um pedido com vários eventos, o que muda a modelagem inteira.
Vale discutir antes de implementar: a maioria dos sites de ingresso **não** faz
isso, e não por preguiça.

### Assentos marcados
O salto mais caro da lista. Deixa de existir "quantidade disponível" e passa a
existir mapa de assentos, reserva de assento específico e o problema clássico de
duas pessoas clicando na mesma poltrona no mesmo milissegundo.
**Backend:** tabela `seats`, reserva com lock por assento, mapa no contrato.
**Front:** mapa interativo — em SVG, que aguenta milhares de elementos melhor
que DOM.

### Cupons e meia-entrada
Preço deixa de sair direto do catálogo e passa por uma regra. O cuidado é não
deixar o desconto ser calculado no cliente: o valor cobrado tem que continuar
saindo do servidor, senão o cliente escolhe quanto pagar.

### Fila de espera para eventos concorridos
Para os lançamentos em que dez mil pessoas clicam no mesmo segundo. Fila com
posição visível, token de vez e janela de compra por pessoa.
**Backend:** Redis para a fila; é o primeiro caso em que uma peça nova de
infraestrutura se justifica de verdade.

### Reembolso
Estorno é um fluxo de pagamento próprio, com estados próprios e a mesma
exigência de idempotência da cobrança. É onde a `PaymentStrategy` mostra se foi
bem desenhada: cada método estorna diferente.

---

## Nível 3 — Experiência

### Atualização por push em vez de polling
Hoje a tela do pedido consulta a API a cada dois segundos. Funciona e é
suficiente, mas SSE no Order Service acabaria com o intervalo e com a espera
entre o pagamento ser aprovado e a tela perceber.
**Backend:** endpoint SSE alimentado pelo consumidor do resultado de pagamento.

### Ingresso no Apple Wallet e Google Wallet
O ingresso já tem código, portador e evento. Falta o `.pkpass` assinado. É o tipo
de detalhe que faz o projeto parecer produto e não exercício.

### PWA e uso offline
O ingresso precisa abrir na portaria, onde o sinal de celular sempre é ruim.
Service worker guardando os ingressos emitidos resolve o pior momento possível
de falta de rede.

### E-mail de verdade
O Notification Service já monta a notificação e a registra. Falta o provedor.

### Acessibilidade auditada
A base está feita — foco visível, alvos grandes, nomes acessíveis, `prefers-
reduced-motion`, contraste conferido nos dois temas. Falta o que só aparece em
teste real: percorrer a compra inteira com leitor de tela e só com o teclado.

### Internacionalização
Textos em português no código. Antes de traduzir, extrair — e aí decidir se vale.
Moeda e data já passam por `Intl`, que é a metade difícil.

---

## Nível 4 — Plataforma

- **Painel do organizador**: criar evento, definir lotes, acompanhar vendas.
  É praticamente um segundo produto, com autorização por papel.
- **Validação na portaria**: aplicativo que lê o QR e marca o ingresso como
  usado, com idempotência — o mesmo ingresso não entra duas vezes.
- **Relatórios**: receita por evento, taxa de recusa por método de pagamento,
  tempo entre pedido e aprovação. Os dados já existem; falta a leitura.
- **Testes de ponta a ponta no front**: Playwright percorrendo a compra contra a
  stack do compose. Hoje a cobertura automatizada para em `*IT`, e a tela é
  verificada à mão.

---

## O que fica de fora, e por quê

**Carrinho no servidor.** Segurar assento por tempo indeterminado para todo mundo
que abriu a página parece cuidado e vira estoque preso.

**Microfrontends.** São três telas.

**Biblioteca de componentes de terceiros.** O design system daqui tem quatro
arquivos CSS e nenhum runtime. Trazer uma biblioteca inteira para reaproveitar
botão e modal sairia mais caro do que os dois componentes.

**GraphQL.** O contrato REST é a fonte da verdade e está bem desenhado. Trocar
por trocar traria o problema de N+1 e o de cache sem nenhuma dor real em
contrapartida.
