$ErrorActionPreference = 'Stop'
dotnet publish windows-companion/InkBridge.Companion.csproj `
  --configuration Release `
  --runtime win-x64 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:EnableCompressionInSingleFile=true `
  --output publish
if ($LASTEXITCODE -ne 0) { throw 'Windows companion publish failed.' }
Write-Host 'EXE: publish/InkBridge.Companion.exe' -ForegroundColor Green
