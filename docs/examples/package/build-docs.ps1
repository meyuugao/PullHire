# Build a single documentation bundle (Markdown -> HTML -> PDF) for PullHire.
# Concatenates the authored docs/*.md files, renders via `npx marked`, and prints to PDF via headless Edge.
# Run from anywhere:  powershell -ExecutionPolicy Bypass -File docs\examples\package\build-docs.ps1

$ErrorActionPreference = 'Stop'

# package dir = .../docs/examples/package ; root = repo root
$pkg  = $PSScriptRoot
$root = Split-Path (Split-Path (Split-Path $pkg -Parent) -Parent) -Parent
$docs = Join-Path $root 'docs'

# Temp workspace. Prefer %TEMP% (guaranteed writable); never reconstruct an 8.3
# short path (the user name here is Cyrillic, e.g. "C:\Users\<user>"), which is
# not stable across sessions. Fall back to a repo-local dir if %TEMP% is unusable.
$work = Join-Path ([System.IO.Path]::GetTempPath()) 'pullhire-docs-build'
if ([string]::IsNullOrWhiteSpace($work) -or -not (Test-Path -LiteralPath ([System.IO.Path]::GetTempPath()))) {
  $work = Join-Path $root '.tmp-docs-build'
}
New-Item -ItemType Directory -Force -Path $work | Out-Null
New-Item -ItemType Directory -Force -Path $pkg  | Out-Null
$utf8    = New-Object System.Text.UTF8Encoding($false)
$utf8bom = New-Object System.Text.UTF8Encoding($true)

$title = 'PullHire — пакет документации'
$tocHeader = 'Содержание'

# Reading order: architecture, data_model, testing_model, periodic_tasks, selection,
# validation, fsp_integration, api, stack, deployment, mvp_scope, user_flow,
# glossary, assumptions, final_summary, backlog/closed_questions, worklog.
$order = @(
  'architecture.md', 'data_model.md', 'testing_model.md', 'periodic_tasks.md',
  'selection.md', 'validation.md', 'fsp_integration.md', 'api.md', 'stack.md',
  'deployment.md', 'mvp_scope.md', 'user_flow.md', 'glossary.md', 'assumptions.md',
  'final_summary.md', 'backlog\closed_questions.md', 'worklog.md'
)

function Shift-Heading([string]$line) {
  if ($line -match '^(#{1,6})(\s+)(.*)$') {
    if ($matches[1].Length -lt 6) { return '#' + $line }
  }
  return $line
}

# --- pass 1: read + shift headings, preserving fenced code blocks ---
$bodyParts = New-Object System.Collections.Generic.List[string]
foreach ($rel in $order) {
  $path = Join-Path $docs $rel
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing source doc: $path" }
  $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
  $text = $text -replace "`r`n", "`n"
  $out = New-Object System.Collections.Generic.List[string]
  $inFence = $false
  foreach ($line in ($text -split "`n")) {
    if ($line -match '^\s*(```|~~~)') { $inFence = -not $inFence; $out.Add($line); continue }
    if ($inFence) { $out.Add($line) } else { $out.Add((Shift-Heading $line)) }
  }
  $bodyParts.Add(($out -join "`n").TrimEnd())
}
$body = ($bodyParts -join "`n`n---`n`n")

# --- collect headings (fence-aware) ---
$headings = New-Object System.Collections.Generic.List[object]
$inFence = $false
foreach ($line in ($body -split "`n")) {
  if ($line -match '^\s*(```|~~~)') { $inFence = -not $inFence; continue }
  if (-not $inFence -and $line -match '^#{1,6}\s+\S') {
    if ($line -match '^(#{1,6})\s+(.*?)\s*#*\s*$') {
      $headings.Add([pscustomobject]@{ level = $matches[1].Length; text = $matches[2] })
    }
  }
}

# --- slug sequence: [title, toc-header, ...body headings] matches HTML heading order ---
$seq = New-Object System.Collections.Generic.List[object]
$seq.Add([pscustomobject]@{ level = 1; text = $title })
$seq.Add([pscustomobject]@{ level = 2; text = $tocHeader })
foreach ($h in $headings) { $seq.Add($h) }
for ($i = 0; $i -lt $seq.Count; $i++) {
  $seq[$i] | Add-Member -NotePropertyName slug -NotePropertyValue ('section-' + ($i + 1)) -Force
}

# --- table of contents (nested markdown list with links) ---
$tocLines = New-Object System.Collections.Generic.List[string]
foreach ($h in $headings) {
  $indent = '  ' * [Math]::Max(0, $h.level - 2)
  $t = $h.text -replace '\[', '\[' -replace '\]', '\]'
  $tocLines.Add($indent + '- [' + $t + '](#' + $h.slug + ')')
}

$md = '# ' + $title + "`n`n" + '## ' + $tocHeader + "`n`n" + ($tocLines -join "`n") + "`n`n" + $body + "`n"

$mdPath = Join-Path $work 'PullHire-docs.md'
[System.IO.File]::WriteAllText($mdPath, $md, $utf8)

# --- render markdown -> HTML body via npx marked ---
$bodyHtmlPath = Join-Path $work 'PullHire-docs.body.html'
# Best-effort stale cleanup: never let this abort the run before publishing.
if (Test-Path -LiteralPath $bodyHtmlPath) {
  try { Remove-Item -LiteralPath $bodyHtmlPath -Force -ErrorAction Stop }
  catch { Write-Warning "Could not remove stale body html: $_" }
}
cmd /c "npx --yes marked -i `"$mdPath`" -o `"$bodyHtmlPath`" 2>&1" | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $bodyHtmlPath)) { throw 'marked conversion failed' }
$bodyHtml = [System.IO.File]::ReadAllText($bodyHtmlPath, [System.Text.Encoding]::UTF8)

