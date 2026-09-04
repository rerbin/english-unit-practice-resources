---
description: 'Use this skill when updating, building, publishing or verifying the
  英语单元练 Android app: add a unit, generate British English audio packs, build/sign
  the APK, push resources to GitHub+Gitee, verify upload integrity. 更新/发布/校验英语单元练。'
name: 英语单元练-持续更新
version: 1.9.0
---

# 英语单元练-持续更新

维护“英语单元练”Android App（上海小学英语 4A，面向小学生与家长）的完整更新流水线：单元内容录入 → 英式语音合成 → 单元语音包与目录发布（GitHub+Gitee 双镜像）→ 上传后完整性校验并返回结果 → APK 构建签名 → 交付。

## 背景与架构（复盘结论）

- 产品边界：APK 只含程序、数据库、下载器与少量试听音；单元语音按单元在线下载，下载后完全离线；APK 不随单元数量变大（1.21.0 起约 110KB）。
- 数据层：单一 `AppDatabase`（SQLite，WAL+外键）拥有连接；`ContentDb`/`WrongBookDb` 只是仓储。表：textbooks、units、sections、content_items、item_options、audio_assets、package_imports、app_state、mistakes、mistake_words、learning_events。开发期结构变更直接破坏性重建，不写迁移。
- 文件层：`files/learning_content/` 私有目录存 packs、audio、images、packages；`cache/import_staging/` 临时目录用完清理；数据库只存相对路径与 SHA-256。
- 语音策略：公开单元包用 Piper `en_GB-alba-medium`（数据集 CC BY 4.0，保留归属）；Talkify/Edge 消费者接口仅研究结论，不复制进 App、不批量公开分发；系统 TTS 仅作无包时自动后备；不再引导家长手工安装第三方 TTS APK。
- 托管：GitHub `rerbin/english-unit-practice-resources`（main）为权威归档与国际备用；Gitee 同名仓库（master）为中国优先镜像。App 目录与下载均“Gitee 优先、GitHub 自动回退”。正式长期可再增国内对象存储。
- 错题本：正在练习/已经掌握两区；“已掌握”只打对号不删除；“移出练习”需确认并进入已经掌握；拼写正确自动打对号并返回；删除单元语音时保留错题引用音频。
- 历史教训（避免重犯）：WebView 内联 onclick 遇英文引号会失效，改为事件委托；`localStorage` 不存业务数据；Readify 不是系统 TTS 引擎，不能被他 App 调用；Gitee Release 有防盗链/验证码，发布用仓库 raw 文件而非 Release 附件；旧 GitHub 令牌对新仓库无权限（403），新仓库需新 fine-grained PAT；令牌不得回显（sed 脱敏）。
- 1.22.0 起设置只保留三档语音：内置离线（已下载包优先、系统 TTS 兜底）、系统英式、系统美式；在线 TTS 配置与“导入新单元”入口已删除（单元内容随 APK 种子或后续在线目录提供，家长不需要手工导入 JSON）。

## 纯离线包路线定稿（1.26.0）

- 设置中删除全部 TTS 项（含系统英式/美式、打开系统 TTS 设置）；发音唯一来源=已下载语音包；未下载时状态行与横幅提示下载，不再静默落系统 TTS。Java 端 TextToSpeech 相关代码整体移除。
- 语速对 MediaPlayer 生效：prepare 后 `PlaybackParams.setSpeed(speechRate)`；setSpeechRate 时对在播实例即时 applyRate；Web 启动用 `Android.getSpeechRate()` 回填选中态。
- 拼写判定归一化：统一 ’‘’/“”/‑–— ，去除标点两侧空格后再比较，消除 IME 与排版标点造成的“答对判错”。
- 错题本按钮文案：听／拼（短文案，大点击区）。
- 起步单元显示名统一为 **Starter**（单元 JSON/种子/目录/单元列表同源）。

## 双镜像角色定稿（1.27.1）

