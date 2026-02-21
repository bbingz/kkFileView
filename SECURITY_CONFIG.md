# kkFileView 安全配置指南

## ⚠️ 重要安全更新

本版本对 kkFileView 进行了全面安全审计与加固，共修复 **14 项安全漏洞**，涵盖 SSRF、文件上传绕过、XSS、未授权访问、请求头注入、开放重定向等多个类别。所有修复均为代码级别，无需额外部署 WAF 即可生效。

> 从 4.4.0 之后版本开始，kkFileView 默认拒绝所有未配置的外部文件预览请求，以防止 SSRF（服务器端请求伪造）攻击。

---

## 📋 安全修复清单

| 编号 | 漏洞类型 | 严重程度 | 修复说明 |
|------|----------|----------|----------|
| #1 | SSRF 黑名单通配符失效 | 高危 | 实现通配符前缀匹配，私有 IP 通过 `not.trust.host` 配置拦截 |
| #2 | notTrustHost.html 反射型 XSS | 高危 | 对 host 值进行 HTML 转义后再输出 |
| #3 | htmlEscape 函数自毁漏洞 | 高危 | 移除错误的 `&amp;` 反转义逻辑 |
| #4 | X-Base-Url Header 注入 | 高危 | 配置优先 + Header 协议校验 |
| #5 | SSL 证书校验全局禁用 | 中危 | 新增 `ssl.disabled` 配置项，默认启用校验 |
| #6 | 文件上传后缀绕过限制 | 极危 | 废弃旧配置黑名单，强制重构为内置扩展名**强白名单**进行强过滤 |
| #7 | /addTask 接口未授权访问 | 中危 | 新增 `addTask.secret.key` 密钥认证 |
| #8 | deleteFile 接口 GET 方法导致 CSRF | 中危 | 改为 POST 方法 |
| #9 | UrlCheckFilter 开放重定向 | 中危 | 改用相对路径重定向 |
| #10 | kk-proxy-authorization 请求头注入 | 高危 | 新增敏感 Header 黑名单过滤 |
| #11 | FTL 模板反射/存储型 XSS | 低危 | 引用外部变量均补充 `?html` 或 `?js_string` 强制渲染转义 |
| #12 | /directory 目录遍历风险 | 低危 | 增强路径安全校验，移除协议头后检测非法路径字符 |
| #13 | 热更新竞态读写异常 | 低危 | 在应用属性热加载覆盖过程中增加 `synchronized` 同步锁机制 |
| #14 | fileDir 静态资源挂载安全 | 架构说明 | fileDir 映射为预览核心依赖，安全由上传白名单 + `file.upload.disable` 控制 |

---

## 🔒 安全配置说明

### 1. 信任主机白名单配置（推荐）

在 `application.properties` 中配置允许预览的域名：

```properties
# 方式1：通过配置文件
trust.host = kkview.cn,yourdomain.com,cdn.example.com

# 方式2：通过环境变量
KK_TRUST_HOST=kkview.cn,yourdomain.com,cdn.example.com
```

**示例场景**：
- 只允许预览来自 `oss.aliyuncs.com` 和 `cdn.example.com` 的文件
```properties
trust.host = oss.aliyuncs.com,cdn.example.com
```

### 2. 允许所有主机（不推荐，仅测试环境）

```properties
trust.host = *
```

⚠️ **警告**：此配置会允许访问任意外部地址，存在安全风险，仅应在测试环境使用！建议同时配置 `not.trust.host` 黑名单拦截内网地址。

### 3. 黑名单配置（高级）

禁止特定域名或内网地址，**支持通配符前缀匹配**：

```properties
# 禁止访问内网地址（强烈推荐）
# 通配符说明：192.168.* 会匹配所有以 192.168. 开头的地址（如 192.168.1.1、192.168.100.200）
not.trust.host = localhost,127.0.0.1,192.168.*,10.*,172.16.*,169.254.*

# 禁止特定恶意域名
not.trust.host = malicious-site.com,spam-domain.net
```

**优先级**：黑名单 > 白名单

> **与旧版本的区别**：旧版本黑名单使用 `Set.contains()` 精确匹配，`192.168.*` 不会匹配 `192.168.1.1`。新版本已修复此问题，通配符现在可以正确工作。**生产环境强烈建议配置 `not.trust.host` 黑名单拦截内网地址**（参见上方示例）。

### 4. base.url 安全配置

