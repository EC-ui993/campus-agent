# 阶段 6 Task 3 总结：Python + FastAPI 复刻最小闭环（2026-10-25）

> 独立仓库：<https://github.com/EC-ui993/campus-agent-py>（Public）
> 成果：**单文件 131 行**跑通「收消息 → LLM 抽取 → 入库 → 带库内容问答」
> 本篇记**概念与踩坑**，操作见仓库 README。

## 一、venv ↔ Maven：隔离的是不同层面的东西

**这是 Java 程序员转 Python 的第一个认知断层。**

### Maven 怎么解决依赖冲突

```
pom.xml 声明  →  从 ~/.m2/repository 取  →  拼出本项目专属的 classpath
```

`~/.m2/repository` 是**全局共享**的 ✓，冲突靠**坐标 + 版本**在 **classpath（逻辑层）** 上解决——A 项目用 jackson 2.17、B 项目用 2.15，各取各的 ✓。

### Python 默认怎么装包

`pip install x` 装进 Python 安装目录下的 `site-packages`，**全局共用** ✗。两个项目要不同版本就打架 ✗。

Python 没有"每项目一份 classpath"，所以用**另一个办法**：给每个项目**一套独立的安装目录**——`venv`。

| | Java / Maven | Python / pip + venv |
|---|---|---|
| 依赖声明 | `pom.xml` | `requirements.txt` |
| 包仓库 | `~/.m2/repository`（全局） | PyPI（远端）→ `site-packages` |
| **隔离方式** | **逻辑隔离**：每项目一份 classpath | **物理隔离**：每项目一份 `site-packages` |
| 隔离工具 | Maven 自带 | **`venv`（要自己建）** |

> **一句话**：Maven 隔离 **classpath**，venv 隔离 **安装目录**。所以 Python 项目**必建 venv**，不建就是在污染全局环境。

补充两条：

- `.venv` 自带一个内嵌 `.gitignore`（内容 `*`），所以它不会误提交 ✓；但自己再写一遍更稳妥
- 激活只是改 PATH 的便利；不激活也能用 `.venv\Scripts\python.exe -m pip ...` ✓

## 二、类型注解：默认没有执行力，被框架"赋予"执行力

```python
def add(a: int, b: int) -> int:
    return a + b

add("1", "2")     # 不报错，返回 "12"
```

**同样的事在 Java 里编译期就挂。** Python 的注解**不参与运行时检查** ✗ ——它只是"标注"。

三个用途，重要性递增：

| 用途 | 谁在读 |
|---|---|
| 可读性 | 人 |
| 静态检查 | `mypy`（可选装，不装就没有） |
| **校验 + 文档生成** | **Pydantic / FastAPI** |

> **注解是被动数据，框架提供执行力。** 你写 `req: ChatRequest`，校验却发生了——因为是 Pydantic **主动去读** `__annotations__`。

## 三、装饰器 ≠ Java 注解

```python
@app.get("/")
def hello(): ...
```

**等价于**：

```python
def hello(): ...
hello = app.get("/")(hello)
```

**装饰器是"函数调用"的语法糖，在定义时立刻执行。**

| | Java 注解 | Python 装饰器 |
|---|---|---|
| 本质 | **元数据** | **函数调用** |
| 生效时机 | 框架**反射扫描**到才生效 | **定义那一刻**就跑了 |
| 没有框架时 | 什么也不发生 | 已经执行完了 |

**这解释了 FastAPI 的路由登记时机**：uvicorn import 模块时，`@app.get` 就把路由写进了 `app` 对象——**没有"扫描"阶段**。

## 四、Pydantic：声明式校验

Java 里做一个带校验的接口要：DTO + Jackson + `@Valid` + `@NotNull` + 异常处理器。
Python 只要一个类 + 类型注解：

```python
class ExtractedItem(BaseModel):
    type: Literal["assignment", "exam", "todo", "event"]
    title: str
    content: str | None = None
```

| 写法 | 含义 | Java 对应 |
|---|---|---|
| `Literal[...]` | 只能是这几个值 | `enum` |
| `str \| None = None` | 可空，默认 null | `String` + 允许 null |
| `title: str` | **必填** | `@NotNull String` |
| `model_validate_json(text)` | 解析 **并** 校验，一步 | `fromJson()` + `validate()` 两步 |

**关键差异**：Java 的 `validate()` 是**手写**的；Python 的校验规则**就是字段声明本身**。

### 错误结构设计得很值得看

故意把 `Literal` 改成 `["sales"]` 后，返回：

```json
{"type": "literal_error", "loc": ["type"], "msg": "Input should be 'sales'",
 "input": "assignment", "ctx": {"expected": "'sales'"}, "url": ".../literal_error"}
```

| 字段 | 给谁 | 价值 |
|---|---|---|
| `type` | **程序** | 可据此决定"要不要重试" |
| `loc` | 程序 + 人 | 错误**位置路径**；嵌套会是 `["body","items",0,"title"]` |
| `msg` | 人 | 一句话看懂 |
| **`input`** | 人 | **模型实际给了什么**——就是"失败原文"，对应 Java 版的 `raw_inbox` |
| `ctx` | 程序 | 结构化参数 |
| `url` | 人 | 指到**你这个版本**（2.14）的文档 |

