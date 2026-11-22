using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace InkBridge.Companion;

/// <summary>
/// Live in-app page preview. Coordinates use the normalized page coordinate
/// system sent by Android, so the whole document page is shown inside the desktop app.
/// </summary>
public sealed class InkCanvasView : Canvas
{
    private sealed record InkPoint(double X, double Y, double Pressure);

    private sealed class InkStroke
    {
        public string Mode { get; init; } = "draw";
        public Color Color { get; init; } = Colors.Black;
        public double WidthRatio { get; init; } = 0.006;
        public List<InkPoint> Points { get; } = new();
        public bool Visible { get; set; } = true;
    }

    private sealed record HistoryAction(string Kind, List<InkStroke> Strokes);

    private readonly List<InkStroke> _strokes = new();
    private readonly List<HistoryAction> _undo = new();
    private readonly List<HistoryAction> _redo = new();
    private InkStroke? _current;
    private ImageSource? _paperImage;
    private double _pageAspectRatio = 0.75;

    public InkCanvasView()
    {
        Background = Brushes.Transparent;
        ClipToBounds = true;
        IsHitTestVisible = false;
        SizeChanged += (_, _) => InvalidateVisual();
    }

    public void HandleFrame(string jsonLine)
    {
        try
        {
            using var document = JsonDocument.Parse(jsonLine);
            var root = document.RootElement;
            var type = root.TryGetProperty("type", out var typeValue) ? typeValue.GetString() : null;
            switch (type)
            {
                case "paper":
                    SetPaper(root);
                    break;
                case "begin":
                    BeginStroke(root);
                    break;
                case "point":
                    AddPoint(root);
                    break;
                case "end":
                    EndStroke();
                    break;
                case "undo":
                    Undo();
                    break;
                case "redo":
                    Redo();
                    break;
                case "clear":
                    ClearInk();
                    break;
            }
            InvalidateVisual();
        }
        catch (Exception)
        {
            // A malformed or unsupported frame must not terminate the live connection.
        }
    }

    private void SetPaper(JsonElement root)
    {
        if (_current is not null) EndStroke();
        _strokes.Clear();
        _undo.Clear();
        _redo.Clear();
        _paperImage = null;

        if (root.TryGetProperty("aspect", out var aspect))
            _pageAspectRatio = Math.Clamp(aspect.GetDouble(), 0.3, 3.2);

        if (root.TryGetProperty("image", out var imageValue))
        {
            var encoded = imageValue.GetString();
            if (!string.IsNullOrWhiteSpace(encoded))
            {
                var bytes = Convert.FromBase64String(encoded);
                using var stream = new MemoryStream(bytes);
                var image = new BitmapImage();
                image.BeginInit();
                image.CacheOption = BitmapCacheOption.OnLoad;
                image.StreamSource = stream;
                image.EndInit();
                image.Freeze();
                _paperImage = image;
            }
        }
    }

    private void BeginStroke(JsonElement root)
    {
        if (_current is not null) EndStroke();
        var mode = root.TryGetProperty("mode", out var modeValue) ? modeValue.GetString() ?? "draw" : "draw";
        var colorText = root.TryGetProperty("color", out var colorValue) ? colorValue.GetString() ?? "#FF19202B" : "#FF19202B";
        var color = Colors.Black;
        try { color = (Color)ColorConverter.ConvertFromString(colorText)!; } catch { }
        var width = root.TryGetProperty("width", out var widthValue)
            ? Math.Clamp(widthValue.GetDouble(), 0.0005, 0.2)
            : 0.006;

        _current = new InkStroke { Mode = mode, Color = color, WidthRatio = width };
        AddPoint(root);
    }

    private void AddPoint(JsonElement root)
    {
        if (_current is null) return;
        if (!root.TryGetProperty("x", out var xValue) || !root.TryGetProperty("y", out var yValue)) return;
        var x = Math.Clamp(xValue.GetDouble(), 0, 1);
        var y = Math.Clamp(yValue.GetDouble(), 0, 1);
        var pressure = root.TryGetProperty("p", out var pressureValue)
            ? Math.Clamp(pressureValue.GetDouble(), 0.15, 1.0)
            : 0.55;
        _current.Points.Add(new InkPoint(x, y, pressure));
    }

    private void EndStroke()
    {
        if (_current is null) return;
        var finished = _current;
        _current = null;

        if (finished.Mode == "erase")
        {
            var removed = _strokes.Where(stroke => stroke.Visible && IsHitByEraser(stroke, finished)).ToList();
            if (removed.Count > 0)
            {
                foreach (var stroke in removed) stroke.Visible = false;
                _undo.Add(new HistoryAction("erase", removed));
                _redo.Clear();
            }
            return;
        }

        if (finished.Points.Count > 0)
        {
            _strokes.Add(finished);
            _undo.Add(new HistoryAction("draw", new List<InkStroke> { finished }));
            _redo.Clear();
        }
    }