```properties
# 使用反向代理时，必须配置此项
# 配置后系统将忽略客户端发送的 X-Base-Url 请求头，防止 URL 注入攻击
base.url = https://preview.yourdomain.com
```

**安全机制说明**：
- 当 `base.url` 已配置（非 `default`）时，系统**忽略**客户端 `X-Base-Url` 请求头
- 当 `base.url` 未配置时，系统仅接受以 `http://` 或 `https://` 开头的 `X-Base-Url` 值，且不允许包含控制字符
- 建议生产环境**必须配置** `base.url`

### 5. SSL 证书校验配置（新增）

```properties
# 默认启用 SSL 证书校验（推荐）
ssl.disabled = false

# 仅在需要访问自签名证书的文件服务器时设置为 true
# ssl.disabled = true
```

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `KK_SSL_DISABLED` | `false` | 设为 `true` 时跳过 SSL 证书校验 |

⚠️ **警告**：禁用 SSL 校验会使系统容易遭受中间人攻击，仅在确实需要访问自签名证书服务器时才开启。

### 6. /addTask 接口认证配置（新增）

`/addTask` 接口用于通过 API 向转换队列添加任务。新版本要求配置密钥后才能使用此接口：

```properties
# 配置密钥后启用 /addTask 接口（为空时接口禁用）
addTask.secret.key = your-secret-key-here
```

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `KK_ADD_TASK_SECRET_KEY` | 空 | 为空时禁用接口；配置后需在请求中携带 `secretKey` 参数 |

**调用示例**：
```
GET /addTask?url=<encoded-url>&secretKey=your-secret-key-here
```

### 7. 文件删除接口安全（变更）

文件删除接口已从 **GET** 方法改为 **POST** 方法，防止 CSRF 攻击。

```
# 旧版本（已废弃）
GET /deleteFile?fileName=xxx&password=xxx

# 新版本
POST /deleteFile
Content-Type: application/x-www-form-urlencoded
Body: fileName=xxx&password=xxx
```

> **注意**：如果您的系统有第三方集成调用 `/deleteFile` 接口，需要将请求方法从 GET 改为 POST。前端页面已自动适配。

相关配置：
```properties
# 删除密码（强烈建议修改默认密码）
delete.password = your-strong-password

# 启用验证码保护（推荐）
delete.captcha = true
```

### 8. 文件上传控制（重大变更）

从版本 4.4.0（及安全强化补丁）开始，系统已经**彻底废弃基于黑名单的拦截模式**，以切断攻击者上传特殊后缀脚本的后路，转而强制采用严格的多媒体及文档**强白名单模式**。

```properties
# 生产环境建议主动禁用不必要的文件上传
file.upload.disable = true

# 【已废弃】由于采用硬编码白名单防御，旧版本的黑名单属性将不再起到拦截作用
# prohibit = exe,dll,dat,sh,bat

# 注意：系统现在内置支持约 130+ 种文档、音视频、CAD、3D 模型及压缩包后缀（覆盖 FileType.java 全部可预览类型）。若私有网络需要特殊冷门文件扩展名支持，请修改 KkFileUtils.ALLOWED_TYPES 源码手动放行！
```

### 9. Docker 环境配置

```bash
docker run -d \
  -e KK_TRUST_HOST=yourdomain.com,cdn.example.com \
  -e KK_NOT_TRUST_HOST=localhost,127.0.0.1 \
  -e KK_BASE_URL=https://preview.yourdomain.com \
  -e KK_SSL_DISABLED=false \
  -e KK_FILE_UPLOAD_DISABLE=true \
  -e KK_ADD_TASK_SECRET_KEY=your-secret-key \
  -e KK_DELETE_PASSWORD=your-strong-password \
  -e KK_DELETE_CAPTCHA=true \
  -p 8012:8012 \
  keking/kkfileview
```

---

## 🛡️ 安全最佳实践

### ✅ 生产环境推荐配置

```properties
# ====== 必须配置 ======
# 1. 明确配置信任主机白名单
trust.host = your-cdn.com,your-storage.com

# 2. 配置基础URL（防止 X-Base-Url 注入）
base.url = https://preview.yourdomain.com

# 3. 禁用文件上传
file.upload.disable = true

# 4. 设置强密码
delete.password = your-strong-password-here

# ====== 强烈建议配置 ======
# 5. 启用验证码保护删除操作
delete.captcha = true

# 6. 配置 addTask 密钥（或留空禁用该接口）
addTask.secret.key = your-secret-key

# 7. 保持 SSL 校验启用
ssl.disabled = false

# 8. 配置黑名单拦截内网地址（强烈推荐，支持通配符）
not.trust.host = localhost,0.0.0.0,::1,192.168.*,10.*,172.16.*,169.254.*
```

