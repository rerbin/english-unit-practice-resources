#!/bin/sh
# Clone-based push: always fast-forward safe, works for empty and existing repos.
# usage: push_mirrors.sh <publish_dir> <repo> <gh_token_file> <gg_token_file> <stage_dir>
set -e
PUB="$1"; REPO="$2"; GH_TOKEN_FILE="$3"; GG_TOKEN_FILE="$4"; STAGE="$5"
export GIT_HTTP_POST_BUFFER=524288000
GHT=$(sed -n '1p' "$GH_TOKEN_FILE" | tr -d '\r ')
GGT=$(grep -v '^#' "$GG_TOKEN_FILE" | sed -n '1p' | tr -d '\r ')

push_one() { # $1 = clone url, $2 = push refspec, $3 = label, $4 = exclude_packs(1|0)
  rm -rf "$STAGE"
  if git clone -q "$1" "$STAGE" 2>/dev/null && [ -d "$STAGE/.git" ]; then :; else
    rm -rf "$STAGE"; mkdir -p "$STAGE"; cd "$STAGE"; git init -q -b main
  fi
  cd "$STAGE"
  git config user.name 'English Unit Practice Publisher'
  git config user.email 'noreply@english-unit-practice.local'
  # overlay publish content
  find . -maxdepth 1 ! -name '.git' ! -name '.' -exec rm -rf {} +
  cp -r "$PUB/." "$STAGE/"
  [ "$4" = "1" ] && { rm -rf "$STAGE/packs"; rm -f "$STAGE"/*-audio-v*.zip; }
  git add -A
  if git diff --cached --quiet; then echo "$3: no changes"; return 0; fi
  git commit -q -m "Update audio catalog and unit packs"
  BR=$(git rev-parse --abbrev-ref HEAD)
  if git push -q "$1" "$BR:$2" 2>/tmp/push_err_$3.log; then echo "$3: pushed"; else
    # we are the sole publisher of this repo; content is fully overlaid, so force is safe
    if git push -q -f "$1" "$BR:$2" 2>>/tmp/push_err_$3.log; then echo "$3: pushed (force)"; else sed -E 's#(https://[^:]+:)[^@]+@#\1<redacted>@#g' /tmp/push_err_$3.log; echo "$3: PUSH_FAILED"; exit 1; fi
  fi
}

push_one "https://rerbin:${GHT}@github.com/${REPO}.git" main github
push_one "https://rerbin:${GGT}@gitee.com/${REPO}.git" master gitee 1
echo PUSH_DONE
