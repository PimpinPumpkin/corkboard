#!/usr/bin/env bash
# The bans that are about how this repo READS, checked instead of remembered.
#
#   scripts/check-writing.sh [<git range>]     default: origin/main..HEAD
#
# 1. NO AI ATTRIBUTION. No commit carries a Co-Authored-By trailer naming an assistant, or a
#    "generated with" line. GitHub turns that trailer into a listed contributor, permanently.
# 2. US ENGLISH, in commit messages and in what a change adds.
# 3. NO EM DASHES.
set -euo pipefail
RANGE="${1:-origin/main..HEAD}"
FAIL=0
BRITISH='\b(colour|centre|behaviour|neighbour|metres?|labelled|travelled|licence|defence|grey|organis|recognis|utilis|favourite|minimis|normalis)'
MSGS="$(git log "$RANGE" --format='%H%n%B' 2>/dev/null || true)"
if [ -n "$MSGS" ]; then
  if grep -inE "co-authored-by:.*(assistant|vendor|copilot|openai)|generated with \[?assistant|noreply@vendor|🤖" <<<"$MSGS"; then
    echo "FAIL: a commit message carries AI attribution" >&2; FAIL=1
  fi
  if grep -n "—" <<<"$MSGS"; then
    echo "FAIL: a commit message contains an em dash" >&2; FAIL=1
  fi
  if grep -inE "$BRITISH" <<<"$MSGS"; then
    echo "FAIL: a commit message uses a British spelling" >&2; FAIL=1
  fi
fi
# Authors and committers too: the trailer is not the only way a name gets onto a commit.
if git log "$RANGE" --format='%an <%ae> / %cn <%ce>' 2>/dev/null | grep -iE "assistant|vendor"; then
  echo "FAIL: a commit is authored or committed under an assistant's name" >&2; FAIL=1
fi
# What this change ADDS. This script is excluded from its own scan: it has to contain the words it looks for.
ADDED="$(git diff "$RANGE" -U0 -- '*.md' '*.kt' '*.kts' '*.xml' '*.sh' '*.yml' ':(exclude)scripts/check-writing.sh' 2>/dev/null | grep '^+' | grep -v '^+++' || true)"
if grep -inE "$BRITISH" <<<"$ADDED" >/dev/null 2>&1; then
  echo "FAIL: this change adds a British spelling:" >&2
  grep -inE "$BRITISH" <<<"$ADDED" | head -5 >&2
  FAIL=1
fi
if grep -n "—" <<<"$ADDED" >/dev/null 2>&1; then
  echo "FAIL: this change adds an em dash:" >&2
  grep -n "—" <<<"$ADDED" | head -5 >&2
  FAIL=1
fi
[ "$FAIL" -eq 0 ] && echo "writing checks passed"
exit "$FAIL"
