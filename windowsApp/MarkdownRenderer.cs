using Markdig;
using Markdig.Syntax;
using Markdig.Syntax.Inlines;
using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Documents;
using Microsoft.UI.Xaml.Media;
using Windows.UI;
using XamlInline = Microsoft.UI.Xaml.Documents.Inline;

namespace FeltnerAINative.Windows;

internal static class MarkdownRenderer
{
    private static readonly MarkdownPipeline Pipeline = new MarkdownPipelineBuilder()
        .UseAdvancedExtensions()
        .Build();

    private static readonly FontFamily MonoFont = new("Cascadia Mono, Consolas, Courier New");

    public static FrameworkElement Render(
        string markdown,
        Brush foreground,
        Brush secondaryForeground,
        Brush codeBackground,
        Brush borderBrush)
    {
        if (string.IsNullOrWhiteSpace(markdown))
        {
            return TextBlock("...", foreground);
        }

        var document = Markdown.Parse(markdown, Pipeline);
        var stack = new StackPanel { Spacing = 8 };
        var options = new RenderOptions(foreground, secondaryForeground, codeBackground, borderBrush);

        foreach (var block in document)
        {
            RenderBlock(block, stack, options, 0);
        }

        if (stack.Children.Count == 0)
        {
            stack.Children.Add(TextBlock(markdown, foreground));
        }

        return stack;
    }

    private static void RenderBlock(Markdig.Syntax.Block block, StackPanel target, RenderOptions options, int depth)
    {
        switch (block)
        {
            case HeadingBlock heading:
                target.Children.Add(RichBlock(heading.Inline, options.Foreground, FontSizeForHeading(heading.Level), FontWeights.SemiBold));
                break;

            case ParagraphBlock paragraph:
                target.Children.Add(RichBlock(paragraph.Inline, options.Foreground, 15, FontWeights.Normal));
                break;

            case ListBlock list:
                RenderList(list, target, options, depth);
                break;

            case QuoteBlock quote:
                target.Children.Add(QuoteBlock(quote, options, depth));
                break;

            case FencedCodeBlock fenced:
                target.Children.Add(CodeBlock(fenced.Lines.ToString(), options));
                break;

            case CodeBlock code:
                target.Children.Add(CodeBlock(code.Lines.ToString(), options));
                break;

            case ThematicBreakBlock:
                target.Children.Add(new Border
                {
                    Height = 1,
                    Background = options.BorderBrush,
                    Margin = new Thickness(0, 6, 0, 6),
                });
                break;

            case LeafBlock leaf when leaf.Inline is not null:
                target.Children.Add(RichBlock(leaf.Inline, options.Foreground, 15, FontWeights.Normal));
                break;

            case ContainerBlock container:
                foreach (var child in container)
                {
                    RenderBlock(child, target, options, depth);
                }
                break;
        }
    }

    private static void RenderList(ListBlock list, StackPanel target, RenderOptions options, int depth)
    {
        var index = 1;
        foreach (var child in list)
        {
            if (child is not ListItemBlock item) continue;

            var row = new Grid
            {
                ColumnSpacing = 8,
                Margin = new Thickness(depth * 14, 0, 0, 0),
                ColumnDefinitions =
                {
                    new ColumnDefinition { Width = GridLength.Auto },
                    new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                },
            };

            row.Children.Add(new TextBlock
            {
                Text = list.IsOrdered ? $"{index}." : "•",
                Foreground = options.SecondaryForeground,
                FontSize = 15,
                MinWidth = list.IsOrdered ? 24 : 14,
                TextAlignment = TextAlignment.Right,
            });

            var itemStack = new StackPanel { Spacing = 6 };
            foreach (var itemChild in item)
            {
                RenderBlock(itemChild, itemStack, options, depth + 1);
            }

            Grid.SetColumn(itemStack, 1);
            row.Children.Add(itemStack);
            target.Children.Add(row);
            index++;
        }
    }