- GitHub＝权威全量：catalog、解包 packs/、整包 ZIP、源码、APK；App 取目录**GitHub 优先**（保证版本一致），Gitee 备用。
- Gitee＝中国瘦镜像：仅 catalog、源码、APK（小文件），不含 packs/ 与 ZIP；整包下载 gitee 404 时自动回退 github。
- 推送脚本注意：新克隆暂存库必须先 `git config user.name/email`；`pkill -f` 模式会匹配自身命令行，禁止在含该模式的脚本里使用。

## 1.30.0 美观/导出/清理盘

- 按钮美观：正在练习“已掌握”→“掌握”、“移出练习”→“移出”；已经掌握“移除”→“删除”；.smallbtn 加 white-space:nowrap+13px 杜绝折行。
- 已经掌握页“导出错题”：原生生成 HTML 表格 .xls（Excel 直接打开、中文正常、带边框可打印），列：序号/单元/状态/错误类型/内容(英)/错误单词/中文；SAF 保存，默认名 错题本.xls。
- 备份/还原彻底移除：Web 按钮、bridge、MainActivity 方法、WrongBookDb.backup/restoreBackup/fullRow、ContentDb.backupTables/restoreTables 全删。

## Skill 同步规则（1.9.0 起）

- 每次 skill 版本递增后，必须把 skill 目录（SKILL.md+scripts/）同步推送 GitHub 资源仓库 `skill/英语单元练-持续更新/` 路径，与 APK/目录同批或单独提交均可；台账记录 skill 版本。
- Gitee 不同步 skill：其 raw 有反爬 302/验证码、上行不稳，机制不健全；Gitee 仅保留 APK+catalog 瘦镜像。此为定策，除非用户另行要求。

## 1.29.0 真机四修复盘（WebView 对话框根因）

- “移出练习/移除”点击无效根因：WebView 未设 WebChromeClient，JS 的 confirm()/alert() 被 Android 静默吞掉，confirm 恒 false。修复：实现 onJsAlert/onJsConfirm 原生 AlertDialog。凡依赖 confirm 的流程（移出、移除、还原、删语音）此前全部失效，一并恢复。
- 拼写误判根因之二：中文输入法全角字符（ｈｉｓｔｏｒｙ）与零宽字符。修复：比较前全角→半角、去零宽；失败提示改为“再试一次。正确拼写：<原文>”，便于家长核对。
- 纪律：WebView 应用必须配置 WebChromeClient 支持 JS 对话框；任何“按钮点了没反应”先查对话框/回调链，再查业务逻辑。

## 1.28.0 真机三修复盘

- 已掌握内容“移除”无效根因：已经掌握 tab 只有“重新练习”，没有删除入口。修复：已掌握条目增加“移除”按钮（confirm 后 WrongBookDb.delete 物理删除，级联清理 mistake_words/learning_events）；正在练习 tab 的“移出练习”语义不变（移入已经掌握）。
- 单元选择框下拉箭头：`.chev{margin-left:auto}` 恒定靠右对齐。
- 设置页底部显示版本号：桥 `getAppVersion()` 返回 versionName(versionCode)。

## 1.27.0 真机二修复盘

- 错题本“听”无效根因：list() 未返回 audioKey，Web 以空资源请求播放。修复：list() 以 source_item_id 关联 content_items.audio_key 返回；Web 听/听发音按钮显式 `Android.play(unitId, audioKey, text, ...)`。无需改表结构，不 bump DB 版本。
- 拼写仍误判根因：尾标点差异（如输入 `B-o-o-k` 对原文 `B-o-o-k.`）。修复：严格归一化之外增加“纯字母数字”回退比较。
- 字母音“/ə/ 不对”：裸音素过短难辨。改为“音素+0.3s 静音+例词”合成（er→ə…teacher，W→w…wall），RMS 校验非静音；语音包 v4。
- 布局：顶栏整行删除（logo/设置），设置入口移入底部 tab 最右（学习/错题本/设置）。
- 验收提速：verify_mirrors 改为 GitHub 全量 SHA（权威）+ Gitee 仅 Range/Content-Length 大小核对；禁止整 ZIP 下载 Gitee；禁止 sleep 链等待，依赖任务通知。

## 后台长任务根因与治理（1.26.0）

