#!/bin/bash
# MindLink 源码构建（build.ps1 的 bash 移植版）。
#
# 用法：
#   MINDLINK_KEYSTORE=/path/app.jks MINDLINK_KS_PASS=... MINDLINK_KEY_ALIAS=... \
#     bash scripts/build.sh
#
# 未提供 MINDLINK_KEYSTORE 时执行到「对齐」为止，产出 <build>/aligned.apk 并退出，
# 不生成任何替代密钥。
#
# 可用环境变量覆盖：
#   MINDLINK_JDK / MINDLINK_SDK / MINDLINK_GRADLE / MINDLINK_APKTOOL / MINDLINK_D8
#   MINDLINK_AAPT2 / MINDLINK_KEYSTORE / MINDLINK_KS_PASS / MINDLINK_KEY_PASS / MINDLINK_KEY_ALIAS
#   MINDLINK_PAYLOAD_MINIFY=true|false（默认 false）
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

log() { printf '\n=== %s\n' "$*"; }
die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

JDK=${MINDLINK_JDK:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
JAVAC="$JDK/bin/javac"
JAVA="$JDK/bin/java"
JAR="$JDK/bin/jar"
SDK=${MINDLINK_SDK:-/root/tools/sdk}
ANDROID_JAR="$SDK/platforms/android-37.0/android.jar"
GRADLE=${MINDLINK_GRADLE:-/root/tools/gradle-9.9.2/gradle-9.9.2/bin/gradle}
APKTOOL=${MINDLINK_APKTOOL:-/root/tools/apktool.jar}
D8_JAR=${MINDLINK_D8:-/root/workspace/tools/r8.jar}
AAPT2=${MINDLINK_AAPT2:-/root/tools/aapt2-wrapper/aapt2-9.3.2-15703166-linux/aapt2}
BT=$(printf '%s\n' "$SDK"/build-tools/*/ | sort -V | tail -1)
# SDK 里的 zipalign/apksigner 是 x86 二进制；ARM 主机优先用系统自带（已有 qemu 包装或脚本）。
ZIPALIGN=${MINDLINK_ZIPALIGN:-$(command -v zipalign || echo "${BT}zipalign")}
APKSIGNER=${MINDLINK_APKSIGNER:-$(command -v apksigner || echo "${BT}apksigner")}

BUILD=${MINDLINK_BUILD_ROOT:-$root/build/srcbuild-$(date +%Y%m%d-%H%M%S)}
for tool in "$JAVAC" "$ANDROID_JAR" "$GRADLE" "$APKTOOL" "$D8_JAR" "$AAPT2" "$ZIPALIGN"; do
  [ -e "$tool" ] || die "missing tool: $tool"
done
[ ! -e "$BUILD" ] || die "build root must be new: $BUILD"
mkdir -p "$BUILD"/{stub,srcout,dex,resources,logs}

export JAVA_HOME="$JDK" ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
gradle_run() { # 参数：工作目录 gradle 参数...
  local dir=$1; shift
  # aapt2 用可执行文件覆盖，避免把本机绝对路径写进 gradle.properties。
  "$GRADLE" -p "$dir" --no-daemon -Pandroid.aapt2FromMavenOverride="$AAPT2" --max-workers="${MINDLINK_GRADLE_WORKERS:-2}" --console=plain "$@" \
    > "$BUILD/logs/gradle-$(date +%H%M%S)-$RANDOM.log" 2>&1 || {
      tail -30 "$(ls -t "$BUILD"/logs/gradle-*.log | head -1)" >&2
      return 1
    }
}

log '[1/8] 编译 Xposed stub'
find java_stub -name '*.java' > "$BUILD/stub-files.txt"
"$JAVAC" -nowarn -source 11 -target 11 -classpath "$ANDROID_JAR" -encoding UTF-8 \
  -d "$BUILD/stub" @"$BUILD/stub-files.txt"

log '[2/8] 编译 java_src'
find java_src xposed_compat shared-ui/src/main/java -name '*.java' > "$BUILD/src-files.txt"
"$JAVAC" -nowarn -source 11 -target 11 -classpath "$ANDROID_JAR:$BUILD/stub" -encoding UTF-8 \
  -d "$BUILD/srcout" @"$BUILD/src-files.txt"

log '[3/8] d8 生成功能 dex'
"$JAR" --create --file "$BUILD/classes.jar" -C "$BUILD/srcout" .
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 33 \
  --output "$BUILD/dex" "$BUILD/classes.jar"
[ -f "$BUILD/dex/classes.dex" ] || die 'd8 produced no classes.dex'

log '[4/8] apktool 回编宿主 APK'
"$JAVA" -Xmx2048m -jar "$APKTOOL" b . -o "$BUILD/stage.apk" --aapt "$AAPT2"

log '[5/8] Compose 载荷：运行时清单与共享资源暂存'
gradle_run ui-compose "-PmdlBuildRoot=$BUILD/desktop-inventory" :app:writeRuntimeInventory
gradle_run ui-compose/sidepanel "-PmdlSidePanelBuildRoot=$BUILD/sidepanel-inventory" writeRuntimeInventory
python3 -I scripts/prepare_compose_resources.py \
  --source res --output "$BUILD/resources/res" \
  --sidepanel-res ui-compose/sidepanel/src/main/res \
  --inventory "$BUILD/desktop-inventory/reports/runtime-artifacts.tsv" \
  --checksums ui-compose/runtime-checksums.json \
  --sidepanel-inventory "$BUILD/sidepanel-inventory/reports/runtime-artifacts.tsv" \
  --sidepanel-checksums ui-compose/sidepanel/runtime-checksums.json \
  --public res/values/public.xml \
  --stable-ids-output "$BUILD/resources/original-stable-ids.txt" \
  --manifest-output "$BUILD/resources/staging.json"

log '[6/8] Compose 载荷：引导链接 + 编译 + 双运行时校验'
gradle_run ui-compose \
  "-PmdlBuildRoot=$BUILD/desktop-bootstrap" \
  "-PmdlResourceStage=$BUILD/resources/res" \
  "-PmdlStableIds=$BUILD/resources/original-stable-ids.txt" \
  "-PmdlEmitIds=$BUILD/resources/full-stable-ids.txt" \
  :app:processReleaseResources
# 默认不裁剪：R8 会把 Compose 运行时与 Material3 剥掉，导致模块内设置面板/侧栏
# 加载时报 IllegalStateException（isolation/entry 校验失败）。
readonly payload_minify=${MINDLINK_PAYLOAD_MINIFY:-false}
gradle_run ui-compose/sidepanel \
  "-PmdlPayloadMinify=$payload_minify" \
  "-PmdlSidePanelBuildRoot=$BUILD/sidepanel-final" \
  "-PmdlSidePanelResourceStage=$BUILD/resources/res" \
  "-PmdlSidePanelStableIds=$BUILD/resources/full-stable-ids.txt" \
  "-PmdlSidePanelEmitIds=$BUILD/resources/sidepanel-final-ids.txt" \
  assembleRelease writeRuntimeInventory
python3 -I scripts/check_compose_resources.py \
  --sidepanel-ui-apk "$BUILD/sidepanel-final/outputs/apk/release/MindLinkSidePanel-release-unsigned.apk" \
  --public res/values/public.xml \
  --stable-ids "$BUILD/resources/full-stable-ids.txt" \
  --sidepanel-emitted-ids "$BUILD/resources/sidepanel-final-ids.txt" \
  --inventory "$BUILD/desktop-inventory/reports/runtime-artifacts.tsv" \
  --checksums ui-compose/runtime-checksums.json \
  --sidepanel-inventory "$BUILD/sidepanel-final/reports/runtime-artifacts.tsv" \
  --sidepanel-checksums ui-compose/sidepanel/runtime-checksums.json \
  --aapt2 "$AAPT2" --staging-manifest "$BUILD/resources/staging.json" \
  --report "$BUILD/dual-runtime-check.json"

log '[7/8] 合并功能 DEX 与隔离载荷'
python3 -I scripts/merge.py "$BUILD/stage.apk" "$BUILD/dex/classes.dex" "$BUILD/merged.apk" \
  --sidepanel-ui-apk "$BUILD/sidepanel-final/outputs/apk/release/MindLinkSidePanel-release-unsigned.apk" \
  --public res/values/public.xml \
  --stable-ids "$BUILD/resources/full-stable-ids.txt" \
  --sidepanel-emitted-ids "$BUILD/resources/sidepanel-final-ids.txt" \
  --inventory "$BUILD/desktop-inventory/reports/runtime-artifacts.tsv" \
  --checksums ui-compose/runtime-checksums.json \
  --sidepanel-inventory "$BUILD/sidepanel-final/reports/runtime-artifacts.tsv" \
  --sidepanel-checksums ui-compose/sidepanel/runtime-checksums.json \
  --aapt2 "$AAPT2" --staging-manifest "$BUILD/resources/staging.json" \
  --report "$BUILD/merge-check.json"
"$ZIPALIGN" -p -f 4 "$BUILD/merged.apk" "$BUILD/aligned.apk"
"$ZIPALIGN" -c -p 4 "$BUILD/aligned.apk"

log '[8/8] 签名'
if [ -z "${MINDLINK_KEYSTORE:-}" ]; then
  printf '未设置 MINDLINK_KEYSTORE：跳过签名。\n对齐产物：%s\n' "$BUILD/aligned.apk"
  exit 0
fi
[ -e "$MINDLINK_KEYSTORE" ] || die "keystore missing: $MINDLINK_KEYSTORE"
[ -n "${MINDLINK_KEY_ALIAS:-}" ] || die 'MINDLINK_KEY_ALIAS is required'
"$APKSIGNER" sign \
  --ks "$MINDLINK_KEYSTORE" \
  --ks-pass "pass:${MINDLINK_KS_PASS:?MINDLINK_KS_PASS required}" \
  --key-pass "pass:${MINDLINK_KEY_PASS:-${MINDLINK_KS_PASS}}" \
  --ks-key-alias "$MINDLINK_KEY_ALIAS" \
  --out "$BUILD/MindLink-1.0.apk" "$BUILD/aligned.apk"
"$APKSIGNER" verify --verbose --print-certs "$BUILD/MindLink-1.0.apk"
"$ZIPALIGN" -c -p 4 "$BUILD/MindLink-1.0.apk"
printf '\n完成：%s\n' "$BUILD/MindLink-1.0.apk"
sha256sum "$BUILD/MindLink-1.0.apk"
