#!/usr/bin/env bash
# Install the git hooks that enforce the repo's writing rules locally, so a slip is caught BEFORE
# it is public. A commit message cannot be amended once it is pushed, and an attribution trailer
# that reaches GitHub lists its subject as a contributor for good.
#
#   bash scripts/install-hooks.sh
set -euo pipefail
HOOKS="$(git rev-parse --git-common-dir)/hooks"
mkdir -p "$HOOKS"

# Refuses the commit itself, so the bad message never exists to be pushed.
cat > "$HOOKS/commit-msg" <<'HOOK'
#!/usr/bin/env bash
if grep -iqE "co-authored-by:.*(assistant|vendor|copilot|openai)|generated with \[?assistant|noreply@vendor|🤖" "$1"; then
  echo "commit refused: the message carries AI attribution. Remove the trailer and commit again." >&2
  exit 1
fi
HOOK

cat > "$HOOKS/pre-push" <<'HOOK'
#!/usr/bin/env bash
# No AI attribution, US English, no em dashes. Installed by scripts/install-hooks.sh.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
[ -f "$ROOT/scripts/check-writing.sh" ] || exit 0
FAIL=0
while read -r _local_ref local_sha _remote_ref remote_sha; do
  [ "$local_sha" = "0000000000000000000000000000000000000000" ] && continue
  if [ "$remote_sha" = "0000000000000000000000000000000000000000" ] || ! git cat-file -e "$remote_sha^{commit}" 2>/dev/null; then
    RANGE="$local_sha"   # a new branch or rewritten history: check everything reachable
  else
    RANGE="$remote_sha..$local_sha"
  fi
  bash "$ROOT/scripts/check-writing.sh" "$RANGE" || FAIL=1
done
if [ "$FAIL" -ne 0 ]; then
  echo "push blocked by the writing rules. A commit message cannot be fixed after it is pushed:" >&2
  echo "  git commit --amend    (or git rebase -i) and try again." >&2
  exit 1
fi
HOOK
chmod +x "$HOOKS/commit-msg" "$HOOKS/pre-push"
echo "installed $HOOKS/commit-msg and $HOOKS/pre-push"
