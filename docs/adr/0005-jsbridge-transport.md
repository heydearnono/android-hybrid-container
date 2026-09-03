# ADR-0005：JSBridge 走 `addWebMessageListener`

| | |
|---|---|
| 编号 | ADR-0005 |
| 日期 | 2026-09-03 |
| 状态 | 已采纳 |
| 影响范围 | 架构分层 / 依赖 |

## 决策

JS ↔ Native 的通道用 `androidx.webkit` 的 `WebViewCompat.addWebMessageListener`，按 origin 授权。不用 `addJavascriptInterface`，也不用 URL / prompt 拦截。

配套两条硬规则：

1. **双闸门。** 同一份 `BridgeSecurityConfig` 同时喂给 `addWebMessageListener` 的 `allowedOriginRules`（注入层）和 `BridgePolicy`（分发层），两层各校验一次。
2. **`isMainFrame == false` 直接丢弃。** iframe 不给 bridge。

为了让 assets 里的页面有一个可授权的 origin，引入 `WebViewAssetLoader`，把 `src/main/assets/` 挂到 `https://appassets.androidplatform.net/assets/...`。

## 背景

容器要给 web 页开放原生能力（设备信息、toast、页面控制、本地存储）。这些能力一旦泄漏给任意页面，等于把 App 的一部分权限交给了任何能被容器加载的 URL。所以「谁能调」是通道选型的首要指标，「怎么调」是次要的。

另一条约束来自 ADR-0001：本仓库能自跑的验证只有编译、JVM 单测、静态检查。通道代码碰的是 `android.webkit`，在单测里是 stub，且 `unitTests.isReturnDefaultValues = true` 会让 stub 静静返回默认值——**写一个「测通道」的用例会绿，但那个绿是假的**。所以通道层必须窄、必须没有分支。

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）`addWebMessageListener` | 原生支持 origin 作用域；报文是字符串，收发对称；`replyProxy` 天然把回包配到发起的 frame | 要引 `androidx.webkit`；需要 `WebViewFeature.isFeatureSupported` 判断；老 WebView 上不可用 | 系统 WebView 版本过低时能力缺失 |
| B `addJavascriptInterface` | 零额外依赖，API 老而稳 | **没有 origin 概念**，注入了就是全局可见，任何页面、任何 iframe 都能调 | 一个被注入的 XSS 页面即可拿到全部原生能力；历史上这是 WebView 最经典的漏洞面 |
| C 拦 URL（`shouldOverrideUrlLoading` / `prompt`） | 不依赖任何新 API | 单向、要自己编解码、长报文被 URL 长度截断；回包只能靠 `evaluateJavascript` 拼字符串 | 拼 JS 字符串就是注入面；且同样没有 origin 概念 |

`androidx.webkit` 的 API 形状不是凭记忆写的，是下载 `webkit-1.17.0-sources.jar` 读出来的（`developer.android.com` 在本环境连不上）。核实到的关键事实：

- `onPostMessage(view, message, sourceOrigin, isMainFrame, replyProxy)`，标了 `@UiThread`；`JavaScriptReplyProxy.postMessage` 同样是 `@UiThread`。
- origin 规则语法 `SCHEME "://" [HOSTNAME_PATTERN [":" PORT]]`，不带尾斜杠，`*` 是全放开。
- **`file:` / `content:` 页面的 `sourceOrigin` 是字面字符串 `"null"`。** 这就是必须引 `WebViewAssetLoader` 的原因——`file:///android_asset/...` 加载的 demo 页没有身份可写进白名单。
- `WebMessageCompat.getData()` 在类型是 ArrayBuffer 时会抛，所以必须先判 `type == TYPE_STRING`。
- `WebViewAssetLoader.DEFAULT_DOMAIN = "appassets.androidplatform.net"`，`setHttpAllowed` 默认 false。

## 理由

B 的致命处不是「不够现代」，是**它没有「谁在调」这个概念**。只要注入过，页面里任何一段 JS——包括第三方广告 iframe、包括一次 XSS——都能调。要在 B 上补出等价的访问控制，只能自己在每个接口里读 `webView.url` 再比对，而那个值在导航过程中是会变的，判断本身就不可靠。A 把这件事交给 WebView 自己做，判断发生在注入之前。

选 A 也接受了它的代价：`WEB_MESSAGE_LISTENER` 不被支持时**不静默降级**到 B。降级会把一个「这台设备上没有访问控制」的状态藏起来，线上表现成偶发的行为差异，且没人会去查。所以容器直接把页面推进 `BRIDGE_UNAVAILABLE` 错误态，文案明确说是系统 WebView 需要更新。

双闸门看着冗余，留它是因为两层挡的不是同一类错误：注入层挡「页面拿不到 bridge 对象」，分发层挡「拿到了对象但调了没授权的能力」（能力级白名单只有分发层有）。而且它们各自会被独立地改错，`BridgeWiringTest` 锁住两者同源。

`isMainFrame` 这道则是补 origin 规则的一个空缺：同 origin 的 iframe 会通过 origin 校验，但「页面自己」和「页面里嵌的一个 frame」在能力授权上不该等价。

引 `WebViewAssetLoader` 是对「首批不做离线包」的一处刻意偏离，已和使用方确认。它在这里不是离线包，是 origin 的来源；顺带的好处是离线包将来挂的就是同一个 `shouldInterceptRequest` 钩子。

## 后果

- 接受了什么代价：新增依赖 `androidx.webkit:webkit:1.17.0`；老系统 WebView 上页面直接不可用而不是降级可用；assets 页面必须经 `WebViewAssetLoader` 才能用 bridge，直接 `file://` 打开是不通的。
- 引入了什么依赖 / 锁定：`BRIDGE_JS_OBJECT_NAME = "__hybridNative"` 与 `app/src/main/assets/demo/bridge.js` 里的字面量必须一致，**这是全仓唯一一处编译器管不到的对应关系**；报文格式同理（`BridgeMessage.kt` ↔ `bridge.js`）。
- 什么条件下重新评估：需要支持 `WEB_MESSAGE_LISTENER` 不可用的设备（届时正确做法是给出降级页面，而不是换回 `addJavascriptInterface`）；或要传二进制（`WebMessageCompat` 支持 ArrayBuffer，现在刻意只收字符串）。

## 参考

- `core/webview/.../BridgeInstaller.kt`、`BridgeTransport.kt`、`HybridAssets.kt`
- `core/bridge/.../BridgePolicy.kt`
- `app/.../bridge/BridgeSecurity.kt`、`app/src/test/.../bridge/BridgeWiringTest.kt`
- `webkit-1.17.0-sources.jar` 里 `WebViewCompat.WebMessageListener` / `WebViewAssetLoader` 的 javadoc
