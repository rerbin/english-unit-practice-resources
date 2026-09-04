# 英语单元练资源库：首次发布步骤

请将本目录中的以下四个文件上传到 **GitHub** 和 **Gitee** 项目的根目录：

1. `catalog.json`
2. `README.md`
3. `4A-Starter-audio-v1.zip`
4. `4A-U1-audio-v1.zip`

项目地址：

- GitHub：<https://github.com/rerbin/english-unit-practice-resources>
- Gitee：<https://gitee.com/rerbin/english-unit-practice-resources>

## 最简单的上传方式

### GitHub

1. 打开项目页面，点击 **Add file → Upload files**。
2. 一次选择上述四个文件。
3. 填写提交说明：`发布起步单元和 Unit 1 语音包`。
4. 点击 **Commit changes**，提交到 `main` 分支。

### Gitee

1. 打开项目页面，点击 **上传文件**。
2. 一次选择上述四个文件。
3. 提交到默认 `master` 分支。
4. 填写提交说明：`发布起步单元和 Unit 1 语音包`。

## 上传后验证

在浏览器分别打开下列链接，应当开始下载而不是显示404：

- <https://gitee.com/rerbin/english-unit-practice-resources/raw/master/catalog.json>
- <https://raw.githubusercontent.com/rerbin/english-unit-practice-resources/main/catalog.json>
- <https://gitee.com/rerbin/english-unit-practice-resources/raw/master/4A-Starter-audio-v1.zip>
- <https://raw.githubusercontent.com/rerbin/english-unit-practice-resources/main/4A-U1-audio-v1.zip>

上传完成后，把任意一个 `catalog.json` 的可访问链接回复给我。我会将下载目录写入 App，完成“下载语音 / 可离线使用 / 删除单元语音 / 自动镜像切换”功能，并重新发送轻量 APK。

## 注意

- 不要解压两个 ZIP 后分别上传内部音频；App 按单元下载 ZIP 并在本机校验、解压。
- 后续新增单元时，新增一个 `4A-U2-audio-v1.zip` 并更新 `catalog.json`，无需重新上传整个 APK。
- 文件名不要修改；`catalog.json` 的 SHA-256 校验值与 ZIP 文件一一对应。
