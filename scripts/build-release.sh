#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" != */* ]]; then
  echo "Usage: $0 OWNER/REPOSITORY" >&2
  exit 1
fi

repository="$1"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dist="$root/dist"

cd "$root"
GITHUB_REPOSITORY="$repository" ./gradlew test make makePluginsJson

rm -rf "$dist"
mkdir -p "$dist"
find KKPhimGenresProvider/build -maxdepth 1 -name '*.cs3' -exec cp {} "$dist/" \;
cp build/plugins.json "$dist/plugins.json"

jq --arg url "https://raw.githubusercontent.com/$repository/builds/plugins.json" \
  '.pluginLists = [$url]' repo.json > "$dist/repo.json"

echo "Release written to $dist"
