---
description: 'Use this skill when updating, building, publishing or verifying the
  英语单元练 Android app: add a unit, generate British English audio packs, build/sign
  the APK, push resources to GitHub+Gitee, verify upload integrity. 更新/发布/校验英语单元练。'
name: 英语单元练-持续更新
version: 2.4.5
---

# 英语单元练-持续更新

维护“英语单元练”Android App（上海小学英语 4A，面向小学生与家长）的完整更新流水线：单元内容录入 → 英式语音合成 → 单元语音包与目录发布（GitHub+Gitee 双镜像）→ 上传后完整性校验并返回结果 → APK 构建签名 → 交付。

## 背景与架构（复盘结论）

- 产品边界：APK 只含程序、数据库、下载器与少量试听音；单元语音按单元在线下载，下载后完全离线；APK 不随单元数量变大（1.21.0 起约 110KB）。
- 数据层：单一 `AppDatabase`（SQLite，WAL+外键）拥有连接；`ContentDb`/`WrongBookDb` 只是仓储。表：textbooks、units、sections、content_items、item_options、audio_assets、package_imports、app_state、mistakes、mistake_words、learning_events。开发期结构变更直接破坏性重建，不写迁移。
- 文件层：`files/learning_content/` 私有目录存 packs、audio、images、packages；`cache/import_staging/` 临时目录用完清理；数据库只存相对路径与 SHA-256。
- 【历史废弃】语音策略：公开单元包用 Piper `en_GB-alba-medium`（数据集 CC BY 4.0，保留归属）；Talkify/Edge 消费者接口仅研究结论，不复制进 App、不批量公开分发；系统 TTS 仅作无包时自动后备；不再引导家长手工安装第三方 TTS APK。
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

## UI 视觉自检流程（1.10.0 起，强制）

任何界面改动交付前必须截图自检：
1. 生成 /tmp/preview.html：home.html 注入 Android 桥 stub（requestLastUnit/requestCatalog/requestUnit/requestPackState/requestWrongList/requestWrongCounts/getSpeechRate/getAppVersion 等，目录为单元数组）；
2. `playwright` + 系统 `/usr/bin/chromium`（--no-sandbox）以 390x844 截四页：学习、错题本、设置、单元选择；
3. 逐图检查：对齐/折行/留白/层级/选中态；headless 无 emoji 字体出现方框属环境差异，真机正常；
4. 发现不一致（如简称回退全名）当场修数据或 CSS，再构建。

## 1.35.0 状态语义与下载进度盘

- 顶部状态行改为状态机，禁止残留“正在播放”：
  - 空闲且包就绪→“语音包已就绪，可离线使用”；未下载→“语音包未下载，点读前请先下载”；
  - 下载成功→“语音包下载成功，可离线使用”（justDownloaded 标记优先）；
  - 播放中→“正在播放…”（playbackStarted）；播放完成/停止/出错→playbackEnded 回空闲；
  - 连读完成短暂提示后 1.2s 回空闲。
- 注意：packState 在 ready 分支有 early return，setIdleStatus 必须放在 handler 头部（本次踩坑）。
- 下载进度可视化：横幅内进度条（.packbar）+ 百分比文案；downloadFinished 隐藏并归零。
- 交付前用 playwright 逐状态断言（ready/not ready/playing/ended/progress/after-download）。

## 1.34.0 使用说明页盘

- 学习页“停止”改“停止连读”，语义与连读本页对应。
- 设置新增“使用说明”：独立全屏页（渐变头+返回），卡片分区：下载语音/点读与连读/切换单元/语速/错题本/其他；文案面向家长与学生，不出现技术名词；helppage[hidden] 需显式 display:none（flex 会覆盖 hidden 属性）。
- 交付前 playwright 截帮助页自检。

## 1.33.0 数据结构审查盘（DB v6）

删除（只写不读/无查询对应）：
- audio_assets 表及其写入（音频实体走语音包 manifest，ref_count 只增不减属误导）；
- mistake_words.normalized_word（Web 即时归一化）；
- idx_units_recent / idx_mistakes_unit / idx_items_audio / 与 UNIQUE 重复的 idx_units_book_order。

约束补强：
- units UNIQUE(textbook_id,sort_order) 防排序冲突；short_title/title NOT NULL；
- mistakes CHECK(stage IN active/mastered)、CHECK(mastered/两错误位 IN 0/1)；
- item_options.option_text / sections.title / learning_events.event_type / app_state.state_value NOT NULL。

提效：
- unit() 选项与 list() 错词改“单查询+内存分组”（消除 N+1）；
- archive/restore 改 ContentValues.update（类型安全，自增字段先读后写）。

验证门禁：新 DDL 用 sqlite3 复跑，断言 UNIQUE/CHECK/级联删除生效后才允许构建。

## 1.32.0 代码质量盘（清理+提效）

清理（死代码归零，grep 门禁：package_imports/importUnit/copyTreeIntoStore/contentReady/unitImported/audioMap/currentMode 全 0）：
- MainActivity：删 getAudioState/importPaper/read(Uri)/PICK_JSON/ZIP 导入/ByteArrayOutputStream；contentReady 调用移除；versionCode 改 getLongVersionCode。
- ContentDb：删 importUnit/validate/log 与 package_imports 表（AppDatabase VERSION 4→5 开发期重建）。
- PrivateFileStore：删 copyTreeIntoStore/copyTree（还原链路已废）。
- Web：删 audioMap/currentMode/contentReady/unitImported；playText 仅用 metaById.audioResource。

提效：
- WrongBookDb.list 的 audio_key 由“每行一次查询”改为 LEFT JOIN content_items 一次取回；
- 其余既有缓存（catalog 5 分钟、keyMap 内存缓存）保留。

纪律：大段删除后必须立即编译+预览冒烟；单行方法删除用行锚，多行方法删除后 grep 调用点确认未误吞（本次 installUnit 曾被误吞，已恢复并加门禁）。

## 1.31.0 全面美化盘

- 设计系统重写：统一色板（navy/blue/orange/green/red 各带浅底）、圆角 16、阴影两级、按压缩放 .96、sheet/dialog 入场动画、theme-color 状态栏；
- 头部：渐变+单元选择器白卡阴影、chevron 靠右、连读/停止 44px；
- 分区标题加蓝色竖条、说明文字改整幅浅蓝条带；
- 错题卡：圆角胶囊标签、左色条（橙=练习/绿=掌握）、按钮 nowrap；
- 底部 tab：active 浅蓝底、图标+文字；设置入列；
- 单元卡：选中渐变数字徽标+绿勾；
- 单元简称统一：4au1 JSON/种子/目录/发布单元 JSON 均 shortTitle=Unit 1，顶部框与列表一致。

