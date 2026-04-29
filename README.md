# Tally - 灵活、私有的个人记账工具

[](https://adoptium.net/)[https://img.shields.io/badge/Java-21-blue.svg](https://img.shields.io/badge/Java-21-blue.svg)  
[](https://spring.io/projects/spring-boot)[https://img.shields.io/badge/Spring%2520Boot-3.5.14-brightgreen.svg](https://img.shields.io/badge/Spring%2520Boot-3.5.14-brightgreen.svg)  
[](https://license/)[https://img.shields.io/badge/License-MIT-yellow.svg](https://img.shields.io/badge/License-MIT-yellow.svg)

**Tally** 是一款以**标签系统为核心**、**数据完全由你掌控**的记账应用。“Tally” 一词源自古老的计数与记账传统，寓意每一笔收支都清晰可循。我们摒弃了传统软件中僵化的资产账户管理，代之以极灵活的 **标签 + 标签组** 体系——无论是银行卡、信用卡、旅游开销，还是借款与报销，你都能自由定义、轻松追踪。
数据存储在你自己的 **GitHub 仓库**中，借助 **CRDT 风格的操作日志**，多设备、离线编辑均能自动合并，**永不出现冲突**。

[https://docs/preview.png](https://docs/preview.png)
## ✨ 核心特性
- **标签即一切** – 无需预设账户。创建 `银行卡A`、`餐饮`、`旅游` 等标签，记账时勾选即可。通过筛选器即可查看任意标签的收支与结余。
- **标签组 + 规则** – 将同类标签归组（如“资产”组、“消费类别”组），支持单选/多选/必选，记账更快、更准确。
- **多币种原生支持** – 每笔交易记录原始币种，可为标签设置偏好币种，记账时自动切换。
- **预算管理** – 设置总预算 + 子预算（按标签），首页进度条展示实时支出与理论时间进度的对比，超支自动预警。支持排除标签（“逃生舱”），意外支出不影响预算统计。
- **CRDT 操作日志** – 所有新增、修改、删除均以追加日志形式存储，多设备拉取后按时间顺序重放，最终数据完全一致，**无冲突、无丢失**。
- **GitHub 托管** – 所有数据存储在你自己的 GitHub 仓库中（支持私有仓库），历史可追溯，数据永归你所有。
- **跨设备使用** – 后端只需运行一处，手机、电脑通过浏览器访问（局域网或公网部署）。
- **丰富统计** – 年度/月度趋势图，标签占比饼图，预算历史圆点视图（绿/红标识超支状态）。
- **深度搜索与筛选器** – 多条件筛选，常用条件可保存为自定义筛选器，一键复用。
## 🛠 技术栈

|组件|版本|说明|
|---|---|---|
|JDK|21 (LTS)|Eclipse Temurin / Amazon Corretto 推荐|
|Spring Boot|3.5.14|稳定、安全，兼容 Java 21 虚拟线程|
|JGit|7.x|纯 Java Git 操作库|
|数据存储|GitHub 仓库 + 本地缓存|操作日志追加式存储（CRDT风格）|
|前端|Vue 3 + Vant|移动优先，响应式设计|
|构建工具|Maven 3.9+|统一依赖管理|

## 🚀 快速开始

### 前置条件
- 安装 JDK 21（推荐 [Eclipse Temurin](https://adoptium.net/)）
- 安装 Git
- 拥有 GitHub 账号，创建一个用于存放记账数据的 **私有仓库**（也可公开）
- 生成 [GitHub Personal Access Token](https://github.com/settings/tokens)（需要 `repo` 权限）
### 下载与运行

#### 1. 下载 JAR 包
从 [Releases]() 下载最新 `tally-{version}.jar`
#### 2. 首次启动（自动初始化）
```bash
java -jar tally-{version}.jar \
  --github.repo=https://github.com/yourname/accounting-data.git \
  --github.token=ghp_xxxxxxxxxxxx
```
- 程序在当前目录创建 `./repo_cache` 缓存远程仓库
- 若仓库为空，自动初始化目录结构和示例数据
- 默认监听 `http://localhost:8080`
#### 3. 访问应用
- 电脑访问：`http://localhost:8080`
- 手机访问：确保与电脑同一局域网，访问 `http://电脑IP:8080`
### Docker 运行
```bash
docker run -d \
  -p 8080:8080 \
  -e GITHUB_REPO_URL=https://github.com/yourname/accounting-data.git \
  -e GITHUB_TOKEN=ghp_xxxxxxxxxxxx \
  -v tally-cache:/app/repo_cache \
  tally:latest
```

## 📦 构建与开发

### 克隆项目
```bash
git clone https://github.com/Chlorine001/tally.git
cd tally
```
### 前端构建
```bash
cd frontend
npm install
npm run build
```
# 产物自动复制到 src/main/resources/static

### 后端构建
```bash
mvn clean package
java -jar target/tally-{version}.jar
```

### IDE 直接运行

运行 `TallyApplication` 主类，开发环境默认使用 `application-dev.properties` 配置。

## 📁 数据存储结构

远程 GitHub 仓库中的数据结构：
```text
/
├── actions/                     # CRDT 操作日志（按月份分片）
│   ├── 2026-01.log
│   ├── 2026-02.log
│   └── ...
├── meta/                        # 元数据操作日志（标签、预算、筛选器等）
│   └── meta.log
├── snapshots/                   # 可选快照文件（加速重放）
│   └── snapshot-2026-03.json
└── README.md
```

- 所有用户操作（添加、修改、删除交易）以 **追加写** 形式写入 `actions/` 对应月份文件。
- 每一条操作包含 `type`（add/update/delete/meta）、`id`、`timestamp` 和负载。
- 多设备同步时，自动拉取远程新增操作，按时间戳合并重放，**无需冲突处理**。
## 📖 使用指南

### 1. 记账

- 点击底部大 `+` 按钮进入记账页。
- 选择 **支出** 或 **收入**，输入金额。
- 选择标签：标签按组分块显示，遵守分组规则（如“资产”组必选且单选）。
- 多币种：若标签设置了偏好币种，金额输入框自动切换。
- 可选日期、备注。
### 2. 预算设置

- 进入“设置” → “预算管理”。
- 创建预算：设置周期（月/年/自定义）、总金额、子预算（按标签配额度）。
- 设置“排除标签”（如“意外支出”），这些标签的交易不计入预算统计。
- 首页自动展示预算进度条：
    - **黄色**：今日支出占比
    - **绿色/红色**：累计支出占比（超支变红）
    - **黑色竖线**：理论时间进度对应支出点
### 3. 筛选器管理
- 在搜索页设置复杂条件（标签、时间、金额、是否包含/排除某些标签），点击“保存为筛选器”。
- 筛选器会同步到所有设备，方便快速查看特定资产（如“银行卡A”余额）。
### 4. 数据同步
- 每次记账、修改、删除后，**自动 commit + push** 到 GitHub。
- 若网络异常，操作会暂存本地队列，恢复后自动同步。
- 手动同步：设置页点击“立即同步”，执行 `git pull` 并合并操作日志。

## ❓ 常见问题

**Q: 为什么不用传统资产账户模式？**  
A: 标签系统更灵活。你只需为“银行卡A”创建一个标签，然后通过筛选器查看该卡的所有收支和余额，无需预设账户。转账、多币种、旅游支出等场景都能用标签优雅表达。

**Q: 多设备同时记账会冲突吗？**  
A: 不会。所有设备只追加操作日志，服务器合并时按时间戳排序重放，最终所有设备数据一致。这是 CRDT 思想在记账场景的简化实现。

**Q: 数据安全吗？**  
A: 数据存储在你自己控制的 GitHub 仓库，可以是私有仓库。GitHub 企业级加密和访问控制。Token 只存储在你本地或环境变量中，后端不会泄露。

**Q: 可以导入其他记账软件的数据吗？**  
A: 支持 CSV/Excel 导入。设置页提供导入工具，你只需将其他软件导出的数据按模板映射字段即可。

**Q: 需要公网 IP 才能手机访问吗？**  
A: 不需要。手机和电脑连接同一 WiFi，使用电脑局域网 IP 即可访问。也可以部署到免费云服务（如 Railway）获得公网地址。

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request。请确保：

- 代码符合 Google Java Style Guide
- 提交信息清晰描述变更内容
- 若添加新特性，补充相应说明文档

## 📄 许可证

Tally 使用 MIT 许可证。你可以自由使用、修改、分发，甚至用于商业目的。

---
**开始用 Tally 掌控你的财务，让每一笔账都清晰、自由、安全！**