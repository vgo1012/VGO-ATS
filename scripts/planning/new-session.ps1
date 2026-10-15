[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Name
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$planningRoot = Join-Path $repoRoot '.planning'

$slug = $Name.Trim().ToLowerInvariant()
$slug = $slug -replace '[^a-z0-9\p{IsCJKUnifiedIdeographs}]+', '-'
$slug = $slug.Trim('-')
if ([string]::IsNullOrWhiteSpace($slug)) {
    throw 'Name must contain letters, numbers, or Chinese characters.'
}
if ($slug.Length -gt 48) {
    $slug = $slug.Substring(0, 48).TrimEnd('-')
}

$planId = '{0}-{1}' -f (Get-Date -Format 'yyyy-MM-dd'), $slug
$planDir = Join-Path $planningRoot $planId
if (Test-Path -LiteralPath $planDir) {
    $planId = '{0}-{1}-{2}' -f (Get-Date -Format 'yyyy-MM-dd'), $slug, (Get-Date -Format 'HHmmss')
    $planDir = Join-Path $planningRoot $planId
}

New-Item -ItemType Directory -Path (Join-Path $planDir 'evidence') -Force | Out-Null

$createdAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
$taskPlan = @'
# Task Plan: {{NAME}}

## Goal

Describe the concrete completion outcome.

## Current Phase

Phase 1 - clarify and inspect

## Phases

### Phase 1: Clarify and inspect
- [ ] Confirm scope and existing state
- **Status:** in_progress

### Phase 2: Implement
- [ ] Complete the smallest verifiable slice
- **Status:** pending

### Phase 3: Verify and hand off
- [ ] Run proportional checks and report evidence
- **Status:** pending

## Decisions

- None yet.

## Errors

- None.

Created: {{CREATED_AT}}
'@
$taskPlan = $taskPlan.Replace('{{NAME}}', $Name).Replace('{{CREATED_AT}}', $createdAt)

$findings = @'
# Findings: {{NAME}}

Keep verified summaries here. Put raw output under `evidence/` and link it.

## Findings

- None yet.
'@
$findings = $findings.Replace('{{NAME}}', $Name)

$progress = @'
# Progress: {{NAME}}

## {{CREATED_AT}}

- Created isolated plan `{{PLAN_ID}}`.
'@
$progress = $progress.Replace('{{NAME}}', $Name).Replace('{{CREATED_AT}}', $createdAt).Replace('{{PLAN_ID}}', $planId)

Set-Content -LiteralPath (Join-Path $planDir 'task_plan.md') -Value $taskPlan -Encoding utf8
Set-Content -LiteralPath (Join-Path $planDir 'findings.md') -Value $findings -Encoding utf8
Set-Content -LiteralPath (Join-Path $planDir 'progress.md') -Value $progress -Encoding utf8

Write-Output "Created plan: $planId"
Write-Output "Directory: $planDir"
Write-Output "Use this plan ID explicitly; do not modify .planning/.active_plan in concurrent work."
