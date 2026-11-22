using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;

namespace InkBridge.Companion;

public partial class MainWindow : Window
{
    private const int InkPort = 45832;
    private const int MaxFrameLength = 8 * 1024 * 1024;

    private TcpListener? _listener;
    private TcpClient? _client;
    private StreamWriter? _writer;
    private CancellationTokenSource? _serverCts;
    private Task? _acceptTask;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private readonly DispatcherTimer _updateTimer = new() { Interval = TimeSpan.FromMinutes(45) };
    private OverlayWindow? _overlay;
    private bool _overlaySuppressed = true;
    private string _pairingCode = "";

    public MainWindow()
    {
        InitializeComponent();
        HideOverlayButton.IsEnabled = false;
        Loaded += MainWindow_Loaded;
        _updateTimer.Tick += async (_, _) => await CheckUpdatesAsync(manual: false);
        _updateTimer.Start();
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        _pairingCode = LoadOrCreatePairingCode();
        PairingCodeText.Text = _pairingCode;
        RefreshNetworkAddresses();
        StartWifiServer();
        await CheckUpdatesAsync(manual: false);
    }

    private static string LoadOrCreatePairingCode()
    {
        var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "InkBridge");
        Directory.CreateDirectory(folder);
        var path = Path.Combine(folder, "wifi-pairing-code.txt");
        if (File.Exists(path))
        {
            var existing = File.ReadAllText(path).Trim();
            if (existing.Length == 6 && existing.All(char.IsDigit)) return existing;
        }

