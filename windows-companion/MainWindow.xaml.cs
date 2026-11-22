using InTheHand.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Diagnostics;

namespace InkBridge.Companion;

public partial class MainWindow : Window
{
    private static readonly Guid InkBridgeServiceId = Guid.Parse("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothListener? _listener;
    private BluetoothClient? _client;
    private StreamWriter? _writer;
    private CancellationTokenSource? _bluetoothCts;
    private Task? _acceptTask;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private OverlayWindow? _overlay;
    private bool _overlaySuppressed;
    private bool _isListening;
    private readonly DispatcherTimer _updateTimer = new() { Interval = TimeSpan.FromMinutes(45) };

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
        await StartBluetoothAsync();
        await CheckUpdatesAsync(manual: false);
    }

    private async void StartBluetoothButton_Click(object sender, RoutedEventArgs e) => await StartBluetoothAsync();

    private async Task StartBluetoothAsync()
    {
        if (_isListening) return;
        try
        {
            _bluetoothCts = new CancellationTokenSource();
            _listener = new BluetoothListener(InkBridgeServiceId)
            {
                ServiceName = "InkBridge"
            };
            _listener.Start();
            _isListening = true;
            StartBluetoothButton.Content = "Bluetooth ready";
            StartBluetoothButton.IsEnabled = false;
            SetConnectionBadge("WAITING FOR TABLET", Brushes.LightSkyBlue);
            Log("Bluetooth service is live. Pair the tablet with this PC, open InkBridge on Android and tap Connect.");
            _acceptTask = AcceptLoopAsync(_bluetoothCts.Token);
        }
        catch (Exception ex)
        {
            _isListening = false;
            StartBluetoothButton.Content = "Try Bluetooth again";
            StartBluetoothButton.IsEnabled = true;
            SetConnectionBadge("BLUETOOTH NOT READY", Brushes.Orange);
            Log($"Could not start the Bluetooth service: {ex.Message}");
            Log("Check that Windows Bluetooth is enabled and the Bluetooth adapter/driver supports Classic RFCOMM.");
        }
        await Task.CompletedTask;
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _listener is not null)
        {
            BluetoothClient? incoming = null;
            try
            {
                incoming = await _listener.AcceptBluetoothClientAsync();
                _client = incoming;
                await Dispatcher.InvokeAsync(() =>
                {
                    SetConnectionBadge("TABLET CONNECTED", Brushes.LightGreen);
                    Log("Tablet connected. Ready to receive pen strokes.");
                });

                using var stream = incoming.GetStream();
                using var reader = new StreamReader(stream, Encoding.UTF8, false, 2048, leaveOpen: true);
                using var writer = new StreamWriter(stream, new UTF8Encoding(false), 2048, leaveOpen: true)
                {
                    AutoFlush = true,
                    NewLine = "\n"
                };
                _writer = writer;

                while (!token.IsCancellationRequested)
                {
                    var line = await reader.ReadLineAsync(token);
                    if (line is null) break;
                    if (line.Length == 0 || line.Length > 32768) continue;
                    await Dispatcher.InvokeAsync(() => ProcessFrame(line));
                }
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) when (token.IsCancellationRequested) { break; }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                    await Dispatcher.InvokeAsync(() => Log($"Bluetooth connection ended: {ex.Message}"));
            }
            finally
            {
                _writer = null;
                if (ReferenceEquals(_client, incoming)) _client = null;
                try { incoming?.Close(); } catch { }
                if (!token.IsCancellationRequested)
                {
                    await Dispatcher.InvokeAsync(() =>
                    {
                        SetConnectionBadge("WAITING FOR TABLET", Brushes.LightSkyBlue);
                        Log("Disconnected. InkBridge is waiting for the tablet to reconnect.");
                    });
                }
            }
        }
    }

    private void ProcessFrame(string line)
    {
        try
        {
            using var json = JsonDocument.Parse(line);
            if (!json.RootElement.TryGetProperty("type", out var typeElement)) return;
            var type = typeElement.GetString();
            if (type is not ("begin" or "point" or "end" or "undo" or "redo" or "clear")) return;

            if (type == "begin" && !_overlaySuppressed)
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
            _overlay?.HandleFrame(line);
        }
        catch (Exception ex)
        {
            Log($"Ignored invalid ink frame: {ex.Message}");
        }
    }

    private void ShowOverlayButton_Click(object sender, RoutedEventArgs e)
    {
        _overlaySuppressed = false;
        _overlay ??= new OverlayWindow();
        if (!_overlay.IsVisible) _overlay.Show();
        _overlay.PlaceOverVirtualDesktop();
        ShowOverlayButton.IsEnabled = false;
        HideOverlayButton.IsEnabled = true;
        Log("Overlay shown. It is click-through; continue using the keyboard and mouse normally.");
    }

    private void HideOverlayButton_Click(object sender, RoutedEventArgs e)
    {
        _overlaySuppressed = true;
        _overlay?.Hide();
        ShowOverlayButton.IsEnabled = true;
        HideOverlayButton.IsEnabled = false;
        Log("Overlay hidden. New incoming strokes are retained after you show it again.");
    }

    private void UndoButton_Click(object sender, RoutedEventArgs e)
    {
        _overlay?.Undo();
        _ = SendCommandToTabletAsync("undo");
    }

    private void RedoButton_Click(object sender, RoutedEventArgs e)
    {
        _overlay?.Redo();
        _ = SendCommandToTabletAsync("redo");
    }

    private void ClearButton_Click(object sender, RoutedEventArgs e)
    {
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
        ConnectionBadge.Foreground = color;
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

    private async void CheckUpdatesButton_Click(object sender, RoutedEventArgs e) => await CheckUpdatesAsync(manual: true);

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
        try { _bluetoothCts?.Cancel(); } catch { }
        try { _listener?.Stop(); } catch { }
        try { _client?.Close(); } catch { }
        try { _listener?.Dispose(); } catch { }
        _overlay?.Close();
        _bluetoothCts?.Dispose();
        _sendLock.Dispose();
        base.OnClosed(e);
    }
}
