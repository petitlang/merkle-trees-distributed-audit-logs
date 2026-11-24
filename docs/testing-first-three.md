# 前三个测试：运行与说明

录制视频时，使用新建的 [独立场景测试目录](../scenario-tests/README.md)：
三个 Java 文件分别对应三个场景，打印完整输入、树、哈希与验证过程。
下面保留的是 Cucumber 自动测试的运行说明。

本轮以 main 的 `324f8d1` 为基准，只整理老师要求的前三项测试。
`src/main`、`pom.xml` 和原来的 Cucumber runner 均保持一致。
测试调用现有的 `MerkleTree.build`、`LogServer.genPath` 和
`Auditor.isMember`。第 4、5 项由同学负责，本轮没有新增或修改。

## 怎么运行

在项目根目录，用 JDK 17 和 Maven 运行：

```powershell
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@first-three" test
```

这会选择 25 个例子。没有被选中的场景可能显示为 skipped，属于正常筛选。
需要逐项讲解、截图时，分别运行：

```powershell
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@scenario-1" test
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@scenario-2" test
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@scenario-3" test
```

终端和 `target/cucumber/report.html` 都会显示实际计算结果。
每次运行会覆盖默认报告。本次也保存了只含前三项的报告：
`target/cucumber/first-three.html`。生成的报告不提交到 Git；
报告和视频需要的截图，可以从实际运行输出中截取。

## 测试 1：建树和小规模核对

使用字符串 e1 到 e8 建树，检查根哈希为：

```text
7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8
```

老师要求用 2 条或 4 条记录核对小树。这里选最简单的 2 条：

```text
h1 = SHA256(0x00 | UTF8("e1"))
h2 = SHA256(0x00 | UTF8("e2"))
root = SHA256(0x01 | h1 | h2)
```

`MerkleTreeSteps.checkTwoEventRoot` 直接用 MessageDigest 按这三步计算，
不调用正式代码的哈希辅助方法。终端打印 h1、h2 和计算出的根，
再用断言检查它与建树结果相同。前缀是单个字节，父节点输入是原始
哈希字节，不是十六进制字符串。两条记录的正确根为：

```text
1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216
```

本组共 7 个例子：2/4/8 条记录建树、从文件加载这三种大小，以及
两条记录的逐步核对。截图展示 8 条记录的根和两条记录的核对过程。

## 测试 2：验证第一条、中间和最后一条记录

对 8 条记录分别执行：

```text
genPath(1) -> isMember("e1", 1, path) = true
genPath(4) -> isMember("e4", 4, path) = true
genPath(8) -> isMember("e8", 8, path) = true
```

LogServer 提供兄弟哈希，Auditor 根据记录、编号和路径重算根，
与初始化时保存的根比较。编号从 1 开始。
终端会打印路径哈希和验证结果。本组共 3 个例子。

## 测试 3：5、6、7 条记录

检查根节点分别拆成 4+1、4+2、4+3，叶子没有补齐或重复。
建树和从文件加载得到的根，都要与已有的固定哈希一致。

每种大小再验证第一条、中间和最后一条：

```text
5 条：编号 1、3、5 -> true
6 条：编号 1、3、6 -> true
7 条：编号 1、4、7 -> true
```

本组共 15 个例子：3 个建树、3 个文件加载和 9 个成员验证。
截图展示三种大小的根节点拆分和成员验证结果。

## 代码为什么这样写

- 复用 main 已有的 feature 和步骤类，只添加筛选标签、输出和一个小树核对步骤。
- 两个步骤类都用相同的 `enableFirstThreeOutput` 和 `report` 方式记录结果。
- 小树核对只有“两片叶子加一个父节点”，没有额外的通用工具类。
- 正式功能已经存在，这轮是补充测试展示，不制造缺少功能的 RED 阶段。

核验环境为 JDK 17.0.12、Maven 3.9.9。前三项的 25 个例子、108 个步骤
全部通过；完整 `mvn clean verify` 通过 158 个测试，并生成 JAR。