根因：① Gitee 上行带宽低且大仓库（含解包音频约 26MB）易 connection reset；② 推送与校验常合并进单条命令，超 60s 被自动转后台后叠加等待循环。治理：
- Gitee 改为**瘦镜像**：只含 catalog/zip/app/apk，不含 packs/（增量文件仅 GitHub 提供，App 逐文件下载已带 gitee→github 回退）；
- `GIT_HTTP_POST_BUFFER=524288000`；推送失败自动 force 重试一次（唯一发布者，覆盖式安全）；
- 推送与校验拆成独立步骤；校验只读小文件+按需下载 zip；
- 任何 >60s 的命令一律主动拆步，不依赖自动后台化。

## 真机回归修复纪律（1.25.0 复盘）

- 说明文字不显示类问题：内容字段必须贯通“单元 JSON → DB 表列 → unit() 查询 → Web 渲染”四层；加列时开发期递增 AppDatabase VERSION 强制重建，否则旧库静默丢字段。
- 字母/字母组合发音：Piper 对裸字母读字母名（W→“double-u”），必须用 espeak 音素输入 `[[IPA]]`；符号须存在于模型 `phoneme_id_map`（en_GB-alba 用 IPA Unicode：w、ə、ɜ、ɑː、ɔː）；生成后核对时长>0.1s 再入包。
- 连读队列必须携带真实条目 id（metaById 的键）以取得 audioResource；临时 id（line_N）会落空到系统 TTS 导致无声。
- 无真机时的自测替代：node --check JS、grep 契约断言、音频时长核对、双镜像 SHA 复算；仍要请用户真机确认交互类问题。

## 单元内容规范（1.23.0 定稿）

- 每个一级分区（语音/单词/短语/句子/作文）如原试卷带说明文字，单元 JSON 的 section 必须含 `description` 字段；App 以蓝色引用条（📖）显示在分区标题下。录入新单元时先核对原卷，漏抄说明视为内容缺陷。
- 语音分区若针对具体字母/字母组合（如 Unit 1 的 W、Starter 的 er/ir/ur/ar/or），列表必须把该字母作为 `type:"letter"` 条目置于对应组最上方，可点读、可记错题；App 对 letter 条目放大显示并加 🔤 前缀。
- 单元 JSON 与 DB 种子（catalog_seed.json）同步更新；音频键 audioResource 由 gen_audio 的 audio_map 回填。

## 语音包版本与增量更新（1.23.0 定稿）

- 目录条目含 `audioVersion`（包版本）与 `minAppVersionCode`（所需最低 App versionCode）；App 横幅三态：未下载→“下载语音”；`audioVersion>已装`→“更新语音（v a→v b）”；`minAppVersionCode>当前App`→隐藏按钮并提示先更新 App。
- 仓库同时发布解包目录 `packs/<unitId>/{manifest.json,audio/*}` 与整包 ZIP；目录条目含 `manifestUrl` 双镜像。
- 增量策略：已装包且版本落后时，先取新 manifest，按**文件 SHA 名**求缺失集；缺失总量 ≤1.5MB 时逐文件下载（双镜像回退、单文件 SHA 校验），写新 manifest 即完成；否则回退整包 ZIP（断点续传）。首次安装走整包。
- 结论记录：不做二进制差分包；“按单元全量 ZIP 首装 + manifest 级增量补新”在简单性与流量间取平衡（例：补一个 W 发音只下载约几十KB）。

## 存储目录与清理规则（1.22.0 定稿）

```text
files/learning_content/
└── packs/<unitId>/
    ├── manifest.json          单元清单（itemId/文本哈希/音频哈希/大小）
    └── audio/<SHA-256>.wav    按内容哈希去重的音频
    （下载中的 <unitId>.part 也放在 packs/ 下，支持跨会话续传）
cache/import_staging/          仅解压/还原临时数据，App 启动即清空
```

- 数据库只存元数据与相对路径；音频实体只在 packs/。
- 清理入口：设置“删除本单元语音”（保留错题引用音频）；`cache` 启动自动清；`.part` 在校验成功或删除单元时移除。
- 备份 ZIP 含 `data.json` + `files/`（即 packs），还原时 `copyTreeIntoStore` 覆盖回私有目录。