        var code = RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        File.WriteAllText(path, code, new UTF8Encoding(false));
        return code;
    }

    private void RefreshNetworkAddresses()
    {
        try
        {
            var addresses = NetworkInterface.GetAllNetworkInterfaces()
                .Where(adapter => adapter.OperationalStatus == OperationalStatus.Up &&
                                  adapter.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .SelectMany(adapter => adapter.GetIPProperties().UnicastAddresses
                    .Where(item => item.Address.AddressFamily == AddressFamily.InterNetwork &&
                                   !IPAddress.IsLoopback(item.Address) &&
                                   !item.Address.ToString().StartsWith("169.254.", StringComparison.Ordinal))
                    .Select(item => new
                    {
                        Priority = adapter.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 0 :
                                   adapter.NetworkInterfaceType == NetworkInterfaceType.Ethernet ? 1 : 2,
                        Adapter = adapter.Name,
                        Address = item.Address.ToString()
                    }))
                .OrderBy(item => item.Priority)
                .ThenBy(item => item.Adapter, StringComparer.OrdinalIgnoreCase)
                .Take(5)
                .ToList();

            NetworkAddressText.Text = addresses.Count == 0
                ? "No network address found. Connect this PC to Wi-Fi or Ethernet, then restart the service."
                : string.Join(Environment.NewLine, addresses.Select(item => $"{item.Address}   ·   {item.Adapter}"));
        }
        catch (Exception ex)
        {
            NetworkAddressText.Text = $"Could not read network addresses: {ex.Message}";
        }
    }

    private void StartWifiServer()
    {
        if (_listener is not null) return;
        try
        {
            var listener = new TcpListener(IPAddress.Any, InkPort);
            listener.Start(8);
            _listener = listener;
            _serverCts = new CancellationTokenSource();
            RestartServerButton.Content = "Wi-Fi service running";
            SetConnectionBadge("WI-FI READY · WAITING FOR TABLET", Brushes.LightSkyBlue);
            PreviewStatusText.Text = "Waiting for your tablet to connect over the same network.";
            Log($"Wi-Fi ink service is listening on TCP port {InkPort}.");
            Log("Connect both devices to the same Wi-Fi network or hotspot, then enter one of the IP addresses and the 6-digit code in Android.");
            Log("If Windows Firewall asks, allow InkBridge on Private networks.");
            _acceptTask = AcceptLoopAsync(listener, _serverCts.Token);
        }
        catch (Exception ex)
        {
            _listener = null;
            RestartServerButton.Content = "Retry Wi-Fi service";
            SetConnectionBadge("WI-FI SERVICE ERROR", Brushes.Orange);
            Log($"Could not start the Wi-Fi service: {ex.Message}");
            Log("Check whether another application is using TCP port 45832.");
        }
    }

    private async void RestartServerButton_Click(object sender, RoutedEventArgs e)
    {
        await StopWifiServerAsync();
        RefreshNetworkAddresses();
        StartWifiServer();
    }

    private async Task StopWifiServerAsync()
    {
        var listener = _listener;
        var cts = _serverCts;
        _listener = null;
        _serverCts = null;
        try { cts?.Cancel(); } catch { }
        try { listener?.Stop(); } catch { }
        try { _client?.Close(); } catch { }
        if (_acceptTask is not null)
        {
            try { await Task.WhenAny(_acceptTask, Task.Delay(1200)); } catch { }
        }
        try { cts?.Dispose(); } catch { }
        _acceptTask = null;
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            TcpClient? incoming = null;
            try
            {
                incoming = await listener.AcceptTcpClientAsync(token);
                incoming.NoDelay = true;
                await HandleIncomingClientAsync(incoming, token);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) when (token.IsCancellationRequested) { break; }
            catch (SocketException) when (token.IsCancellationRequested) { break; }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                    await Dispatcher.InvokeAsync(() => Log($"Connection listener error: {ex.Message}"));
            }
            finally
            {
                if (incoming is not null)
                {
                    try { incoming.Close(); } catch { }
                }
            }
        }
    }

    private async Task HandleIncomingClientAsync(TcpClient incoming, CancellationToken token)
    {
        using var stream = incoming.GetStream();
        using var reader = new StreamReader(stream, new UTF8Encoding(false), false, 8192, leaveOpen: true);
        using var writer = new StreamWriter(stream, new UTF8Encoding(false), 8192, leaveOpen: true)
        {
            AutoFlush = true,
            NewLine = "\n"
        };

        var hello = await reader.ReadLineAsync(token);
        if (string.IsNullOrWhiteSpace(hello) || hello.Length > 4096 || !IsValidHandshake(hello))
        {
            await writer.WriteLineAsync("{\"type\":\"denied\"}");
            await Dispatcher.InvokeAsync(() => Log("Rejected a connection attempt: invalid or missing 6-digit connection code."));
            return;
        }

        await writer.WriteLineAsync("{\"type\":\"welcome\",\"protocol\":2}");
        _client = incoming;
        _writer = writer;
        await Dispatcher.InvokeAsync(() =>
        {
            SetConnectionBadge("TABLET CONNECTED", Brushes.LightGreen);
            PreviewStatusText.Text = "Connected · receiving tablet page and pen strokes live.";
            Log($"Authenticated tablet connection from {incoming.Client.RemoteEndPoint}.");
        });

        try
        {
            while (!token.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(token);
                if (line is null) break;
                if (line.Length == 0 || line.Length > MaxFrameLength) continue;
                await Dispatcher.InvokeAsync(() => ProcessFrame(line));
            }
        }
        catch (OperationCanceledException) { }
        catch (IOException ex)
        {
            if (!token.IsCancellationRequested)
                await Dispatcher.InvokeAsync(() => Log($"Tablet stream closed: {ex.Message}"));
        }
        finally
        {
            if (ReferenceEquals(_client, incoming))
            {
                _client = null;
                _writer = null;
                await Dispatcher.InvokeAsync(() =>
                {
                    if (_listener is not null)
                    {
                        SetConnectionBadge("WI-FI READY · WAITING FOR TABLET", Brushes.LightSkyBlue);
                        PreviewStatusText.Text = "Tablet disconnected. Your last page remains in the preview until a new page is sent.";
                    }
                    Log("Tablet disconnected. InkBridge is ready for another connection.");
                });
            }
        }
    }

    private bool IsValidHandshake(string line)
    {
        try
        {
            using var document = JsonDocument.Parse(line);
            var root = document.RootElement;
            if (root.GetProperty("type").GetString() != "hello") return false;
            var supplied = root.GetProperty("token").GetString() ?? "";
            var expectedBytes = Encoding.UTF8.GetBytes(_pairingCode);
            var suppliedBytes = Encoding.UTF8.GetBytes(supplied);
            return suppliedBytes.Length == expectedBytes.Length &&
                   CryptographicOperations.FixedTimeEquals(suppliedBytes, expectedBytes);
        }
        catch { return false; }
    }

    private void ProcessFrame(string line)
    {
        try
        {
            using var json = JsonDocument.Parse(line);
            if (!json.RootElement.TryGetProperty("type", out var typeElement)) return;
            var type = typeElement.GetString();
            if (type is not ("paper" or "begin" or "point" or "end" or "undo" or "redo" or "clear")) return;

            // Always mirror the tablet's page and ink inside this application.
            PreviewCanvas.HandleFrame(line);

            // The transparent Windows presentation overlay is intentionally optional.
            if (type == "begin" && !_overlaySuppressed) EnsureOverlayVisible();
            _overlay?.HandleFrame(line);
        }
        catch (Exception ex)
        {
            Log($"Ignored invalid ink frame: {ex.Message}");
        }
    }

    private void EnsureOverlayVisible()
    {
        _overlay ??= new OverlayWindow();
        if (!_overlay.IsVisible)
        {
            _overlay.Show();
            _overlay.PlaceOverVirtualDesktop();
            ShowOverlayButton.IsEnabled = false;
            HideOverlayButton.IsEnabled = true;
        }
    }

    private void ShowOverlayButton_Click(object sender, RoutedEventArgs e)
    {
        _overlaySuppressed = false;
        EnsureOverlayVisible();
        Log("Desktop presentation overlay enabled. The in-app page preview remains available.");
    }

    private void HideOverlayButton_Click(object sender, RoutedEventArgs e)
    {
        _overlaySuppressed = true;
        _overlay?.Hide();
        ShowOverlayButton.IsEnabled = true;
        HideOverlayButton.IsEnabled = false;
        Log("Desktop overlay hidden. Tablet writing continues to appear in the in-app preview.");
    }

    private void UndoButton_Click(object sender, RoutedEventArgs e)
    {
        PreviewCanvas.Undo();
        _overlay?.Undo();
        _ = SendCommandToTabletAsync("undo");
    }

    private void RedoButton_Click(object sender, RoutedEventArgs e)
    {
        PreviewCanvas.Redo();
        _overlay?.Redo();
        _ = SendCommandToTabletAsync("redo");
    }

    private void ClearButton_Click(object sender, RoutedEventArgs e)
    {
        PreviewCanvas.ClearInk();
        _overlay?.ClearInk();
        _ = SendCommandToTabletAsync("clear");
    }

    private async Task SendCommandToTabletAsync(string command)
    {
        var writer = _writer;
        if (writer is null) return;
        await _sendLock.WaitAsync();
        try
        {
            if (ReferenceEquals(writer, _writer))
                await writer.WriteLineAsync($"{{\"type\":\"{command}\"}}");
        }
        catch (Exception ex) { Log($"Could not send {command} to tablet: {ex.Message}"); }
        finally { _sendLock.Release(); }
    }

    private void SetConnectionBadge(string text, Brush color)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(new Action(() => SetConnectionBadge(text, color)));
            return;
        }
        ConnectionBadge.Text = text;
        ConnectionDot.Fill = color;
    }

    private void Log(string message)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(new Action(() => Log(message)));
            return;
        }
        LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
        LogBox.ScrollToEnd();
    }

    private void CheckUpdatesButton_Click(object sender, RoutedEventArgs e) => _ = CheckUpdatesAsync(manual: true);

    private async Task CheckUpdatesAsync(bool manual)
    {
        try
        {
            var update = await UpdateService.CheckAsync();
            if (update is null)
            {
                if (manual) MessageBox.Show(this, "InkBridge is up to date.", "Updates", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            var answer = MessageBox.Show(this,
                $"InkBridge {update.TagName} is available. Download and install it now?\n\n{update.ReleaseNotes}",
                "InkBridge update available", MessageBoxButton.YesNo, MessageBoxImage.Information);
            if (answer != MessageBoxResult.Yes) return;

            Log($"Downloading update {update.TagName}…");
            await UpdateService.DownloadAndLaunchAsync(update);
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            Log($"Update check failed: {ex.Message}");
            if (manual) MessageBox.Show(this, ex.Message, "Update check failed", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        _updateTimer.Stop();
        try { _serverCts?.Cancel(); } catch { }
        try { _listener?.Stop(); } catch { }
        try { _client?.Close(); } catch { }
        _overlay?.Close();
        _serverCts?.Dispose();
        _sendLock.Dispose();
        base.OnClosed(e);
    }
}
