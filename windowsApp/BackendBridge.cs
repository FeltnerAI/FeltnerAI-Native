using System.Runtime.InteropServices;
using System.Text.Json;

namespace FeltnerAINative.Windows;

internal sealed class BackendBridge : IDisposable
{
    private readonly StateChangedCallback stateChangedCallback;
    private IntPtr handle;
    private bool disposed;

    public BackendBridge()
    {
        stateChangedCallback = OnNativeStateChanged;
        handle = feltner_bridge_create();
        feltner_bridge_set_state_callback(handle, stateChangedCallback);
    }

    public event Action? StateChanged;

    public NativeBridgeState State
    {
        get
        {
            var jsonPtr = feltner_bridge_state_json(handle);
            try
            {
                var json = Marshal.PtrToStringUTF8(jsonPtr) ?? "{}";
                return JsonSerializer.Deserialize<NativeBridgeState>(json, BridgeJson.Options) ?? new NativeBridgeState();
            }
            finally
            {
                feltner_bridge_free_string(jsonPtr);
            }
        }
    }

    public void ConnectToServer(string rawUrl) => feltner_bridge_connect_to_server(handle, rawUrl);
    public void OpenSavedProfile(string profileId) => feltner_bridge_open_saved_profile(handle, profileId);
    public void DeleteProfile(string profileId) => feltner_bridge_delete_profile(handle, profileId);
    public void Login(string username, string password) => feltner_bridge_login(handle, username, password);
    public void Logout() => feltner_bridge_logout(handle);
    public void BackToServers() => feltner_bridge_back_to_servers(handle);
    public void DismissError() => feltner_bridge_dismiss_error(handle);
    public void SelectSection(string sectionName) => feltner_bridge_select_section(handle, sectionName);
    public void SetTheme(string themeName) => feltner_bridge_set_theme(handle, themeName);
    public void ChangePassword(string current, string replacement) => feltner_bridge_change_password(handle, current, replacement);
    public void SelectModel(string modelId) => feltner_bridge_select_model(handle, modelId);
    public void OpenChat(string chatId) => feltner_bridge_open_chat(handle, chatId);
    public void NewChat() => feltner_bridge_new_chat(handle);
    public void RenameChat(string chatId, string title) => feltner_bridge_rename_chat(handle, chatId, title);
    public void DeleteChat(string chatId) => feltner_bridge_delete_chat(handle, chatId);
    public void Send(string text) => feltner_bridge_send(handle, text);
    public void Regenerate() => feltner_bridge_regenerate(handle);
    public void StopStreaming() => feltner_bridge_stop_streaming(handle);

    private void OnNativeStateChanged(IntPtr _) => StateChanged?.Invoke();

    public void Dispose()
    {
        if (disposed) return;
        disposed = true;
        if (handle != IntPtr.Zero)
        {
            feltner_bridge_set_state_callback(handle, null);
            feltner_bridge_dispose(handle);
            handle = IntPtr.Zero;
        }
    }

    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    private delegate void StateChangedCallback(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr feltner_bridge_create();

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_dispose(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_set_state_callback(IntPtr handle, StateChangedCallback? callback);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr feltner_bridge_state_json(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_free_string(IntPtr value);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_connect_to_server(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string rawUrl);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_open_saved_profile(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string profileId);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_delete_profile(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string profileId);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_login(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string password);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_logout(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_back_to_servers(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_dismiss_error(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_select_section(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string sectionName);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_set_theme(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string themeName);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_change_password(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string current,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string replacement);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_select_model(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string modelId);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_open_chat(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string chatId);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_new_chat(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_rename_chat(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string chatId,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string title);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_delete_chat(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string chatId);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
    private static extern void feltner_bridge_send(
        IntPtr handle,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string text);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_regenerate(IntPtr handle);

    [DllImport("FeltnerAINativeShared.dll", CallingConvention = CallingConvention.Cdecl)]
    private static extern void feltner_bridge_stop_streaming(IntPtr handle);
}