## 源码与 APK 发布（1.24.0 定稿）

- 仓库根目录增加 `app/`（src、res、AndroidManifest、build.sh、BUILD.md）与 `apk/`（APK + `.sha256` 侧车文件）。
- **脱敏硬门禁**：发布版 build.sh 的签名口令必须来自环境变量 `KS_PATH/KS_PASS/KEY_PASS`；推送前 `grep -c` 口令必须为 0；keystore 永不进仓库。
- 每次对外版本：先构建 APK → 复制源码与 APK 进 publish → 随资源一起双站推送 → 校验时附带检查 `apk/<name>` 两站 200 且 sha 与侧车一致。

## UI 一致性纪律（1.24.0 定稿）

- Web 头部 logo 必须引用启动图标同一资源：`file:///android_res/drawable/app_icon.png`，禁止另画近似 SVG。
- logo 旁不重复显示应用名文字（桌面名已足够），仅保留语音状态副标题。
- 界面图标一律内联 SVG；禁止 `⌄` 等文本字符当图标（旧 WebView 会渲染成“V”类字形）。

## 更新推送机制（APK/资源向两平台）

1. 本地工作区为唯一事实源；每次变更一个 commit，提交信息含版本号。
2. 推送顺序：先 GitHub（快、稳定、权威），后 Gitee（慢，必须后台+重试，`main:master`）。
3. 只推差异文件（`git add -A`）；资源仓库打 `audio-v<N>` annotated tag 便于回溯。
4. APK 二进制不进资源仓库日常提交：GitHub Release 挂 APK+源码 ZIP 作权威归档；Gitee Release 仅供家长手工下载（App 不程序化访问 Release）。
5. 推送后必须跑 `verify_mirrors.py` 双镜像复算 SHA-256 并回报；GitHub 为硬门禁，Gitee 传输错误只报告；另检查 `apk/` 文件两站可达且 sha 一致。结果追加到“发布台账”。
7. 推送复盘：暂存库直推遇 non-fast-forward 时改用“克隆远端→覆盖→提交→推送”；Gitee 上传慢（分钟级）必须后台执行；Gitee raw 在推送后短时可能返回非 JSON（反爬/传播延迟），verify 需重试而非判死。
6. 若 Gitee raw 对 App 下载持续不稳：catalog 的 mirrors 支持有序列表，升级为国内对象存储主源，两站转归档。

## App 内下载机制（高质量/准确/可续传）

1. 先取 `catalog.json`（Gitee→GitHub 故障切换），按 unitId 找版本、大小、SHA-256、镜像。
2. 断点续传：`Range: bytes=<part长度>-`；206 追加、200 重头；part 文件持久化在 packs/，中断后再点“下载语音”即续传。
3. 完整性：整包 SHA-256+大小双重校验失败即删 part 重来；解压防 `..`/绝对路径越界；先解压到 staging 再整体落位。
4. 体验：横幅显示百分比；失败提示“稍后可继续下载”；成功后“可离线使用”。

## 关键路径与凭据

- 项目根：`<ws>/english-paper-reader/`；构建：`./english-paper-reader/build.sh <versionCode> <versionName>`（内部用 `<nas>/tools/jdk` 与 `<nas>/tools/android-sdk/build-tools/35.0.0`；apksigner 需 java 在 PATH：`export PATH=<nas>/tools/jdk/bin:$PATH`）。
- 签名：`mooc-tv-work/mooc-tv-release.jks`，alias `mooc-tv`（密码已在 build.sh 内）。
- 单元内容 JSON：`res/raw/starter_unit.json`、`res/raw/4au1_my_school.json`；新增单元放 `english-paper-reader/publish/units/<unitId>.json`（schema `english-paper/v1`，含 translation）。
- Piper 模型（持久）：`english-paper-reader/tools/piper/en_GB-alba-medium.onnx(.json)`；piper Python 包安装到持久目录 `<ws>/tools/piper-tts`（不要装 /tmp，容器重启即失）。
- 发布目录：`english-paper-reader/publish/`（catalog.json、units/、<unitId>-audio-v1.zip、解压包目录）。
- 凭据：GitHub 令牌在 `<ws>/github-token-template.txt` 第 1 行；Gitee 令牌在 `<ws>/gitee-token-template.txt` 第一个非 `#` 行。缺失或 403 时让用户按模板重新生成（GitHub fine-grained：仅该仓库、Contents 读写；Gitee：projects 权限）。

