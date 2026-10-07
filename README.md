# workHelper

个人本地求职投递管理。已完成基础、投递管理与流程安排：投递增删改查、公司和岗位展开、流程录入、时间安排和当前阶段自动计算。

## 已实现

- 公司按去除首尾空格后的名称分组；每次投递使用独立 ID，相同公司、岗位也允许多次投递。
- 公司、岗位必填，投递时间默认当前业务时间，其余信息可补充。编辑不会修改创建时间。
- 公司 / 岗位 / 地点搜索，按投递时间倒序排列；公司和岗位分别展开。
- 弹窗新增 / 编辑、删除确认、空状态、加载和错误提示；服务保存失败保留表单。
- 岗位下添加、编辑、删除流程，支持定时安排、截止任务、仅记录阶段；测评和笔试均可选择时间方式。
- 安排支持完成、取消、恢复待完成和改期。仅记录阶段明确显示“未设置提醒”。
- 当前阶段自动计算：普通阶段取最高进度；Offer、未通过、已撤回优先，按阶段发生时间选择最新结果（同一时间取较大 ID）。
- 取消安排保留招聘阶段；删除流程重算阶段。补录早期流程不会导致进度倒退，标记完成不会新增下一阶段。
- 一面、二面、三面支持自定义轮次名称，进度仍由所选阶段决定，例如“HR 面”可归属于二面。
- MySQL 持久化、Flyway 版本化迁移、健康检查、统一配置、前后端打包成单个 JAR。

首页提醒、面经和备份恢复属于后续阶段。首页导航目前明确显示功能待开放，不展示虚假待办。

## 环境

- Java 21，MySQL 8.0+（本次真实联调版本为 8.0.31）。
- 构建需要 Node.js 22.12+ 或 24，以及 npm；日常运行只需要 Java 和 MySQL。
- 保留项目原有 Spring Boot 4.1.1，使用 Spring JDBC、Flyway；Vue 3、TypeScript、Vite、Element Plus、Vue Router。前端精确依赖由 `package-lock.json` 锁定。

## 首次运行

1. 启动自己的 MySQL 服务，用管理工具预先建立空数据库（应用不会创建数据库）：

   ```sql
   CREATE DATABASE work_helper CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
   ```

   配置一个有此数据库建表、索引和数据读写权限的本地账号。无需授予创建其他数据库权限。

2. 编辑 `src/main/resources/application.yml`，直接填写数据库地址、用户名和密码。所有运行配置统一在此文件中。
3. 双击或在终端运行 `build.bat`，构建前端并执行后端测试，生成 `target/workHelper.jar`。
4. 双击 `start.bat`。脚本检查 Java 21、JAR、配置和端口，等待数据库健康检查成功，再自动打开浏览器。默认地址为 `http://127.0.0.1:8080/#/applications`。
5. 保留启动控制台，使用 Ctrl+C 停止服务。关闭浏览器不会停止服务。启动及运行日志保留在 `logs/`。

已经启动时再次运行脚本会打开已有页面。端口被其他程序占用会明确报错；连接失败请检查 MySQL 服务、数据库是否存在及账号权限；启动超过 90 秒会提示并停止本次进程。脚本不安装或管理 MySQL 服务。

`build.bat` 使用 Maven Wrapper；首次需要网络下载 Maven 和依赖。已安装 Maven 时也可依次执行 `npm ci`、`npm run build`（在 frontend 目录）和 `mvn package`（项目根目录）。

## 配置

| 配置 | 默认值 |
| --- | --- |
| `spring.datasource.url` | MySQL 地址，默认 `127.0.0.1:3306/work_helper` |
| `spring.datasource.username` | 填写自己的 MySQL 用户名 |
| `spring.datasource.password` | 填写自己的 MySQL 密码，保留引号 |
| `server.port` | `8080`（整数） |
| `app.time-zone` | `Asia/Shanghai` |

只需编辑 `src/main/resources/application.yml`。启动脚本和 IDE 使用这份运行配置。`src/test/resources/application.yml` 仅用于自动化测试的内存数据库，不用于日常启动。

配置采用 YAML，使用空格缩进，不使用 Tab。启动脚本读取 `server:` 下缩进两个空格的 `port: 8080`。默认仅监听 `127.0.0.1`。如需修改数据库主机、端口或库名，修改 `spring.datasource.url` 中对应部分；修改业务时区时同步修改 URL 中的 `connectionTimeZone`。

