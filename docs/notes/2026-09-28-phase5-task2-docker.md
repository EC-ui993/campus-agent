# 阶段 5 Task 2：Docker 打包本项目（2026-09-28）

> 状态：完成——镜像 **109MB**，容器跑通（8081 端口问答正常），数据卷持久化验证通过，全量测试 82 绿

## 今日范围

| 文件 | 类型 | 说明 |
|---|---|---|
| `Dockerfile` | 新建 | 多阶段构建（builder + runtime） |
| `.dockerignore` | 新建 | 构建上下文排除（target/data/.git/docs…） |
| `.gitignore` | 改 | +`.env` |
| `pom.xml` | **修复** | 补 `spring-boot-maven-plugin`（见下方"重大发现"） |

## 知识点

### 1. Docker 四概念 + 多阶段收益（实证）
- **镜像**：只读模板（程序 + 运行环境）；**容器**：镜像的运行实例（可启停删）
- **Dockerfile**：构建说明书；**多阶段**：builder 阶段用 Maven+JDK 编译，runtime 阶段只留 JRE
- **实证**：单阶段约 500MB+ → 多阶段 **109MB**（编译工具/源码/依赖缓存都不进最终镜像）

### 2. 层缓存机制（实证）
- Dockerfile 每条指令 = 一层，**没变就复用缓存**
- `COPY pom.xml` 放 `COPY src` 之前 → 改代码只重跑编译层，**不重下依赖**
- **实证**：改 pom 重建 → 依赖层失效 → 全量重跑（依赖层之前的所有层都命中缓存）

### 3. 构建时 vs 运行时（配置分层）
| 配置 | 层级 | 位置 |
|---|---|---|
| 代码 / 依赖 / 时区默认值（`ENV TZ`）/ `EXPOSE` | 构建时 | **Dockerfile** |
| **密钥** | 运行时 | `--env-file .env`（**绝不可写进 Dockerfile**——镜像会公开） |
| **数据卷** | 运行时 | `-v agent-data:/app/data` |

### 4. 数据卷 = 容器无状态、数据外置（实证）
- 删容器 → 用同一个卷重新 run → **数据还在** ✅
- 不挂卷 → 容器一删，`/app/data` 随之销毁
- 挂载路径必须是 **`/app/data`**（DB 是相对 `WORKDIR /app` 的 `data/agent.db`；计划里写的 `/data` 是错的）

### 5. 【重大发现】开发能跑 ≠ 能部署
- **现象**：容器启动崩溃 `no main manifest attribute, in app.jar`
- **根因**：pom 没声明 `spring-boot-maven-plugin` → `mvn package` 只产出**普通 jar**（无 `Main-Class`）
- **为什么藏了 5 个阶段**：本地一直用 `mvn spring-boot:run`（插件直接跑 main 类，**不需要可执行 jar**）——直到 Docker 里 `java -jar` 才暴露
- **修法**：pom 加插件声明（parent 的 `pluginManagement` 预配了 repackage，声明即继承）
- **验证**：jar 体积 几百 KB → **61.7MB**（依赖打进去了），MANIFEST 含 `Main-Class`(JarLauncher) + `Start-Class`(CampusAgentApplication)
- **面试价值**：能讲清"开发运行方式与部署产物的差异""fat jar / repackage 的作用"

## 排障记录（Docker 网络，占了今天大半时间）

1. **Docker daemon 不读 Windows 系统代理**——"开了代理"≠ Docker 走代理（daemon 是独立服务进程）；WSL2 下 `127.0.0.1` 指向 WSL 自己，要用 `host.docker.internal`；且代理软件须开 **Allow LAN**（否则 WSL 连不进去）
2. **Docker Desktop 会覆盖 daemon.json 的 `proxies`**——`docker info` 显示的是它内置的 `http.docker.internal:3128`
3. **最终解法：镜像加速器**（`registry-mirrors`: docker.1ms.run / daocloud / dockerproxy.net）→ `docker info` 验证生效 → 拉镜像成功
4. **`.env` 不能带引号**——Docker `--env-file` **原样取值**（引号会变成值的一部分）；而 PowerShell 的 `$env:X="..."` 会剥引号（**同名文件/不同工具，规则不同**）
5. **`docker build` 末尾的 `.` 是"构建上下文"**，不是 Dockerfile 路径；不在项目目录跑会报 `failed to read dockerfile: no such file or directory`
6. **PowerShell here-string 粘贴事故**：`@'...'@` 是**命令语法**，误当成文件内容粘进 `daemon.json` → JSON 语法错误（教训：给命令要说明"这是命令、不是文件内容"）

## 命令速查

```powershell
docker build -t campus-agent .            # 构建（末尾 . = 构建上下文）
docker run -d --name campus-agent -p 8081:8080 --env-file .env -v agent-data:/app/data campus-agent
docker ps / docker ps -a                  # 运行中 / 全部
docker logs campus-agent                  # 日志
docker exec campus-agent ls -la /app/data # 容器内执行命令
docker rm -f campus-agent                 # 删容器（卷保留）
docker info | Select-String Proxy         # 查代理
docker info | Select-String "Registry Mirrors" -Context 0,4   # 查加速器
```

## 下一步
- Task 3：量化指标（代码行数 / 测试数 / 抽取准确率 / 接口延迟 → README「数据」节）
- Task 4（GitHub 收尾）**已完成**（阶段 5 前已发布 + 历史清理）
