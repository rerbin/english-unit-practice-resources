# 英语单元练资源发布库

本仓库用于发布“英语单元练”的单元目录和离线语音包。

## 发布文件

- `catalog.json`：应用读取的资源目录。
- `4A-Starter-audio-v1.zip`：起步单元语音包。
- `4A-U1-audio-v1.zip`：Unit 1 语音包。

## 发布步骤

1. 在 GitHub 和 Gitee 创建同名公开仓库。
2. 将本目录提交到两个仓库。
3. 创建 `audio-v1` Release，并上传两个 ZIP。
4. 将 `catalog.json` 中两个占位地址替换为实际固定下载地址。
5. 更新 App 中的目录地址后构建。

## 许可

应用源代码与语音资源许可分开。当前语音由 Piper `en_GB-alba-medium` 生成；模型数据集许可为 CC BY 4.0。发布资源时必须保留归属说明。
