using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.ApplicationModel.DataTransfer;
using Windows.Graphics;
using Windows.UI;

namespace FeltnerAINative.Windows;

public sealed class MainWindow : Window
{
    private const double FormWidth = 640;

    private readonly BackendBridge backend = new();

    private NativeBridgeState state = new();
    private string renderedView = "";
    private StackPanel? chatListPanel;
    private StackPanel? messagePanel;
    private ScrollViewer? messageScroller;
    private TextBox? draftBox;
    private Button? sendButton;
    private Button? stopButton;
    private bool showingError;

    public MainWindow()
    {
        Title = "FeltnerAI Native";
        SystemBackdrop = new MicaBackdrop();
        SizeForCurrentDisplay();

        state = backend.State;
        backend.StateChanged += () => DispatcherQueue.TryEnqueue(OnBackendStateChanged);
        Closed += (_, _) => backend.Dispose();
        Render(force: true);
    }

    private void OnBackendStateChanged()
    {
        state = backend.State;
        Render();

        if (!string.IsNullOrWhiteSpace(state.Error) && !showingError)
        {
            _ = ShowErrorAsync(state.Error);
        }
    }

    private void Render(bool force = false)
    {
        var nextView = $"{state.Screen}:{state.Section}";
        if (!force && nextView == renderedView && state.Screen == "MAIN" && state.Section == "CHATS")
        {
            RenderChatList();
            RenderMessages();
            UpdateInputState();
            return;
        }

        renderedView = nextView;
        switch (state.Screen)
        {
            case "LOGIN":
                ShowLogin();
                break;
            case "MAIN":
                ShowMain();
                break;
            default:
                ShowServers();
                break;
        }
    }

    private void ShowServers()
    {
        var root = AppRoot();
        var panel = CenterPanel();
        root.Children.Add(CenterCard(panel));

        panel.Children.Add(PageTitle("Connect to FeltnerAI"));
        panel.Children.Add(BodyText("Choose a saved server or enter a server URL."));

        var urlBox = new TextBox
        {
            Header = "Server URL",
            PlaceholderText = "chat.example.com",
            IsEnabled = !state.Busy,
            MinHeight = 44,
            FontSize = 15,
        };
        panel.Children.Add(urlBox);

        panel.Children.Add(PrimaryButton(state.Busy ? "Connecting..." : "Connect", (_, _) =>
        {
            if (!string.IsNullOrWhiteSpace(urlBox.Text))
            {
                backend.ConnectToServer(urlBox.Text);
            }
        }, enabled: !state.Busy));

        if (state.Profiles.Count > 0)
        {
            panel.Children.Add(SectionHeader("Saved servers"));
            foreach (var profile in state.Profiles)
            {
                panel.Children.Add(ProfileRow(profile));
            }
        }

        Content = root;
        ApplyTheme(root);
    }

