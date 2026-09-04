# 构建说明

- 工具链：JDK + Android build-tools 35.0.0（aapt2/d8/zipalign/apksigner）。
- 签名：自备 keystore，运行前设置环境变量 `KS_PATH`、`KS_PASS`、`KEY_PASS`；仓库不含任何密钥与口令。
- 命令：`./build.sh <versionCode> <versionName>`。
- 语音资源不随源码分发，见仓库根目录 catalog.json 与 packs/。
