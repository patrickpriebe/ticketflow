# Frontend

O front do TicketFlow existe por um motivo específico: um sistema distribuído
só demonstra o que sabe fazer quando alguém consegue *ver* a resposta imediata,
o pedido mudando de status sozinho e o ingresso aparecendo. Sem tela, tudo isso
vira captura de terminal.

Ele consome duas APIs — Order Service e Notification Service — e não fala com o
Payment Service. Isso não é limitação: o Payment não tem API pública, ele reage
a eventos.

## Stack

| Peça | Escolha | Por quê |
|---|---|---|
| Build | Vite | Servidor de desenvolvimento rápido e proxy embutido |
| UI | React 18 + TypeScript estrito | Tipos espelham o contrato OpenAPI |
| Estilo | CSS com custom properties | Tema claro/escuro sai de graça; zero runtime |
| Rotas | Roteador próprio (~60 linhas) | Ver [Decisões](#decisões) |
| Estado | `useState` + `sessionStorage` | Não há estado global suficiente para justificar biblioteca |

Nenhuma dependência além de `react` e `react-dom`.

## Telas

```
/                    Home: hero de busca, destaques, próximos eventos
/events              Descobrir: filtros de cidade, preço e texto
/events/:id          Evento: sobre, local e seletor de ingressos
/checkout            Pagamento: escolha do método e resumo
/orders              Meus pedidos, com filtro de status
/orders/:id          Pedido: canhoto, prazo, ingressos e linha do tempo
/signin              Identificação
```

## Design system

Tudo sai de `src/styles/tokens.css`. **Nenhum componente escreve um
hexadecimal** — se escrever, o tema escuro não alcança aquele pedaço da tela, e
é sempre assim que um dark mode fica pela metade.

- **Primária `#0052ff`** — ações, links e estados ativos
- **Tinta `#121212`** — texto e superfícies invertidas
- **Destaque `#bf3003`** — urgência, esgotado, recusa. Usado com parcimônia: se
  aparece em tudo, deixa de significar alguma coisa
- **Neutra `#f8f9fa`** — fundo de seção
- **Inter**, com a pilha do sistema como reserva

O tema tem **três estados**: claro, escuro e "seguir o sistema". Sem o terceiro,
quem escolheu uma vez fica preso — e a maioria das pessoas nunca volta ao botão
para corrigir. O padrão é o terceiro, e nesse caso o atributo `data-theme` nem
existe no DOM: quem decide é o `prefers-color-scheme`.

No escuro a paleta não é a clara invertida. O azul de marca puro sobre fundo
quase preto fica abaixo do contraste mínimo, então clareia para `#4d84ff`. E o
fundo não é preto puro, porque sombra nenhuma aparece sobre `#000` e a
hierarquia de profundidade some junto.

## Decisões

**Roteador próprio em vez de React Router.** Não é economia de dependência: é
que um front deste tamanho usa uma fração da biblioteca, e a parte que usa cabe
em cinquenta linhas legíveis sobre a History API. O que não cabe — rotas
aninhadas, carregamento por rota, guardas — também não é necessário aqui. O
inegociável era existir URL de verdade: botão voltar funcionando, pedido aberto
por link direto e recarregar sem cair na home.

**Carrinho no cliente, não no servidor.** Nada é reservado enquanto a pessoa
escolhe. A reserva acontece no `POST /orders`, numa transação só. Um carrinho no
servidor teria de segurar assento por tempo indeterminado para todo mundo que
abriu a página — é o tipo de coisa que parece cuidado e vira estoque preso.
Fica em `sessionStorage` para sobreviver a um F5 no meio do checkout.

**Pôster desenhado, não fotografado.** O catálogo não tem imagem, e criar um
campo `imageUrl` no contrato só para o front ficar bonito seria o rabo abanando
o cachorro: sem lugar para hospedar e sem fluxo de upload, o campo nasceria
vazio. Cada evento ganha um SVG derivado do próprio id — mesma cara sempre,
grade variada, zero bytes de rede.

**Sem coleta de dados de cartão.** A tela de pagamento explica o que cada método
faz e não pede número nem CVV. Não é simplificação: o Payment Service conversa
com um gateway simulado, e mesmo em produção o sistema guardaria só bandeira e
últimos quatro dígitos. Um formulário de cartão que não vai a lugar nenhum seria
teatro — e teatro convincente demais para o gosto.

**Filtro de cidade no backend, preço e texto no cliente.** Cidade é parâmetro
real do catálogo. Preço e texto são filtrados sobre a página carregada, porque o
endpoint não tem esses parâmetros. Com o catálogo atual cabe tudo numa página;
quando não couber, filtrar no cliente passa a mentir — e a resposta certa é
levar os dois para o backend, não paginar melhor no front.

**Polling, não WebSocket.** É o que o contrato define: o `POST` responde `202` e
o cliente consulta `GET /orders/{id}`. O intervalo para sozinho quando o pedido
chega a um estado final.

## Rodando

```bash
npm install --prefix frontend
npm run dev --prefix frontend
```

O Vite faz proxy de `/api/v1/tickets` para o Notification Service (8083) e do
resto de `/api` para o Order Service (8081). A ordem das regras importa: a mais
específica primeiro, senão `/api` engole tudo.

Proxy em vez de CORS no backend porque, em produção, o front seria servido pelo
mesmo domínio — e abrir CORS só para o ambiente de desenvolvimento é uma
configuração que costuma vazar para produção por esquecimento.

## O que vem depois

Ver [05-roadmap-produto.md](05-roadmap-produto.md).