### ❌ 不推荐配置

```properties
# 危险：允许所有主机访问
trust.host = *

# 危险：启用文件上传（生产环境）
file.upload.disable = false

# 危险：使用默认删除密码
delete.password = 123456

# 危险：禁用 SSL 证书校验
ssl.disabled = true

# 危险：不配置 base.url（允许 X-Base-Url 注入）
base.url = default
```

---

## 🔍 配置验证

### 测试白名单是否生效

1. 配置白名单：
```properties
trust.host = kkview.cn
```

2. 尝试预览白名单内的文件：
```
http://localhost:8012/onlinePreview?url=<Base64(https://kkview.cn/test.pdf)>
✅ 应该可以正常预览
```

3. 尝试预览白名单外的文件：
```
http://localhost:8012/onlinePreview?url=<Base64(https://other-domain.com/test.pdf)>
❌ 应该被拒绝，显示"不信任的文件源"
```

### 测试黑名单通配符是否生效

1. 配置黑名单：
```properties
not.trust.host = 192.168.*,10.*
```

2. 尝试访问内网地址：
```
http://localhost:8012/onlinePreview?url=<Base64(http://192.168.1.100/secret.pdf)>
❌ 应该被拒绝（通配符匹配）
```

### 测试黑名单拦截内网地址

配置 `not.trust.host = localhost,127.0.0.1,0.0.0.0,192.168.*,10.*,172.16.*,169.254.*` 后：
```
http://127.0.0.1:8080/admin        ❌ 精确匹配拦截
http://10.0.0.1/internal           ❌ 通配符 10.* 匹配拦截
http://172.16.0.1/internal         ❌ 通配符 172.16.* 匹配拦截
http://192.168.1.1/internal        ❌ 通配符 192.168.* 匹配拦截
http://169.254.169.254/metadata    ❌ 通配符 169.254.* 匹配拦截（云平台元数据）
```

### 测试 X-Base-Url 防护

1. 配置 `base.url`：
```properties
base.url = https://preview.yourdomain.com
```

2. 尝试通过请求头注入：
```bash
curl -H "X-Base-Url: https://evil.com" http://localhost:8012/onlinePreview?url=...
# base.url 已配置，X-Base-Url 请求头将被忽略
```

### 测试 addTask 认证

```bash
# 未携带密钥 → 拒绝
curl "http://localhost:8012/addTask?url=test"
# 返回: error: addTask interface is disabled

# 携带正确密钥 → 成功
curl "http://localhost:8012/addTask?url=test&secretKey=your-secret-key"
# 返回: success
```

### 测试 deleteFile POST 方法

```bash
# GET 请求 → 405 Method Not Allowed
curl "http://localhost:8012/deleteFile?fileName=test&password=xxx"

# POST 请求 → 正常处理
curl -X POST -d "fileName=test&password=xxx" http://localhost:8012/deleteFile
```

---

## 🔐 安全修复技术细节

### #1 SSRF 黑名单通配符匹配修复

**问题**：旧版本使用 `Set.contains(host)` 精确匹配，配置 `192.168.*` 无法匹配 `192.168.1.1`。

**修复**：
- 实现 `matchesHostPattern()` 方法，当模式以 `*` 结尾时使用前缀匹配
- 私有 IP 拦截不再硬编码，完全通过 `not.trust.host` 配置控制，生产环境建议配置：
  ```properties
  not.trust.host = localhost,127.0.0.1,0.0.0.0,::1,192.168.*,10.*,172.16.*,169.254.*
  ```

**文件**：`TrustHostFilter.java`

### #2 notTrustHost.html 反射型 XSS 修复

**问题**：不受信任的 host 值直接拼接到 HTML 中输出，攻击者可构造恶意 host 名注入 JavaScript。

**修复**：使用 `HtmlUtils.htmlEscape()` 对 host 值进行 HTML 实体编码后再插入模板。

**文件**：`TrustHostFilter.java`

### #3 htmlEscape 函数自毁漏洞修复

**问题**：`KkFileUtils.htmlEscape()` 在调用 `HtmlUtils.htmlEscape()` 之后，又执行了 `.replace("&amp;", "&")`，将 `&amp;` 还原为 `&`。这导致 HTML 实体编码的 XSS 载荷（如 `&#x3C;script&#x3E;`）可以绕过过滤。

