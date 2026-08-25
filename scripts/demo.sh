#!/usr/bin/env bash
# Walks the whole API against a running service: ingest, poll, search, invalidate, stats.
# Usage: ./scripts/demo.sh [base-url]      (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

submit() {
  curl -sS -X POST "$BASE/api/v1/documents" \
    -H 'Content-Type: application/json' \
    -d "{\"documentId\":\"$1\",\"content\":\"$2\",\"channel\":\"$3\"}"
}

# The task's own status is the first "status" in the response; the nested document has one too.
task_status() {
  curl -sS "$BASE/api/v1/tasks/$1" | grep -o '"status":"[A-Z]*"' | head -1 | cut -d'"' -f4
}

await() {
  for _ in $(seq 1 100); do
    local status
    status=$(task_status "$1")
    case "$status" in
      SUCCEEDED|FAILED|CANCELLED) echo "$status"; return 0 ;;
    esac
    sleep 0.2
  done
  echo "TIMEOUT"; return 1
}

say "1. Submit three documents (returns immediately with a task id)"
ACCEPTED=$(submit "demo-1" "vector search over embeddings" "demo")
echo "$ACCEPTED"
TASK=$(echo "$ACCEPTED" | sed -n 's/.*"taskId":"\([^"]*\)".*/\1/p')
submit "demo-2" "searching vectors and embeddings at scale" "demo" > /dev/null
submit "demo-3" "banana bread recipe with walnuts" "demo" > /dev/null

say "2. Wait for the first task to finish"
echo "task $TASK -> $(await "$TASK")"
sleep 1

say "3. Search"
curl -sS -X POST "$BASE/api/v1/search" -H 'Content-Type: application/json' \
  -d '{"query":"vector search embeddings","topK":3,"channel":"demo"}'

say "4. Invalidate the top document and search again"
curl -sS -X POST "$BASE/api/v1/documents/demo-1/invalidate" > /dev/null
curl -sS -X POST "$BASE/api/v1/search" -H 'Content-Type: application/json' \
  -d '{"query":"vector search embeddings","topK":3,"channel":"demo"}'

say "5. Provenance of a document (hit count, timestamps)"
curl -sS "$BASE/api/v1/documents/demo-2"

say "6. Service statistics"
curl -sS "$BASE/api/v1/stats"
echo
