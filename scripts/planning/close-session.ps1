[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9\p{IsCJKUnifiedIdeographs}][a-zA-Z0-9\p{IsCJKUnifiedIdeographs}._-]*$')]
    [string]$PlanId
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$planningRoot = Join-Path $repoRoot '.planning'
$source = Join-Path $planningRoot $PlanId
$archiveRoot = Join-Path $planningRoot 'archive'
$destination = Join-Path $archiveRoot $PlanId

if (-not (Test-Path -LiteralPath $source -PathType Container)) {
    throw "Plan not found: $PlanId"
}

$resolvedSource = (Resolve-Path -LiteralPath $source).Path
$resolvedPlanning = (Resolve-Path -LiteralPath $planningRoot).Path
if (-not $resolvedSource.StartsWith($resolvedPlanning + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Resolved plan path escaped .planning.'
}
if ($resolvedSource.StartsWith((Join-Path $resolvedPlanning 'archive') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Plan is already archived.'
}
if (Test-Path -LiteralPath $destination) {
    throw "Archive destination already exists: $destination"
}

$taskPlanPath = Join-Path $resolvedSource 'task_plan.md'
if (-not (Test-Path -LiteralPath $taskPlanPath -PathType Leaf)) {
    throw 'task_plan.md is missing.'
}
$taskPlan = Get-Content -Raw -LiteralPath $taskPlanPath
if ($taskPlan -match '(?im)^\s*-?\s*\*\*Status:\*\*\s*in_progress\s*$') {
    throw 'Plan still contains an in_progress phase. Complete it before archiving.'
}

& (Join-Path $PSScriptRoot 'check-size.ps1') -PlanId $PlanId
New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
Move-Item -LiteralPath $resolvedSource -Destination $destination

$activePointer = Join-Path $planningRoot '.active_plan'
if (Test-Path -LiteralPath $activePointer) {
    $activeId = (Get-Content -Raw -LiteralPath $activePointer).Trim()
    if ($activeId -eq $PlanId) {
        Remove-Item -LiteralPath $activePointer
    }
}

Write-Output "Archived plan: $destination"
