#!/bin/sh
# Download each Kids Mode channel's avatar into the launcher's assets (assets/kids/<channelId>.jpg).
cd "$(dirname "$0")/.."
OUT=launcher/app/src/main/assets/kids
mkdir -p "$OUT"
grep -o '<item>[^<]*</item>' launcher/app/src/main/res/values/kids.xml | sed 's/<[^>]*>//g' | while IFS='|' read -r name id; do
  [ -s "$OUT/$id.jpg" ] && continue
  img=$(curl -sL -A 'Mozilla/5.0' "https://www.youtube.com/channel/$id" | tr '\n' ' ' \
        | grep -o '<meta property="og:image" content="[^"]*"' | head -1 | sed 's/.*content="//;s/"$//')
  if [ -z "$img" ]; then echo "no picture for $name ($id)"; continue; fi
  curl -sL "$img" -o "$OUT/$id.jpg" && sips -Z 320 -s format jpeg "$OUT/$id.jpg" --out "$OUT/$id.jpg" >/dev/null
  echo "$name: ok"
done
