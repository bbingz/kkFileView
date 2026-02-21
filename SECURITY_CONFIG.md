# kkFileView 安全配置指南

## 重要安全更新

本文档记录 kkFileView 的两轮安全加固内容。

- **第一轮**（commit a531f025）：修复 14 项漏洞，涵盖 SSRF、文件上传绕过、XSS、未授权访问等
- **第二轮**（v4.4.1）：修复 11 项漏洞，涵盖反序列化 RCE、凭据泄露、Shell 注入、时序攻击等

> 从 4.4.0 之后版本开始，kkFileView 默认拒绝所有未配置的外部文件预览请求，以防止 SSRF 攻击。

---

## 升级须知（Breaking Changes）

### 从 v4.4.0 升级到 v4.4.1

| 配置项 | 旧默认值 | 新默认值 | 影响 |
|-------|---------|---------|------|
| `delete.password` | `123456` | 空 | **未配置时删除功能被禁用**，需显式设置密码才能使用删除接口 |
| `ftp.password` | `123456` | 空 | 未配置时 FTP 密码为空，需显式配置才能登录 FTP 服务器 |
| Actuator 暴露端点 | `health,info,metrics` | `health` | `info` 和 `metrics` 不再对外暴露 |
| Actuator show-details | `always` | `when-authorized` | 健康检查详情需授权后才可查看 |

如果依赖旧行为，请在 `application.properties` 或环境变量中显式配置：

```properties
# 恢复删除功能（请使用强密码）
delete.password = ${KK_DELETE_PASSWORD:your-strong-password}

# 恢复 FTP 默认密码（请使用实际密码）
ftp.password = ${KK_FTP_PASSWORD:your-ftp-password}

# 恢复 Actuator 旧行为（不推荐用于生产环境）
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```

### 从 v4.4.0 之前版本升级

| 变更 | 影响 |
|------|------|
| `/deleteFile` 从 GET 改为 POST | 第三方集成需改为 POST 请求 |
| `trust.host` 未配置时默认拒绝所有外部请求 | 必须配置白名单才能预览外部文件 |
| `addTask.secret.key` 未配置时接口禁用 | 需配置密钥才能使用 addTask API |
| 文件上传改为硬编码白名单 | 旧 `prohibit` 黑名单配置不再生效 |

---

## 第二轮安全修复清单（v4.4.1）

### CRITICAL 级别

| 编号 | 漏洞类型 | OWASP 分类 | 修复说明 |
|------|---------|-----------|---------|
| #R2-1 | Java 反序列化 RCE | A08 | 添加 ObjectInputFilter 白名单，仅允许安全 JDK 类型 |
| #R2-2 | FTP 密码泄露到日志 | A02/A09 | 从 DEBUG 日志中移除 username 和 password |
| #R2-3 | 默认弱密码 123456 | A05/A07 | 去掉默认密码，未配置时禁用相关功能 |

### HIGH 级别

| 编号 | 漏洞类型 | OWASP 分类 | 修复说明 |
|------|---------|-----------|---------|
| #R2-4 | 缺少 HTTP 安全响应头 | A05 | 新增 SecurityHeadersFilter，添加 5 个安全头 |
| #R2-5 | FreeMarker JS 上下文注入 | A03/A07 | 使用 `?js_string` 和 `?c` 过滤器 |
| #R2-6 | Actuator 端点信息泄露 | A05 | 缩减暴露端点，详情需授权 |
| #R2-7 | SSRF 黑名单配置建议 | A10 | 注释中提供生产环境推荐黑名单 |
| #R2-8 | HTTP 重定向绕过 SSRF 防护 | A10 | 自定义 SafeRedirectStrategy 校验重定向目标 |
| #R2-9 | 进程管理 Shell 注入 | A03 | 用 ProcessBuilder + pgrep/pkill 替代 sh -c 管道 |
| #R2-10 | 路径遍历编码绕过 | A01 | 循环 URL 解码 + Path.normalize() 校验 |
| #R2-11 | addTask 密钥时序攻击 | A01 | 使用 MessageDigest.isEqual() 恒定时间比较 |

---

