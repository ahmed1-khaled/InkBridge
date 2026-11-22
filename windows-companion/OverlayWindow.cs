using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Shapes;

namespace InkBridge.Companion;

/// <summary>Transparent, topmost, click-through surface over the Windows virtual desktop.</summary>
public sealed class OverlayWindow : Window
{
    private const int GwlExStyle = -20;
    private const long WsExTransparent = 0x00000020L;
    private const long WsExLayered = 0x00080000L;
    private const long WsExToolWindow = 0x00000080L;
    private const long WsExNoActivate = 0x08000000L;

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW", SetLastError = true)]
    private static extern IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW", SetLastError = true)]
    private static extern IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong);
    [DllImport("user32.dll", EntryPoint = "GetWindowLongW", SetLastError = true)]
    private static extern int GetWindowLong32(IntPtr hWnd, int nIndex);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongW", SetLastError = true)]
    private static extern int SetWindowLong32(IntPtr hWnd, int nIndex, int dwNewLong);

    private sealed record InkPoint(double X, double Y, double Pressure);
    private sealed class InkStroke
    {
        public string Mode { get; init; } = "draw";
        public string Color { get; init; } = "#FF19202B";
        public double WidthRatio { get; init; }
        public List<InkPoint> Points { get; } = new();
        public Canvas Visual { get; } = new() { IsHitTestVisible = false };
        public bool Visible { get; set; } = true;
    }
    private sealed record HistoryAction(string Kind, List<InkStroke> Strokes);

    private readonly Canvas _inkCanvas = new() { Background = Brushes.Transparent, IsHitTestVisible = false };
    private readonly List<InkStroke> _strokes = new();
    private readonly List<HistoryAction> _undo = new();
    private readonly List<HistoryAction> _redo = new();
    private InkStroke? _current;

    public OverlayWindow()
    {
        WindowStyle = WindowStyle.None;
        AllowsTransparency = true;
        Background = Brushes.Transparent;
        Opacity = 1;
        Topmost = true;
        ShowInTaskbar = false;
        ShowActivated = false;
        ResizeMode = ResizeMode.NoResize;
        Content = _inkCanvas;
        SourceInitialized += (_, _) => ApplyClickThroughStyle();
        Loaded += (_, _) => PlaceOverVirtualDesktop();
    }

    public void PlaceOverVirtualDesktop()
    {
        Left = SystemParameters.VirtualScreenLeft;
        Top = SystemParameters.VirtualScreenTop;
        Width = SystemParameters.VirtualScreenWidth;
        Height = SystemParameters.VirtualScreenHeight;
        Topmost = true;
        UpdateLayout();
    }

    private void ApplyClickThroughStyle()
    {
        var hwnd = new WindowInteropHelper(this).Handle;
        if (IntPtr.Size == 8)
        {
            long current = GetWindowLongPtr(hwnd, GwlExStyle).ToInt64();
            current |= WsExTransparent | WsExLayered | WsExToolWindow | WsExNoActivate;
            SetWindowLongPtr(hwnd, GwlExStyle, new IntPtr(current));
        }
        else
        {
            int current = GetWindowLong32(hwnd, GwlExStyle);
            current |= unchecked((int)(WsExTransparent | WsExLayered | WsExToolWindow | WsExNoActivate));
            SetWindowLong32(hwnd, GwlExStyle, current);
        }
    }

    public void HandleFrame(string jsonLine)
    {
        using var document = JsonDocument.Parse(jsonLine);
        var root = document.RootElement;
        var type = root.GetProperty("type").GetString();
        switch (type)
        {
            case "begin": BeginStroke(root); break;
            case "point": AddPoint(root); break;
            case "end": EndStroke(); break;
            case "undo": Undo(); break;
            case "redo": Redo(); break;
            case "clear": ClearInk(); break;
        }
    }

    private void BeginStroke(JsonElement root)
    {
        if (_current is not null) EndStroke();
        var mode = root.TryGetProperty("mode", out var modeValue) ? modeValue.GetString() ?? "draw" : "draw";
        var stroke = new InkStroke
        {
            Mode = mode,
            Color = root.TryGetProperty("color", out var colorValue) ? colorValue.GetString() ?? "#FF19202B" : "#FF19202B",
            WidthRatio = root.TryGetProperty("width", out var widthValue) ? Math.Clamp(widthValue.GetDouble(), 0.0005, 0.2) : 0.006
        };
        stroke.Visual.Width = ActualWidth;
        stroke.Visual.Height = ActualHeight;
        _current = stroke;
        AddPoint(root);
        if (mode != "erase") _inkCanvas.Children.Add(stroke.Visual);
    }

    private void AddPoint(JsonElement root)
    {
        if (_current is null) return;
        var point = new InkPoint(
            Math.Clamp(root.GetProperty("x").GetDouble(), 0, 1),
            Math.Clamp(root.GetProperty("y").GetDouble(), 0, 1),
            root.TryGetProperty("p", out var pressure) ? Math.Clamp(pressure.GetDouble(), 0.15, 1.0) : 0.55);
        var previous = _current.Points.LastOrDefault();
        _current.Points.Add(point);
        if (_current.Mode == "erase") return;

        var currentPx = ToPixel(point);
        var previousPx = previous is null ? currentPx : ToPixel(previous);
        var minDimension = Math.Max(1, Math.Min(ActualWidth, ActualHeight));
        var averagePressure = previous is null ? point.Pressure : (previous.Pressure + point.Pressure) / 2.0;
        var thickness = _current.WidthRatio * minDimension * (0.48 + averagePressure * 0.82);
        var line = new Line
        {
            X1 = previousPx.X, Y1 = previousPx.Y,
            X2 = currentPx.X, Y2 = currentPx.Y,
            Stroke = ParseColor(_current.Color),
            StrokeThickness = Math.Max(1.0, thickness),
            StrokeStartLineCap = PenLineCap.Round,
            StrokeEndLineCap = PenLineCap.Round,
            StrokeLineJoin = PenLineJoin.Round,
            IsHitTestVisible = false
        };
        _current.Visual.Children.Add(line);
    }

    private void EndStroke()
    {
        if (_current is null) return;
        var finished = _current;
        _current = null;
        if (finished.Mode == "erase")
        {
            var removed = _strokes.Where(s => s.Visible && IsHitByEraser(s, finished)).ToList();
            if (removed.Count > 0)
            {
                foreach (var item in removed) RemoveVisual(item);
                _undo.Add(new HistoryAction("erase", removed));
                _redo.Clear();
            }
            return;
        }
        _strokes.Add(finished);
        _undo.Add(new HistoryAction("draw", new List<InkStroke> { finished }));
        _redo.Clear();
    }

    private bool IsHitByEraser(InkStroke candidate, InkStroke eraser)
    {
        var radius = Math.Max(8, eraser.WidthRatio * Math.Max(1, Math.Min(ActualWidth, ActualHeight)) * 1.25);
        foreach (var erasePoint in eraser.Points)
        {
            var ep = ToPixel(erasePoint);
            foreach (var candidatePoint in candidate.Points)
            {
                var cp = ToPixel(candidatePoint);
                if ((ep - cp).Length <= radius) return true;
            }
        }
        // Segment-to-point checks reduce missed hits when the eraser moves quickly.
        if (eraser.Points.Count > 1)
        {
            for (var i = 1; i < eraser.Points.Count; i++)
            {
                var a = ToPixel(eraser.Points[i - 1]);
                var b = ToPixel(eraser.Points[i]);
                foreach (var candidatePoint in candidate.Points)
                    if (DistanceToSegment(ToPixel(candidatePoint), a, b) <= radius) return true;
            }
        }
        return false;
    }

    private static double DistanceToSegment(Point p, Point a, Point b)
    {
        var dx = b.X - a.X;
        var dy = b.Y - a.Y;
        if (dx == 0 && dy == 0) return (p - a).Length;
        var t = Math.Clamp(((p.X - a.X) * dx + (p.Y - a.Y) * dy) / (dx * dx + dy * dy), 0, 1);
        var projection = new Point(a.X + t * dx, a.Y + t * dy);
        return (p - projection).Length;
    }

    private Point ToPixel(InkPoint point) => new(point.X * ActualWidth, point.Y * ActualHeight);

    private static Brush ParseColor(string value)
    {
        try
        {
            var hex = value.Trim().TrimStart('#');
            byte a, r, g, b;
            if (hex.Length == 8)
            {
                a = Convert.ToByte(hex[..2], 16);
                r = Convert.ToByte(hex[2..4], 16);
                g = Convert.ToByte(hex[4..6], 16);
                b = Convert.ToByte(hex[6..8], 16);
            }
            else if (hex.Length == 6)
            {
                a = 255;
                r = Convert.ToByte(hex[..2], 16);
                g = Convert.ToByte(hex[2..4], 16);
                b = Convert.ToByte(hex[4..6], 16);
            }
            else return Brushes.Black;
            return new SolidColorBrush(Color.FromArgb(a, r, g, b));
        }
        catch { return Brushes.Black; }
    }

    private void RemoveVisual(InkStroke stroke)
    {
        _inkCanvas.Children.Remove(stroke.Visual);
        stroke.Visible = false;
    }

    private void RestoreVisual(InkStroke stroke)
    {
        if (!_inkCanvas.Children.Contains(stroke.Visual)) _inkCanvas.Children.Add(stroke.Visual);
        stroke.Visible = true;
    }

    public void Undo()
    {
        if (_current is not null) EndStroke();
        if (_undo.Count == 0) return;
        var action = _undo[^1];
        _undo.RemoveAt(_undo.Count - 1);
        if (action.Kind == "draw") foreach (var stroke in action.Strokes) RemoveVisual(stroke);
        else foreach (var stroke in action.Strokes) RestoreVisual(stroke);
        _redo.Add(action);
    }

    public void Redo()
    {
        if (_current is not null) EndStroke();
        if (_redo.Count == 0) return;
        var action = _redo[^1];
        _redo.RemoveAt(_redo.Count - 1);
        if (action.Kind == "draw") foreach (var stroke in action.Strokes) RestoreVisual(stroke);
        else foreach (var stroke in action.Strokes) RemoveVisual(stroke);
        _undo.Add(action);
    }

    public void ClearInk()
    {
        if (_current is not null) EndStroke();
        var visible = _strokes.Where(s => s.Visible).ToList();
        if (visible.Count == 0) return;
        foreach (var stroke in visible) RemoveVisual(stroke);
        _undo.Add(new HistoryAction("erase", visible));
        _redo.Clear();
    }
}
