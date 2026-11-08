#!/usr/bin/env bash
set -euo pipefail
hb_gradle_dir="${RUNNER_TEMP:-$PWD/work}/hyperbackup-gradle-9.7.1"
hb_gradle_sha=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
if [[ ! -x "$hb_gradle_dir/gradle-9.7.1/bin/gradle" ]]; then
    mkdir -p "$hb_gradle_dir"
    # GitHub's browser release URL currently returns 504 in hosted runners.
    # Prefer the publisher's asset API; a mirror is accepted only with the pinned publisher digest.
    if ! curl --fail --location --retry 2 --connect-timeout 20 --max-time 180 \
        -H 'Accept: application/octet-stream' \
        https://api.github.com/repos/gradle/gradle-distributions/releases/assets/520945879 \
        -o "$hb_gradle_dir/gradle.zip" || \
        ! echo "$hb_gradle_sha  $hb_gradle_dir/gradle.zip" | sha256sum --check --status; then
        curl --fail --location --retry 2 --connect-timeout 20 --max-time 300 \
            https://repo.huaweicloud.com/gradle/gradle-9.7.1-bin.zip -o "$hb_gradle_dir/gradle.zip"
    fi
    echo "$hb_gradle_sha  $hb_gradle_dir/gradle.zip" | sha256sum --check --status
    unzip -q "$hb_gradle_dir/gradle.zip" -d "$hb_gradle_dir"
    rm "$hb_gradle_dir/gradle.zip"
fi
mkdir -p work
hb_gradle_log="$(mktemp "$PWD/work/gradle-network.XXXXXX")"
trap 'rm -f "$hb_gradle_log"' EXIT
for hb_attempt in 1 2 3; do
    set +e
    "$hb_gradle_dir/gradle-9.7.1/bin/gradle" "$@" 2>&1 | tee "$hb_gradle_log"
    hb_result=${PIPESTATUS[0]}
    set -e
    if [[ "$hb_result" == 0 ]]; then exit 0; fi
    if [[ "$hb_attempt" == 3 ]] || ! grep -Eq 'Could not (resolve|download)' "$hb_gradle_log" || \
        ! grep -Eq 'Received status code 50[234]|Read timed out|ConnectTimeoutException|Could not (GET|HEAD)' "$hb_gradle_log"; then
        exit "$hb_result"
    fi
    echo "Dependency transport failed; retrying download (attempt $((hb_attempt + 1))/3)."
    sleep 5
done