## 第二轮修复技术细节

### #R2-1 不安全的 Java 反序列化（CRITICAL）

- **文件**: `CacheServiceRocksDBImpl.java`
- **风险**: `ObjectInputStream.readObject()` 无过滤，可被反序列化 gadget chain 利用实现远程代码执行
- **修复**: 在 `toObject()` 方法中添加 `ObjectInputFilter` 白名单：
  - `java.util.HashMap`、`java.util.ArrayList`
  - `java.lang.String`、`java.lang.Integer`、`java.lang.Long`、`java.lang.Number`
  - 数组类型
  - 其他类型一律 REJECTED，并记录 WARN 日志

### #R2-2 FTP 密码泄露到日志（CRITICAL）

- **文件**: `FtpUtils.java`
- **风险**: DEBUG 日志明文打印 `username` 和 `password`
- **修复**: 日志输出中移除 username 和 password，仅保留 url、controlEncoding、localFilePath

### #R2-3 默认弱密码 123456（CRITICAL）

- **文件**: `application.properties`、`ConfigConstants.java`、`FileController.java`
- **风险**: `delete.password` 和 `ftp.password` 默认密码为 `123456`，极易被猜测
- **修复**:
  - `delete.password` 默认值改为空，未配置时返回"删除功能未启用"
  - `ftp.password` 默认值改为空
  - `ConfigConstants.DEFAULT_PASSWORD` 改为空字符串

### #R2-4 缺少 HTTP 安全响应头（HIGH）

- **文件**: `SecurityHeadersFilter.java`（新增）、`WebConfig.java`
- **风险**: 缺少关键安全头，易受 Clickjacking、MIME sniffing 攻击
- **修复**: 所有响应自动添加以下头：

| 响应头 | 值 | 作用 |
|-------|---|------|
| `X-Frame-Options` | `SAMEORIGIN` | 防止 Clickjacking |
| `X-Content-Type-Options` | `nosniff` | 防止 MIME sniffing |
| `X-XSS-Protection` | `1; mode=block` | 浏览器 XSS 过滤 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 控制 Referer 泄露 |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | 禁用敏感浏览器 API |

### #R2-5 FreeMarker 模板 JS 上下文注入（HIGH）

- **文件**: `commonHeader.ftl`
- **风险**: 水印变量用 `'${var}'` 直接嵌入 JavaScript，HTML 转义无法防御 JS 上下文注入（如 `'; alert(1); '`）
- **修复**:
  - 字符串变量使用 `?js_string`：`'${watermarkTxt?js_string}'`
  - 数值变量使用 `?c`：`${watermarkAlpha?c}`

### #R2-6 Actuator 端点信息泄露（HIGH）

- **文件**: `application.properties`
- **风险**: `show-details=always` 暴露内存、磁盘、数据库连接等系统内部信息
- **修复**:
  - `management.endpoints.web.exposure.include` 从 `health,info,metrics` 改为 `health`
  - `management.endpoint.health.show-details` 从 `always` 改为 `when-authorized`

### #R2-7 SSRF 黑名单配置建议（HIGH）

- **文件**: `application.properties`
- **风险**: `not.trust.host` 默认为空，未自动封锁内网地址
- **修复**: 默认值保持为空（`default`）以保证兼容性，在配置注释中给出生产推荐值
- **生产环境推荐配置**:

```properties
not.trust.host = 0.0.0.0,10.*,172.16.*,172.17.*,172.18.*,172.19.*,172.20.*,172.21.*,172.22.*,172.23.*,172.24.*,172.25.*,172.26.*,172.27.*,172.28.*,172.29.*,172.30.*,172.31.*,192.168.*,169.254.*
```

> 注意：不建议默认封锁 `127.0.0.1` 和 `localhost`，因为服务自身可能需要回环访问。如需封锁，请根据实际网络拓扑手动添加。

### #R2-8 HTTP 重定向绕过 SSRF 防护（HIGH）