    private UIElement ProfileRow(ServerProfile profile)
    {
        var row = new Border
        {
            Background = ThemeBrush("CardBackgroundFillColorDefaultBrush", "LayerFillColorDefaultBrush"),
            BorderBrush = ThemeBrush("CardStrokeColorDefaultBrush", "ControlStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(14),
            Child = new Grid
            {
                ColumnSpacing = 10,
                ColumnDefinitions =
                {
                    new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                    new ColumnDefinition { Width = GridLength.Auto },
                    new ColumnDefinition { Width = GridLength.Auto },
                },
            },
        };

        var grid = (Grid)row.Child;
        var text = new StackPanel { Spacing = 2 };
        text.Children.Add(SectionText(profile.Name));
        text.Children.Add(BodyText(profile.Url));
        grid.Children.Add(text);

        var open = IconButton(Symbol.OpenFile, "Open server");
        open.Click += (_, _) => backend.OpenSavedProfile(profile.Id);
        Grid.SetColumn(open, 1);
        grid.Children.Add(open);

        var delete = IconButton(Symbol.Delete, "Remove server");
        delete.Click += (_, _) => backend.DeleteProfile(profile.Id);
        Grid.SetColumn(delete, 2);
        grid.Children.Add(delete);

        return row;
    }

    private void ShowLogin()
    {
        var root = AppRoot();
        var panel = CenterPanel();
        root.Children.Add(CenterCard(panel));

        var back = IconButton(Symbol.Back, "Back to servers");
        back.HorizontalAlignment = HorizontalAlignment.Left;
        back.Click += (_, _) => backend.BackToServers();
        panel.Children.Add(back);

        panel.Children.Add(PageTitle(state.LoginServerName ?? state.ServerName));
        panel.Children.Add(BodyText("Sign in with your FeltnerAI account."));

        var username = new TextBox
        {
            Header = "Username or email",
            IsEnabled = !state.Busy,
            MinHeight = 44,
            FontSize = 15,
        };
        var password = new PasswordBox
        {
            Header = "Password",
            IsEnabled = !state.Busy,
            MinHeight = 44,
            FontSize = 15,
        };
        panel.Children.Add(username);
        panel.Children.Add(password);
        panel.Children.Add(PrimaryButton(state.Busy ? "Signing in..." : "Sign in", (_, _) =>
        {
            backend.Login(username.Text, password.Password);
        }, enabled: !state.Busy));

        Content = root;
        ApplyTheme(root);
    }

    private void ShowMain()
    {
        var nav = new NavigationView
        {
            IsBackButtonVisible = NavigationViewBackButtonVisible.Collapsed,
            IsSettingsVisible = false,
            IsPaneOpen = true,
            IsPaneToggleButtonVisible = false,
            PaneDisplayMode = NavigationViewPaneDisplayMode.Left,
            OpenPaneLength = 280,
            CompactPaneLength = 56,
            PaneTitle = state.ServerName,
            Background = ThemeBrush("ApplicationPageBackgroundThemeBrush"),
        };

        var chats = NavItem("CHATS", "Chats", Symbol.Message);
        var settings = NavItem("SETTINGS", "Settings", Symbol.Setting);
        nav.MenuItems.Add(chats);
        nav.MenuItems.Add(settings);
        nav.SelectedItem = state.Section == "SETTINGS" ? settings : chats;

        var userBlock = new StackPanel
        {
            Spacing = 8,
            Padding = new Thickness(12, 8, 12, 12),
        };
        userBlock.Children.Add(SingleLineBodyText(state.User?.Username ?? "Signed in"));
        userBlock.Children.Add(SingleLineBodyText(state.User?.Email ?? "Shared Kotlin core"));
        userBlock.Children.Add(SecondaryButton("Sign out", (_, _) => backend.Logout()));
        nav.PaneFooter = userBlock;

        nav.SelectionChanged += (_, args) =>
        {
            if (args.SelectedItem is NavigationViewItem item && item.Tag is string section && section != state.Section)
            {
                backend.SelectSection(section);
            }
        };

        nav.Content = state.Section == "SETTINGS" ? SettingsContent() : ChatContent();

        Content = nav;
        ApplyTheme(nav);
    }

    private NavigationViewItem NavItem(string tag, string label, Symbol symbol) => new()
    {
        Tag = tag,
        Content = label,
        Icon = new SymbolIcon(symbol),
    };

    private FrameworkElement ChatContent()
    {
        var root = new Grid
        {
            RowSpacing = 16,
            ColumnSpacing = 18,
            Padding = new Thickness(28, 20, 28, 28),
            Background = ThemeBrush("ApplicationPageBackgroundThemeBrush"),
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = new GridLength(340) },
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
            },
            RowDefinitions =
            {
                new RowDefinition { Height = GridLength.Auto },
                new RowDefinition { Height = new GridLength(1, GridUnitType.Star) },
                new RowDefinition { Height = GridLength.Auto },
            },
        };

