# MyBatis Log Plugin Free - 自测报告

## 1. 测试目标
- 验证新增 JPA/Hibernate SQL 还原功能可编译、可测试、可打包。
- 验证 `@Query` 前小标记（gutter）相关代码可通过项目构建检查。
- 产出可安装的 IDEA 插件包，便于后续手工验证。

## 2. 测试环境
- 操作系统: macOS (aarch64)
- JDK: 11.0.21
- Gradle: `/opt/homebrew/Cellar/gradle@6/6.9.4/libexec/bin/gradle`
- 项目路径: `/Users/hongyan/work/workspace/todo/mybatis-log-plugin-free`

## 3. 执行命令与结果
1. 全量构建与打包
```bash
/opt/homebrew/Cellar/gradle@6/6.9.4/libexec/bin/gradle --no-daemon clean test buildPlugin
```
结果: `BUILD SUCCESSFUL`

2. 关键单测定向回归
```bash
/opt/homebrew/Cellar/gradle@6/6.9.4/libexec/bin/gradle --no-daemon test --tests com.starxg.mybatislog.MyBatisLogConsoleFilterTest
```
结果: `BUILD SUCCESSFUL`

## 4. 测试覆盖点
- `MyBatisLogConsoleFilterTest`:
  - 既有 MyBatis `Preparing/Parameters` SQL 还原。
  - 新增 JPA `binding parameter` 解析。
  - 新增 JPA 参数回填后的 SQL 拼装。
- 编译级验证:
  - `JpaQueryLineMarkerProvider`（`@Query` gutter 标记）可通过编译。
  - `JpaQuerySqlGenerator`（一键生成 SQL）可通过编译。
  - `plugin.xml` 新增扩展点配置可通过打包流程。

## 5. 插件打包产物
- 文件: `build/distributions/mybatis-log-plugin-free-1.3.0.zip`
- 绝对路径: `/Users/hongyan/work/workspace/todo/mybatis-log-plugin-free/build/distributions/mybatis-log-plugin-free-1.3.0.zip`
- 文件大小: 约 64 KB

## 6. 手工验证建议
1. IDEA -> `Settings` -> `Plugins` -> 齿轮 -> `Install Plugin from Disk...`
2. 选择上述 zip 包安装并重启 IDEA。
3. 在 Spring Data JPA Repository 方法上添加 `@Query`，确认注解前出现小标记，点击后可复制/输出 SQL。
4. 启动应用并打开插件 SQL 窗口，确认 Hibernate/JPA 日志可拼装为完整 SQL 输出。

## 7. 结论
- 当前代码已通过本地构建、单测和插件打包验证。
- 已满足“可用于后续验证和使用”的交付要求。