## 双站 Release 发布规则（1.15.0 起）

每个对外版本必须：
1. 两站推送代码+apk/ 目录，并打 annotated tag `v<version>` 推送两站；
2. GitHub：POST /repos/…/releases（tag_name）→ 用返回 id 向 uploads.github.com 上传 APK 附件（Content-Type: application/vnd.android.package-archive）；遇 422 already_exists 说明已创建，GET /releases/tags/<tag> 取 id 续传附件；
3. Gitee：POST api/v5/repos/…/releases 必须带 `target_commitish`（master），否则 422；Gitee 无附件 API，release 正文指向仓库 apk/ 与 GitHub 附件；
4. 台账记录两站 release id。

## 1.45.0 底部 tab 高亮纪律盘

- 根因：通用 tab 切换绑定到全部 .navbtn，设置按钮无 data-view，switchView(undefined) 使 `undefined===undefined` 成立→设置 tab 被点亮且不恢复。
- 修复：视图切换监听与 active 切换只作用于 `.navbtn[data-view]`；设置是弹层不是视图，永不参与 tab 高亮；打开/关闭设置不改变当前视图 tab。
- 纪律：弹层触发器不得复用视图 tab 的绑定与高亮逻辑；无 data-view 的导航按钮必须排除在视图切换之外。

## 【历史废弃】语音质量专题盘（Kokoro 换代，v5 语音包）

- 选型结论：Kokoro-82M（Apache-2.0，权重与音色库均商业可用）取代 Piper en_GB-alba 作主音色；固定 bf_emma（英式女声）全 App 统一，不混用引擎/口音（共享方案评审共识：固定单一 voice 利于小学生形成稳定发音体系）。
- 三级架构裁剪：固定试卷内容全部走“预生成+语音包携带”（零延迟/零耗电/完全离线）；端侧实时生成被否（需 APK 内嵌 325MB 模型）；缓存键思想 SHA256(text+voice+version) 与现有按内容 SHA 命名一致。
- 音素演示（W/er/ir/ur/ar/or）：优先 Kokoro [[phoneme]] 探针；探针失败才回退 Piper 音素+例词拼接，并在 manifest voice 字段注明混合来源。
- 生成参数：speed 0.9（学龄清晰度），24kHz 16bit mono WAV。
- 探针结论：Kokoro espeak 前端原生支持 [[phoneme]]（[[w]] 0.77s / [[ə]] 0.53s），音素演示无需回退 Piper，全 App 单一音色 bf_emma 成立。
- v5 语音包：Starter 8.4MB / U1 9.7MB（24kHz WAV 较 22kHz 增大，下载一次后离线；后续可评估 FLAC/m4a 压缩）。
- 许可台账：Kokoro Apache-2.0；Piper 音素部分 CC BY 4.0（保留归属说明 voice_license.txt）。

## 1.49.0 同行化盘

- 警告与动作合并到同一行：状态行=圆点+文本(flex:1)+右侧橙色胶囊按钮(下载语音/更新语音)；进度条改贴行底 3px 细线；独立橙色横幅(packbanner)整体退役，packText/packBanner 引用清零。
- setRow(text,btnLabel) 为状态行唯一写入点；setIdleStatus 按 packInfo 决策：ready隐藏/需更新App纯文本/有更新带按钮/未下载带按钮/下载成功3秒。
- 视觉原则：告知与动作同行时，动作收缩为小胶囊靠右，文本占满剩余宽度，行高不跳。

## 1.48.0 提示去重盘

- 同义信息只允许出现一处：未下载警告归状态行（“语音包未下载，点读前请先下载”），横幅在未下载时隐藏文字、只承载“下载语音”按钮与进度条（packText display:none）；下载中状态行让位给进度横幅；“有更新/需更新App”等状态行不显示的信息仍由横幅承载。
- 纪律：警告文本单一出口；动作区与告知区职责分离。

## 1.47.0 首装语义盘

- “有更新”判定必须要求“曾经下载过”：updateAvailable = installed>0 && latest>installed；首装(installed=0)只允许出现“下载语音”，不允许“更新语音”（用户首装同时看到两条提示属语义错误）。
- 断言两场景：首装→下载语音；installed3/latest4→更新语音。

## 1.45.0/1.46.0 交互净化盘

- 1.45：设置 tab 无 data-view，switchView(undefined) 使 undefined===undefined 成立而错误高亮；绑定与高亮只作用于 .navbtn[data-view]，设置永不点亮、关闭后原 tab 保持。
- 1.46：单元面板选中态仅保留高亮（蓝框+渐变徽标），去掉右侧 ✓（用户：高亮已足够，对号冗余）。纪律：选中态单一表达，不叠加重复指示符。

## 1.44.0 播放聚焦与刘海避让盘