        var top = new Grid
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            ColumnSpacing = 16,
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
            },
        };
        top.Children.Add(PageTitle("Chats"));
        Grid.SetColumnSpan(top, 2);

        var modelBox = new ComboBox
        {
            ItemsSource = state.Models,
            SelectedItem = state.Models.FirstOrDefault(model => model.Id == state.SelectedModelId),
            MinWidth = 260,
            PlaceholderText = state.Models.Count == 0 ? "No models" : "Select model",
        };
        modelBox.SelectionChanged += (_, _) =>
        {
            if (modelBox.SelectedItem is Model selected && selected.Id != state.SelectedModelId)
            {
                backend.SelectModel(selected.Id);
            }
        };
        Grid.SetColumn(modelBox, 1);
        top.Children.Add(modelBox);

        var newChat = PrimaryButton("New chat", (_, _) => backend.NewChat());
        Grid.SetColumn(newChat, 2);
        top.Children.Add(newChat);

        root.Children.Add(top);

        var chatsPanel = new Border
        {
            Background = ThemeBrush("CardBackgroundFillColorDefaultBrush", "LayerFillColorDefaultBrush"),
            BorderBrush = ThemeBrush("CardStrokeColorDefaultBrush", "ControlStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(10),
            Child = new ScrollViewer(),
        };
        chatListPanel = new StackPanel { Spacing = 4 };
        ((ScrollViewer)chatsPanel.Child).Content = chatListPanel;
        Grid.SetRow(chatsPanel, 1);
        Grid.SetRowSpan(chatsPanel, 2);
        root.Children.Add(chatsPanel);
        RenderChatList();

        var messagesHost = new Border
        {
            Background = ThemeBrush("CardBackgroundFillColorDefaultBrush", "LayerFillColorDefaultBrush"),
            BorderBrush = ThemeBrush("CardStrokeColorDefaultBrush", "ControlStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(16),
            Child = new ScrollViewer(),
        };
        messageScroller = (ScrollViewer)messagesHost.Child;
        messagePanel = new StackPanel { Spacing = 12 };
        messageScroller.Content = messagePanel;
        Grid.SetColumn(messagesHost, 1);
        Grid.SetRow(messagesHost, 1);
        root.Children.Add(messagesHost);

        var input = new Grid
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            ColumnSpacing = 8,
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                new ColumnDefinition { Width = GridLength.Auto },
                new ColumnDefinition { Width = GridLength.Auto },
            },
        };

        draftBox = new TextBox
        {
            PlaceholderText = "Message",
            AcceptsReturn = true,
            TextWrapping = TextWrapping.Wrap,
            MinHeight = 52,
            MaxHeight = 180,
            FontSize = 15,
        };
        input.Children.Add(draftBox);

        sendButton = IconButton(Symbol.Send, "Send");
        sendButton.Click += (_, _) =>
        {
            var text = draftBox.Text;
            draftBox.Text = "";
            backend.Send(text);
        };
        Grid.SetColumn(sendButton, 1);
        input.Children.Add(sendButton);

        stopButton = IconButton(Symbol.Stop, "Stop");
        stopButton.Click += (_, _) => backend.StopStreaming();
        Grid.SetColumn(stopButton, 2);
        input.Children.Add(stopButton);

        Grid.SetRow(input, 2);
        Grid.SetColumn(input, 1);
        root.Children.Add(input);

        RenderMessages();
        UpdateInputState();
        return root;
    }

    private FrameworkElement SettingsContent()
    {
        var root = AppRoot();
        var panel = new StackPanel
        {
            Spacing = 18,
            Padding = new Thickness(28),
            MaxWidth = 680,
            Width = 680,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Top,
        };
        root.Children.Add(panel);

        panel.Children.Add(PageTitle("Settings"));
        panel.Children.Add(SectionHeader("Appearance"));

        var themePicker = new ComboBox
        {
            ItemsSource = Enum.GetValues<Theme>(),
            SelectedItem = state.ThemePref,
            MinWidth = 240,
        };
        themePicker.SelectionChanged += (_, _) =>
        {
            if (themePicker.SelectedItem is Theme selected && selected != state.ThemePref)
            {
                backend.SetTheme(selected.ToString().ToUpperInvariant());
            }
        };
        panel.Children.Add(themePicker);

        panel.Children.Add(SectionHeader("Password"));
        var current = new PasswordBox { Header = "Current password", MinHeight = 44 };
        var replacement = new PasswordBox { Header = "New password (min 12 chars)", MinHeight = 44 };
        panel.Children.Add(current);
        panel.Children.Add(replacement);
        panel.Children.Add(PrimaryButton("Change password", (_, _) =>
        {
            backend.ChangePassword(current.Password, replacement.Password);
            current.Password = "";
            replacement.Password = "";
        }));

        panel.Children.Add(SectionHeader("Account"));
        panel.Children.Add(BodyText(state.User?.Email is null
            ? $"Signed in as {state.User?.Username}"
            : $"Signed in as {state.User.Username} - {state.User.Email}"));

        return root;
    }

    private void RenderChatList()
    {
        if (chatListPanel is null) return;

        chatListPanel.Children.Clear();
        if (state.Chats.Count == 0)
        {
            chatListPanel.Children.Add(BodyText("No chats yet."));
            return;
        }

        foreach (var chat in state.Chats)
        {
            var row = new Grid
            {
                ColumnSpacing = 4,
                ColumnDefinitions =
                {
                    new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) },
                    new ColumnDefinition { Width = GridLength.Auto },
                },
            };

            var open = new Button
            {
                Content = string.IsNullOrWhiteSpace(chat.Title) ? "Untitled chat" : chat.Title,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Left,
                Background = chat.Id == state.ActiveChatId
                    ? ThemeBrush("AccentFillColorTertiaryBrush", "SystemControlHighlightAccentBrush")
                    : ThemeBrush("SubtleFillColorTransparentBrush", "SystemControlBackgroundChromeMediumLowBrush"),
                BorderBrush = new SolidColorBrush(Colors.Transparent),
            };
            open.Click += (_, _) => backend.OpenChat(chat.Id);
            row.Children.Add(open);

            var delete = IconButton(Symbol.Delete, "Delete chat");
            delete.Click += (_, _) => backend.DeleteChat(chat.Id);
            Grid.SetColumn(delete, 1);
            row.Children.Add(delete);

            chatListPanel.Children.Add(row);
        }
    }

    private void RenderMessages()
    {
        if (messagePanel is null) return;

        messagePanel.Children.Clear();
        if (state.Messages.Count == 0)
        {
            messagePanel.Children.Add(new TextBlock
            {
                Text = "Ask anything to get started.",
                Foreground = ThemeBrush("TextFillColorSecondaryBrush"),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 90, 0, 0),
                FontSize = 16,
            });
            return;
        }

        for (var index = 0; index < state.Messages.Count; index++)
        {
            var message = state.Messages[index];
            var isUser = message.Role == MessageRole.User;
            var content = string.IsNullOrWhiteSpace(message.Content) && message.Status == MessageStatus.Streaming
                ? "..."
                : message.Content;
            var foreground = isUser
                ? ThemeBrush("TextOnAccentFillColorPrimaryBrush", "TextFillColorInverseBrush")
                : ThemeBrush("TextFillColorPrimaryBrush");
            var secondaryForeground = isUser
                ? ThemeBrush("TextOnAccentFillColorSecondaryBrush", "TextOnAccentFillColorPrimaryBrush")
                : ThemeBrush("TextFillColorSecondaryBrush");
            var codeBackground = isUser
                ? ThemeBrush("AccentFillColorSecondaryBrush", "ControlFillColorSecondaryBrush")
                : ThemeBrush("ControlFillColorSecondaryBrush", "LayerFillColorAltBrush");
            var markdownBorder = isUser
                ? ThemeBrush("AccentControlElevationBorderBrush", "ControlStrokeColorDefaultBrush")
                : ThemeBrush("ControlStrokeColorDefaultBrush");
            var bubble = new Border
            {
                MaxWidth = isUser ? 760 : 980,
                Padding = new Thickness(14),
                CornerRadius = new CornerRadius(8),
                Background = isUser
                    ? ThemeBrush("AccentFillColorDefaultBrush", "SystemControlHighlightAccentBrush")
                    : ThemeBrush("LayerFillColorDefaultBrush", "CardBackgroundFillColorDefaultBrush"),
                Child = MarkdownRenderer.Render(content, foreground, secondaryForeground, codeBackground, markdownBorder),
            };

            var stack = new StackPanel
            {
                Spacing = 4,
                HorizontalAlignment = isUser ? HorizontalAlignment.Right : HorizontalAlignment.Left,
            };
            stack.Children.Add(bubble);

            if (!isUser && !string.IsNullOrWhiteSpace(message.Content))
            {
                stack.Children.Add(MessageTools(message, canRegenerate: index == state.Messages.Count - 1 && !state.Streaming));
            }

            messagePanel.Children.Add(stack);
        }

        messageScroller?.ChangeView(null, double.MaxValue, null);
    }

    private UIElement MessageTools(Message message, bool canRegenerate)
    {
        var tools = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
        var copy = IconButton(Symbol.Copy, "Copy");
        copy.Click += (_, _) =>
        {
            var package = new DataPackage();
            package.SetText(message.Content);
            Clipboard.SetContent(package);
        };
        tools.Children.Add(copy);

        if (canRegenerate)
        {
            var regenerate = IconButton(Symbol.Refresh, "Regenerate");
            regenerate.Click += (_, _) => backend.Regenerate();
            tools.Children.Add(regenerate);
        }

        var meta = string.Join(" - ", new[] { message.ProviderName, message.ModelName }.Where(value => !string.IsNullOrWhiteSpace(value)));
        if (!string.IsNullOrWhiteSpace(meta))
        {
            tools.Children.Add(BodyText(meta));
        }

        return tools;
    }

    private void UpdateInputState()
    {
        if (draftBox is not null) draftBox.IsEnabled = !state.Streaming;
        if (sendButton is not null) sendButton.Visibility = state.Streaming ? Visibility.Collapsed : Visibility.Visible;
        if (stopButton is not null) stopButton.Visibility = state.Streaming ? Visibility.Visible : Visibility.Collapsed;
    }

    private async Task ShowErrorAsync(string message)
    {
        showingError = true;
        try
        {
            var dialog = new ContentDialog
            {
                Title = "FeltnerAI Native",
                Content = message,
                CloseButtonText = "OK",
            };
            if (Content is FrameworkElement element) dialog.XamlRoot = element.XamlRoot;
            await dialog.ShowAsync();
            backend.DismissError();
        }
        finally
        {
            showingError = false;
        }
    }

    private Grid AppRoot() => new()
    {
        Background = ThemeBrush("MicaBackgroundFillColorDefaultBrush", "ApplicationPageBackgroundThemeBrush"),
    };

    private StackPanel CenterPanel() => new()
    {
        Spacing = 18,
        Padding = new Thickness(36),
    };

    private Border CenterCard(StackPanel panel) => new()
    {
        Width = FormWidth,
        MaxWidth = FormWidth,
        Margin = new Thickness(24),
        HorizontalAlignment = HorizontalAlignment.Center,
        VerticalAlignment = VerticalAlignment.Center,
        Background = ThemeBrush("CardBackgroundFillColorDefaultBrush", "LayerFillColorDefaultBrush"),
        BorderBrush = ThemeBrush("CardStrokeColorDefaultBrush", "ControlStrokeColorDefaultBrush"),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(8),
        Child = panel,
    };

    private static TextBlock PageTitle(string text) => new()
    {
        Text = text,
        FontSize = 32,
        FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
        Foreground = ThemeBrush("TextFillColorPrimaryBrush"),
        TextWrapping = TextWrapping.Wrap,
    };

    private static TextBlock SectionHeader(string text) => new()
    {
        Text = text,
        FontSize = 18,
        FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
        Foreground = ThemeBrush("TextFillColorPrimaryBrush"),
        TextWrapping = TextWrapping.Wrap,
        Margin = new Thickness(0, 8, 0, 0),
    };

    private static TextBlock SectionText(string text) => new()
    {
        Text = text,
        FontSize = 15,
        FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
        Foreground = ThemeBrush("TextFillColorPrimaryBrush"),
        TextWrapping = TextWrapping.Wrap,
    };

    private static TextBlock BodyText(string text) => new()
    {
        Text = text,
        Foreground = ThemeBrush("TextFillColorSecondaryBrush"),
        TextWrapping = TextWrapping.Wrap,
        FontSize = 14,
    };

    private static TextBlock SingleLineBodyText(string text) => new()
    {
        Text = text,
        Foreground = ThemeBrush("TextFillColorSecondaryBrush"),
        TextWrapping = TextWrapping.NoWrap,
        TextTrimming = TextTrimming.CharacterEllipsis,
        FontSize = 13,
    };

    private static Button PrimaryButton(string text, RoutedEventHandler handler, bool enabled = true)
    {
        var button = new Button
        {
            Content = text,
            IsEnabled = enabled,
            MinWidth = 124,
            MinHeight = 40,
            HorizontalAlignment = HorizontalAlignment.Left,
            Background = enabled ? ThemeBrush("AccentFillColorDefaultBrush", "SystemControlHighlightAccentBrush") : null,
            Foreground = enabled ? ThemeBrush("TextOnAccentFillColorPrimaryBrush", "TextFillColorInverseBrush") : null,
            BorderBrush = enabled ? ThemeBrush("AccentFillColorDefaultBrush", "SystemControlHighlightAccentBrush") : null,
        };
        button.Click += handler;
        return button;
    }

    private static Button SecondaryButton(string text, RoutedEventHandler handler)
    {
        var button = new Button
        {
            Content = text,
            MinHeight = 36,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Left,
        };
        button.Click += handler;
        return button;
    }

    private static Button IconButton(Symbol symbol, string label)
    {
        var button = new Button
        {
            Content = new SymbolIcon(symbol),
            MinWidth = 40,
            MinHeight = 40,
            Padding = new Thickness(8),
        };
        ToolTipService.SetToolTip(button, label);
        return button;
    }

    private void ApplyTheme(FrameworkElement root)
    {
        root.RequestedTheme = state.ThemePref switch
        {
            Theme.Dark => ElementTheme.Dark,
            Theme.Light => ElementTheme.Light,
            _ => ElementTheme.Default,
        };
    }

    private static Brush ThemeBrush(string key, string? fallbackKey = null)
    {
        var resources = Application.Current.Resources;
        if (resources.TryGetValue(key, out var value) && value is Brush brush)
        {
            return brush;
        }

        if (fallbackKey is not null && resources.TryGetValue(fallbackKey, out var fallback) && fallback is Brush fallbackBrush)
        {
            return fallbackBrush;
        }

        if (resources.TryGetValue("ApplicationPageBackgroundThemeBrush", out var pageBrush) && pageBrush is Brush defaultBrush)
        {
            return defaultBrush;
        }

        return new SolidColorBrush(Colors.Transparent);
    }

    private void SizeForCurrentDisplay()
    {
        var display = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary);
        var workArea = display.WorkArea;
        var width = Math.Min(1440, Math.Max(1040, workArea.Width - 180));
        var height = Math.Min(900, Math.Max(720, workArea.Height - 140));
        var x = workArea.X + Math.Max(0, (workArea.Width - width) / 2);
        var y = workArea.Y + Math.Max(0, (workArea.Height - height) / 2);
        AppWindow.MoveAndResize(new RectInt32(x, y, width, height));
    }
}
