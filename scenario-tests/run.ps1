param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 5)]
    [int]$Scenario
)

$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # Compile the production classes into a separate directory for the scenarios.
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

    # Run only the selected scenario.
    $scenarioFiles = @(
        'scenario-tests/Scenario1TreeConstruction.java'
        'scenario-tests/Scenario2ValidMembership.java'
        'scenario-tests/Scenario3NonPowerOfTwo.java'
        'scenario-tests/Scenario4AppendAndConsistency.java'
        'scenario-tests/Scenario5TamperDetection.java'
    )
    & java --class-path $classesDirectory $scenarioFiles[$Scenario - 1]
    if ($LASTEXITCODE -ne 0) {
        throw "Scenario $Scenario failed. Read the Java error above."
    }
}
finally {
    Pop-Location
}