    private static UIElement QuoteBlock(QuoteBlock quote, RenderOptions options, int depth)
    {
        var stack = new StackPanel { Spacing = 6 };
        foreach (var child in quote)
        {
            RenderBlock(child, stack, options, depth + 1);
        }

        return new Border
        {
            BorderBrush = options.BorderBrush,
            BorderThickness = new Thickness(3, 0, 0, 0),
            Padding = new Thickness(12, 2, 0, 2),
            Margin = new Thickness(depth * 14, 2, 0, 2),
            Child = stack,
        };
    }

    private static UIElement CodeBlock(string code, RenderOptions options) => new Border
    {
        Background = options.CodeBackground,
        BorderBrush = options.BorderBrush,
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(6),
        Padding = new Thickness(12),
        Child = new TextBlock
        {
            Text = code.TrimEnd('\r', '\n'),
            Foreground = options.Foreground,
            FontFamily = MonoFont,
            FontSize = 13,
            TextWrapping = TextWrapping.Wrap,
            IsTextSelectionEnabled = true,
        },
    };

    private static RichTextBlock RichBlock(ContainerInline? inline, Brush foreground, double fontSize, global::Windows.UI.Text.FontWeight weight)
    {
        var paragraph = new Paragraph();
        if (inline is not null)
        {
            AddInlineChildren(paragraph.Inlines, inline, foreground);
        }

        return new RichTextBlock
        {
            Foreground = foreground,
            FontSize = fontSize,
            FontWeight = weight,
            TextWrapping = TextWrapping.Wrap,
            IsTextSelectionEnabled = true,
            Blocks = { paragraph },
        };
    }

    private static void AddInlineChildren(InlineCollection target, ContainerInline container, Brush foreground)
    {
        foreach (var child in container)
        {
            target.Add(RenderInline(child, foreground));
        }
    }

    private static void AddInlineChildren(InlineCollection target, EmphasisInline container, Brush foreground)
    {
        foreach (var child in container)
        {
            target.Add(RenderInline(child, foreground));
        }
    }

    private static void AddInlineChildren(InlineCollection target, LinkInline container, Brush foreground)
    {
        foreach (var child in container)
        {
            target.Add(RenderInline(child, foreground));
        }
    }

    private static XamlInline RenderInline(Markdig.Syntax.Inlines.Inline inline, Brush foreground)
    {
        switch (inline)
        {
            case LiteralInline literal:
                return new Run { Text = literal.Content.ToString() };

            case CodeInline code:
                return new Run
                {
                    Text = code.Content,
                    FontFamily = MonoFont,
                    Foreground = foreground,
                };

            case LineBreakInline:
                return new LineBreak();

            case EmphasisInline emphasis when emphasis.DelimiterCount >= 2:
                var bold = new Bold();
                AddInlineChildren(bold.Inlines, emphasis, foreground);
                return bold;

            case EmphasisInline emphasis:
                var italic = new Italic();
                AddInlineChildren(italic.Inlines, emphasis, foreground);
                return italic;

            case LinkInline link:
                return LinkInline(link, foreground);

            case ContainerInline container:
                var span = new Span();
                AddInlineChildren(span.Inlines, container, foreground);
                return span;

            default:
                return new Run { Text = inline.ToString() };
        }
    }

    private static XamlInline LinkInline(LinkInline link, Brush foreground)
    {
        if (Uri.TryCreate(link.Url, UriKind.Absolute, out var uri))
        {
            var hyperlink = new Hyperlink { NavigateUri = uri };
            AddInlineChildren(hyperlink.Inlines, link, foreground);
            return hyperlink;
        }

        var span = new Span { Foreground = foreground };
        AddInlineChildren(span.Inlines, link, foreground);
        return span;
    }

    private static TextBlock TextBlock(string text, Brush foreground) => new()
    {
        Text = text,
        Foreground = foreground,
        TextWrapping = TextWrapping.Wrap,
        IsTextSelectionEnabled = true,
        FontSize = 15,
    };

    private static double FontSizeForHeading(int level) => level switch
    {
        1 => 24,
        2 => 21,
        3 => 18,
        _ => 16,
    };

    private sealed record RenderOptions(
        Brush Foreground,
        Brush SecondaryForeground,
        Brush CodeBackground,
        Brush BorderBrush);
}
