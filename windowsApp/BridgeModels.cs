using System.Text.Json;
using System.Text.Json.Serialization;

namespace FeltnerAINative.Windows;

internal static class BridgeJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.CamelCase) },
    };
}

internal enum Role
{
    Admin,
    User,
}

internal enum Theme
{
    Light,
    Dark,
    System,
}

internal enum MessageRole
{
    User,
    Assistant,
}

internal enum MessageStatus
{
    Complete,
    Streaming,
    Canceled,
    Error,
}

internal sealed class NativeBridgeState
{
    public string Screen { get; set; } = "SERVERS";
    public string? LoginProfileId { get; set; }
    public string? LoginServerName { get; set; }
    public List<ServerProfile> Profiles { get; set; } = new();
    public bool Busy { get; set; }
    public string? Error { get; set; }
    public Theme ThemePref { get; set; } = Theme.System;
    public string ServerName { get; set; } = "FeltnerAI";
    public string Section { get; set; } = "CHATS";
    public User? User { get; set; }
    public List<Model> Models { get; set; } = new();
    public string? SelectedModelId { get; set; }
    public List<Chat> Chats { get; set; } = new();
    public string? ActiveChatId { get; set; }
    public List<Message> Messages { get; set; } = new();
    public bool Streaming { get; set; }
    public bool IsAdmin { get; set; }
}

internal sealed class ServerProfile
{
    public string Id { get; set; } = "";
    public string ServerUuid { get; set; } = "";
    public string Name { get; set; } = "";
    public string Url { get; set; } = "";
    public bool AllowInsecureHttp { get; set; }
    public string LastUsedAt { get; set; } = "";
}

internal sealed class User
{
    public string Id { get; set; } = "";
    public string Username { get; set; } = "";
    public string? Email { get; set; }
    public Role Role { get; set; }
    public bool Disabled { get; set; }
    public bool MustChangePassword { get; set; }
    public Theme Theme { get; set; } = Theme.System;
    public string CreatedAt { get; set; } = "";
}

internal sealed class Model
{
    public string Id { get; set; } = "";

    [JsonPropertyName("provider_id")]
    public string ProviderId { get; set; } = "";

    [JsonPropertyName("provider_name")]
    public string ProviderName { get; set; } = "";

    [JsonPropertyName("upstream_id")]
    public string UpstreamId { get; set; } = "";

    [JsonPropertyName("display_name")]
    public string DisplayName { get; set; } = "";

    public bool Enabled { get; set; }

    [JsonPropertyName("is_default")]
    public bool IsDefault { get; set; }

    public override string ToString() => string.IsNullOrWhiteSpace(ProviderName)
        ? DisplayName
        : $"{DisplayName} ({ProviderName})";
}

internal sealed class Chat
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";

    [JsonPropertyName("model_id")]
    public string? ModelId { get; set; }

    [JsonPropertyName("created_at")]
    public string CreatedAt { get; set; } = "";

    [JsonPropertyName("updated_at")]
    public string UpdatedAt { get; set; } = "";
}

internal sealed class Message
{
    public string Id { get; set; } = "";

    [JsonPropertyName("chat_id")]
    public string ChatId { get; set; } = "";

    public MessageRole Role { get; set; }
    public string Content { get; set; } = "";
    public MessageStatus Status { get; set; }

    [JsonPropertyName("model_id")]
    public string? ModelId { get; set; }

    [JsonPropertyName("provider_name")]
    public string? ProviderName { get; set; }

    [JsonPropertyName("model_name")]
    public string? ModelName { get; set; }

    [JsonPropertyName("created_at")]
    public string CreatedAt { get; set; } = "";
}
