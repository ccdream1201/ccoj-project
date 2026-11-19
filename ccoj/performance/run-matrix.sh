#!/usr/bin/env bash
# Start the isolated Compose project and its sandbox before running this script.
set -euo pipefail
cd "$(dirname "$0")"
: "${CCOJ_ACCOUNT:?Set the acceptance account}"
: "${CCOJ_PASSWORD:?Set the acceptance password}"
: "${CCOJ_PROBLEM_ID:?Set the published two-number sum problem ID}"

results="${CCOJ_RESULTS_DIR:-results/local}"
mkdir -p "$results"

run_case() {
    local output="$1"
    shift
    docker run --rm --network ccoj-acceptance_default \
        -e CCOJ_BASE_URL=http://gateway:8080 \
        -e CCOJ_ACCOUNT -e CCOJ_PASSWORD -e CCOJ_PROBLEM_ID \
        "$@" -v "$PWD:/work" grafana/k6:1.4.0 \
        run --summary-export "/work/$results/$output.json" /work/ccoj.js
}

for vus in 5 20 50; do
    run_case "query-$vus" -e CCOJ_SCENARIO=query -e "CCOJ_QUERY_VUS=$vus"
done
run_case sustained -e CCOJ_SCENARIO=sustained -e CCOJ_SUBMIT_RATE=1
run_case mixed -e CCOJ_SCENARIO=mixed -e CCOJ_QUERY_VUS=20 -e CCOJ_SUBMIT_RATE=1
