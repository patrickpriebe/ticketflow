# ==============================================================================
# O gateway de pagamento simulado, empacotado para rodar fora desta máquina.
#
# No compose as regras entram por volume, que é o certo em desenvolvimento:
# editar um arquivo e recarregar vale mais do que reconstruir uma imagem. Nenhuma
# hospedagem monta volume do repositório, então aqui elas são assadas na imagem.
#
# Este contêiner nunca vê dinheiro de verdade. Ele existe para que os caminhos
# infelizes sejam reproduzíveis: ninguém consegue pedir a um provedor real que dê
# timeout sob demanda, e o corte em 2000 dá aprovação e recusa sem editar nada.
# ==============================================================================
FROM wiremock/wiremock:3.13.2-3

COPY infra/wiremock/mappings /home/wiremock/mappings

COPY --chmod=755 infra/wiremock/entrypoint.sh /usr/local/bin/gateway-entrypoint.sh

EXPOSE 8080

# A decisão sobre a API de administração mora no script, não aqui: ela depende do
# ambiente e o padrão precisa ser o trancado. Ver infra/wiremock/entrypoint.sh.
ENTRYPOINT ["/usr/local/bin/gateway-entrypoint.sh"]
