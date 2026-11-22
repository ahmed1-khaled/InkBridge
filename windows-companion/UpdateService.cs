using System.IO;
using System.Diagnostics;
using System.Net.Http;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json;

namespace InkBridge.Companion;

public sealed record InkBridgeDesktopUpdate(Version Version, string TagName, Uri DownloadUri, string? Sha256, string ReleaseNotes);

public static class UpdateService
{
    private const string ReleaseApi = "https://api.github.com/repos/ahmed1-khaled/InkBridge/releases/latest";
    private const string ExeAssetName = "InkBridge.Companion.exe";
    private static readonly HttpClient Http = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(35) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("InkBridge-Windows");
        client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        return client;
    }

    public static async Task<InkBridgeDesktopUpdate?> CheckAsync(CancellationToken cancellationToken = default)
    {
        using var response = await Http.GetAsync(ReleaseApi, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
        var root = document.RootElement;
        if (root.TryGetProperty("draft", out var draft) && draft.GetBoolean()) return null;
        if (root.TryGetProperty("prerelease", out var prerelease) && prerelease.GetBoolean()) return null;

        var tag = root.GetProperty("tag_name").GetString() ?? "";
        if (!Version.TryParse(tag.TrimStart('v', 'V'), out var remoteVersion)) return null;
        var localVersion = Assembly.GetExecutingAssembly().GetName().Version ?? new Version(0, 0, 0);
        if (remoteVersion <= localVersion) return null;

        if (!root.TryGetProperty("assets", out var assets) || assets.ValueKind != JsonValueKind.Array)
            throw new InvalidOperationException("The latest GitHub release has no downloadable assets.");

        foreach (var asset in assets.EnumerateArray())
        {
            if (!asset.TryGetProperty("name", out var name) || name.GetString() != ExeAssetName) continue;
            var url = asset.GetProperty("browser_download_url").GetString();
            if (!Uri.TryCreate(url, UriKind.Absolute, out var downloadUri))
                throw new InvalidOperationException("The Windows update URL is invalid.");
            var digest = asset.TryGetProperty("digest", out var digestElement) ? digestElement.GetString() : null;
            if (digest?.StartsWith("sha256:", StringComparison.OrdinalIgnoreCase) == true)
                digest = digest["sha256:".Length..];
            else
                digest = null;
            var notes = root.TryGetProperty("body", out var body) ? body.GetString() ?? "" : "";
            return new InkBridgeDesktopUpdate(remoteVersion, tag, downloadUri, digest, notes);
        }

        throw new InvalidOperationException("The latest GitHub release does not contain the Windows EXE.");
    }

    public static async Task DownloadAndLaunchAsync(InkBridgeDesktopUpdate update, CancellationToken cancellationToken = default)
    {
        var appRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "InkBridge", "updates");
        Directory.CreateDirectory(appRoot);
        var downloaded = Path.Combine(appRoot, $"InkBridge-{update.Version}.exe.part");
        var ready = Path.Combine(appRoot, "InkBridge.Companion.new.exe");

        using (var response = await Http.GetAsync(update.DownloadUri, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
        {
            response.EnsureSuccessStatusCode();
            await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
            await using var output = new FileStream(downloaded, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
            await input.CopyToAsync(output, cancellationToken);
        }

        var info = new FileInfo(downloaded);
        if (info.Length < 1_000_000)
        {
            File.Delete(downloaded);
            throw new InvalidOperationException("The downloaded Windows update is too small to be valid.");
        }

        if (update.Sha256 is not null)
        {
            await using var verifyStream = File.OpenRead(downloaded);
            var actual = Convert.ToHexString(await SHA256.HashDataAsync(verifyStream, cancellationToken));
            if (!actual.Equals(update.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(downloaded);
                throw new InvalidOperationException("The Windows update checksum did not match GitHub's release digest.");
            }
        }
        if (File.Exists(ready)) File.Delete(ready);
        File.Move(downloaded, ready);

        var currentExe = Environment.ProcessPath;
        if (string.IsNullOrWhiteSpace(currentExe) || !File.Exists(currentExe))
            throw new InvalidOperationException("Could not locate the running InkBridge executable.");

        var script = Path.Combine(appRoot, "apply-update.ps1");
        await File.WriteAllTextAsync(script, UpdateScript, cancellationToken);
        var args = $"-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"{script}\" -ProcessId {Environment.ProcessId} -Source \"{ready}\" -Target \"{currentExe}\"";
        var updaterProcess = Process.Start(new ProcessStartInfo("powershell.exe", args)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            WorkingDirectory = appRoot
        });
        if (updaterProcess is null)
            throw new InvalidOperationException("Could not start the InkBridge updater.");

        // The updater waits for this process to exit, replaces the executable, and starts it again.
    }

    private const string UpdateScript = """
param(
  [Parameter(Mandatory=$true)][int]$ProcessId,
  [Parameter(Mandatory=$true)][string]$Source,
  [Parameter(Mandatory=$true)][string]$Target
)
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:LOCALAPPDATA 'InkBridge\update.log'
try {
  while (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
    Start-Sleep -Milliseconds 350
  }
  $targetDirectory = Split-Path -Parent $Target
  New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
  try {
    Copy-Item -LiteralPath $Source -Destination $Target -Force
  } catch {
    $fallbackDirectory = Join-Path $env:LOCALAPPDATA 'Programs\InkBridge'
    New-Item -ItemType Directory -Path $fallbackDirectory -Force | Out-Null
    $Target = Join-Path $fallbackDirectory 'InkBridge.Companion.exe'
    Copy-Item -LiteralPath $Source -Destination $Target -Force
    "Installed to per-user folder because the previous location was not writable." | Set-Content -Path $log
  }
  Start-Process -FilePath $Target
} catch {
  $_ | Out-String | Add-Content -Path $log
}
""";
}