> **手写 `validate()` 只会给出"给人看的一条字符串"** ✗ ——因为手写时你只想着"我自己看得懂"。框架天生服务**两类消费者：人和程序** ✓。

## 五、参数化查询的本质：不是"更好地转义"

```python
conn.execute("INSERT INTO items (type, title) VALUES (?, ?)", (item.type, item.title))
```

**它的机制是三步：**

```
① 先把模板发给数据库："INSERT INTO items (...) VALUES (?, ?)"
   → 数据库此时就知道：这是 SQL，两个位置待填
② 再把值单独送过去：("assignment", "高数")
③ 数据库把值填进已经编译好的模板
```

**值从头到尾没被当成 SQL 文本解析过** ✓ ——所以 `'` 不是引号、`--` 不是注释、`;` 结束不了语句、**注入不可能发生** ✓。

| | 做法 | 本质 |
|---|---|---|
| 拼字符串 | 值**拼进** SQL，再努力让它不被当语法 | 先污染再补救 ✗ |
| **参数化** | 值和 SQL **分开传送** | **压根不给它被误认的机会** ✓ |

> 做 SQLite → MySQL 数据迁移时手写 `\` 双写、`'` 双减——那是第一条路的代价。参数化一个字都不用写。

## 六、踩坑记录

### 坑 1：把示意代码里的 `...` 整段粘进去

我给的示意片段里有 `conn.execute(...)`，本意是"这里省略"。粘进项目后**不报语法错** ✗ ——因为 **`...` 在 Python 里是合法字面量**（`Ellipsis`），只在**运行时**炸：

```
TypeError: execute() argument 1 must be str, not ellipsis
```

**教训**：`...`、`pass`、`Ellipsis` 这类东西语法合法但语义空洞；示意代码应该用 `# ...` 注释，而不是合法的字面量。**"不报错"不等于"能跑"**。

### 坑 2：`ModuleNotFoundError` 的三步排查

| # | 命令 | 看什么 |
|---|---|---|
| 1 | `python -m pip show <包>` | 装了没有 |
| 2 | `python -c "import sys; print(sys.executable)"` | 路径里有没有 `.venv` |
| 3 | 对照包名 | **安装名 ≠ import 名** |

**安装名与 import 名不同的常见例子**：`pillow`→`PIL`、`pyyaml`→`yaml`、`python-dotenv`→`dotenv`、`beautifulsoup4`→`bs4`。

顺带：`--reload` **只监听源代码文件**，装包不会触发重启 ✗ ——装完必须手动重启。

### 坑 3：漏掉 `commit()` 是静默失败

`sqlite3` **默认不自动提交**。漏了 `commit()`：接口返回 `ok: true`，**库里却是空的** ✗。

> **这个 bug 特别难发现，因为唯一的判据是"去查库"**——返回值、日志、页面全都正常。
> 这就是"看产物，不看返回值"的又一处实践。

### 坑 4：`dict(tuple)` 不会给你列名

```python
python -c "... [print(dict(r)) for r in c.execute('select ...')]"
# TypeError: cannot convert dictionary update sequence element #0 to a sequence
```

`dict()` 只吃**键值对序列**（`[('a',1)]`）。不设 `row_factory` 时每行是**普通元组**，`dict()` 会试图把第一个元素拆成一对 → 报错。

**正解**：`conn.row_factory = sqlite3.Row` 之后 `dict(r)` 才能给出列名 ✓。

### 坑 5：`closing` 和 `with conn` 管的是两件事

```python
with closing(db()) as conn:     # ← 管"用完关闭连接"
    with conn:                  # ← 管"事务"：成功 commit，异常 rollback
        ...
```

**`with conn:` 不会关闭连接** ✗ ——这是 Python 里一个经典误解。

## 七、Java / Python 速查

| Java | Python |
|---|---|
| `true / false / null` | `True / False / None` |
| `&& \|\| !` | `and or not` |
| `int x = 5;` | `x = 5` |
| `for (String s : list)` | `for s in lst:` |
| `list.get(0)` | `lst[0]` |
| `map.put(k,v)` / `map.get(k)` | `d[k] = v` / `d.get(k)` |
| `"a" + b` | `f"a{b}"` |
| `throw new X()` | `raise X()` |
| `try/catch/finally` | `try/except/finally` |
| `stream().map(...).collect(joining())` | `"".join(... for x in xs)` |
| `Optional.ofNullable(x).orElse("无")` | `x or "无"`（注意 `0`/`""` 也是假值） |

## 八、自测

1. `venv` 隔离的是什么层面？Maven 隔离的是什么层面？
2. `def f(x: int)` 里的 `int` 在 `f("abc")` 时起作用吗？那 FastAPI 里的类型注解为什么起作用？
3. 装饰器和 Java 注解在**生效时机**上有什么区别？
4. 参数化查询为什么不需要转义？它和"把值拼进 SQL"在底层差在哪？
5. 漏掉 `commit()` 会出现什么现象？为什么这个 bug 特别难发现？
6. `with conn:` 和 `with closing(conn):` 分别管什么？

## 九、下一步

阶段 6 全部结束（Task 1 MySQL 迁移 / Task 2 Compose / Task 3 FastAPI 复刻）。
下一步：阶段 7 —— Agent 工具调用 Demo（Spring AI / LangChain4j 二选一）。
