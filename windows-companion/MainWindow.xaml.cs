using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Input;
using Microsoft.Win32;
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
    private bool _captureSelectionActive;
    private Point _captureStart;
    private Point _captureEnd;
    private readonly Dictionary<string, IncomingFileTransfer> _incomingFiles = new();
    private sealed class IncomingFileTransfer
    {
        public required string Name { get; init; }
        public required long ExpectedBytes { get; init; }
        public required FileStream Stream { get; init; }
        public long ReceivedBytes { get; set; }
    }

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
        _pairingCode = GeneratePairingCode();
        PairingCodeText.Text = _pairingCode;
        RefreshNetworkAddresses();
        StartWifiServer();
        await CheckUpdatesAsync(manual: false);
    }

    private static string GeneratePairingCode() => RandomNumberGenerator.GetInt32(100000, 1000000).ToString();

    private void NewPairingCodeButton_Click(object sender, RoutedEventArgs e)
    {
        if (_client is not null)
        {
            MessageBox.Show(this, "Disconnect the tablet before changing the session code.",
                "InkBridge session", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        _pairingCode = GeneratePairingCode();
        PairingCodeText.Text = _pairingCode;
        Log("A new session code was generated. The previous code is no longer valid.");
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
            if (type is "file_start" or "file_chunk" or "file_end") { ProcessIncomingFileFrame(json.RootElement); return; }
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


    private void CapturePageButton_Click(object sender, RoutedEventArgs e) => SavePreviewCapture(null);
    private void CopyPageButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            int width=(int)Math.Ceiling(PreviewCanvas.ActualWidth), height=(int)Math.Ceiling(PreviewCanvas.ActualHeight);
            if(width<2||height<2) throw new InvalidOperationException("The tablet preview is not ready yet.");
            var bitmap=new RenderTargetBitmap(width,height,96,96,PixelFormats.Pbgra32);
            bitmap.Render(PreviewCanvas);
            Clipboard.SetImage(bitmap);
            PreviewStatusText.Text="Page copied to clipboard.";
            Log("Tablet page copied to the Windows clipboard.");
        }
        catch(Exception ex){MessageBox.Show(this,ex.Message,"Copy failed",MessageBoxButton.OK,MessageBoxImage.Warning);}
    }
    private void SelectCaptureAreaButton_Click(object sender, RoutedEventArgs e)
    {
        _captureSelectionActive = true;
        CaptureSelectionRect.Visibility = Visibility.Collapsed;
        CaptureSelectionLayer.Cursor = Cursors.Cross;
        PreviewStatusText.Text = "Drag over the page to select an area · Esc to cancel";
        Log("Select an area inside the tablet preview.");
    }
    private void CaptureSelectionLayer_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (!_captureSelectionActive) return;
        _captureStart = e.GetPosition(CaptureSelectionLayer);
        _captureEnd = _captureStart;
        CaptureSelectionRect.Visibility = Visibility.Visible;
        CaptureSelectionLayer.CaptureMouse();
        UpdateCaptureSelection();
    }
    private void CaptureSelectionLayer_MouseMove(object sender, MouseEventArgs e)
    {
        if (!_captureSelectionActive || !CaptureSelectionLayer.IsMouseCaptured) return;
        _captureEnd = e.GetPosition(CaptureSelectionLayer);
        UpdateCaptureSelection();
    }
    private void CaptureSelectionLayer_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (!_captureSelectionActive || !CaptureSelectionLayer.IsMouseCaptured) return;
        _captureEnd = e.GetPosition(CaptureSelectionLayer);
        CaptureSelectionLayer.ReleaseMouseCapture();
        _captureSelectionActive = false;
        CaptureSelectionLayer.Cursor = Cursors.Arrow;
        CaptureSelectionRect.Visibility = Visibility.Collapsed;
        var area = MakeCaptureRect(_captureStart, _captureEnd);
        if (area.Width < 8 || area.Height < 8) { PreviewStatusText.Text = "Selection too small."; return; }
        SavePreviewCapture(area);
    }
    private void UpdateCaptureSelection()
    {
        var r = MakeCaptureRect(_captureStart, _captureEnd);
        Canvas.SetLeft(CaptureSelectionRect, r.Left); Canvas.SetTop(CaptureSelectionRect, r.Top);
        CaptureSelectionRect.Width = r.Width; CaptureSelectionRect.Height = r.Height;
    }
    private static Rect MakeCaptureRect(Point a, Point b) => new(Math.Min(a.X,b.X),Math.Min(a.Y,b.Y),Math.Abs(a.X-b.X),Math.Abs(a.Y-b.Y));
    private void SavePreviewCapture(Rect? crop)
    {
        try
        {
            int width=(int)Math.Ceiling(PreviewCanvas.ActualWidth), height=(int)Math.Ceiling(PreviewCanvas.ActualHeight);
            if(width<2||height<2) throw new InvalidOperationException("The tablet preview is not ready yet.");
            var bitmap=new RenderTargetBitmap(width,height,96,96,PixelFormats.Pbgra32); bitmap.Render(PreviewCanvas);
            BitmapSource output=bitmap;
            if(crop is Rect r)
            {
                int px=Math.Clamp((int)Math.Floor(r.X),0,width-1), py=Math.Clamp((int)Math.Floor(r.Y),0,height-1);
                int pw=Math.Clamp((int)Math.Ceiling(r.Width),1,width-px), ph=Math.Clamp((int)Math.Ceiling(r.Height),1,height-py);
                output=new CroppedBitmap(bitmap,new Int32Rect(px,py,pw,ph));
            }
            var dialog=new SaveFileDialog { Title="Save InkBridge capture",Filter="PNG image|*.png",FileName=$"InkBridge-{DateTime.Now:yyyyMMdd-HHmmss}.png",AddExtension=true,DefaultExt=".png" };
            if(dialog.ShowDialog(this)!=true)return;
            using var file=File.Create(dialog.FileName);
            var encoder=new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(output)); encoder.Save(file);
            PreviewStatusText.Text="Capture saved."; Log($"Capture saved: {dialog.FileName}");
        }
        catch(Exception ex){MessageBox.Show(this,ex.Message,"Capture failed",MessageBoxButton.OK,MessageBoxImage.Warning);}
    }
    private async void SendFileButton_Click(object sender, RoutedEventArgs e)
    {
        if(_writer is null){MessageBox.Show(this,"Connect the tablet before sending a file.");return;}
        var dialog=new OpenFileDialog{Title="Choose a file to send to the tablet",CheckFileExists=true};
        if(dialog.ShowDialog(this)!=true)return;
        var info=new FileInfo(dialog.FileName); const int chunkSize=256*1024;
        if(info.Length>100L*1024*1024){MessageBox.Show(this,"Files larger than 100 MB are not supported yet.");return;}
        var id=Guid.NewGuid().ToString("N"); var name=Path.GetFileName(dialog.FileName);
        var total=(int)Math.Ceiling(info.Length/(double)chunkSize);
        try
        {
            await SendJsonFrameAsync(new{type="file_start",id,name,size=info.Length,total});
            await using var source=File.OpenRead(dialog.FileName); var buffer=new byte[chunkSize]; int index=0,read;
            while((read=await source.ReadAsync(buffer.AsMemory(0,buffer.Length)))>0)
                await SendJsonFrameAsync(new{type="file_chunk",id,index=index++,data=Convert.ToBase64String(buffer,0,read)});
            await SendJsonFrameAsync(new{type="file_end",id});
            Log($"Sent {name} to tablet ({info.Length:N0} bytes).");
        }
        catch(Exception ex){Log($"File transfer failed: {ex.Message}");MessageBox.Show(this,ex.Message,"File transfer failed",MessageBoxButton.OK,MessageBoxImage.Warning);}
    }
    private async Task SendJsonFrameAsync(object frame)
    {
        var writer=_writer??throw new IOException("Tablet is disconnected.");
        var line=JsonSerializer.Serialize(frame); if(line.Length>MaxFrameLength)throw new InvalidOperationException("Transfer chunk exceeded frame limit.");
        await _sendLock.WaitAsync();
        try{if(!ReferenceEquals(writer,_writer))throw new IOException("Tablet disconnected during transfer.");await writer.WriteLineAsync(line);}
        finally{_sendLock.Release();}
    }
    private void ProcessIncomingFileFrame(JsonElement root)
    {
        var type=root.GetProperty("type").GetString(); var id=root.TryGetProperty("id",out var idValue)?idValue.GetString()??"": "";
        if(string.IsNullOrWhiteSpace(id)||id.Length>64)return;
        if(type=="file_start")
        {
            var name=Path.GetFileName(root.GetProperty("name").GetString()??"received-file");
            var size=root.GetProperty("size").GetInt64(); if(size<0||size>100L*1024*1024)throw new InvalidDataException("Incoming file exceeds 100 MB.");
            var folder=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),"InkBridge Transfers");Directory.CreateDirectory(folder);
            var path=Path.Combine(folder,$"{Path.GetFileNameWithoutExtension(name)}-{DateTime.Now:yyyyMMdd-HHmmssfff}{Path.GetExtension(name)}");
            _incomingFiles[id]=new IncomingFileTransfer{Name=path,ExpectedBytes=size,Stream=File.Create(path)};Log($"Receiving {name}…");return;
        }
        if(!_incomingFiles.TryGetValue(id,out var transfer))return;
        if(type=="file_chunk")
        {
            var bytes=Convert.FromBase64String(root.GetProperty("data").GetString()??"");
            if(transfer.ReceivedBytes+bytes.Length>transfer.ExpectedBytes)throw new InvalidDataException("Incoming transfer exceeded declared size.");
            transfer.Stream.Write(bytes);transfer.ReceivedBytes+=bytes.Length;return;
        }
        if(type=="file_end")
        {
            transfer.Stream.Dispose();_incomingFiles.Remove(id);
            if(transfer.ReceivedBytes!=transfer.ExpectedBytes)
            {
                try { File.Delete(transfer.Name); } catch { }
                throw new InvalidDataException("Received file size does not match declared size.");
            }
            Log($"File received: {transfer.Name}");PreviewStatusText.Text=$"Received {Path.GetFileName(transfer.Name)}";
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
        foreach(var transfer in _incomingFiles.Values){try{transfer.Stream.Dispose();}catch{}}
        _incomingFiles.Clear();
        _serverCts?.Dispose();
        _sendLock.Dispose();
        base.OnClosed(e);
    }
}