**攻击路径**：
```
输入: &#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;
经 HtmlUtils.htmlEscape(): &amp;#x3C;script&amp;#x3E;alert(1)&amp;#x3C;/script&amp;#x3E;
经 .replace("&amp;","&"): &#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;
浏览器解析 HTML 实体: <script>alert(1)</script>  → XSS 执行！
```

**修复**：移除 `.replace("&amp;", "&")` 行。在 HTML 上下文中，`&amp;` 是 `&` 的正确表示，浏览器会在 `href`、`src` 等属性中正确解析。

**文件**：`KkFileUtils.java`

### #4 X-Base-Url Header 注入修复

**问题**：客户端可通过 `X-Base-Url` 请求头覆盖服务端 `base.url` 配置，注入任意 URL，导致页面中所有资源引用指向攻击者控制的地址。

**修复**：
- 调整优先级：**配置文件 > 请求头 > 动态拼接**（原来是请求头最高优先级）
- 当 `base.url` 已配置时，完全忽略 `X-Base-Url` 请求头
- 当使用请求头时，校验必须以 `http://` 或 `https://` 开头，且不含控制字符

**文件**：`BaseUrlFilter.java`

### #5 SSL 证书校验全局禁用修复

**问题**：`DownloadUtils.downLoad()` 在每次下载文件时无条件调用 `SslUtils.ignoreSsl()`，全局关闭 JVM 的 SSL 证书校验，使所有 HTTPS 连接都容易遭受中间人攻击。

**修复**：
- 新增配置项 `ssl.disabled`（默认 `false`）
- 仅在明确配置 `ssl.disabled=true` 时才调用 `SslUtils.ignoreSsl()`
- 支持运行时动态刷新配置

**文件**：`DownloadUtils.java`、`ConfigConstants.java`、`ConfigRefreshComponent.java`、`application.properties`

### #7 /addTask 接口未授权访问修复

**问题**：`/addTask` 接口无任何认证，任何人都可以向转换队列添加任意 URL 任务，可能导致资源耗尽或被用作 SSRF 跳板。

**修复**：
- 新增配置项 `addTask.secret.key`
- 未配置密钥时接口直接返回错误（默认禁用）
- 配置密钥后，请求必须携带匹配的 `secretKey` 参数

**文件**：`OnlinePreviewController.java`、`ConfigConstants.java`、`ConfigRefreshComponent.java`、`application.properties`

### #8 deleteFile GET 方法 CSRF 修复

**问题**：`/deleteFile` 使用 GET 方法，攻击者可通过 `<img src="/deleteFile?fileName=xxx&password=123456">` 在用户不知情的情况下删除文件。

**修复**：
- 将 `@GetMapping("/deleteFile")` 改为 `@PostMapping("/deleteFile")`
- 前端 JavaScript 同步改为 `$.post()` / `$.ajax({type:'POST'})`

**文件**：`FileController.java`、`index.ftl`

### #9 UrlCheckFilter 开放重定向修复

**问题**：当请求路径需要规范化时（去除双斜杠、尾斜杠），`UrlCheckFilter` 使用 `BaseUrlFilter.getBaseUrl()` 拼接重定向目标。如果 Base URL 被污染（#4 漏洞），重定向地址可被攻击者控制。

**修复**：重定向时始终使用相对路径（`contextPath + servletPath`），不依赖可能被污染的 Base URL。

**文件**：`UrlCheckFilter.java`

### #10 kk-proxy-authorization 请求头注入修复

**问题**：`kk-proxy-authorization` 请求头的值（JSON 格式的键值对）会被直接设置为对外 HTTP 请求的头部。攻击者可注入 `Host`、`Cookie`、`X-Forwarded-For` 等敏感头部，实现请求走私或权限绕过。

**修复**：
- 新增敏感 Header 黑名单（16 项），包括：`host`、`cookie`、`set-cookie`、`x-forwarded-for`、`x-forwarded-host`、`x-forwarded-proto`、`x-forwarded-port`、`x-real-ip`、`forwarded`、`via`、`origin`、`referer`、`connection`、`transfer-encoding`、`proxy-authorization`、`proxy-connection` 等
- 在 `OnlinePreviewController.getCorsFile()` 和 `DownloadUtils.downLoad()` 两处请求回调中过滤
- 被禁止的 Header 会被跳过，并记录 WARN 级别日志

**文件**：`WebUtils.java`、`OnlinePreviewController.java`、`DownloadUtils.java`

---