所有业务时间是所配置时区的本地时间，数据库采用 `DATETIME(6)`，API 使用无偏移的 ISO 日期时间（例如 `2026-10-07T14:30:00`）。前端通过 `/api/info` 获取业务时区。无需导入 MySQL 命名时区表。已有记录不会随时区配置变更自动换算。

构建不会修改源码配置，也不清理业务数据库；不要把日常数据库的数据目录放入项目 `target/`。首次启动 Flyway 创建投递表、流程表及迁移历史；已有 V1 数据库会自动执行 V2，保留投递并为其设置默认“已投递”阶段。不要修改已执行的迁移文件。

## 开发

后端：在项目根目录执行 `mvnw.cmd spring-boot:run`（自动读取 `src/main/resources/application.yml`）。

前端：在 `frontend` 执行 `npm ci`、`npm run dev`，访问 `http://127.0.0.1:5173`。默认代理 API 至 8080；修改后端端口时，在 PowerShell 设置 `$env:API_TARGET='http://127.0.0.1:你的端口'` 后启动前端。

Vue 使用 hash 路由，刷新或直接访问页面无需服务器路由回退。Maven 会把 `frontend/dist` 复制到 JAR 的静态资源目录；页面改动后需重新构建前端。

## API

| 方法 | 地址 | 功能 |
| --- | --- | --- |
| GET | `/api/applications?search=&stage=` | 投递列表、文字搜索、阶段筛选 |
| GET | `/api/applications/{id}` | 单条详情 |
| POST | `/api/applications` | 新增，返回 201 |
| PUT | `/api/applications/{id}` | 修改 |
| DELETE | `/api/applications/{id}` | 删除，返回 204 |
| GET | `/api/info` | 应用标识、业务时区、当前业务时间 |
| GET | `/actuator/health` | 包含数据库连通性的健康检查 |

请求字段：`companyName`、`positionName`、`location`、`requirements`、`appliedAt`、`channel`、`jobUrl`、`notes`。`currentStage` 只读，不从请求写入。支持的筛选阶段见 `frontend/src/api.ts`。无效输入返回 400、不存在返回 404、数据库异常返回 503，错误体统一含 `message`。

采用物理删除，需前端确认。删除投递会通过外键级联删除流程和安排。流程写入与投递阶段重算位于同一事务，并锁定所属投递以避免并发重算丢失。问答表将在面经阶段实现。

流程 API（`{appId}` 为所属投递，所有操作校验归属关系）：

| 方法 | 地址 | 功能 |
| --- | --- | --- |
| GET / POST | `/api/applications/{appId}/processes` | 查询列表 / 新增流程 |
| GET / PUT / DELETE | `/api/applications/{appId}/processes/{id}` | 查询 / 编辑 / 删除流程 |
| PATCH | `/api/applications/{appId}/processes/{id}/status` | 设置 `PENDING`、`COMPLETED` 或 `CANCELLED` |

流程字段：`stage`、`roundName`、`timeMode`、`startAt`、`endAt`、`deadlineAt`、`status`、`location`、`notes`、`occurredAt`。

- `SCHEDULED`：开始时间必填，结束不能早于开始。
- `DEADLINE`：截止时间必填，可选开始时间不能晚于截止。
- `RECORD_ONLY`：不设置安排时间；切换到此模式保存时清除旧安排时间。
- `occurredAt` 用于补录及结果阶段排序；未提供时新增默认业务当前时间，编辑保留原值。
- 流程列表按发生时间从新到旧排列；最高进度计算不依赖列表顺序或完成状态。

## 验证

`mvnw.cmd test` 使用隔离的 H2 MySQL 兼容模式，不读取或删除真实 MySQL 业务数据，当前 9 项用例覆盖真实 HTTP 请求、迁移、投递 CRUD、流程 CRUD、阶段推进/回退/终态、改期与时间方式切换、取消/完成、归属校验、级联删除、并发更新、非法输入和健康检查。H2 测试不能代替真实 MySQL 联调。

`npm run build` 执行 TypeScript 检查和生产构建。本次还使用 MySQL 8.0.31 专用临时库验证 V1 → V2 升级、阶段规则及关联删除；详情见 `docs/phase-one-validation.md`。

设计依据：[Spring Boot 数据库迁移文档](https://docs.spring.io/spring-boot/how-to/data-initialization.html)、[Vite 环境要求](https://vite.dev/guide/)。
