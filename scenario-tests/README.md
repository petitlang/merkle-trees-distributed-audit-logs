# 独立场景测试：演示视频用

你负责的前三个场景各有一个独立的 Java 文件，每个文件都有自己的 `main`：

```text
scenario-tests/
  Scenario1TreeConstruction.java
  Scenario2ValidMembership.java
  Scenario3NonPowerOfTwo.java
  run.ps1
  ledgers/
    events5.txt
    events6.txt
    events7.txt
    events8.txt
```

这些程序直接使用正式的 `MerkleTree`、`LogServer` 和 `Auditor`。
成员验证场景也打印逐层重算，方便讲解；最终是否通过仍检查正式
`Auditor.isMember()` 的结果。输入日志保持原样。

## 最简单的运行方式

需要 JDK 17 或更新版本，`java` 和 `javac` 在 PATH 中。
在项目根目录的 PowerShell 终端里，按场景分别运行：

```powershell
.\scenario-tests\run.ps1 1
```

```powershell
.\scenario-tests\run.ps1 2
```

```powershell
.\scenario-tests\run.ps1 3
```

脚本每次先把当前正式代码编译到 `target/scenario-tests/classes`，
再运行选中的 Java 文件。这个过程不需要 Maven，也不需要下载依赖。
场景之间互不调用，不依赖运行顺序。运行脚本会自动定位项目根目录。

如果已经编译好正式代码，也可以从项目根目录直接运行单个 Java 文件：

```powershell
java --class-path target/scenario-tests/classes scenario-tests/Scenario1TreeConstruction.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario2ValidMembership.java
java --class-path target/scenario-tests/classes scenario-tests/Scenario3NonPowerOfTwo.java
```

这里利用 Java 17 的源文件运行功能，不需要给三个场景再配置 Maven 项目。

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

## 失败时会怎样

每个 `PASSED` 都在实际比较后才打印。根哈希不符、大小不对、
逐层重建不符或正式 Auditor 返回 false，程序都会抛出异常，
脚本也会报失败，方便定位问题。

本次三个文件已分别执行成功。验证输出保存在本机
`target/scenario-tests/output/scenario1.log`、`scenario2.log` 和 `scenario3.log`；
这些是本次运行记录，脚本默认直接向终端打印。
此目录不包含第 4、5 个场景，也不替代原有的 Cucumber/JUnit 自动测试。
