# 阶段 6 Task 2 总结：Docker Compose 全家桶（2026-10-19）

> 验收全绿：一条 `docker compose up -d` 拉起 nginx + app + mysql，浏览器流式正常，手机同 WiFi 可访问。
> 操作手册见 [`docs/部署文档.md`](../部署文档.md)；本文记**概念与踩坑**。

## 今日范围

| 文件 | 说明 |
|---|---|
| `docker-compose.yml` | 新增，三个服务 |
| `docker/nginx.conf` | 新增，反代 + SSE 关键配置 |
| `src/main/resources/application.yml` | `url` 改为 `jdbc:mysql://${DB_HOST:localhost}:3306/agent?...` |
| `docs/部署文档.md` | 新增，步骤 + 架构图 + 排障表 |

结果：镜像重建（阶段 5 那个 109MB 镜像里是 **SQLite 时代**的代码，必须重打）；`docker compose up -d --build` 一次起全栈；最终入口 `http://<局域网IP>:8088`。

## 一、心智转变：命令式 → 声明式

以前起全栈要敲一串命令，每条都在描述"**怎么起**"：

```powershell
docker run -d --name agent-mysql -e ... -p 3306:3306 -v ... mysql:8
mvn spring-boot:run
```

Compose 改成一份 YAML 描述"**最终要什么样**"，然后 `docker compose up -d` / `down` 起停。

> **命令会过时、会被记错；文件不会。** 你维护的不再是"一串命令 + 一份说明"，而是**一份可执行的文件**。

术语：yml 里定义的每一项叫 **service**；启动后每个 service 变成**一到多个容器**。容器名带**项目名前缀**（默认取目录名）——本项目是 `myagent-app-1`、`myagent-mysql-1`、`myagent-nginx-1`。

## 二、服务名就是主机名（Compose 内部网络）

Compose 自动建一个内部网络把所有服务接进去。在里面，**每个服务可以用自己的「服务名」当主机名**：

```
nginx  →  http://app:8080
app    →  mysql:3306
```

所以 `docker-compose.yml` 里的 `DB_HOST=mysql`、`nginx.conf` 里的 `proxy_pass http://app:8080`，都不是随便写的名字。

### ⚠️ 由此推出：`localhost` 在容器里是"这个容器自己"

这是把应用塞进容器时**必踩的第一坑**。

- 宿主机直跑：`jdbc:mysql://localhost:3306/agent` ✓
- 应用进容器后：`localhost:3306` 指的是**应用容器自己**，那里没有 MySQL ✗

**解法：把主机名做成变量，同一份配置适应两种环境**

```yaml
url: jdbc:mysql://${DB_HOST:localhost}:3306/agent?...
```

- 本地开发：不设 `DB_HOST` → 默认 `localhost` ✓
- Compose：注入 `DB_HOST=mysql` ✓

> **可复用判断**：**任何"地址/密码/开关"都不该写死在配置文件里**。写死就意味着"换个运行环境就得改代码"，而改代码就意味着"本地能跑、部署崩"。

## 三、`depends_on` 不等于"对方准备好了"

```yaml
app:
  depends_on: [mysql]          # 只保证"先起 mysql 容器"
```

**它不等 MySQL「能用」。** MySQL 容器起来后还要十几秒初始化（建数据目录、建库、建用户），这期间 app 已经启动、去连数据库 → 连接被拒 → 崩溃退出。

**完整的三件套：**

```
healthcheck                  定义"怎么算能用"
+ condition: service_healthy  让 depends_on 等到这个状态
+ depends_on                  保证启动顺序
```

```yaml
mysql:
  healthcheck:
    test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD --silent"]
    interval: 5s
    retries: 20
    start_period: 30s
app:
  depends_on:
    mysql:
      condition: service_healthy
```

两个细节：

- **`$$` 是转义**：让 `$$MYSQL_ROOT_PASSWORD` 交给**容器内的 shell** 展开，这样密码不必写进 yml
- **用 `-h 127.0.0.1` 走 TCP**，而不是默认的 socket——初始化期间 MySQL 会先起一个**只有 socket 的临时实例**，走 TCP 才能避免"误判为健康"

> **诊断指纹**：没配健康检查时，症状不是"一直起不来"，而是「**第一次 `up` 必崩，再 `up` 一次就好了**」——因为第二次 MySQL 已经初始化完了。看到这个"再试一次就好了"，先想到健康检查。

## 四、`ports` 是"对宿主机的口子"，不是"容器间的通道"

```yaml
nginx:
  ports: ["8088:80"]     # 唯一对宿主机开的口子
mysql:                   # 没有 ports
app:                     # 没有 ports
```

`docker compose ps` 里能直接看到差别：

```
myagent-app-1     8080/tcp                      ← 只有"暴露"，宿主机够不着
myagent-mysql-1   3306/tcp, 33060/tcp           ← 同上
myagent-nginx-1   0.0.0.0:8088->80/tcp          ← 真的映射到宿主机
```

- `8080/tcp` = 容器内部监听，外部不可达
- `0.0.0.0:8088->80` = 宿主机 8088 映射进容器 80

**所以数据库完全藏在内部网络里**，攻击面只剩 nginx 一个入口。这不是"顺手省事"，是安全边界。

顺带：`8088:80` 的**左边是宿主机、右边是容器**。容器里 nginx 仍然监听 80，我们只是换了个对外窗口——**所以换宿主机端口不需要改 `nginx.conf` 一个字**。