## 📝 完整安全配置参考

以下是所有安全相关配置项的汇总：

```properties
# ==================== 安全配置 ====================

# --- SSRF 防护 ---
# 信任主机白名单（必须配置，否则默认拒绝所有外部请求）
trust.host = ${KK_TRUST_HOST:default}
# 不信任主机黑名单（支持通配符，如 192.168.*）
not.trust.host = ${KK_NOT_TRUST_HOST:default}

# --- URL 安全 ---
# 基础 URL（配置后忽略 X-Base-Url 请求头）
base.url = ${KK_BASE_URL:default}

# --- SSL 安全 ---
# SSL 证书校验（默认启用，仅在需要时设为 true 禁用）
ssl.disabled = ${KK_SSL_DISABLED:false}

# --- 接口认证 ---
# addTask 接口密钥（为空时禁用该接口）
addTask.secret.key = ${KK_ADD_TASK_SECRET_KEY:}

# --- 文件操作安全 ---
# 禁用文件上传（生产环境建议 true）
file.upload.disable = ${KK_FILE_UPLOAD_DISABLE:true}
# [已废弃] 由于采用硬编码强白名单防御，以前的 prohibit 属性将不再起作用
# prohibit = ${KK_PROHIBIT:exe,dll,dat}
# 文件删除密码（请修改默认值！）
delete.password = ${KK_DELETE_PASSWORD:123456}
# 启用验证码保护删除（推荐 true）
delete.captcha = ${KK_DELETE_CAPTCHA:false}
```

---

## 📋 常见问题

### Q1: 升级后无法预览文件了？

**原因**：新版本默认拒绝未配置的主机。

**解决**：在配置文件中添加信任主机列表：
```properties
trust.host = your-file-server.com
```

### Q2: 如何临时恢复旧版本行为？

**不推荐**，但如果确实需要：
```properties
trust.host = *
```

> 注意：即使设为 `*`，内置私有 IP 检测仍然生效，内网地址仍会被拦截。

### Q3: 配置了白名单但还是无法访问？

检查以下几点：
1. 域名是否匹配（配置会自动转小写处理）
2. 是否配置了黑名单，黑名单优先级更高
3. 目标地址是否解析到内网 IP（会被内置私有 IP 检测拦截）
4. 查看日志中的 WARNING 信息
5. 确认环境变量是否正确设置

### Q4: 如何允许子域名？

支持通配符前缀匹配：
```properties
# 方式1：逐个列出
trust.host = cdn.example.com,api.example.com,storage.example.com

# 方式2：使用通配符（匹配所有以指定前缀开头的域名）
# 注意：通配符仅支持尾部匹配
trust.host = *.example.com
```

### Q5: 升级后第三方系统调用 deleteFile 接口失败？

**原因**：`/deleteFile` 已从 GET 改为 POST 方法。

**解决**：将调用方的请求方法改为 POST，并将参数放在请求体中：
```bash
curl -X POST -d "fileName=xxx&password=xxx" http://your-server:8012/deleteFile
```

### Q6: addTask 接口返回 "disabled" 错误？

**原因**：新版本要求配置密钥才能使用。

**解决**：
```properties
addTask.secret.key = your-secret-key
```
然后在请求中携带 `secretKey` 参数。

### Q7: 访问自签名证书的文件服务器失败？

**原因**：新版本默认启用 SSL 证书校验。

**解决**：
```properties
ssl.disabled = true
```
⚠️ 建议仅在内网环境使用此配置。

---

## 🚨 安全事件响应

如果发现可疑请求，请检查以下日志关键字：

| 日志关键字 | 含义 |
|-----------|------|
| `拒绝访问主机` | 请求了未授权的外部地址（黑名单命中或不在白名单中） |
| `拒绝不合法的X-Base-Url header` | X-Base-Url 注入攻击 |
| `kk-proxy-authorization 中包含被禁止的请求头` | 请求头注入攻击 |
| `addTask接口密钥校验失败` | 未授权 API 调用 |
| `删除文件失败，密码错误` | 暴力破解删除密码 |

建议配合日志监控系统设置告警规则。

---

## 📞 获取帮助

- GitHub Issues: https://github.com/kekingcn/kkFileView/issues
- Gitee Issues: https://gitee.com/kekingcn/file-online-preview/issues

---

**安全提示**：定期检查和更新信任主机列表，遵循最小权限原则。建议在生产环境中配合反向代理（Nginx/Caddy）和 WAF 使用，形成纵深防御。
