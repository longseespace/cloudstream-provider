#!/usr/bin/env bash
set -euo pipefail

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

api="https://phimapi.com"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

genres_json="$tmp_dir/genres.json"
genre_json="$tmp_dir/genre.json"
detail_json="$tmp_dir/detail.json"
search_json="$tmp_dir/search.json"
playlist="$tmp_dir/playlist.m3u8"

expected_genres='[
  "bi-an", "chien-tranh", "chinh-kich", "co-trang", "gia-dinh", "hai-huoc",
  "hanh-dong", "hinh-su", "hoc-duong", "khoa-hoc", "kinh-di", "kinh-dien",
  "lich-su", "mien-tay", "phim-18", "phim-ngan", "phieu-luu", "than-thoai",
  "the-thao", "tre-em", "tai-lieu", "tam-ly", "tinh-cam", "vien-tuong",
  "vo-thuat", "am-nhac"
]'

curl --fail --silent --show-error --location \
  "$api/the-loai" > "$genres_json"

jq -e --argjson expected "$expected_genres" '
  (.status == "success" or .status == true) and
  (([.data.items[].slug] | sort) == ($expected | sort))
' "$genres_json" >/dev/null

curl --fail --silent --show-error --location \
  "$api/v1/api/the-loai/kinh-di?page=1&limit=5" > "$genre_json"

jq -e '
  .status == true and
  (.data.APP_DOMAIN_CDN_IMAGE | type == "string") and
  (.data.items | length > 0) and
  (.data.params.pagination.totalPages > 0)
' "$genre_json" >/dev/null

slug="$(jq -r '.data.items[0].slug' "$genre_json")"
test -n "$slug" && test "$slug" != "null"

curl --fail --silent --show-error --location \
  "$api/phim/$slug" > "$detail_json"

jq -e '
  .status == true and
  (.movie.name | type == "string") and
  (.movie.category | length > 0) and
  (.episodes | length > 0) and
  ([.episodes[].server_data[]?.link_m3u8 | select(type == "string" and length > 0)] | length > 0)
' "$detail_json" >/dev/null

movie_name="$(jq -r '.movie.name' "$detail_json")"
stream_url="$(jq -r '[.episodes[].server_data[]?.link_m3u8 | select(type == "string" and length > 0)][0]' "$detail_json")"

# The API contract guarantees a non-empty link_m3u8. The separate player CDN
# can temporarily return 404/403 to GitHub-hosted runners, so probe it for
# diagnostics without making that external CDN's availability gate the build.
playlist_status="$(curl --silent --location \
  --referer "https://kkphim.com/" \
  --output "$playlist" \
  --write-out '%{http_code}' \
  "$stream_url" || true)"

if [[ "$playlist_status" =~ ^2[0-9][0-9]$ ]] && grep -q '#EXTM3U' "$playlist"; then
  printf 'KKPhim HLS probe passed: HTTP %s\n' "$playlist_status"
else
  printf 'KKPhim HLS probe advisory: CDN returned HTTP %s for %s\n' \
    "${playlist_status:-unavailable}" "$stream_url" >&2
fi

curl --fail --silent --show-error --location --get \
  --data-urlencode "keyword=$movie_name" \
  --data-urlencode "limit=5" \
  "$api/v1/api/tim-kiem" > "$search_json"

jq -e --arg slug "$slug" '
  (.status == true or .status == "success") and
  (.data.items | length > 0) and
  ([.data.items[].slug] | index($slug) != null)
' "$search_json" >/dev/null

printf 'KKPhim API integration passed: %s (%s)\n' "$movie_name" "$slug"