## 五、反代 SSE：`proxy_buffering off` 必关

nginx 默认会把上游响应**攒进缓冲区**再发。普通页面无所谓，但对 SSE（`/api/chat/stream` 的打字机输出）是致命的：

> 一旦被缓冲，前端就变成"等十几秒，然后**整段一次性蹦出来**"。

```nginx
proxy_buffering off;
proxy_cache off;
proxy_set_header Connection "";
proxy_read_timeout 300s;      # SSE 是长连接，超时给足
```

**这个症状和"后端没做流式"一模一样**——你会去查后端的 `SseEmitter`，其实问题在代理层。

> **由此得出一条验证纪律**：**"页面能打开"不能证明反代配对了。** `proxy_buffering` 只影响流式输出，页面和普通请求全都正常。**必须真的发一条消息，看打字机效果。**

## 六、两个真实踩坑

### 坑 1：宿主机 80 端口被别的进程接走了（最费时的一个）

**现象**：

| 访问方式 | 结果 |
|---|---|
| `http://localhost`（回环） | ✅ 页面正常、流式正常 |
| `http://10.195.97.190`（网卡） | ❌ 404 |

排查过程与结论：

1. 先怀疑防火墙 → 放行了，没用
2. 再怀疑校园网客户端隔离 → 但**自己电脑连自己 IP 也不通**，隔离不成立（隔离是"别的设备连不上"）
3. **决定性证据：响应头里没有 `Server: nginx`** ✗
   nginx **一定**会发 `Server: nginx/...`。没有这个头，说明**应答的根本不是 nginx**。
4. 查监听者：

```powershell
Get-NetTCPConnection -LocalPort 80 -State Listen | Select-Object LocalAddress, OwningProcess
```

```
::1  :80  pid 42508     ← IPv6 回环
::   :80  pid 82056     ← IPv6 任意地址（双栈，抢走了 IPv4 连接）
0.0.0.0 :80 pid 95128   ← 这个才是 Docker
```

Windows 上绑定 `::` 的 socket 可能同时接受 IPv4 连接，于是发往网卡地址的连接被它接走；而 `localhost` 走回环、落到另一条路——**两边行为不同，全对上了**。

**解法：给 nginx 换个宿主机端口**（`8088:80`）。

> **教训**：**宿主机 80 作为开发项目的默认端口是个坏选择**——Windows 上容易被系统服务/工具抢占，而且可能"`up` 不报错，连接却被抢走"。
> **另一个教训**：`localhost` 能通**不代表**服务对外可达。两者走的是**不同的网络路径**（回环 vs 网卡），防火墙、端口占用、网关策略都只影响后者。**验证对外可达性，必须用局域网 IP。**

### 坑 2：PowerShell 5.1 传参时会丢掉内嵌双引号

```powershell
docker compose exec -T mysql sh -c 'mysql -uroot -p$P -e "USE agent; SHOW TABLES;"'
# ERROR at line 1: USE must be followed by a database name
```

`sh` 实际收到的是 `... -e USE agent; SHOW TABLES;`——**引号被 PowerShell 吃了**，SQL 被按空格拆开，mysql 只拿到 `-e USE`。

**解法**：别用 `sh -c` 套内嵌双引号，直接把参数一个个传：

```powershell
docker compose exec -T mysql mysql -ucampus -p<pwd> agent -e "SHOW TABLES;"
```

（PowerShell 7 修了这个问题；5.1 上要绕。同类场景还有 `docker build` 的相对路径、`<` 重定向要用 `cmd /c`——**都是"PowerShell 只懂自己的语法，别的语言的东西进来都得先过它这一关"**。）

## 七、最危险的失败：镜像里是旧代码

编排前那个 `campus-agent` 镜像（109MB）是**阶段 5 构建的**，里面的 jar 是 **SQLite 时代**的代码。

**如果直接拿它编排**：容器启动 → 旧应用连容器内 SQLite → 自己建表 → 页面能开、聊天能用 → **而 MySQL 从头到尾没被碰过**。你会以为"compose 起来了"。

> **这是最坏的一类失败：不报错，只是跑了旧版本。**
> 判据：去 `mysql` 服务里看有没有你的表；没有就是跑错了代码。
> 解法：`docker compose up -d --build`（改了代码永远带 `--build`）。

## 八、自测（能答上来才算真懂）

1. `docker-compose.yml` 里 app 连数据库，主机名写什么？为什么不能写 `localhost`？
2. `depends_on: [mysql]` 和 `depends_on: {mysql: {condition: service_healthy}}` 差在哪？少了后者有什么诊断指纹？
3. 为什么 mysql 服务**不写** `ports`？`8080/tcp` 和 `0.0.0.0:8080->8080` 有什么区别？
4. nginx 不加 `proxy_buffering off` 会出现什么现象？这个现象容易和什么混淆？
5. `localhost:8088` 能通，能推出"手机也能访问"吗？为什么？

## 九、下一步

Task 3：**Python + FastAPI 复刻最小闭环**——独立仓库 `campus-agent-py`，全新栈，从"Python 与 Java 的对照"重新开讲。

注意：Compose 起的是**第二个 MySQL 实例**（独立空卷），`agent`（本地那套）也是独立实例。**改 schema 时要记得两边都对齐**——这就是"该上 Flyway"的信号，仍待观察。