- 头部彻底不显示“正在播放”：播放中状态行隐藏，焦点交给行内高亮（用户定规：行高亮已足够，头部提示是干扰）。
- 播放行“突出而柔和”配方：左 3px 蓝竖条(inset shadow)+90deg 淡蓝渐变底+柔和外发光(0 2px 14px rgba(47,127,224,.14))+英文变蓝+喇叭钮变实心蓝带投影；.item/.choice 加 background/box-shadow .25s 过渡避免生硬；不改尺寸防布局跳动。
- 水滴屏/刘海：Java 设 LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER（API28+）让系统避让挖孔，状态栏染头部同色(#16406F)视觉无缝；CSS 再兜底 .top padding-top:env(safe-area-inset-top)。

## 1.43.0 状态行事件化盘

- 原则：状态不广播，事件才提醒。“已就绪”是常态状态，常驻提醒=噪音；“下载成功”是事件，提醒一次（3 秒窗口后回落）。
- 状态行规则：ready+空闲→整行隐藏；未下载→显示警告（唯一需要用户行动的状态）；下载完成→显示“语音包下载成功，可离线使用”3 秒；播放中→“正在播放…”；结束/停止→回空闲规则。
- 测试注意：let 变量不在 window 上，playwright 断言要走真实定时器路径。

## 1.42.0 性能根因盘（错题列表慢的彻底排查）

根因（与条数无关）：packs.state() 在共享单线程 dbExecutor 上同步做目录网络请求（每镜像 connect 8s+read 15s），错题列表/计数等所有 DB 任务排队等网络——3 条错题也慢。
方案（缓存分层+线程分离+合并+预热，模式源自 TV 版 serveFromCacheOrFetch/warmStaticCache 经验）：
1. 目录缓存三层：内存(5min)→磁盘(SharedPreferences)→网络；fetchCatalog 永不触网；refreshCatalogAsync 在独立 netExecutor 后台 revalidate（stale-while-revalidate）；download 无缓存时才在自己的线程同步拉网。
2. requestPackState 先本地秒回，再异步补新。
3. 错题列表+计数合并为单桥 requestWrongAll(stage,filter,offset)，payload 带 stage/filter 以区分预热响应；删除 requestWrongList/requestWrongCounts/requestCounts 全链路。
4. 预热：receiveUnit 后后台拉 active|all 与 mastered|all 两缓存；进入错题本即缓存渲染。
量化：模拟 400ms 桥延迟下首进渲染 98ms（改前 482ms），再进 103ms。
纪律：任何网络 IO 禁止进入 DB/UI 数据线程；列表类数据必须缓存优先+合并桥+预热；改完用 playwright 量化首渲染时延。

## 1.41.0 拼写文案与列表缓存盘

- 用户可见文案“书写”全部改“拼写”（筛选、标签、记错弹窗、帮助页、Excel 导出类型）；DB 列名 writing_error 保持内部不变；
- 掌握规则文案明确：只有拼写错误拼写通过才自动掌握，发音错误必须手动（规则自 1.39 已在 spellResult：we==1&&pe==0 才自动）；
- 错题列表加载优化：Web 端按 stage|filter 缓存首页结果，进入即渲染缓存再异步刷新（pendingOffset 区分首页/翻页）；变更类回调（wrongBookChanged/mistakeSaved/wrongDeleted/archiveFinished/spellSaved）先清缓存再刷新；
- 自检：二次进入 50ms 内卡片可见。

## 1.40.0 设置卡片化盘

- 单元选择列表去掉“· N项”计数，只留副标题；
- 设置面板重构为卡片分组：setcard(浅灰底+settitle 粗体卡标题) 三组——播放语速(白底分段控件)、本单元(删除语音红色行)、帮助(使用说明行)；版本脚注+完成主按钮；
- 教训：悬浮小灰字标签(settingtitle)观感差，标题必须进入卡片成为层级一部分。

## 1.39.0 掌握规则与统计文案盘

- 掌握自动标记规则收紧：拼写通过仅当“纯书写错误（writing=1 且 pronunciation=0）”才自动 mastered；含发音错误的错题即使拼写正确也只记录成绩，掌握必须手动点（用户定规）；
- 拼写成功提示按类型区分：含发音错误→“拼写正确！发音错误仍需手动点‘掌握’。”；
- 统计胶囊文案改“X项待练习/X项已掌握”，胶囊整体 13px 与“导出错题”字号一致（playwright 断言三处 fontSize 相等）。

## 1.38.0 单元面板全尺寸下拉盘

- 单元选择面板在手机上也锚定 unitBar 正下方（下拉列表形态），不再用底部抽屉；遮罩调淡、去 handle；
- 去掉“完成”按钮：点选单元即 selectUnit+closeUnits 直接切换；mask 点击关闭保留；
- 自检断言：手机视口锚定误差<2px、#closeUnits 计数 0、点选后面板关闭且标题切换。

## 1.37.0 加载占位与平板锚定盘

- 错题本“正在读取错题…”占位删除：本地 SQLite 毫秒级返回，占位只会造成闪烁/卡留；requestWrong 重置只清数据不清 DOM，receiveWrongList 直达终态；receive 加 try/catch 防卡留。
- 平板（≥720px）单元选择面板改“下拉锚定”：openUnits 用 unitBar.getBoundingClientRect() 设 sheet top/left/width，正好位于选择框正下方；media 里 .sheet 的右下卡片样式只作用于 #settings；#units 遮罩调淡、隐藏 handle。
- 自检：playwright 双视口（390 手机空态即时、1024 平板锚定误差<2px）。

## 1.36.0 错题本头部盘

- 头部右侧改“统计胶囊+导出最右”：statpill（数字 16px tabular-nums+min-width，三位数不挤）、导出错题橙色胶囊置行最右；wronghead 允许 wrap 防窄屏溢出；
- 数字随当前 stage 切换（待练习/已掌握）。

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
- 【历史废弃】Piper/Kokoro/Alba/Cori 已从现行工具链删除；当前正式单元语音仅 Microsoft Sonia，生成别名含 `Ms→Miz`。
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

## 2.4.5 真根因：zip 顶层目录剥离启发式误剥 lib/（2.4.4 仍复发的原因）

- 2.4.4 只修了“校验认不认识文件”，但下载解压阶段还有第二个 bug：单文件分支用“首条目首段作为 wrapper 目录剥离”的启发式。sherpa 运行库 zip 根就是 `lib/…`+`manifest.json`（无 wrapper），首条目 `lib/arm64-v8a/…` 的首段 `lib/` 被当 wrapper 剥掉 → 解出 `stage/arm64-v8a/libsherpa-onnx-jni.so`（丢 lib/ 前缀）→ 校验仍找不到 `lib/arm64-v8a/…` → 删 → 换镜像 → 报失败。所以 2.4.4 仍复发。
- 修复：`isRuntime` 时**不剥离**（verbatim 解压，rel=nm）；仅 model（上游带 wrapper 的 zip）才剥离。另加 unwrap 兜底：runtime 解完若根无 manifest 且仅一个子目录，则把子目录内容上移（兼容带 wrapper 的 runtime zip）。
- 验证：用真实 sherpa runtime zip 模拟 verbatim 解压，确认 `lib/arm64-v8a/libsherpa-onnx-jni.so`+`manifest.json` 落在 stage 根，校验通过。
- 教训：zip 安装类 bug 要同时检查“解压布局”和“安装校验”两处；启发式剥 wrapper 只对“单一顶层 wrapper 目录”的 zip 安全，对根即多条目（含裸文件）的 zip 会误剥。

## 2.4.4 修复“下到100%后报下载失败”（安装校验按引擎区分）

- 症状：超高精度运行库进度到100%后弹“下载失败，已尝试全部镜像”。
- 根因：`downloadItem` 单文件分支安装前校验用 `SpeechEngine.abiLibDir(stage)`，它只认 **Vosk** 的 `libvosk.so/libjnidispatch.so`；sherpa 运行库 zip 里是 `libsherpa-onnx-jni.so` → 校验判 null → 删除已下载文件 → 落到下一个（Gitee 404）镜像 → 报“已尝试全部镜像”。下载与 SHA 其实都成功，是安装校验误杀。
- 修复：校验改为按引擎区分——model 看 `model/am/final.mdl|am/final.mdl|small.en-*.onnx`；`sherpa-runtime` 看 `lib/{arm64-v8a,armeabi-v7a}/libsherpa-onnx-jni.so`；vosk runtime 才用 `abiLibDir`。
- 提速/容灾：sherpa 运行库 zip（8.7MB）提交进 repo main，注册表首选 **jsdelivr**（`cdn.jsdelivr.net/gh/rerbin/...@main/speech-runtime-sherpa-v1.zip`，国内快），其后 Gitee、GitHub release。实测 jsdelivr 206 可用、GitHub CDN 慢易超时。
- 教训：任何“下载成功但安装失败”先查安装前校验是否认得该包的文件布局；多镜像时一个镜像校验误杀会伪装成“全部镜像失败”。

## 2.4.3 运行库随模型自动管理（用户三条需求）

- 需求：①超高精度版下载后仍显示未下载；②运行库下载合并进模型下载；③Vosk 运行库两精度共用，不独立下载，下载任意 vosk 模型同步下载、删除最后一个 vosk 模型同步删除。
- 设计：运行库不再是用户可点的独立下载项。`downloadEngineModel` 对任意引擎先检查 `runtimeReadyFor(engine)`，缺则链式“运行库→模型”一次完成（标签分别标注）；`deleteEngineModel` 删除模型后若无其它同引擎已装模型则 `deleteTree(runtimeRootFor(engine))`。
- UI：模型页顶部改为只读 `.runtimestatus` 状态条（Vosk 运行库/超高精度运行库 已安装/未安装+自动下载说明），删除两个运行库下载按钮；模型下载按钮空闲标签用 `data-idle` 存“下载（含运行库）”，`applyEngineDlUi` 空闲分支从 `data-idle` 恢复（**教训：applyEngineDlUi 空闲分支曾把按钮文本重置为“下载”，覆盖 renderModelPage 的后缀**）。
- modelReady 放宽：不再要求 manifest.json；sherpa 兼容文件在根目录或 `model/` 子目录；SherpaEngine.recognize 同样兼容两种布局。
- 编译/发布：2.4.3/code93；dex 校验用行为字符串（runtimeReadyFor/otherUsesRuntime 为局部名不入 dex，勿用作断言）。

## 2.4.2 超高精度版下载三连问题修复

- 根因1「下载三次」：多文件 source 逐文件各报 0→100%，且进度无说明。修复：`downloadItem` 多文件分支按 size 加权合并为一次总进度，`downloadLabel` 标注当前文件友好名（encoder=识别编码器/decoder=解码器/tokens=词表）＋“第 X/N 部分”；单文件分支 label=运行库名；`engineDownloadLabel()` getter；MainActivity engineListener 的 onProgress 携带 label；Web 进度行显示“label：x%”，按钮下载态加 `.voiceaction.downloading`（琥珀色＋dlpulse 脉冲）。
- 根因2「下载完仍未下载/不可选」：`modelReady(id)` 硬编码 Vosk 布局 `model/am/final.mdl`，sherpa 模型文件在根目录（3 个 onnx/txt）→ 永远 false。修复：按 `engineForModel(id)` 区分，sherpa 校验 `small.en-encoder.int8.onnx/decoder/tokens`。
- 根因3「按钮无状态区分」：加 downloading 类 + CSS 动画；未下载=下载按钮、下载中=琥珀色脉冲+百分比、已下载=无下载按钮（只剩选择/删除）。
- 顺带：`downloadEngineModel` 对 sherpa 模型先链式下载 sherpaRuntime 再下模型；`downloadSelectedEngine` 链式时模型已装则跳过。
- 编译坑：匿名类引用非 final 局部变量（`total`）→ 改 final `totalBytes`；`model.getString` 的 JSONException → 匿名类外用 final 变量预取。

## 2.4.1 sherpa 运行库改按需下载（APK 瘦身）

- 用户质疑 APK +23.6MB：确认为内嵌 `libsherpa-onnx-jni.so`（23,650,584B）。2.4.1 将其移出 APK，改为独立运行库包 `speech-runtime-sherpa-v1.zip`（压缩后 8,729,659B，sha 21b62b13f6f6d6bf729266afcbdef1ea47c7bfdc173fb9e2e4acae40130de5a3），APK 回到 923,347B。
- 注册表新增顶层 `sherpaRuntime`；源=Gitee raw（国内）+GitHub release v2.4.1；管理页新增“超高精度运行库”卡（约 9MB），选择 Whisper 模型时 `downloadSelectedEngine` 自动链式：运行库→（模型未装才下模型）。
- SherpaEngine.load(File libDir) 改为 System.load(私有目录绝对路径)，与 Vosk 同机制（真机已验证可行）；build.sh 移除 lib 追加；libs/jni 删除。
- engineReady 按所选引擎分别要求对应运行库；downloadEnginePart 支持 kind=runtime|sherpa-runtime|model。
- Web 修复：engineDownloadProgress 残留的“全量 forEach 改所有 dl 按钮文案”与 applyEngineDlUi 精确匹配冲突，删除全量分支；非匹配按钮下载期间保持原文案仅禁用。
- 迁移：2.4.0 已装用户升级后 sherpa 档需重新下载运行库（提示自动出现）；Vosk 档不受影响。
- 教训：release asset 上传后立即下载 URL 可能短暂 404（CDN 传播），用 assets API 状态+range GET 复核；GitHub release APK asset 本会话两次首传丢失，需补传并复核。

## 2.4.0 sherpa-onnx Whisper 超高精度版接入

- 新模型 `sherpa-whisper-small-en`（engine=sherpa）：whisper small.en int8（encoder 112,442,483B sha 8bdac288…e462f；decoder 262,223,042B sha 710ccf89…877fb；tokens 835,554B sha 306cd27f…d930），注册表 sizeLabel 约 375MB。
- 源：hf-mirror.com（国内）+ huggingface.co 的 csukuangfj/sherpa-onnx-whisper-small.en；**多文件 source**（base+files[]，逐文件断点续传+SHA，跨源失败重起），AudioPackManager.downloadItem 支持 files 模式；不重打包。
- 运行库：sherpa-onnx v1.13.7 `sherpa-onnx-static-link-onnxruntime` AAR（classes.jar 238KB 入 dex；arm64 libsherpa-onnx-jni.so 23,650,584B 入 APK lib/，ZIP_STORED+zipalign -p 对齐）；APK 增至约 24.6MB；32 位设备不可用该档（Vosk 档不受影响）。
- SherpaEngine：System.loadLibrary("sherpa-onnx-jni")；OfflineRecognizer(null, config) 走文件路径；FeatureConfig(16000,80)+OfflineWhisperModelConfig(encoder/decoder/language=en/task=transcribe)+tokens+numThreads=2+greedy_search；recognize 返回文本，失败 null+loadError；全 Throwable 捕获。
- 分发：MainActivity 按 packs.engineForModel(selected) 分流 sherpa/vosk；sherpa 无 grammar（自由识别），评分规则与 Vosk 相同（完全匹配/句≥0.8/空=unclear）。
- 坑复盘：AAR 的 android.tar.bz2 只含 jniLibs 无 classes；kotlin-api 目录不存在（Java API 在 AAR classes.jar）；配置类均有无参构造+setter；cp 相对层数算错会把文件落到 tools/ 下。
- ModelScope 仍无 sherpa/vosk 托管；hf-mirror 为唯一实测可用国内镜像。

## 2.3.0 发音检查引擎命名＋半对掌握态＋下载即时态＋弹窗右上关闭

- 全 App 名称统一：跟读模型/跟读引擎/跟读练习 → **发音检查引擎/发音检查**（设置组、管理页、弹窗标题、按钮、帮助、原生提示、注册表 runtime 名）。卡片按钮仍为“读”，aria-label=发音检查；开始按钮文案“开始跟读”→“发音检查”。
- 双错误（发音+拼写）错题的掌握按钮半对状态：未过=半圆+掌握；仅发音过=半圆+发音已过；仅拼写过=半圆+拼写已过；双过或 mastered=对号+已掌握。半圆图标=圆+半填充 path。
- 分项过关用 learning_events 事件 `pron_passed`/`write_passed` 记录（**不改表结构，避免 onUpgrade drop 清掉用户数据**）；readPass(id) 标记发音过关并按需自动 mastered；spellResultJson 返回 {result,pronPassed,writePassed,mastered,...}；list() 增加 pronPassed/writePassed。
- 下载按钮即时态：点击立即 disabled+“准备下载…”，进度变“下载中 x%”；engineDl 状态在 renderModelPage 重渲染后保持；读弹窗与管理页同步。
- 发音检查弹窗：关闭从底部移到右上角 iconclose（40px），并支持点遮罩关闭（closeRead 会取消录音）；底部只留“再试一次”。
- 引擎调研结论（2026-09）：ModelScope 无 vosk/sherpa 托管；hf-mirror 为可用国内镜像。更优端侧路线=sherpa-onnx（GitHub release AAR，构建期入包）+ whisper small.en/zipformer en（HF k2-fsa，hf-mirror 可下）；真·音素打分端侧无生产级开源，云端 Azure Pronunciation Assessment（21Vianet 中国区）/讯飞/驰声为商业选项，留作可选在线模式。

## 2.2.0 跟读模型可管理＋可选＋国内镜像

- 设置新增“跟读模型”入口与独立管理页：运行库卡（约6MB，共用一次）＋模型卡（轻量版约40MB／高精度版约125MB），每卡含名称、简介、体积说明、安装状态；按钮：下载／选择此模型／删除（删除仅限非当前模型，confirm 明示可重下）。
- 注册表 `res/raw/readaloud_models.json`：runtime+models，每 source 含 url/sha256/size；模型源 **hf-mirror.com 国内镜像优先**，HuggingFace 与 GitHub release 作回退；ModelScope 经核验无 vosk 托管（API/页面 404），不作为源。
- 下载优化：`orderedSources` 并行 HEAD 探测（2.5s 超时）按 RTT 排序；同源断点续传；跨源失败删除 part 重新起（不同 zip 字节不可跨源续传）；进度同时更新读弹窗与管理页按钮（百分比）。
- 存储分层：`files/speech-runtime`（lib）＋ `files/speech-models/<id>`（model）；旧 v2 合并包 `files/speech-engine` 启动时自动迁移拆分后删除。
- SpeechEngine.load(runtimeRoot, modelRoot)；识别用所选模型；grammar 仍走构造器。
- JS 陷阱复盘：`engineDownloadProgress/Finished` 曾重复定义，函数声明提升导致后定义覆盖前定义，管理页进度不刷新；**同名函数只能有一处定义**，合并而非新增。
- 发布：release v2.2.0 三 asset（runtime 5,860,944 / small 41,183,169 / APK）；runtime 另推 Gitee raw 作国内源。APK asset 首次上传丢失需补传并 HEAD 复核 content-length。

## 2.1.2 Bridge 同名自委托递归闪退

- 真机栈：`StackOverflowError`，帧为 `Bridge.startReadAloud → lambda → runOnUiThread → Bridge.startReadAloud` 无限循环。根因：内部类 `Bridge` 的 `@JavascriptInterface` 方法体内 `runOnUiThread(() -> startReadAloud(id,text))` 中无限定调用解析为 **Bridge 自身同名方法**，而非外层 Activity 方法；`stopReadAloud` 同病。
- 修复：一律 `MainActivity.this.startReadAloud(...)` / `MainActivity.this.stopReadAloud()` 限定。
- 发布门禁（必跑）：正则扫描 Bridge 类体内 `runOnUiThread(() -> (startReadAloud|stopReadAloud|cancelReadAloud)(` 必须为零；浏览器 mock 桥接测不到 Java 委托路径，**不能替代该门禁**。
- 教训：任何内部类桥接方法委托外层同名方法必须显式 `Outer.this.`；新增桥接方法时先检查外层是否存在同名私有/公有方法。

## 2.1.1 跟读闪退修复（原生加载加固）

- 真机闪退根因族：native/JNA 抛 `Error`（UnsatisfiedLinkError 等）而代码只 `catch (Exception)`，线程未捕获即杀进程；以及单 ABI 包在 32 位设备必然加载失败。
- 纪律：所有 native 加载/识别/录音路径一律 `catch (Throwable)`；失败只走 js 状态（engineLoadResult/readAloudState=error）弹窗提示，**永不闪退**。
- 引擎包 v2：双 ABI（arm64-v8a+armeabi-v7a，47,044,076B，sha 8b69912fd6e1ecf08ab9d4aa9fb28f7cd01924e86783f6d147aa418566c98676）；运行时 `SpeechEngine.abiLibDir` 按 `Build.SUPPORTED_ABIS` 选目录；manifest version=2，旧 v1 安装自动判未就绪引导重下。
- 预加载时机：engineState 报 ready 时、引擎下载完成时后台 preload；加载失败立即 engineLoadResult(ok=false,message) → 弹窗提示+禁用开始按钮。
- engineState ready 判定改为 ABI 感知（任一受支持 ABI 的 libvosk.so 存在）。
- 浏览器回归：加载失败=提示+禁用（无崩溃路径）；加载成功=全流程 pass 仍通过。

## 2.1.0 跟读练习（读按钮 + 离线 Vosk）

- 错题卡“听”永久改为“读”（mic 图标，data-act=read）；点开“跟读练习”弹窗：听示范（readModel 焦点 ID 承担播放态）→ 开始/结束跟读 → 对比结果。拼写弹窗删除听发音，仅纯拼写检查（提示语“看中文，写出完整的英文。”）。
- 引擎：vosk-android 0.3.45 AAR（classes.jar 16KB 进 dex；libvosk.so/libjnidispatch.so arm64 进按需引擎包，APK 不含 .so）+ JNA 5.13.0。加载顺序：`jna.boot.library.path` → System.load(libjnidispatch) → System.load(libvosk) → new Model(modelDir)。grammar 走构造器 `Recognizer(Model,float,String)`（0.3.45 无 setGrammar），并 setWords(true)。
- 引擎包 `speech-engine-vosk-v1.zip`（44,099,422B，sha 290cc1f3e69822f144009aa3b69d4a24d9991735b3e26a3a8245d0ca007fec2f）= lib/arm64-v8a + model(vosk-model-small-en-us-0.15, sha 30f26242…498) + manifest；仅 GitHub release asset（v2.1.0），AudioPackManager.downloadEngine 断点续传+SHA+结构校验后装到 files/speech-engine。
- 录音：AudioRecord 16k mono 16bit → cache PCM → WAV 头 → recognize；RECORD_AUDIO 运行时权限，拒绝给明确文案；音频对比后立即删除。
- 评分（ReadAloudScorer，纯 Java 可 JVM 单测）：normalize；单词=完全匹配；句子=词级匹配率≥0.8；空=unclear（不算失败）；fail 给 phonemeHint（CMUdict 子集 phoneme_dict.json，94→130KB：教材词+教学易混词，IPA+音素近邻≤3 用于约束语法与纠音；近邻优先教材词）。
- pass → setMastered(id,true) + 全局提示“读得很准，已标记为“已掌握”。”+ 刷新；可撤销（再点已掌握）。移出仍手动。
- 弹窗复用拼写弹窗稳定锚点（top16px、只改高度不改位置）；引擎未下载时弹窗内显示下载面板与进度。
- 构建：build.sh classpath/d8 增加 libs/vosk-classes.jar、libs/jna-classes.jar；APK 约 825KB。
- 门禁：浏览器 mock 桥接全流程（engine 未就绪禁用→下载→recording/scoring→pass/fail/unclear→retry→close cancel）；JVM 单测 ReadAloudScorer；APK 解包核对 dex 含 org/vosk 与 com/sun/jna、manifest UTF-16 RECORD_AUDIO、phoneme_dict 在 raw。

## 2.0.6 拼写弹窗稳定锚点

- 根因：通用 dialog 先垂直居中；输入框延迟聚焦后键盘触发 visualViewport 缩小，再切 keyboard-open 顶部定位；点听音失焦收键盘后又退出顶部定位，造成中间→顶部→中间往返。
- 拼写弹窗必须从打开第一帧起固定顶部安全区16px；键盘弹出/收起只能更新 `--spell-height`，禁止切换定位 class、top 或 transform。手机固定 left/right16；平板固定水平居中。
- 拼写弹窗禁止复用通用 pop 位移/缩放动画，只允许 `spellFade` 120ms 透明度淡入；首帧及0/16/50/120/250ms top必须完全一致。
- 听音按钮 pointerdown 必须 preventDefault 保留输入框焦点，尽量不收输入法；即使特定输入法仍收起，弹窗也保持top16。
- visualViewport resize/scroll 只计算可用高度和必要的内部 scrollIntoView；底部操作行 sticky。实测844→430→844→430全序列top=16，输入框与操作按钮均在可视区域内。

## 2.0.5 错题本单元筛选纯文字卡

- 错题本单元筛选中的“全/S/1”等徽标与“全部/Starter/Unit 1”重复，永久移除；禁止用首字母、序号或新图标补位。
- 错题筛选项使用 `.unitcard.textonly` 纯文字双行卡：第一行简称，第二行范围说明或 `unitTopic(u)` 完整英文主题；最小高度72px、左右内边距18px，长主题自然换行。
- 当前筛选项只用浅蓝色面和蓝色边框表达，不加对号；同时设置 `aria-pressed=true/false`，状态不只靠颜色。
- “全部”和各具体单元必须使用同一布局，文字左边缘完全对齐。释放出的宽度用于完整主题。
- 该变更仅用于错题本上下文；学习页单元列表仍可保留顺序徽标和原切换逻辑。筛选不得修改学习页 `currentUnitIndex` 或调用 `requestUnit`。

## 2.0.4 拼写弹窗、键盘与完整单元名称

- 拼写弹窗的听音按钮自身就是播放焦点：原生播放 ID 固定为 `spellAudio`；播放开始后主蓝 `#315DA8` + 白色、文案“正在播放”、`aria-pressed=true`，完成/停止/失败/关闭/答对自动关闭时恢复“听发音”。禁止把焦点落到弹窗背后的错题卡。
- 键盘适配双层实现：Activity 的 `windowSoftInputMode=adjustResize` 与运行时 `SOFT_INPUT_ADJUST_RESIZE`；Web 层监听 `visualViewport.resize/scroll`。可视高度比弹窗打开基线小 120px 以上时进入 keyboard-open，顶部 8px、最大高度=可视高度-16px，输入框自动滚入中部，底部操作行 sticky。
- 禁止按固定键盘高度或机型写死偏移；必须使用 visualViewport 的 `height/offsetTop`。恢复可视高度后弹窗回到正常居中；平板 keyboard-open 需覆盖 desktop transform，只保留水平居中。
- 顶部单元选择框采用两行 `unitcopy`：第一行 shortTitle，第二行完整英文主题；主题优先从 title 中点号后的文本提取，回退 subtitle/title。允许自然换行、容器自增高，禁止 ellipsis。Starter=`Our families and our friends`，Unit 1=`My school`。

## 2.0.3 跨页面播放反馈一致性

- 所有播放入口必须共用 `markPlaying()/clearPlaying()` 的视觉与语义状态，不得只调用原生播放而无反馈。
- 错题卡 article 的 id 必须与传给原生播放器的焦点 ID 一致：`w+错题ID`；否则原生播放开始时无法找到对应卡片。
- 错题“听”按钮与学习播放按钮共用播放态：最终计算色为主蓝 `#315DA8` 加白色；卡片使用浅蓝高亮和左侧蓝条。颜色验收应等待 160ms 过渡结束。
- 两类播放按钮初始 `aria-pressed=false`；播放当前项时设为 true，并先清除上一项；完成、停止、失败和切换页面时恢复 class 与 aria。
- 错题单条播放不触发自动滚动；焦点跟随仍只允许在连续朗读时执行。
- `clearPlaying` 必须直接遍历播放节点并删除 playing class，禁止递归调用自己；该错误曾在实现期被浏览器测试拦截。

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
- 2026-09-05：APK 1.31.0（code35）。全页面视觉重写+playwright 四页截图自检流程入 skill v1.10.0；单元简称统一。
- 2026-09-05：APK 1.32.0（code36）。代码质量盘：死代码清理、LEFT JOIN 提效、DB v5；skill v1.11.0。
- 2026-09-05：APK 1.33.0（code37）。数据结构审查：删 audio_assets/normalized_word/四冗余索引；UNIQUE+CHECK 补强；N+1 清零；DB v6；sqlite3 约束门禁入 skill v1.12.0。
- 2026-09-05：APK 1.34.0（code38）。“停止连读”文案；设置“使用说明”独立页（六分区卡片）；skill v1.13.0。
- 2026-09-05：APK 1.35.0（code39）。状态行状态机（空闲/播放/下载成功语义）+下载进度条；playwright 状态断言入 skill v1.14.0。
- 2026-09-05：APK 1.36.0（code40）。错题本头部统计胶囊+导出最右；双站 Release 机制（GitHub 附件/Gitee target_commitish）入 skill v1.15.0；release：GitHub 383109124、Gitee 1124398。
- 2026-09-05：APK 1.37.0（code41）。去加载占位+平板单元面板锚定；release：GitHub 383111980、Gitee 1124473；skill v1.16.0。
- 2026-09-05：APK 1.38.0（code42）。单元面板全尺寸下拉锚定、点选即切换、去完成按钮；release：GitHub 383114726、Gitee 1124505；skill v1.17.0。
- 2026-09-05：APK 1.39.0（code43）。掌握自动标记仅限纯书写错误；统计“X项待练习”+13px 字号一致；release：GitHub 383116543、Gitee 1124570；skill v1.18.0。
- 2026-09-05：APK 1.40.0（code44）。设置卡片化三组；单元列表去项数；release：GitHub 383119081、Gitee 1124590；skill v1.19.0。
- 2026-09-05：APK 1.41.0（code45）。书写→拼写全文案；错题列表缓存即时渲染；release：GitHub 383121775、Gitee 1124648；skill v1.20.0。
- 2026-09-05：APK 1.42.0（code46）。性能根因修复：缓存三层+线程分离+合并桥+预热；首进 98ms；release：GitHub 383127641、Gitee 1124684；skill v1.21.0。
- 2026-09-05：APK 1.43.0（code47）。状态行事件化：就绪静默隐藏、下载成功仅 3 秒、未下载才警告；release：GitHub 383131671、Gitee 1124703；skill v1.22.0。
- 2026-09-05：APK 1.44.0（code48）。播放行高亮配方+头部不显正在播放+刘海 NEVER 避让+状态栏同色；release：GitHub 383134217、Gitee 1124719；skill v1.23.0。
- 2026-09-05：APK 1.45.0（code49）设置 tab 高亮修复（GitHub 383136982/Gitee 1124758）；APK 1.46.0（code50）单元面板去对号（GitHub 383138803/Gitee 1124765）；skill v1.25.0。
- 2026-09-05：APK 1.47.0（code51）。首装不提示更新（installed>0 才算更新）；release：GitHub 383142740、Gitee 1124790；skill v1.26.0。
- 2026-09-05：APK 1.48.0（code52）。未下载警告单出口（状态行），横幅只留按钮/进度；release：GitHub 383145971、Gitee 1124800；skill v1.27.0。
- 2026-09-05：APK 1.49.0（code53）。警告+动作同行（内嵌胶囊按钮、行底进度细线），横幅退役；release：GitHub 383149537、Gitee 1124924；skill v1.28.0。
- 2026-09-05：APK 1.50.0（code55）。语音换代 Kokoro-82M bf_emma（Apache-2.0），语音包 v5，172 条重生成，单一音色；skill v1.29.0。
- 2026-09-05：APK 1.54.0（code58）。独立 phonics-base-v1（44 音素）已生成，App 首次下载入口已接入；GitHub Release 383263094 含 APK+基础包；Gitee 因连接超时未确认同步。skill v1.30.0。
- 2026-09-06：APK 1.55.0（code59）。基础包 catalog 版本检查：installed=0 仅下载、installed<latest 更新、最新隐藏；skill v1.31.0。
- 2026-09-06：APK 1.56.0（code60）。GitHub Raw+jsDelivr 双入口、Range断点续传、运行时去Gitee；skill v1.32.0。
- 2026-09-06：APK 1.57.0（code61）。phonics-base-v2 真实英国隔离音素 44/44；修 parser/or映射/SHA；skill v1.33.0。
- 2026-09-06：APK 1.58.0（code62，DB v7）。强制重建 phoneme: 键+seed 自检，确保真正调用 v2；skill v1.34.0。
- 2026-09-06：APK 1.59.0（code63）。设置改音色选择、Microsoft默认、可选音色按需；默认单元包v7无音素；skill v1.35.0。
- 2026-09-06：APK 1.60.0（code64）。修复 Microsoft default 点击误走下载；skill v1.36.0。
- 2026-09-06：APK 1.61.0（code65）。永久删除 Kokoro Emma，旧选中/目录/part 自动清理；GitHub release 383471364；skill v1.37.0。
- 2026-09-06：APK 1.62.0（code66）。保留音色选择架构，当前只上线 Microsoft Sonia；彻底删除 Alba/Cori 及模型、生成脚本、试听产物；GitHub release 383501321；skill v1.38.0。
- 2026-09-06：APK 1.63.0（code67）。phonics-base-v2（592,210字节/44音标）内置，首次启动解压，基础音标无需网络；GitHub release 383516176；skill v1.39.0。
- 2026-09-06：APK 1.64.0（code68）。设置页重构为状态摘要与功能分组；使用说明同步音标内置、Microsoft-only、离线缓存及数据规则；GitHub release 383522996；skill v1.40.0。
- 2026-09-06：APK 1.65.0（code69）。Starter Ms 用 Miz 合成覆盖，语音包 v8；设置播放语速标题去重；GitHub release 383529374；Gitee HTTP 429 待补；skill v1.41.0。
- 2026-09-06：APK 1.66.0（code70）。连读/停止按钮按独立连读状态切换主次并补齐全退出路径；GitHub release 383546361；skill v1.42.0。
- 2026-09-06：APK 1.67.0（code71）。连读新增安全区触发的平滑焦点跟随，单条点读不滚动；GitHub release 383561854；skill v1.43.0。
- 2026-09-06：APK 1.68.0（code72）。错题导出默认文件名增加本机时间戳到分钟；GitHub release 383565544；skill v1.44.0。
- 2026-09-06：APK 1.69.0（code73）。错题本顶部单元框支持全部/具体单元筛选，与学习单元状态隔离；GitHub release 383571693；skill v1.45.0。
- 2026-09-06：APK 1.70.0（code74）。错题本删除重复页标题和统计胶囊，重排状态/类型+导出/列表层级；GitHub release 383581196；skill v1.46.0。
- 2026-09-06：APK 1.71.0（code75）。已掌握卡片删除“已收好”，动作统一为听/拼/重练/删除图标短文案；GitHub release 383586658；skill v1.47.0。
- 2026-09-06：APK 1.72.0（code76）。正在练习掌握/移出补图标；底部三 Tab 改统一 22x22 线性 SVG 并消除 badge 基线偏差；GitHub release 383593446；skill v1.48.0。
- 2026-09-06：APK 1.73.0（code77）。每单元会话内记忆阅读位置并瞬间恢复；导航/筛选无平滑动画，仅连读焦点保留；GitHub release 383597990；skill v1.49.0。
- 2026-09-06：APK 2.0.0（code78）。M3 Expressive-inspired全视觉系统重构，语义色/统一SVG/形状层级/克制动效/可访问性；保留全部1.73业务与数据；GitHub release 383618660，最终 commit 7066257；APK SHA-256 581f25b7d17b8d43a69145175f22cb4198be84f73960ab6900a6ad8760e0ba96；skill v2.0.0。
- 2026-09-07：APK 2.0.1（code79）。未掌握空心圆/掌握，已掌握对号/已掌握；物理删除改专用永久删除危险弹窗；GitHub release 383737615，commit 4d79751；skill v2.0.1。
- 2026-09-07：APK 2.0.2（code80）。全量文案审计与统一术语，新增跨页面提示、删除分级、可恢复错误提示、导出表格可读单元；GitHub release 383749804，commit e78f149；skill v2.0.2。
- 2026-09-07：APK 2.0.3（code81）。错题本听音与学习页统一卡片、按钮播放态及 aria，完成、停止、失败时清理；GitHub release 383763090，commit f39eb46；skill v2.0.3。
- 2026-09-07：APK 2.0.4（code82）。拼写听音增加正在播放态；键盘按 visualViewport 自动上移限高；顶部两行完整显示英文单元主题；GitHub release 383768702，commit 4689315；skill v2.0.4。
- 2026-09-07：APK 2.0.5（code83）。错题本单元筛选移除全/S/1徽标，改纯文字双行卡并保留选中语义；GitHub release 383773859，commit e0b63f1；skill v2.0.5。
- 2026-09-07：APK 2.0.6（code84）。拼写弹窗首帧固定顶部16px，键盘/听音只改高度不改位置，消除往返跳动；GitHub release 383779427，commit 69844ef；skill v2.0.6。
- 2026-09-07：APK 2.1.0（code85）。错题“听”改“读”：跟读练习+离线Vosk引擎包按需下载+音素纠音；拼写弹窗纯化；GitHub release 384105171；skill v2.1.0。
- 2026-09-07：APK 2.1.1（code86）。跟读原生加载加固：Throwable 捕获+双 ABI 引擎包 v2+预加载失败提示；GitHub release v2.1.1；skill v2.1.1。
- 2026-09-07：APK 2.1.2（code87）。修复 Bridge 同名自委托无限递归闪退（MainActivity.this 限定+门禁）；GitHub release 384125273；skill v2.1.2。
- 2026-09-08：APK 2.2.0（code88）。跟读模型设置可管理/可选/国内镜像（hf-mirror 优先+探测+续传+故障切换）；旧包自动迁移；GitHub release 384347373；skill v2.2.0。
- 2026-09-08：APK 2.3.0（code89）。统一发音检查引擎命名；双错误半对掌握态+分项过关事件；下载按钮即时态；弹窗右上关闭；GitHub release 384373046；skill v2.3.0。
- 2026-09-08：APK 2.4.0（code90）。接入 sherpa-onnx Whisper small.en 超高精度版（hf-mirror 多文件断点下载；运行库入 APK）；GitHub release 384382395；skill v2.4.0。
- 2026-09-08：APK 2.4.1（code91）。sherpa 运行库移出 APK 改按需包（8.7MB，Gitee+GitHub）；APK 回 0.9MB；GitHub release 384388257；skill v2.4.1。
- 2026-09-08：APK 2.4.2（code92）。修复超高精度版下载三连、模型就绪判定、下载按钮动效；GitHub release（后台确认）；skill v2.4.2。
- 2026-09-08：APK 2.4.3（code93）。运行库随模型自动下载/删除；运行库改只读状态条；修复下载后仍显示未下载与空闲标签覆盖；GitHub release（后台确认）；skill v2.4.3。
- 2026-09-08：APK 2.4.4（code94）。修复运行库下到100%后安装校验误杀（按引擎区分校验）；jsdelivr 首选镜像；GitHub release（后台确认）；skill v2.4.4。
- 2026-09-08：APK 2.4.5（code95）。修复 zip 顶层剥离误剥 lib/（runtime 改 verbatim 解压+unwrap 兜底），彻底解决 100% 后失败；GitHub release（后台确认）；skill v2.4.5。
- 2026-09-05：APK 1.45.0（code49）。设置 tab 高亮修复（navbtn[data-view] 限定）；release：GitHub 383136982、Gitee 1124758；skill v1.24.0。