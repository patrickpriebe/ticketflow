#!/usr/bin/env bash
# =============================================================================
# Procura credencial em TODO o histórico do repositório.
#
# O hook de pre-commit é a primeira barreira, mas ele vive na máquina de quem
# desenvolve: quem clona o projeto não o tem até rodar `git config
# core.hooksPath .githooks`. Esta varredura roda na CI e não depende de ninguém
# ter configurado nada.
#
# Olha o histórico inteiro de propósito. Uma chave commitada por engano e
# apagada no commit seguinte continua acessível — e é exatamente essa que
# precisa ser rotacionada no provedor, porque remover o arquivo não a invalida.
# =============================================================================
set -uo pipefail

# tipo|regex — o mesmo conjunto do hook, em formato de credencial e não de
# senha específica, para continuar valendo depois de qualquer rotação.
PATTERNS=$(cat <<'EOF'
chave secreta Stripe|sk_(live|test)_[0-9a-zA-Z]{20}
chave restrita Stripe|rk_(live|test)_[0-9a-zA-Z]{20}
segredo de webhook Stripe|whsec_[0-9a-zA-Z]{20}
token do GitHub|(ghp_|github_pat_)[0-9a-zA-Z_]{20}
chave de acesso AWS|AKIA[0-9A-Z]{16}
URI do MongoDB com senha|mongodb\+srv://[^<$"'"'"'[:space:]]+:[^@$"'"'"'[:space:]]+@
URL do Postgres com senha|postgres(ql)?://[^<$"'"'"'[:space:]]+:[^@$"'"'"'[:space:]]+@
chave privada|BEGIN [A-Z ]*PRIVATE KEY
token do Slack|xox[baprs]-[0-9a-zA-Z]{10}
EOF
)

found=0

while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    kind=${entry%%|*}
    pattern=${entry#*|}

    # `git grep` em todos os commits alcançáveis. O `.env.example` e os próprios
    # arquivos de varredura ficam de fora: eles contêm os formatos por dever de
    # ofício.
    hits=$(git grep -I -n -E -e "$pattern" \
              $(git rev-list --all) -- \
              ':!.env.example' ':!.githooks/pre-commit' ':!.github/scripts/scan-secrets.sh' \
           2>/dev/null | head -5)

    if [ -n "$hits" ]; then
        echo "::error::$kind encontrada no histórico"
        # Mostra commit e arquivo, nunca a linha inteira — o log da CI é público
        # em repositório público, e imprimir o segredo aqui seria vazá-lo de novo.
        echo "$hits" | cut -d: -f1,2 | sed 's/^/  /'
        found=1
    fi
done <<< "$PATTERNS"

if [ "$found" -ne 0 ]; then
    cat <<'EOF'

Uma credencial está no histórico do repositório. Remover o arquivo agora NÃO
resolve: ela continua acessível em commits anteriores.

O que fazer, nesta ordem:
  1. Rotacionar a credencial no provedor. Isto é o que realmente a invalida.
  2. Trocar o valor por variável de ambiente no código.
  3. Só então, se quiser limpar o histórico, reescrevê-lo com git-filter-repo.
EOF
    exit 1
fi

echo "Nenhuma credencial encontrada no histórico."
