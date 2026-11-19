# CCOJ 微服务

| 应用 | 默认端口 | 职责 |
| --- | --- | --- |
| Gateway | 8080 | 公开路由、阻断内部路径 |
| User | 8000 | 账号管理、登录、共享会话、会话撤销 |
| Problem | 8001 | 题目、提交记录、判题任务发布与结果存储 |
| Judge | 8002 | RabbitMQ 消费、调用代码沙箱、判定输出 |
| Docker 沙箱 | 8081 | 从独立的 `ccoj-sandbox` 工程启动 |

构建使用 JDK 21 和 `mvn clean package`。启动前准备 MySQL、Redis、Nacos 和 RabbitMQ。新数据库可以执行 `sql/create_tables.sql`。已有 CCOJ 表可直接使用。

| 环境变量 | 使用方 | 作用 |
| --- | --- | --- |
| `NACOS_ADDRESS`、`NACOS_USERNAME`、`NACOS_PASSWORD` | 网关、用户、题目、判题 | 服务发现地址和凭证 |
| `CCOJ_DB_URL`、`CCOJ_DB_USERNAME`、`CCOJ_DB_PASSWORD` | 用户、题目 | 数据库连接 |
| `REDIS_HOST`、`REDIS_PORT` | 用户、题目 | 共享会话连接，两边使用相同的 Redis |
| `CCOJ_INTERNAL_KEY` | 用户、题目、判题 | 三个服务设置同一个随机内部密钥 |
| `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` | 题目、判题 | 消息队列连接 |
| `CCOJ_SANDBOX_URL` | 判题 | 独立沙箱地址，默认 `http://localhost:8081` |

对外只开放网关。业务服务与沙箱端口限制在可信内部网络，跨主机使用受保护的连接。沙箱默认仅监听本机。对外开放判题时，将 Docker 执行服务部署在与业务和数据库隔离的执行节点。

## 启动 Docker 沙箱

1. 启动 Docker Desktop 或 Docker Engine，再执行 `docker pull eclipse-temurin:21-jdk-jammy`。
2. 在独立的 `ccoj-sandbox` 工程中运行 `CcojSandboxApplication`。默认使用 Docker 模式，监听 `127.0.0.1:8081`。判题服务通过 `CCOJ_SANDBOX_URL` 调用，原有 `POST /execute` 地址继续有效。
3. 若 IDEA 找不到 Docker CLI，在沙箱启动环境中设置 `CCOJ_DOCKER_EXECUTABLE` 为 `command -v docker` 输出的完整路径。需要替换镜像时先手动拉取，再通过 `CCOJ_SANDBOX_IMAGE` 指定。镜像需要 Java 21 JDK 和常见 Linux 命令 `sh`、`timeout`、`tar`、`base64`。
4. 若要本机调试可信代码，可以显式设置 `CCOJ_SANDBOX_MODE=native`。

题目的 `judgeConfig.timeLimit` 以毫秒计，`judgeConfig.memoryLimit` 以 MB 计。Docker 模式的默认值为每用例 2 秒、256 MB，并将题目配置分别限制在 100～5000 毫秒和 64～512 MB。JVM 堆上限为容器内存上限的一半，容器内核报告 OOM 时记为 `MEMORY_LIMIT`。运行时 stdout、stderr 各限 64 KiB。每份提交最多 64 个用例，所有输入合计最多 256 KiB。

每份提交在容器里编译一次，每个用例在新的容器中执行。执行容器无网络、根文件系统只读、以非 root 用户运行，没有宿主目录或 Docker socket 挂载，结束后由沙箱服务清理。

## 在浏览器调试接口

