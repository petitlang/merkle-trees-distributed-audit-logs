# 独立场景测试：演示视频用

五个场景各有一个独立的 Java 文件，每个文件都有自己的 `main`：

```text
scenario-tests/
  Scenario1TreeConstruction.java
  Scenario2ValidMembership.java
  Scenario3NonPowerOfTwo.java
  Scenario4AppendAndConsistency.java
  Scenario5TamperDetection.java
  run.ps1
  ledgers/
    events5.txt
    events6.txt
    events7.txt
    events8.txt
```

这些程序直接使用项目的 `MerkleTree`、`LogServer` 和 `Auditor`。
需要写入日志的场景使用临时副本，运行后清理，方便重复录制。

## 最简单的运行方式

需要 JDK 17 或更新版本，`java` 和 `javac` 在 PATH 中。
在项目根目录的 PowerShell 终端里，按场景分别运行：

```powershell
.\scenario-tests\run.ps1 1
.\scenario-tests\run.ps1 2
.\scenario-tests\run.ps1 3
.\scenario-tests\run.ps1 4
.\scenario-tests\run.ps1 5
```

脚本每次先把当前正式代码编译到 `target/scenario-tests/classes`，
再运行选中的 Java 文件。这个过程不需要 Maven，也不需要下载依赖。
场景之间互不调用，不依赖运行顺序。运行脚本会自动定位项目根目录。

如果已经编译好正式代码，也可以从项目根目录直接运行单个 Java 文件：

```powershell
java --class-path target/scenario-tests/classes scenario-tests/Scenario1TreeConstruction.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario2ValidMembership.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario3NonPowerOfTwo.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario4AppendAndConsistency.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario5TamperDetection.java
```

这里利用 Java 17 的源文件运行功能，不需要为演示场景另建 Maven 项目。

## 场景 1：八条记录建树，再核对两条记录

程序逐项打印：

1. 实际读取的日志文件和 e1 到 e8 的每条记录。
2. 叶子、父节点的哈希公式。
3. 整棵树每个节点的范围和完整 SHA-256 哈希。
4. MerkleTree、LogServer、固定参考根三者的比较结果。
5. 两条记录的 UTF-8 字节、叶子输入、h1、h2、父节点输入长度和计算结果。
6. 按公式算出的两条记录根与建树结果、固定参考根的比较。

小规模核对用这三个公式：

```text
h1 = SHA256(0x00 | UTF8("e1"))
h2 = SHA256(0x00 | UTF8("e2"))
root = SHA256(0x01 | h1 | h2)
```

SHA-256 的数值由 MessageDigest 算出；视频中解释每一步的输入和组合方式。
父节点使用原始哈希字节，终端只把哈希转换成十六进制显示。
最后必须出现 `SCENARIO 1 PASSED`。

## 场景 2：第一条、中间和最后一条记录

使用八条记录，分别验证 e1、e4、e8。每条记录都打印：

1. 记录编号、内容以及 `genPath` 返回的兄弟哈希数量。
2. 记录自己的叶子哈希。
3. 每个兄弟节点的范围、位于左边还是右边、实际证明哈希。
4. 本层左右拼接的顺序和计算出来的父节点哈希。
5. 最后重建的根与事先固定的可信根。
6. 正式 `Auditor.isMember(event, index, path)` 的实际返回值。

为了标注范围，打印代码会查看完整树；正式 Auditor 只收到大小、可信根、
记录、编号和证明，不读取树或日志文件。
最后必须出现 `SCENARIO 2 PASSED`，共三次成员验证。

## 场景 3：5、6、7 条记录

依次读取三个日志文件，每种大小都打印输入、完整树、实际叶子数、
根节点拆分、两种正式组件算出的根和固定参考根。
程序检查根节点分别拆成 4+1、4+2、4+3，叶子数等于输入记录数。

然后像场景 2 一样，完整打印首、中、末三条记录的证明和验证过程：

```text
5 条记录：编号 1、3、5
6 条记录：编号 1、3、6
7 条记录：编号 1、4、7
```

最后必须出现 `SCENARIO 3 PASSED: 3 tree checks and 9 memberships.`。
录制时可以在每种大小的根检查和三次成员结果处停留、讲解。

## 场景 4：追加事件并验证一致性

从五条记录开始，先单条追加 `e6`，再批量追加 `[e7, e8]`。每一步都会显示
追加前后的大小、完整根哈希和 `Auditor.isConsistent` 的返回值。第二步还会打印
`genProof(6)` 的三个哈希，按 `[h7..8, h5..6, h1..4]` 的顺序标注。
最后显示从文件读回的八条记录。

录制时先展示 `5 -> 6` 的第一处 `true`，再停在 `6 -> 8` 的证明顺序、
第二处 `true` 和 `SCENARIO 4 PASSED`。

## 场景 5：检测日志中的篡改

先保存八条记录的可信根和第 4 条的审计路径，验证 `e4` 并显示 `true`。
程序把临时日志的第 4 行改成 `e4-tampered`，重新打开日志并打印新的根哈希。
之后用同一个可信根、索引和路径验证修改后的内容，预期返回 `false`。

录制时对比 `Before` 和 `After` 两行，再展示 `SCENARIO 5 PASSED`。
这里的 `false` 表示 Auditor 正确拒绝了被修改的记录。

## 失败时会怎样

程序只在所有检查都满足预期时打印 `PASSED`。遇到意外的验证结果，
脚本会报错退出。演示输出直接显示在终端，自动回归测试仍通过 `mvn test` 运行。