## 执行

本 skill 带两个批处理 JSON。用读取本 SKILL.md 时看到的绝对目录拼接 `file_path`。

### A. 发布新单元/更新资源（含上传后校验）

```
run_tool_batch(
  file_path="<skill_dir>/scripts/publish_resources.json",
  args={
    "ws": "<ws 绝对路径>",
    "unit_json": "<ws>/english-paper-reader/publish/units/4A-U2.json",
    "repo": "rerbin/english-unit-practice-resources",
    "version": "1"
  }
)
```

最后一步 `verify_mirrors.py` 的 stdout 是 JSON 校验报告，必须原样转述给用户（见“校验报告格式”）。

### B. 构建并验证 APK

```
run_tool_batch(
  file_path="<skill_dir>/scripts/build_apk.json",
  args={
    "ws": "<ws 绝对路径>",
    "version_code": "25",
    "version_name": "1.22.0"
  }
)
```

### 批处理参数

- `ws`：工作区根目录；示例 `<ws>`。
- `unit_json`：待发布单元 JSON；新单元先按 `english-paper/v1` 录好英文+中文。
- `repo`：GitHub/Gitee 同名的 `owner/name`。
- `version`：语音包 packageVersion，内容修订时递增。
- `version_code`/`version_name`：APK 版本；每次对外交付递增。

### 批处理失败处理

1. 先核对参数是否都传了真实值（不要 `args={}`）。
2. `PIPER_MISSING`：跑 `python3 -m pip install --target <ws>/tools/piper-tts piper-tts -q` 后重试步骤 1。
3. push 步骤超 60 秒会被自动转后台：等待完成通知，勿重复 push；用 `git ls-remote` 两个远端确认分支存在。
4. 校验 FAIL：HASH_MISMATCH 重新跑 make_packs+push；gitee 传输错误（exit 56/超时）而 github PASS 时，报告中标注“Gitee 传输不稳定，完整性以 GitHub 复算为准”，并稍后重试 gitee 全量校验。
5. 仍失败则按“逐步参考”手工执行，并询问用户是否用 edit_file 优化本 skill 脚本。

### 校验报告格式

ALWAYS use this exact template:

```
资源校验结果（catalog 来源：<gitee|github>）
| 镜像 | 单元 | 大小 | SHA-256 一致 | 状态 |
|------|------|------|--------------|------|
| github | 4A-Starter | 3877657 | 是 | OK |
总体：<PASS|FAIL>
```

## 逐步参考

1. 确保 piper：`python3 -c "import sys;sys.path.insert(0,'<ws>/tools/piper-tts');import piper"`，失败则 pip install --target 持久目录。
2. 合成音频：`scripts/gen_audio.py <piper_dir> <model_dir> <unit_json> <audio_out_dir>`；对唯一文本逐条 `synthesize_wav`，`SynthesisConfig(length_scale=1.12,noise_scale=0.667,noise_w_scale=0.8,volume=1.0)`；输出 `audio_map.json`。已存在的 wav 跳过，可续跑。
3. 打包：`scripts/make_packs.py <ws> <unit_json> <audio_out_dir> <publish_dir> <version> <repo>`；生成 manifest（items 含 itemId/textHash/audioKey/file/sha256/size/mime）、ZIP、更新 catalog.json、写 `publish/units/<unitId>.json` 供未来在线目录。
4. 推送：`scripts/push_mirrors.sh <publish_dir> <repo> <gh_token_file> <gg_token_file> <stage_dir>`；先 pull `--allow-unrelated-histories` 再 push main→github、main:master→gitee；输出必须 sed 脱敏。
5. 校验：`scripts/verify_mirrors.py <gitee_catalog_url> <github_catalog_url>`；github 全量下载复算 SHA-256+大小；gitee 先取目录，ZIP 做状态+大小核对，网络允许时全量复算；返回 JSON 报告。
6. APK：`build.sh` 后 `apksigner verify --verbose`、`aapt list | grep -c 'res/raw/gb_\\|res/raw/st_'` 应为 0、`sha256sum` 与 `ls -lh` 一并回报。

