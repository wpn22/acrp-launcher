#!/usr/bin/env bash
# ============================================================
#  إعداد سيرفر تجربة ACRPJobs
#
#    ./setup.sh              ← Paper 1.12.2 (أسرع وأخف - يكفي لتجربة البلق إن)
#    ./setup.sh --mohist     ← Mohist 1.12.2 (لو تبي تجرب مع المودات مثل سيرفرك)
#
#  يحتاج: Java 8  +  curl
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$HERE/run"
PLUGIN_SRC="$HERE/../acrp-jobs"
FLAVOUR="paper"

[ "${1:-}" = "--mohist" ] && FLAVOUR="mohist"

say() { printf '\n\033[1;36m==>\033[0m %s\n' "$1"; }
die() { printf '\n\033[1;31mخطأ:\033[0m %s\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- java 8
say "أتحقق من Java"
command -v java >/dev/null 2>&1 || die "ما لقيت Java. ركّب Java 8 أول: https://adoptium.net/temurin/releases/?version=8"
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+)\.?([0-9]+)?.*/\1 \2/' | awk '{print ($1=="1")?$2:$1}')"
if [ "$JAVA_MAJOR" != "8" ]; then
  printf '\033[1;33mتنبيه:\033[0m نسخة Java عندك %s. ماين كرافت 1.12.2 يحتاج **Java 8** بالذات.\n' "$JAVA_MAJOR"
  printf '        حمّله من: https://adoptium.net/temurin/releases/?version=8\n'
  printf '        أو حدد مسار جافا 8 يدوياً في متغير JAVA_CMD.\n\n'
fi
JAVA_CMD="${JAVA_CMD:-java}"

# ---------------------------------------------------------------- plugin jar
say "أدوّر على جار البلق إن"
JAR="$(ls -1t "$PLUGIN_SRC"/target/ACRPJobs-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  printf 'ما لقيت جار مبني. أبنيه الحين...\n'
  command -v mvn >/dev/null 2>&1 || die "ما فيه Maven. إما ركّبه، أو حمّل الجار من GitHub Actions ➜ Build Jobs Plugin ➜ Artifacts وحطه في $PLUGIN_SRC/target/"
  (cd "$PLUGIN_SRC" && mvn -q -B -ntp clean package)
  JAR="$(ls -1t "$PLUGIN_SRC"/target/ACRPJobs-*.jar | head -1)"
fi
printf '   %s\n' "$(basename "$JAR")"

# ---------------------------------------------------------------- server jar
mkdir -p "$RUN/plugins"
SERVER_JAR="$RUN/server.jar"

if [ ! -f "$SERVER_JAR" ]; then
  say "أنزّل سيرفر $FLAVOUR 1.12.2"
  if [ "$FLAVOUR" = "paper" ]; then
    BUILD="$(curl -fsS 'https://api.papermc.io/v2/projects/paper/versions/1.12.2' | sed -E 's/.*"builds":\[//; s/\].*//' | tr ',' '\n' | tail -1)"
    [ -n "$BUILD" ] || die "ما قدرت أعرف آخر بِلد من PaperMC. نزّل paper-1.12.2 يدوياً وسمّه $SERVER_JAR"
    URL="https://api.papermc.io/v2/projects/paper/versions/1.12.2/builds/$BUILD/downloads/paper-1.12.2-$BUILD.jar"
  else
    URL="https://mohistmc.com/api/1.12.2/latest/download"
  fi
  curl -fL --progress-bar -o "$SERVER_JAR" "$URL" || die "فشل التنزيل. نزّل السيرفر يدوياً وسمّه: $SERVER_JAR"
else
  say "السيرفر منزّل من قبل - أتخطى التنزيل"
fi

# ---------------------------------------------------------------- files
say "أركّب البلق إن والإعدادات الجاهزة"
cp -f "$JAR" "$RUN/plugins/"
cp -f "$HERE/preset/eula.txt" "$RUN/eula.txt"
[ -f "$RUN/server.properties" ] || cp -f "$HERE/preset/server.properties" "$RUN/server.properties"

# المناطق والمسارات والنقاط: تُنسخ مرة وحدة بس، عشان ما نمسح تعديلاتك.
mkdir -p "$RUN/plugins/ACRPJobs"
for f in zones.yml routes.yml spots.yml; do
  if [ -f "$RUN/plugins/ACRPJobs/$f" ]; then
    printf '   موجود من قبل، ما لمسته: %s\n' "$f"
  else
    cp -f "$HERE/preset/plugins/ACRPJobs/$f" "$RUN/plugins/ACRPJobs/$f"
    printf '   نسخت: %s\n' "$f"
  fi
done

# ---------------------------------------------------------------- start script
cat > "$RUN/start.sh" <<START
#!/usr/bin/env bash
cd "\$(dirname "\${BASH_SOURCE[0]}")"
exec \${JAVA_CMD:-java} -Xms1G -Xmx2G -jar server.jar nogui
START
chmod +x "$RUN/start.sh"

say "تم!"
cat <<DONE

  شغّل السيرفر:     $RUN/start.sh
  ادخل عليه:        localhost
  أول ما يشتغل، اكتب في الكونسول:   op اسمك_في_ماين_كرافت

  وبعدها اقرأ خطوات التجربة في:     $HERE/README.md

DONE