# --- inject deterministic heading ids by ordinal (section-N) ---
$script:hi = 0
$rxH = [regex]'<h([1-6])>'
$bodyHtml2 = $rxH.Replace($bodyHtml, {
  param($m)
  $i = $script:hi
  $script:hi++
  if ($i -ge $seq.Count) { return $m.Value }
  return '<h' + $m.Groups[1].Value + ' id="' + $seq[$i].slug + '">'
})
if ($script:hi -ne $seq.Count) {
  Write-Warning "Heading count mismatch: HTML=$($script:hi) sequence=$($seq.Count)"
}

$css = @'
@page { size: A4; margin: 18mm 16mm; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font-family: "Segoe UI", "PT Sans", Arial, sans-serif; font-size: 11pt; line-height: 1.5; color: #1a1a1a; }
h1 { font-size: 24pt; border-bottom: 2px solid #333; padding-bottom: .2em; }
h2 { font-size: 17pt; margin-top: 1.2em; padding-top: .3em; border-bottom: 1px solid #bbb; page-break-before: always; }
h2:first-of-type { page-break-before: avoid; }
h3 { font-size: 13.5pt; margin-top: 1.1em; }
h4 { font-size: 12pt; }
pre { background: #f5f5f5; border: 1px solid #ddd; border-radius: 4px; padding: 8px 10px; white-space: pre-wrap; word-wrap: break-word; font-size: 9.5pt; }
code { font-family: "Cascadia Mono", Consolas, "Courier New", monospace; font-size: 9.5pt; }
p code, li code, td code, th code { background: #f0f0f0; padding: 0 3px; border-radius: 3px; }
table { border-collapse: collapse; width: 100%; margin: .6em 0; font-size: 9.5pt; }
th, td { border: 1px solid #bbb; padding: 4px 6px; vertical-align: top; }
th { background: #eee; text-align: left; }
blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 12px; color: #444; }
img { max-width: 100%; }
a { color: #0b57d0; text-decoration: none; }
hr { border: none; border-top: 1px solid #ccc; margin: 1.5em 0; }
'@

$fullHtml = '<!DOCTYPE html>' + "`n" + '<html lang="ru">' + "`n" + '<head>' + "`n" +
  '<meta charset="utf-8">' + "`n" + '<meta name="viewport" content="width=device-width, initial-scale=1">' + "`n" +
  '<title>' + $title + '</title>' + "`n" + '<style>' + "`n" + $css + "`n" + '</style>' + "`n" +
  '</head>' + "`n" + '<body>' + "`n" + $bodyHtml2 + "`n" + '</body>' + "`n" + '</html>' + "`n"

$htmlPath = Join-Path $work 'PullHire-docs.html'
[System.IO.File]::WriteAllText($htmlPath, $fullHtml, $utf8)

# --- print to PDF via headless Edge ---
$edgeCandidates = @(
  'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
  'C:\Program Files\Microsoft\Edge\Application\msedge.exe'
)
$edge = $edgeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
$pdfPath = Join-Path $work 'PullHire-docs.pdf'
$pdfOk = $false
if ($edge) {
  if (Test-Path -LiteralPath $pdfPath) {
    try { Remove-Item -LiteralPath $pdfPath -Force -ErrorAction Stop }
    catch { Write-Warning "Could not remove stale pdf: $_" }
  }
  $fileUrl = ([System.Uri]::new($htmlPath)).AbsoluteUri
  $edgeArgs = @('--headless=new', '--disable-gpu', '--no-pdf-header-footer',
    ('--user-data-dir=' + (Join-Path $work 'edge-profile')), ('--print-to-pdf=' + $pdfPath), $fileUrl)
  try {
    Start-Process -FilePath $edge -ArgumentList $edgeArgs -Wait -NoNewWindow | Out-Null
  } catch { Write-Warning "Edge (headless=new) failed: $_" }
  if (-not (Test-Path -LiteralPath $pdfPath)) {
    $edgeArgs[0] = '--headless'
    try { Start-Process -FilePath $edge -ArgumentList $edgeArgs -Wait -NoNewWindow | Out-Null } catch { Write-Warning "Edge (headless) failed: $_" }
  }
  if (Test-Path -LiteralPath $pdfPath) {
    $head = [System.IO.File]::ReadAllText($pdfPath, [System.Text.Encoding]::ASCII)
    if ($head.StartsWith('%PDF')) { $pdfOk = $true } else { Write-Warning 'Generated file is not a valid PDF' }
  }
} else {
  Write-Warning 'Microsoft Edge not found; skipping PDF.'
}

# --- copy artifacts into docs/examples/package ---
Copy-Item -LiteralPath $mdPath   -Destination (Join-Path $pkg 'PullHire-docs.md')   -Force
Copy-Item -LiteralPath $htmlPath -Destination (Join-Path $pkg 'PullHire-docs.html') -Force
if ($pdfOk) {
  Copy-Item -LiteralPath $pdfPath -Destination (Join-Path $pkg 'PullHire-docs.pdf') -Force
}

Write-Output ("bundle_headings=" + $seq.Count)
Write-Output ("separators=" + ($order.Count - 1))
Write-Output ("pdf_success=" + $pdfOk)
Get-ChildItem -LiteralPath $pkg -File | Select-Object Name, Length | Format-Table -AutoSize | Out-String | Write-Output

# --- cleanup temp workspace (best-effort, AFTER publishing; must never block the run) ---
try {
  if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction Stop }
} catch {
  Write-Warning "Temp cleanup skipped: $_"
}