## 注意事项

- 不向 App 或仓库写入任何云厂商密钥；在线 TTS 如需正式音色，走服务端中转。
- 新增单元顺序值 order：起步单元 0、Unit 1 为 1，依次递增。
- 发布后提醒用户轮换在进程列表暴露过的令牌。
- 真机交付前说明镜像状态：Gitee 优先、GitHub 回退。

## 自我更新（skill 持续更新机制）

1. 流程变化时直接用 edit_file 修改本 skill 的 SKILL.md 与 scripts/*，并把 SKILL.md frontmatter 的 `version` 递增（当前 1.0.0）。
2. 本 skill 已登记在 `<ws>/state/user-skills.json`，遵循夸克同步规则：版本递增后运行 `OPENCLAW_SERVICE_MARKER=openclaw node <ws>/scripts/quark-sync.cjs skills` 上传 `v<版本>` 快照，旧快照保留。
3. 每次实际发布后，把“单元、版本、两站 commit、校验结论”追加到本文件“发布台账”一节，保持 skill 与项目状态同步。

## 发布台账

- 2026-09-04：首发 audio-v1。4A-Starter（104 条/90 音频/3877657B，sha 3f6e8c04…c9ce5564）、4A-U1（78 条/76 音频/4783454B，sha e6ec1316…abf2df92c）。GitHub main 已验证 200+大小一致；Gitee master 推送成功、raw 200；APK 1.21.0（110KB）交付。
- 2026-09-04：APK 1.22.0（110KB）。设置精简为三档语音；删除在线 TTS 配置与“导入新单元”；存储定稿 packs/ 结构；下载改 Range 断点续传；推送/下载机制写入本 skill v1.1.0。
- 2026-09-04：APK 1.23.0（102KB，code26）。分区说明文字与字母条目入内容规范；语音包 v2（Starter 109 条/95 音频，U1 79 条/77 音频，含 W 与 er/ir/ur/ar/or 发音）；目录加 audioVersion/minAppVersionCode/manifestUrl；仓库加解包 packs/ 支持增量；skill v1.2.0。
- 2026-09-04：APK 1.24.0（code27）。头部 logo 改用启动图标资源、去掉重复应用名、⌄ 改 SVG；源码（脱敏 build.sh）与 APK+sha256 入仓库双站发布；push_mirrors 改克隆式；skill v1.3.0。
- 2026-09-04：APK 1.25.0（code28，DB v3）。修三处真机问题：sections 增加 description 列并在 App 显示；字母发音改 [[IPA]] 音素合成（语音包 v3）；连读队列改真实条目 id。skill v1.4.0。
- 2026-09-04：APK 1.26.0（code29，DB v4）。TTS 全清理、语速对音频生效、拼写归一化、听/拼短文案、Starter 命名；Gitee 瘦镜像+postBuffer+拆步治理长任务。skill v1.5.0。
- 2026-09-04：APK 1.27.0（code30）。错题本听修复（audioKey 关联）、拼写纯字母数字回退、字母音改音素+例词（语音包 v4）、顶栏删除设置入底部 tab、验收改快模式。skill v1.6.0。
- 2026-09-04：APK 1.28.0（code32）。已掌握可移除（物理删除+级联）、箭头靠右、设置页版本号。skill v1.7.0。
- 2026-09-04：APK 1.29.0（code33）。WebChromeClient 补 JS 对话框（移出/移除/还原等 confirm 流程恢复）；拼写归一化加全角→半角与零宽清理，失败显示正确拼写。skill v1.8.0。
- 2026-09-05：APK 1.30.0（code34）。按钮文案/nowrap 美观、导出错题 .xls、备份还原彻底移除；skill v1.9.0 起同步 GitHub（Gitee 不同步 skill，定策）。