#!/usr/bin/env bash
# Tests for docker-build/run.sh with stubbed aws and java (no AWS access needed). Run: bash docker-build/test/run_sh_test.sh
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FAILURES=0

# runs run.sh in a sandbox; $1 = java exit code, $2 = array index or "" for a single job. Prints the sandbox path.
run_case() {
    local exit_code=$1 index=$2
    local box; box=$(mktemp -d)
    mkdir -p "$box/bin" "$box/run" "$box/out"
    cp "$HERE/../run.sh" "$box/run/run.sh"
    printf '#!/usr/bin/env bash\nexit 0\n' > "$box/run/sync-loop.sh"
    # aws stub: logs every call; the job prefix already holds the output of another array child
    cat > "$box/bin/aws" <<STUB
#!/usr/bin/env bash
echo "\$*" >> "$box/aws.log"
if [ "\$1 \$2" = "s3api list-objects-v2" ]; then
    printf 'out/job/other-child/result.csv\tout/job/own/result.csv\n'
fi
if [ "\$1 \$2" = "s3api head-object" ]; then exit 1; fi
exit 0
STUB
    # java stub: logs the class path and writes one result file, then exits with the given code
    cat > "$box/bin/java" <<STUB
#!/usr/bin/env bash
prev=""; for a in "\$@"; do [ "\$prev" = "-cp" ] && echo "\$a" > "$box/classpath"; prev="\$a"; done
mkdir -p "$box/out/own"; echo ok > "$box/out/own/result.csv"
exit $exit_code
STUB
    chmod +x "$box/bin/aws" "$box/bin/java" "$box/run/run.sh" "$box/run/sync-loop.sh"
    (
        export PATH="$box/bin:$PATH" JAR_NAME="jars/sub/app.jar" XMX=1g JOB_NAME=job JOB_INPUT_BUCKET=in \
            JOB_OUTPUT_BUCKET=outbucket OUTPUT_SCENARIO=out MAIN_CLASS=Main OUTPUT_DIR="$box/out" \
            MATSIM_TMPDIR="$box/tmp" INPUT_PATHS="inputs/"
        [ -n "$index" ] && export AWS_BATCH_JOB_ARRAY_INDEX="$index"
        cd "$box/run" && bash ./run.sh > "$box/stdout.log" 2>&1
    )
    echo "$box"
}

check() { # description, condition (evaluated)
    if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; FAILURES=$((FAILURES + 1)); fi
}

box=$(run_case 1 3)
check "array child: own output tagged failed" "grep -q 'put-object-tagging.*--key out/job/own/result.csv.*Value=failed' $box/aws.log"
check "array child: other child's output not tagged" "! grep -q 'other-child' $box/aws.log"
check "array child: own metadata file" "[ -f $box/out/_run_metadata_3.json ] && grep -q 'key out/job/_run_metadata_3.json' $box/aws.log"
check "array child: class path of the downloaded jar" "[ \"\$(cat $box/classpath)\" = app.jar ]"

box=$(run_case 1 "")
check "single job: all objects under the prefix tagged failed" "grep -q 'key out/job/other-child/result.csv.*Value=failed' $box/aws.log && grep -q 'key out/job/own/result.csv.*Value=failed' $box/aws.log"
check "single job: metadata file name unchanged" "[ -f $box/out/_run_metadata.json ]"

box=$(run_case 0 2)
check "successful array child: only the sentinel tag" "[ \$(grep -c put-object-tagging $box/aws.log) -eq 1 ] && grep -q 'Value=success' $box/aws.log"

[ "$FAILURES" -eq 0 ] && echo "all tests passed" || { echo "$FAILURES test(s) failed"; exit 1; }
