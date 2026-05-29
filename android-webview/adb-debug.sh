#!/bin/bash
# 浪险橙 Kiosk 真机调试工具
# 用法:
#   ./adb-debug.sh push      — 推送最新HTML+图片到设备sdcard（热更新，不用重装APK）
#   ./adb-debug.sh reload    — 重启APP加载sdcard文件
#   ./adb-debug.sh log       — 实时查看ASR日志
#   ./adb-debug.sh install   — 安装最新APK（需要先从Actions下载到 this dir）
#   ./adb-debug.sh status    — 查看设备连接状态
#   ./adb-debug.sh clean     — 清除sdcard上的开发文件（切回内置版）

DEVICE="172.20.10.2:43241"
SDCARD_DIR="/sdcard/Download/kiosk"
KIOSK_PREVIEW="../kiosk-preview"
APK_PATTERN="app-release-unsigned.apk"
ADB_CMD="adb -s $DEVICE"

CMD=${1:-status}

# Ensure connected
ensure_connected() {
    $ADB_CMD devices | grep -q "$DEVICE.*device" || {
        echo "Connecting to $DEVICE..."
        adb connect $DEVICE
        sleep 1
    }
}

case $CMD in
    push)
        ensure_connected
        echo "=== Pushing HTML + images to device ==="
        $ADB_CMD shell "mkdir -p $SDCARD_DIR/images"
        $ADB_CMD push "$KIOSK_PREVIEW/index.html" "$SDCARD_DIR/"
        for img in "$KIOSK_PREVIEW/images/"*; do
            [ -f "$img" ] && $ADB_CMD push "$img" "$SDCARD_DIR/images/"
        done
        echo "=== Done! Run './adb-debug.sh reload' to restart app ==="
        ;;

    reload)
        ensure_connected
        echo "=== Force-stopping and restarting kiosk app ==="
        $ADB_CMD shell am force-stop com.langxiancheng.kiosk
        sleep 0.5
        $ADB_CMD shell am start -n com.langxiancheng.kiosk/.MainActivity
        echo "=== App restarted (loading from sdcard) ==="
        ;;

    log)
        ensure_connected
        echo "=== Live ASR log (Ctrl+C to stop) ==="
        $ADB_CMD logcat -c  # clear old logs
        $ADB_CMD logcat -v time | grep -iE "LXCKiosk|ASR Match|AndroidASR|SpeechRecognizer|SUNMI"
        ;;

    logall)
        ensure_connected
        echo "=== All kiosk logs ==="
        $ADB_CMD logcat -c
        $ADB_CMD logcat -v time | grep -iE "LXCKiosk|chromium|WebView"
        ;;

    install)
        ensure_connected
        APK=$(ls -t ./$APK_PATTERN 2>/dev/null | head -1)
        if [ -z "$APK" ]; then
            echo "ERROR: No APK found. Download from GitHub Actions and place ./app-release-unsigned.apk here."
            echo "Actions: https://github.com/xinyineee/langxiancheng-kiosk-h5/actions"
            exit 1
        fi
        echo "=== Installing $APK ==="
        $ADB_CMD install -r "$APK"
        echo "=== Launching app ==="
        $ADB_CMD shell am start -n com.langxiancheng.kiosk/.MainActivity
        ;;

    status)
        adb devices | grep "$DEVICE" && echo "Device connected: $DEVICE" || echo "Device NOT connected. Run: adb connect $DEVICE"
        # Check if dev mode files exist
        ensure_connected
        echo ""
        echo "--- Dev mode check ---"
        $ADB_CMD shell "ls -la $SDCARD_DIR/index.html 2>/dev/null && echo 'DEV MODE: sdcard HTML exists' || echo 'PROD MODE: will load from APK assets'"
        ;;

    clean)
        ensure_connected
        echo "=== Removing dev files from device ==="
        $ADB_CMD shell "rm -rf $SDCARD_DIR"
        echo "=== Restarting app (will use bundled assets) ==="
        $ADB_CMD shell am force-stop com.langxiancheng.kiosk
        sleep 0.5
        $ADB_CMD shell am start -n com.langxiancheng.kiosk/.MainActivity
        ;;

    *)
        echo "浪险橙 Kiosk 真机调试工具"
        echo "用法: $0 {push|reload|log|logall|install|status|clean}"
        echo ""
        echo "  push    推送HTML+图片到设备sdcard（热更新）"
        echo "  reload  重启APP（加载sdcard文件）"
        echo "  log     实时ASR日志"
        echo "  logall  全部WebView日志"
        echo "  install 安装APK"
        echo "  status  查看连接和开发模式状态"
        echo "  clean   清除sdcard开发文件"
        ;;
esac