- **文件**: `SafeRedirectStrategy.java`（新增）、`DownloadUtils.java`、`OnlinePreviewController.java`
- **风险**: `DefaultRedirectStrategy` 允许任意重定向，攻击者可通过可信域名 302 重定向到内网地址
- **修复**: 自定义 `SafeRedirectStrategy` 继承 `DefaultRedirectStrategy`，在 `getLocationURI()` 中校验重定向目标 host 是否命中 `not.trust.host` 黑名单。命中则抛出 `HttpException` 中断请求。

### #R2-9 进程管理 Shell 注入风险（HIGH）

- **文件**: `OfficePluginManager.java`
- **风险**: 使用 `Runtime.exec("sh -c ...")` 配合 shell 管道、awk 等方式管理 Office 进程，可被 shell 元字符注入
- **修复**:
  - Windows: `ProcessBuilder("tasklist")` + `ProcessBuilder("taskkill", "/im", "soffice.bin", "/f")`
  - macOS/Linux: `ProcessBuilder("pgrep", "-f", "soffice.bin")` + `ProcessBuilder("pkill", "-f", "soffice.bin")`
  - 不再使用 `sh -c`，不存在 shell 元字符解释风险

### #R2-10 路径遍历检测不完整（HIGH）

- **文件**: `KkFileUtils.java`
- **风险**: 黑名单方式检测 `../`，可被 URL 编码（`%2e%2e%2f`）、双重编码绕过
- **修复**:
  - 循环 URL 解码直到结果不再变化（防止多重编码绕过）
  - 对原始文件名和解码后的文件名同时进行黑名单检查
  - 追加 `Path.normalize()` 后检测是否仍含 `..`
  - 解码异常直接视为非法文件名

### #R2-11 addTask 接口密钥时序攻击（HIGH）

- **文件**: `OnlinePreviewController.java`
- **风险**: 使用 `String.equals()` 比较密钥，不同字符位置失败时的响应时间差异可被利用逐位猜测密钥
- **修复**: 使用 `MessageDigest.isEqual()` 进行恒定时间比较，同时增加 `secretKey == null` 的前置校验

---

## 第一轮安全修复清单（commit a531f025）

| 编号 | 漏洞类型 | 严重程度 | 修复说明 |
|------|----------|----------|----------|
| #R1-1 | SSRF 黑名单通配符失效 | 高危 | 实现通配符前缀匹配，私有 IP 通过 `not.trust.host` 配置拦截 |
| #R1-2 | notTrustHost.html 反射型 XSS | 高危 | 对 host 值进行 HTML 转义后再输出 |
| #R1-3 | htmlEscape 函数自毁漏洞 | 高危 | 移除错误的 `&amp;` 反转义逻辑 |
| #R1-4 | X-Base-Url Header 注入 | 高危 | 配置优先 + Header 协议校验 |
| #R1-5 | SSL 证书校验全局禁用 | 中危 | 新增 `ssl.disabled` 配置项，默认启用校验 |
| #R1-6 | 文件上传后缀绕过限制 | 极危 | 废弃黑名单，强制采用内置扩展名强白名单 |
| #R1-7 | /addTask 接口未授权访问 | 中危 | 新增 `addTask.secret.key` 密钥认证 |
| #R1-8 | deleteFile GET 方法 CSRF | 中危 | 改为 POST 方法 |
| #R1-9 | UrlCheckFilter 开放重定向 | 中危 | 改用相对路径重定向 |
| #R1-10 | kk-proxy-authorization 请求头注入 | 高危 | 新增敏感 Header 黑名单过滤 |
| #R1-11 | FTL 模板反射/存储型 XSS | 低危 | 引用外部变量均补充 `?html` 转义 |
| #R1-12 | /directory 目录遍历风险 | 低危 | 增强路径安全校验 |
| #R1-13 | 热更新竞态读写异常 | 低危 | 增加 synchronized 同步锁 |
| #R1-14 | fileDir 静态资源挂载安全 | 架构说明 | 安全由上传白名单 + file.upload.disable 控制 |

---

## 安全配置说明

### 1. 信任主机白名单

```properties
# 方式1：通过配置文件
trust.host = kkview.cn,yourdomain.com,cdn.example.com

# 方式2：通过环境变量
KK_TRUST_HOST=kkview.cn,yourdomain.com,cdn.example.com

# 允许所有域名（不推荐，仅测试环境）
trust.host = *
```

