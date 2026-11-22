using System.IO.Ports;
using System.Text;
using System.Windows;
using System.Windows.Threading;
using System.Diagnostics;

namespace InkBridge.Companion;

public partial class MainWindow : Window
{
    private SerialPort? _port;
    private OverlayWindow? _overlay;
    private bool _overlaySuppressed;
    private readonly StringBuilder _receiveBuffer = new();
    private readonly object _bufferLock = new();

    public MainWindow()
    {
        InitializeComponent();
        RefreshPorts();
        Log("Ready. Configure an incoming Bluetooth COM port, open it here, then connect from Android.");
        HideOverlayButton.IsEnabled = false;
    }

    private void RefreshPorts()
    {
        try
        {
            var previous = PortCombo.SelectedItem as string;
            var ports = SerialPort.GetPortNames().OrderBy(PortSortKey).ToArray();
            PortCombo.ItemsSource = ports;
            if (previous is not null && ports.Contains(previous)) PortCombo.SelectedItem = previous;
            else if (ports.Length > 0) PortCombo.SelectedIndex = 0;
            Log(ports.Length == 0 ? "No serial ports found yet. Add an Incoming Bluetooth COM port in classic Bluetooth settings." : $"Detected COM ports: {string.Join(", ", ports)}");
        }
        catch (Exception ex)
        {
            Log($"Could not enumerate COM ports: {ex.Message}");
        }
    }

    private static string PortSortKey(string name)
    {
        var digits = new string(name.Where(char.IsDigit).ToArray());
        return digits.PadLeft(8, '0');
    }

    private void RefreshButton_Click(object sender, RoutedEventArgs e) => RefreshPorts();

    private void ConnectButton_Click(object sender, RoutedEventArgs e)
    {
        if (_port?.IsOpen == true)
        {
            DisconnectPort();
            return;
        }

        if (PortCombo.SelectedItem is not string portName)
        {
            Log("Select an incoming Bluetooth COM port first.");
            return;
        }

        try
        {
            _port = new SerialPort(portName, 115200, Parity.None, 8, StopBits.One)
            {
                Handshake = Handshake.None,
                DtrEnable = false,
                RtsEnable = false,
                ReadTimeout = 500,
                WriteTimeout = 500,
                NewLine = "\n",
                Encoding = System.Text.Encoding.UTF8
            };
            _port.DataReceived += Port_DataReceived;
            _port.Open();
            ConnectButton.Content = "Close port";
            PortCombo.IsEnabled = false;
            RefreshButton.IsEnabled = false;
            ConnectionBadge.Text = $"WAITING ON {portName}";
            ConnectionBadge.Foreground = System.Windows.Media.Brushes.LightSkyBlue;
            Log($"Opened {portName}. Now choose this paired laptop in InkBridge on Android.");
        }
        catch (Exception ex)
        {
            Log($"Could not open {portName}: {ex.Message}");
            DisconnectPort();
        }
    }

    private void Port_DataReceived(object sender, SerialDataReceivedEventArgs e)
    {
        try
        {
            var chunk = _port?.ReadExisting();
            if (string.IsNullOrEmpty(chunk)) return;
            List<string> completeLines = new();
            lock (_bufferLock)
            {
                _receiveBuffer.Append(chunk);
                while (true)
                {
                    var current = _receiveBuffer.ToString();
                    var newline = current.IndexOf('\n');
                    if (newline < 0) break;
                    var line = current[..newline].Trim('\r', ' ', '\t');
                    _receiveBuffer.Remove(0, newline + 1);
                    if (line.Length > 0) completeLines.Add(line);
                }
                // A malformed or disconnected stream must not grow memory indefinitely.
                if (_receiveBuffer.Length > 32_768) _receiveBuffer.Clear();
            }

            foreach (var line in completeLines)
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Render, new Action(() =>
                {
                    try
                    {
                        _overlay ??= new OverlayWindow();
                        if (!_overlaySuppressed && !_overlay.IsVisible)
                        {
                            _overlay.Show();
                            _overlay.PlaceOverVirtualDesktop();
                            ShowOverlayButton.IsEnabled = false;
                            HideOverlayButton.IsEnabled = true;
                        }
                        _overlay.HandleFrame(line);
                        if (ConnectionBadge.Text.StartsWith("WAITING", StringComparison.Ordinal))
                        {
                            ConnectionBadge.Text = "TABLET CONNECTED";
                            ConnectionBadge.Foreground = System.Windows.Media.Brushes.LightGreen;
                            Log("Tablet data received. Ink is streaming to the overlay.");
                        }
                    }
                    catch (Exception ex) { Log($"Ignored invalid frame: {ex.Message}"); }
                }));
            }
        }
        catch (Exception ex)
        {
            Dispatcher.BeginInvoke(new Action(() => Log($"Bluetooth serial read error: {ex.Message}")));
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
        Log("Overlay shown. It is click-through; draw from the tablet.");
    }

    private void HideOverlayButton_Click(object sender, RoutedEventArgs e)
    {
        _overlaySuppressed = true;
        _overlay?.Hide();
        ShowOverlayButton.IsEnabled = true;
        HideOverlayButton.IsEnabled = false;
        Log("Overlay hidden.");
    }

    private void UndoButton_Click(object sender, RoutedEventArgs e)
    {
        _overlay?.Undo();
        SendCommandToTablet("undo");
    }

    private void RedoButton_Click(object sender, RoutedEventArgs e)
    {
        _overlay?.Redo();
        SendCommandToTablet("redo");
    }

    private void ClearButton_Click(object sender, RoutedEventArgs e)
    {
        _overlay?.ClearInk();
        SendCommandToTablet("clear");
    }

    private void SendCommandToTablet(string command)
    {
        if (_port?.IsOpen != true) return;
        try { _port.WriteLine($"{{\"type\":\"{command}\"}}"); }
        catch (Exception ex) { Log($"Could not send {command} to tablet: {ex.Message}"); }
    }

    private void BluetoothSettingsButton_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo("control.exe", "bthprops.cpl") { UseShellExecute = true }); }
        catch (Exception ex) { Log($"Could not open classic Bluetooth settings: {ex.Message}"); }
    }

    private void DisconnectPort()
    {
        try
        {
            if (_port is not null)
            {
                _port.DataReceived -= Port_DataReceived;
                if (_port.IsOpen) _port.Close();
                _port.Dispose();
            }
        }
        catch (Exception ex) { Log($"Port close warning: {ex.Message}"); }
        _port = null;
        lock (_bufferLock) _receiveBuffer.Clear();
        ConnectButton.Content = "Open port";
        PortCombo.IsEnabled = true;
        RefreshButton.IsEnabled = true;
        ConnectionBadge.Text = "NOT CONNECTED";
        ConnectionBadge.Foreground = System.Windows.Media.Brushes.LightGray;
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

    protected override void OnClosed(EventArgs e)
    {
        _overlay?.Close();
        DisconnectPort();
        base.OnClosed(e);
    }
}
