#!/usr/bin/env bash
set -euo pipefail

readonly PROJECT="terlingua-android"
readonly CONTENT_PREFIX="content/"
readonly SKIP_GLOBS=("docs/*")
FULL=0

usage() {
    cat <<USAGE
Usage: bash export.sh [-o FILE] [-f] [-- PATHSPEC...]

Writes an LLM context dump of the repository (default: docs/llm/dump.txt).

  File Tree                all git-tracked files (including ${CONTENT_PREFIX})
  Source, config, docs     full text (excluding docs/ and ${CONTENT_PREFIX})
  ${CONTENT_PREFIX}                 file tree only (excluded from dump text)
  Binary files             path only

  -o, --output FILE   write to FILE
  -f, --full          include text files inside ${CONTENT_PREFIX} too
  -h, --help          show this help

PATHSPECs are passed to git ls-files to narrow the export:

  bash export.sh -- app build-logic .github
  bash export.sh -- ':!*Test*'
  bash export.sh -f -o /tmp/book.txt -- content/books/my-book
USAGE
}

matches_any() {
    local path="$1" glob
    shift
    for glob in "$@"; do
        [[ "$path" == $glob ]] && return 0
    done
    return 1
}

is_text() {
    [[ ! -s "$1" ]] || LC_ALL=C grep -qI '' "$1"
}

classify() {
    local path="$1"
    if [[ "$path" == "$OUTPUT_REL" ]] || matches_any "$path" "${SKIP_GLOBS[@]}"; then
        echo skip
    elif [[ "$path" == "$CONTENT_PREFIX"* ]] && (( ! FULL )); then
        echo content
    elif ! is_text "$path"; then
        echo binary
    else
        echo full
    fi
}

render_tree() {
    awk -F'/' '
    {
        matched = 1
        for (i = 1; i <= NF; i++) {
            if (matched && i < NF && i < prev_len && $i == prev[i]) {
                continue
            }
            matched = 0
            indent = ""
            for (j = 1; j < i; j++) indent = indent "  "
            printf "%s%s%s\n", indent, $i, (i < NF ? "/" : "")
        }
        delete prev
        for (i = 1; i <= NF; i++) prev[i] = $i
        prev_len = NF
    }'
}

emit_file() {
    printf '==> %s <==\n' "$1"
    cat "$1"
    [[ -z "$(tail -c 1 "$1")" ]] || echo
    echo
}

write_dump() {
    echo "# ${PROJECT} export $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    if (( FULL )); then
        echo "# Full text of source, config, docs and content text files. Binary files: path only."
    else
        echo "# Full text of source, config and README. Content files: file tree only. Binary files: path only."
    fi
    echo

    echo "## File Tree (git-tracked)"
    if (( ${#TRACKED_FILES[@]} )); then
        printf '%s\n' "${TRACKED_FILES[@]}" | render_tree
    else
        echo "(none)"
    fi
    echo

    if (( ${#LISTED_FILES[@]} )); then
        echo "## Path only"
        printf '%s\n' "${LISTED_FILES[@]}"
        echo
    fi

    echo "## Files"
    echo
    if (( ${#TEXT_FILES[@]} )); then
        local path
        for path in "${TEXT_FILES[@]}"; do
            emit_file "$path"
        done
    fi
}

report() {
    local bytes
    bytes=$(wc -c < "$OUTPUT")
    {
        echo "Wrote ${OUTPUT}: ${bytes} bytes, ~$((bytes / 4)) tokens"
        echo "  git-tracked files in tree: ${#TRACKED_FILES[@]}"
        echo "  exported text files: ${#TEXT_FILES[@]}, content files (tree only): ${CONTENT_COUNT}, path only: ${#LISTED_FILES[@]}"
        if (( ${#TEXT_FILES[@]} )); then
            echo "  largest exported:"
            local path
            for path in "${TEXT_FILES[@]}"; do
                printf '%s\t%s\n' "$(wc -c < "$path")" "$path"
            done | sort -rn | head -n 10 | awk -F'\t' '{ printf "    %8d  %s\n", $1, $2 }'
        fi
    } >&2
}

main() {
    local output="" path kind
    local -a pathspecs=()
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -o|--output) output="$(realpath -m "$2")"; shift 2 ;;
            -f|--full) FULL=1; shift ;;
            -h|--help) usage; exit 0 ;;
            --) shift; pathspecs=("$@"); break ;;
            *) usage >&2; exit 1 ;;
        esac
    done

    cd "$(git rev-parse --show-toplevel)"
    OUTPUT="${output:-$PWD/docs/llm/dump.txt}"
    OUTPUT_REL="$(realpath -m --relative-to="$PWD" "$OUTPUT")"

    TRACKED_FILES=()
    TEXT_FILES=()
    LISTED_FILES=()
    CONTENT_COUNT=0

    while IFS= read -r path; do
        [[ -n "$path" ]] || continue
        TRACKED_FILES+=("$path")
    done < <(git ls-files -- "${pathspecs[@]}" | LC_ALL=C sort -u)

    while IFS= read -r path; do
        [[ -f "$path" ]] || continue
        kind="$(classify "$path")"
        case "$kind" in
            skip) ;;
            content) ((CONTENT_COUNT += 1)) ;;
            binary) LISTED_FILES+=("$path") ;;
            full) TEXT_FILES+=("$path") ;;
        esac
    done < <(git ls-files --cached --others --exclude-standard -- "${pathspecs[@]}" | LC_ALL=C sort -u)

    mkdir -p "$(dirname "$OUTPUT")"
    write_dump > "$OUTPUT"
    report
}

main "$@"