### 2. 黑名单配置

支持通配符前缀匹配：

```properties
# 生产环境推荐
not.trust.host = 0.0.0.0,10.*,172.16.*,172.17.*,172.18.*,172.19.*,172.20.*,172.21.*,172.22.*,172.23.*,172.24.*,172.25.*,172.26.*,172.27.*,172.28.*,172.29.*,172.30.*,172.31.*,192.168.*,169.254.*
```

优先级：黑名单 > 白名单

### 3. base.url 安全配置

```properties
# 使用反向代理时必须配置，防止 X-Base-Url 注入
base.url = https://preview.yourdomain.com
```

### 4. SSL 证书校验

```properties
# 默认启用（推荐）
ssl.disabled = false
# 仅在需要访问自签名证书时设为 true
```

### 5. /addTask 接口认证

```properties
# 为空时禁用接口（默认）
addTask.secret.key = ${KK_ADD_TASK_SECRET_KEY:}
```

调用示例：`GET /addTask?url=<encoded-url>&secretKey=your-secret-key`

### 6. 文件删除配置

```properties
# 删除密码（为空时禁用删除功能）
delete.password = ${KK_DELETE_PASSWORD:}
# 启用验证码保护（推荐）
delete.captcha = true
```

> `/deleteFile` 为 POST 方法。

### 7. 文件上传控制

```properties
# 生产环境建议禁用
file.upload.disable = true
```

系统使用硬编码白名单（~130 种可预览文件类型），旧 `prohibit` 黑名单不再生效。

---

## 完整推荐配置

### application.properties（生产环境）

```properties
# 必须配置
trust.host = your-cdn.com,your-storage.com
base.url = https://preview.yourdomain.com
file.upload.disable = true

# 强烈建议
not.trust.host = 0.0.0.0,10.*,172.16.*,172.17.*,172.18.*,172.19.*,172.20.*,172.21.*,172.22.*,172.23.*,172.24.*,172.25.*,172.26.*,172.27.*,172.28.*,172.29.*,172.30.*,172.31.*,192.168.*,169.254.*
delete.password = ${KK_DELETE_PASSWORD:}
delete.captcha = true
addTask.secret.key = ${KK_ADD_TASK_SECRET_KEY:}
ssl.disabled = false
```

### Docker 启动

```bash
docker run -d \
  -e KK_TRUST_HOST=yourdomain.com \
  -e KK_NOT_TRUST_HOST=0.0.0.0,10.*,192.168.* \
  -e KK_BASE_URL=https://preview.yourdomain.com \
  -e KK_DELETE_PASSWORD=your-strong-password \
  -e KK_ADD_TASK_SECRET_KEY=your-secret-key \
  -e KK_FILE_UPLOAD_DISABLE=true \
  -e KK_SSL_DISABLED=false \
  -p 8012:8012 \
  keking/kkfileview
```

---

## 修改文件清单

### 第二轮（v4.4.1）

| 文件 | 修改类型 |
|-----|---------|
| `server/src/main/java/cn/keking/service/cache/impl/CacheServiceRocksDBImpl.java` | 编辑 - 反序列化白名单 |
| `server/src/main/java/cn/keking/utils/FtpUtils.java` | 编辑 - 移除敏感日志 |
| `server/src/main/config/application.properties` | 编辑 - 弱密码/Actuator/黑名单建议 |
| `server/src/main/java/cn/keking/config/ConfigConstants.java` | 编辑 - 弱密码默认值 |
| `server/src/main/java/cn/keking/web/controller/FileController.java` | 编辑 - 空密码禁用删除 |
| `server/src/main/java/cn/keking/web/filter/SecurityHeadersFilter.java` | **新增** - 安全响应头过滤器 |
| `server/src/main/java/cn/keking/config/WebConfig.java` | 编辑 - 注册 SecurityHeadersFilter |
| `server/src/main/resources/web/commonHeader.ftl` | 编辑 - js_string/c 过滤器 |
| `server/src/main/java/cn/keking/utils/SafeRedirectStrategy.java` | **新增** - 安全重定向策略 |
| `server/src/main/java/cn/keking/utils/DownloadUtils.java` | 编辑 - 使用 SafeRedirectStrategy |
| `server/src/main/java/cn/keking/web/controller/OnlinePreviewController.java` | 编辑 - 安全重定向 + 恒定时间比较 |
| `server/src/main/java/cn/keking/service/OfficePluginManager.java` | 编辑 - ProcessBuilder 替代 |
| `server/src/main/java/cn/keking/utils/KkFileUtils.java` | 编辑 - 增强路径遍历检测 |