    private bool IsHitByEraser(InkStroke candidate, InkStroke eraser)
    {
        if (candidate.Points.Count == 0 || eraser.Points.Count == 0) return false;
        var page = GetPageRect();
        var minDimension = Math.Max(1, Math.Min(page.Width, page.Height));
        var radius = Math.Max(10, eraser.WidthRatio * minDimension * 1.25);

        foreach (var erasePoint in eraser.Points)
        {
            var ep = ToPixel(erasePoint, page);
            foreach (var candidatePoint in candidate.Points)
            {
                var cp = ToPixel(candidatePoint, page);
                if ((ep - cp).Length <= radius) return true;
            }
        }

        if (eraser.Points.Count > 1)
        {
            for (var i = 1; i < eraser.Points.Count; i++)
            {
                var a = ToPixel(eraser.Points[i - 1], page);
                var b = ToPixel(eraser.Points[i], page);
                foreach (var candidatePoint in candidate.Points)
                    if (DistanceToSegment(ToPixel(candidatePoint, page), a, b) <= radius) return true;
            }
        }
        return false;
    }

    private static double DistanceToSegment(Point point, Point a, Point b)
    {
        var dx = b.X - a.X;
        var dy = b.Y - a.Y;
        var lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.000001) return (point - a).Length;
        var t = Math.Clamp(((point.X - a.X) * dx + (point.Y - a.Y) * dy) / lengthSquared, 0, 1);
        return (point - new Point(a.X + t * dx, a.Y + t * dy)).Length;
    }

    public void Undo()
    {
        if (_current is not null) EndStroke();
        if (_undo.Count == 0) return;
        var action = _undo[^1];
        _undo.RemoveAt(_undo.Count - 1);
        foreach (var stroke in action.Strokes) stroke.Visible = action.Kind != "draw";
        _redo.Add(action);
        InvalidateVisual();
    }

    public void Redo()
    {
        if (_current is not null) EndStroke();
        if (_redo.Count == 0) return;
        var action = _redo[^1];
        _redo.RemoveAt(_redo.Count - 1);
        foreach (var stroke in action.Strokes) stroke.Visible = action.Kind == "draw";
        _undo.Add(action);
        InvalidateVisual();
    }

    public void ClearInk()
    {
        if (_current is not null) EndStroke();
        var visible = _strokes.Where(stroke => stroke.Visible).ToList();
        if (visible.Count == 0) return;
        foreach (var stroke in visible) stroke.Visible = false;
        _undo.Add(new HistoryAction("erase", visible));
        _redo.Clear();
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        base.OnRender(drawingContext);
        var bounds = new Rect(0, 0, Math.Max(0, ActualWidth), Math.Max(0, ActualHeight));
        drawingContext.DrawRectangle(new SolidColorBrush(Color.FromRgb(24, 35, 51)), null, bounds);

        var page = GetPageRect();
        drawingContext.DrawRectangle(Brushes.White, new Pen(new SolidColorBrush(Color.FromRgb(205, 215, 228)), 1), page);
        if (_paperImage is not null) drawingContext.DrawImage(_paperImage, page);

        foreach (var stroke in _strokes)
            if (stroke.Visible) DrawStroke(drawingContext, stroke, page);
        if (_current is not null && _current.Mode != "erase")
            DrawStroke(drawingContext, _current, page);
    }

    private Rect GetPageRect()
    {
        var availableWidth = Math.Max(1, ActualWidth - 36);
        var availableHeight = Math.Max(1, ActualHeight - 36);
        var width = availableWidth;
        var height = width / _pageAspectRatio;
        if (height > availableHeight)
        {
            height = availableHeight;
            width = height * _pageAspectRatio;
        }
        return new Rect((ActualWidth - width) / 2, (ActualHeight - height) / 2, width, height);
    }

    private static Point ToPixel(InkPoint point, Rect page) =>
        new(page.Left + point.X * page.Width, page.Top + point.Y * page.Height);

    private static void DrawStroke(DrawingContext context, InkStroke stroke, Rect page)
    {
        if (stroke.Points.Count == 0) return;
        var minDimension = Math.Max(1, Math.Min(page.Width, page.Height));
        var first = stroke.Points[0];
        if (stroke.Points.Count == 1)
        {
            var diameter = Math.Max(1.1, stroke.WidthRatio * minDimension * (0.48 + first.Pressure * 0.82));
            context.DrawEllipse(new SolidColorBrush(stroke.Color), null, ToPixel(first, page), diameter / 2, diameter / 2);
            return;
        }

        for (var i = 1; i < stroke.Points.Count; i++)
        {
            var previous = stroke.Points[i - 1];
            var current = stroke.Points[i];
            var pressure = (previous.Pressure + current.Pressure) / 2;
            var thickness = Math.Max(1.1, stroke.WidthRatio * minDimension * (0.48 + pressure * 0.82));
            var pen = new Pen(new SolidColorBrush(stroke.Color), thickness)
            {
                StartLineCap = PenLineCap.Round,
                EndLineCap = PenLineCap.Round,
                LineJoin = PenLineJoin.Round
            };
            context.DrawLine(pen, ToPixel(previous, page), ToPixel(current, page));
        }
    }
}