在网关、用户服务和题目服务的运行配置中同时设置 `CCOJ_API_DOCS_ENABLED=true`，重启三个服务。打开 [Knife4j](http://localhost:8080/doc.html)，用页面的服务下拉框切换用户服务和题目与提交服务。默认关闭文档入口。

1. 在用户服务文档中打开 `POST /user/login`，切到 **调试** 标签，填写已有账号和密码，点击 **发送**。浏览器会保存登录响应中的 `CCOJ_SESSION` Cookie。
2. 在同一页面切换到题目与提交服务，执行 `GET /problem`、`POST /submission/judge` 或管理接口。调试请求通过同一网关地址发送，浏览器会自动携带 Session Cookie。管理接口需要用管理员账号登录。
3. 用 `GET /submission/{id}` 查看判题状态。测试完成后可以在用户服务文档中执行 `POST /user/logout`。

用户和题目服务通过 Springdoc 生成 OpenAPI 3.0 文档，网关使用 Knife4j 聚合展示。服务内部的 `/inner/**` 接口不出现在文档里。调试页面默认使用当前网关地址，登录和后续请求请使用同一个主机名，例如始终用 `localhost`。

## 验收流程

1. 通过网关注册账号。在新建环境中，用数据库管理账号将首个管理员的 `oj_user.user_role` 设置为 `ADMIN`，随后重新登录。
2. 调用 `POST /user/login` 保存 `CCOJ_SESSION`。旧格式的会话需要重新登录。用该 Cookie 请求用户和题目接口。
3. 管理员调用 `POST /problem/add` 创建草稿，再调用 `PATCH /problem/{id}/publish` 发布。公开题目列表与详情为 `GET /problem` 和 `GET /problem/{id}`。管理员可用 `GET /problem/admin`、`GET /problem/admin/{id}`、`PUT /problem/{id}`、`DELETE /problem/delete?id=...` 管理题目。
4. 登录用户调用 `POST /submission/judge`，提交 `problemId`、`language`（`JAVA`）和 `sourceCode`，从 `data` 取得提交 ID。用 `GET /submission/{id}` 查询结果。 `GET /submission?page=1&size=20` 查询提交列表。
5. 管理员通过 `PATCH /user/admin/{id}` 设置 `role`（`USER`、`ADMIN`）和 `status`（`0` 正常、`1` 禁用）。会话撤销后，受影响账号需要重新登录。用户管理列表为 `GET /user/admin`，删除为 `DELETE /user/admin/{id}`，本人资料更新为 `PATCH /user/me`。
6. 网关匹配的业务路径包含 `/inner` 段时返回 HTTP 403。内部端点需要 `X-CCOJ-Internal-Key`，该请求头由服务端客户端设置。客户端走网关，不发送内部凭证。

提交先写入 `PENDING` 和待投递时间，消费者原子领取后转为 `RUNNING`。终态为 `ACCEPTED`、`WRONG_ANSWER`、`COMPILE_ERROR`、`RUNTIME_ERROR`、`TIME_LIMIT`、`MEMORY_LIMIT`、`OUTPUT_LIMIT` 或 `SYSTEM_FAILED`。队列 `ccoj.judge` 只传提交 ID。等待超过一小时的提交标记为系统失败。运行超过五分钟的提交按剩余重试次数重新安排，旧轮次结果无法覆盖新轮次。

## 提交限流、沙箱熔断与判题重试

已有数据库需先在题目服务使用的库中执行 [提交重试迁移](sql/migrations/001_submission_retry.sql)。执行完 001 后，再执行 [失败通知迁移](sql/migrations/002_judge_failure_notice.sql)。已执行过的迁移无需重复运行。新库直接执行 `sql/create_tables.sql`。随后重新构建并一起重启题目服务和判题服务，两个服务使用同一版内部接口。网关单独重启即可。

| 环境变量 | 服务 | 默认值 |
| --- | --- | --- |
| `CCOJ_SUBMIT_LIMIT_ENABLED` | 网关 | `true` |
| `CCOJ_SUBMIT_QPS` | 网关 | 每实例每秒 2 次 |
| `CCOJ_SANDBOX_CIRCUIT_ENABLED` | 判题 | `true` |
| `CCOJ_JUDGE_RETRY_ENABLED` | 题目、判题 | `true` |

网关通过 Sentinel 分别限制提交、登录、注册和公开题目查询，超额返回 HTTP 429。登录、注册、题目查询的默认单实例 QPS 为 5、2、30，可通过 `CCOJ_LOGIN_QPS`、`CCOJ_REGISTER_QPS`、`CCOJ_PROBLEM_QUERY_QPS` 配置。提交结果查询不使用这些规则。判题服务对沙箱连接失败、HTTP 超时和 HTTP 5xx 计入熔断，30 秒内至少有 5 次调用且异常比例达到阈值时熔断 10 秒。普通判题结果会直接回写。熔断打开时的拒绝也可进入重试。

重试复用原提交 ID。第一次执行后最多重试三次，间隔至少 5、30、120 秒。题目服务在同一次条件更新中递增 `judge_attempt` 并保存 `retry_at`。定时任务到期后将消息发回 `ccoj.judge`，发布未确认时保留待发布时间。消息正文仍是提交 ID，轮次放在消息头，未携带轮次的旧消息按 0 处理。判题消费者在结果或下一轮计划写入成功后手动 ACK，过期或重复的消息在确认无需执行后 ACK。格式错误的消息拒绝，持久化操作失败时 NACK 且不立即重入队，数据库定时检查负责收尾或接管。领取和回写都校验当前轮次。

提交进入 `SYSTEM_FAILED` 时，题目服务在同一次数据库更新中记录待发通知。定时任务收到 RabbitMQ 发布确认后，才清除待发标记。运维工具可以消费持久队列 `ccoj.judge.failed`，消息包含提交 ID、轮次、状态和原因，使用 `judge-failed:<id>:<attempt>` 作为消息 ID 去重。在确认与清除标记之间发生故障时，同一通知可能再次投递。

验收时可以先用并发提交观察 429，再停止沙箱触发重试，并在重试耗尽前恢复。记录应沿用原提交 ID，且不会重复增加题目提交数。持续停机时，应在重试耗尽后转为 `SYSTEM_FAILED`。

## Docker Compose 启动

Compose 使用项目独立网络和数据卷，默认只在本机发布网关 `18080`。MySQL、Redis、RabbitMQ、Nacos 和四个应用由 Compose 启动。环境变量中的默认凭证用于本机开发。沙箱作为独立进程运行，Docker Desktop 上的判题容器通过 `host.docker.internal` 连接它。

在微服务工程根目录运行：

```bash
mvn clean verify
docker compose up -d --build --wait
docker compose logs -f gateway user problem judge
```

先按前面的沙箱说明启动独立沙箱，默认地址为 `http://host.docker.internal:8081`。如果使用 Linux Docker Engine 或把沙箱部署到独立节点，将 `CCOJ_SANDBOX_URL` 设置成判题容器可达的执行端地址。网关的 Knife4j 地址为 `http://127.0.0.1:18080/doc.html`。前端开发代理可以把目标指向这个网关端口。首次启动使用新数据卷时，MySQL 会自动执行建表脚本。既有默认 Compose 卷和本地业务库继续使用前面的增量迁移说明。

## 复现故障验收

需要 JDK 21、Python 3、Docker Compose，以及由独立沙箱工程打包的可执行 Jar。先拉取 `eclipse-temurin:21-jdk-jammy`。在微服务工程根目录运行：

```bash
mvn clean verify
CCOJ_GATEWAY_PORT=18180 CCOJ_SANDBOX_URL=http://host.docker.internal:18181 \
  docker compose -p ccoj-acceptance -f compose.yaml -f deploy/compose.acceptance.yaml up -d --build --wait
python3 scripts/acceptance.py --extended --sandbox-jar ../ccoj-sandbox/target/ccoj-sandbox-0.0.1-SNAPSHOT.jar
docker compose -p ccoj-acceptance -f compose.yaml -f deploy/compose.acceptance.yaml down
```

脚本只操作名为 `ccoj-acceptance` 的测试环境，自行创建测试账号、题目和提交。它启动本机的 Docker 沙箱，验证会话撤销、题目与提交权限、正确答案、编译错误、答案错误、运行错误、用户代码超时、并发限流、沙箱短暂停机后的同 ID 重试、RabbitMQ 恢复后补发首次任务，以及提交数和通过数。脚本退出时会停止它启动的沙箱并恢复它停止的 RabbitMQ。测试数据留在独立数据卷，便于检查。

首次提交和重试都把待投递时间写在提交记录中，再由题目服务每秒扫描。RabbitMQ 未确认时记录保持待投递。在确认后清除待投递时间的过程中，可能重复发出同一轮消息，领取条件会过滤重复执行。

本地验收记录（2026-11-19）。8 个并发提交中 2 个放行，6 个返回 429，数据库只增加了 2 条。沙箱短暂停止后原提交在轮次 1 通过。RabbitMQ 暂停期间首次提交保持待投递，恢复后轮次 0 通过。测试题共 6 条提交、5 条通过。并发验收发现的 MySQL 外键锁死锁已修正，提交事务先按题目行更新计数，再插入提交记录。该结果只对应本机验收用例。

`--extended` 会按默认间隔等待重试耗尽，检查 RabbitMQ 失败通知，并用过期 `RUNNING` 数据验证旧轮次回写与自动接管，运行时间约 3–5 分钟。若复用旧的验收卷，先执行尚未应用的迁移。验收覆盖文件将题目服务和 RabbitMQ 管理端口绑定到本机，供脚本验证内部回写和通知队列。

k6 查询、提交压测的启动命令与本机结果见 [故障验收与压测报告](performance/README.md)。
