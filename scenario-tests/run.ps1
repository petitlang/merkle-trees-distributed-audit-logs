param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 3)]
    [int]$Scenario
)

$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # 直接编译项目的正式代码，不需要 Maven，也不改动 Maven 的 target/classes。
    $classesDirectory = 'target/scenario-tests/classes'
    New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
    $sources = @(
        'src/main/java/fr/isep/auditlog/MerkleTree.java'
        'src/main/java/fr/isep/auditlog/LogServer.java'
        'src/main/java/fr/isep/auditlog/Auditor.java'
    )
    Write-Host 'Compiling the current MerkleTree, LogServer and Auditor (Java 17)...'
    & javac --release 17 -encoding UTF-8 -d $classesDirectory @sources
    if ($LASTEXITCODE -ne 0) {
        throw 'Compilation failed. Check that JDK 17 or later is on PATH.'
    }

    # 每个场景都是独立的 Java 文件，只运行本次选择的那一个。
    $scenarioFiles = @(
        'scenario-tests/Scenario1TreeConstruction.java'
        'scenario-tests/Scenario2ValidMembership.java'
        'scenario-tests/Scenario3NonPowerOfTwo.java'
    )
    & java --class-path $classesDirectory $scenarioFiles[$Scenario - 1]
    if ($LASTEXITCODE -ne 0) {
        throw "Scenario $Scenario failed. Read the Java error above."
    }
}
finally {
    Pop-Location
}
