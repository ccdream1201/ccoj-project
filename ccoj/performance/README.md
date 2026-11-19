# CCOJ 故障验收与 k6 压测

使用项目 README 的独立 Compose 环境，运行 `scripts/acceptance.py --extended` 验证真实 MySQL、RabbitMQ、Redis、Nacos 和 Docker 沙箱。验收脚本会在结束后关闭它启动的沙箱。跑提交压测时，需要重新启动沙箱，并选一个已发布的两数之和题目，固定同一题目、同一登录用户和同一份 Java 代码。

```bash
docker run --rm --network ccoj-acceptance_default \
  -e CCOJ_BASE_URL=http://gateway:8080 \
  -v "$PWD/performance:/work" grafana/k6:1.4.0 \
  run --summary-export /work/results/query.json /work/ccoj.js
```

将 `CCOJ_ACCOUNT`、`CCOJ_PASSWORD` 和 `CCOJ_PROBLEM_ID` 设为测试环境中对应的值，再运行提交场景：

```bash
docker run --rm --network ccoj-acceptance_default \
  -e CCOJ_BASE_URL=http://gateway:8080 -e CCOJ_SCENARIO=submit -e CCOJ_VUS=8 \
  -e CCOJ_ACCOUNT -e CCOJ_PASSWORD -e CCOJ_PROBLEM_ID \
  -v "$PWD/performance:/work" grafana/k6:1.4.0 \
  run --summary-export /work/results/submit-protected.json /work/ccoj.js
```

对照组使用相同参数，先以 `CCOJ_SUBMIT_LIMIT_ENABLED=false` 重新创建隔离网关，再运行一次，输出文件改为 `submit-baseline.json`。重新创建命令用 README 中的 Compose 参数，加上 `up -d --no-deps gateway`。比较结束后恢复开关。脚本把符合契约的 HTTP 429 和失败分开统计，已接收的提交会轮询到终态。提交至终态的指标包含客户端轮询间隔。

## 本机故障验收

| 场景 | 结果 |
| --- | --- |
| 正常判题 | 正确代码通过，错误语法返回编译错误，两者轮次均为 0 |
| 八路并发 | 2 条放行并通过，6 条 HTTP 429，未入库 |
| 沙箱短暂停机 | 原提交 ID 在轮次 1 通过 |
| RabbitMQ 停机 | 首次提交计划保留在 MySQL，恢复后轮次 0 通过 |
| 沙箱持续停机 | 完整等待 5、30、120 秒重试，轮次 3 进入 SYSTEM_FAILED，失败队列收到 RETRIES_EXHAUSTED |
| 过期轮次回写 | 已过期的轮次 0 结果没有覆盖轮次 1 的夹具，超过 RUNNING 期限后由轮次 2 接管并通过 |

## 本机压测记录

2026-11-19，在 Apple Silicon Mac 的 Docker Desktop 上以单网关、单判题消费者和真实 Docker 沙箱运行。k6 版本为 1.4.0。每组只执行一轮。

| 场景 | 负载 | 放行并通过 | HTTP 429 | 非预期结果 | P95 |
| --- | --- | ---: | ---: | ---: | --- |
| 公开题目查询 | 5 VU，10 秒，190 次请求 | 190 次查询成功 | 0 | 0 | HTTP 31.35 ms |
| 提交限流开启 | 8 个 VU 各提交一次 | 3 | 5 | 0 | 提交到观察到终态 2.97 s |
| 关闭提交限流 | 8 个 VU 各提交一次 | 8 | 0 | 0 | 提交到观察到终态 7.00 s |

[查询原始摘要](results/query.json) · [开启提交限流](results/submit-protected.json) · [关闭提交限流](results/submit-baseline.json)

这轮对比展示了突发提交的放行与拒绝行为。两轮进入判题的工作量不同，P95 差异包含队列等待、沙箱执行和轮询延迟，无法据此估计吞吐容量。另一次 Python 并发验收收到 6 个 HTTP 429，说明两种施压方式的放行数量并非固定。k6 脚本将有效的 429 当作预期结果。

`mvn clean verify` 的原有 7 项测试覆盖网关限流、结果查询放行、沙箱熔断和消费确认顺序。真实故障验收另外覆盖轮次条件更新和失败通知。

失败通知是至少一次投递。消费者应使用消息 ID 去重。消息处理期间若题目服务持续不可用，消费者 NACK 且不立即重入队。已领取任务由超时扫描接管，尚未领取且已确认投递的任务由等待超时收尾。当前验收没有模拟 RabbitMQ 集群故障。

## 扩展回归与负载矩阵

Maven 回归现有 20 项用例，新增会话身份校验及判题结果分类，覆盖换行规范化、部分用例通过、编译诊断截断、资源超限、运行错误和不完整沙箱响应。

在真实 Redis 与 Docker 沙箱的验收中，匿名用户和普通用户无法出题，草稿对外不可见，普通用户无法读取他人的提交，禁用后旧 Cookie 随即失效。答案错误、运行异常和运行超时均直接结束于轮次 0。本次验收题最终为 9 条提交、5 条通过。耗尽重试和超时接管仍由 `--extended` 覆盖。

按上文启动隔离 Compose 和沙箱，设置测试账号、密码和题目 ID 后，执行以下命令：

```bash
bash performance/run-matrix.sh
```

脚本依次运行 5、20、50 VU 查询，以及每秒 1 条的持续提交、20 VU 查询和每秒 1 条提交的混合负载，各阶段持续 10 秒。结果默认写入 `performance/results/local`。查询放行耗时、提交接口耗时、结果轮询耗时和提交到观察到终态的耗时分别记录。预期 429 独立计数，非预期结果、轮询错误和计划提交的丢失会使对应场景失败。

| 负载 | 查询放行 / 429 | 查询放行 P95 | 提交通过 / 429 | 提交接口 P95 | 结果轮询 P95 | 提交到观察到终态 P95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 5 VU 查询 | 185 / 0 | 48.17 ms | — | — | — | — |
| 20 VU 查询 | 315 / 465 | 32.05 ms | — | — | — | — |
| 50 VU 查询 | 330 / 1,670 | 51.37 ms | — | — | — | — |
| 持续提交 | — | — | 11 / 0 | 23.20 ms | 16.07 ms | 2.09 s |
| 20 VU 查询与持续提交 | 321 / 479 | 24.16 ms | 11 / 0 | 32.58 ms | 17.16 ms | 2.36 s |

[5 VU 查询](results/matrix/query-5.json) · [20 VU 查询](results/matrix/query-20.json) · [50 VU 查询](results/matrix/query-50.json) · [持续提交](results/matrix/sustained.json) · [混合负载](results/matrix/mixed.json)

结果来自 2026-11-19 的本机运行，单网关、单判题消费者，保留默认限流。五个场景均无非预期结果，持续和混合场景均无丢弃迭代。查询并发升高时，更多请求收到预期 429。每个场景各采一轮，提交样本各 11 条，P95 用于记录本次响应表现。
