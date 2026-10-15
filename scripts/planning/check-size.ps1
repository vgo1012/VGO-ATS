[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9\p{IsCJKUnifiedIdeographs}][a-zA-Z0-9\p{IsCJKUnifiedIdeographs}._-]*$')]
    [string]$PlanId
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$planningRoot = (Join-Path $repoRoot '.planning')
$planDir = Join-Path $planningRoot $PlanId

if (-not (Test-Path -LiteralPath $planDir -PathType Container)) {
    throw "Plan not found: $PlanId"
}

$resolvedPlan = (Resolve-Path -LiteralPath $planDir).Path
$resolvedPlanning = (Resolve-Path -LiteralPath $planningRoot).Path
if (-not $resolvedPlan.StartsWith($resolvedPlanning + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Resolved plan path escaped .planning.'
}

$limits = @(
    @{ Name = 'task_plan.md'; MaxLines = 200; MaxBytes = 32768 },
    @{ Name = 'findings.md'; MaxLines = 400; MaxBytes = 65536 },
    @{ Name = 'progress.md'; MaxLines = 300; MaxBytes = 49152 }
)

$failed = $false
foreach ($limit in $limits) {
    $path = Join-Path $resolvedPlan $limit.Name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Error "Missing required file: $($limit.Name)"
        $failed = $true
        continue
    }

    $item = Get-Item -LiteralPath $path
    $lineCount = @(Get-Content -LiteralPath $path).Count
    $withinLimit = $lineCount -le $limit.MaxLines -and $item.Length -le $limit.MaxBytes
    $state = if ($withinLimit) { 'OK' } else { 'TOO_LARGE' }
    Write-Output ("{0}: {1} lines, {2} bytes - {3}" -f $limit.Name, $lineCount, $item.Length, $state)
    if (-not $withinLimit) {
        $failed = $true
    }
}

if ($failed) {
    throw 'Planning size gate failed. Move raw detail to evidence/ or close the task.'
}

Write-Output 'Planning size gate passed.'
