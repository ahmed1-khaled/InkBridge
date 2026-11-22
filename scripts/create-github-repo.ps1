[CmdletBinding()]
param(
    [ValidateSet('public', 'private')]
    [string]$Visibility = 'public'
)

$ErrorActionPreference = 'Stop'
$Repo = 'ahmed1-khaled/InkBridge'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git for Windows is not installed. Install it from https://git-scm.com/download/win.'
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI (gh) is not installed. Install it from https://cli.github.com/ and reopen PowerShell.'
}

& gh auth status
if ($LASTEXITCODE -ne 0) {
    throw 'GitHub CLI is not authenticated. Run: gh auth login'
}

if (-not (Test-Path '.git')) {
    git init --initial-branch=main
    if ($LASTEXITCODE -ne 0) { throw 'git init failed.' }
}

if (-not (git config user.name)) {
    git config user.name 'Ahmed Khaled'
}
if (-not (git config user.email)) {
    git config user.email '168237202+ahmed1-khaled@users.noreply.github.com'
}

git add -A
if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }

$staged = git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    git commit -m 'Initial InkBridge alpha implementation'
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed.' }
}
elseif ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect the Git staging area.'
}

$visibilityFlag = if ($Visibility -eq 'public') { '--public' } else { '--private' }
Write-Host "Creating https://github.com/$Repo ($Visibility) and pushing the current branch..." -ForegroundColor Cyan
& gh repo create $Repo $visibilityFlag --description 'Bluetooth tablet-to-PC writing pad for Windows' --source . --remote origin --push
if ($LASTEXITCODE -ne 0) {
    throw "Repository creation/push failed. Check whether $Repo already exists and whether your GitHub account can create repositories."
}

Write-Host "Done: https://github.com/$Repo" -ForegroundColor Green