### 第一轮（commit a531f025）

| 文件 | 修改类型 |
|-----|---------|
| `TrustHostFilter.java` | 编辑 - 通配符匹配 + XSS 修复 |
| `KkFileUtils.java` | 编辑 - htmlEscape 修复 |
| `BaseUrlFilter.java` | 编辑 - X-Base-Url 防护 |
| `DownloadUtils.java` | 编辑 - SSL 配置化 + Header 注入防护 |
| `OnlinePreviewController.java` | 编辑 - addTask 认证 + Header 注入防护 |
| `FileController.java` | 编辑 - POST 方法 |
| `UrlCheckFilter.java` | 编辑 - 相对路径重定向 |
| `WebUtils.java` | 编辑 - Header 黑名单 |
| `ConfigConstants.java` | 编辑 - 新增配置项 |
| `ConfigRefreshComponent.java` | 编辑 - 热更新同步 |
| `application.properties` | 编辑 - 安全配置项 |

---

## 验证方式

### 编译验证

```bash
mvn compile -pl server
```

### 安全响应头验证

```bash
curl -I http://localhost:8012/
# 应包含：
# X-Frame-Options: SAMEORIGIN
# X-Content-Type-Options: nosniff
# X-XSS-Protection: 1; mode=block
# Referrer-Policy: strict-origin-when-cross-origin
# Permissions-Policy: camera=(), microphone=(), geolocation=()
```

### SSRF 重定向防护验证

配置 `not.trust.host = 10.*` 后，如果外部文件服务器 302 重定向到 `http://10.0.0.1/...`，请求会被 `SafeRedirectStrategy` 拦截。

### 反序列化防护验证

RocksDB 缓存功能正常工作即表明白名单配置正确（正常业务只使用 HashMap/ArrayList/String/Integer）。

### 日志无密码泄露验证

```bash
grep -i password logs/kkFileView.log
# FTP 连接日志中不应出现密码
```

### 路径遍历编码绕过验证

尝试以下路径应被拦截：
- `%2e%2e%2f`（`../` 的 URL 编码）
- `%252e%252e%252f`（双重编码）
- `..%2f`（混合编码）

### addTask 时序攻击验证

密钥比较使用 `MessageDigest.isEqual()`，响应时间不随匹配位置变化。

---

## 安全事件监控

日志关键字告警建议：

| 日志关键字 | 含义 |
|-----------|------|
| `拒绝访问主机` | SSRF 黑名单/白名单命中 |
| `SSRF防护：阻止重定向到不信任主机` | 重定向 SSRF 攻击 |
| `反序列化白名单拒绝类型` | 反序列化攻击尝试 |
| `拒绝不合法的X-Base-Url header` | X-Base-Url 注入攻击 |
| `kk-proxy-authorization 中包含被禁止的请求头` | 请求头注入攻击 |
| `addTask接口密钥校验失败` | 未授权 API 调用 |
| `删除功能未启用` | 删除接口未配置密码 |
| `删除文件失败，密码错误` | 暴力破解删除密码 |
| `拒绝不支持的文件类型` | 恶意文件上传尝试 |

---

## 获取帮助

- GitHub Issues: https://github.com/kekingcn/kkFileView/issues
- Gitee Issues: https://gitee.com/kekingcn/file-online-preview/issues

**安全提示**：定期检查和更新信任主机列表，遵循最小权限原则。建议在生产环境中配合反向代理（Nginx/Caddy）和 WAF 使用，形成纵深防御。
