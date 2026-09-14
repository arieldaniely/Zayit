#include <jni.h>

#include <windows.h>
#include <winerror.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.Bluetooth.h>
#include <winrt/Windows.Devices.Bluetooth.Rfcomm.h>
#include <winrt/Windows.Devices.Enumeration.h>
#include <winrt/Windows.Networking.h>
#include <winrt/Windows.Networking.Sockets.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Devices.Radios.h>

using namespace winrt;
using namespace Windows::Devices::Bluetooth;
using namespace Windows::Devices::Bluetooth::Rfcomm;
using namespace Windows::Devices::Enumeration;
using namespace Windows::Devices::Radios;
using namespace Windows::Networking;
using namespace Windows::Networking::Sockets;
using namespace Windows::Storage::Streams;

namespace {
constexpr std::uint32_t kServiceNameAttribute = 0x100;
constexpr std::uint32_t kMaximumFrameSize = 4 * 1024 * 1024;

JavaVM* g_vm = nullptr;
jobject g_owner = nullptr;
jmethodID g_state_callback = nullptr;
jmethodID g_devices_callback = nullptr;
jmethodID g_message_callback = nullptr;

struct Connection {
    StreamSocket socket{nullptr};
    DataReader reader{nullptr};
    DataWriter writer{nullptr};
    std::mutex write_mutex;
};

std::mutex g_mutex;
RfcommServiceProvider g_provider{nullptr};
StreamSocketListener g_listener{nullptr};
event_token g_connection_token{};
std::unordered_map<std::string, std::shared_ptr<Connection>> g_connections;

struct AttachedEnvironment {
    JNIEnv* env = nullptr;
    bool detach = false;
    AttachedEnvironment() {
        if (g_vm == nullptr) return;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) != JNI_OK) {
            if (g_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) == JNI_OK) detach = true;
        }
    }
    ~AttachedEnvironment() {
        if (detach && g_vm != nullptr) g_vm->DetachCurrentThread();
    }
};

std::string to_utf8(jstring value, JNIEnv* env) {
    if (value == nullptr) return {};
    auto chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throw_java(JNIEnv* env, std::string const& message) {
    auto type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

void notify_state(bool enabled) {
    AttachedEnvironment attached;
    if (attached.env != nullptr && g_owner != nullptr && g_state_callback != nullptr) {
        attached.env->CallVoidMethod(g_owner, g_state_callback, enabled ? JNI_TRUE : JNI_FALSE);
    }
}

void notify_devices(std::vector<std::pair<std::string, std::string>> const& devices) {
    AttachedEnvironment attached;
    if (attached.env == nullptr || g_owner == nullptr || g_devices_callback == nullptr) return;
    auto string_type = attached.env->FindClass("java/lang/String");
    auto ids = attached.env->NewObjectArray(static_cast<jsize>(devices.size()), string_type, nullptr);
    auto names = attached.env->NewObjectArray(static_cast<jsize>(devices.size()), string_type, nullptr);
    for (std::size_t index = 0; index < devices.size(); ++index) {
        auto id = attached.env->NewStringUTF(devices[index].first.c_str());
        auto name = attached.env->NewStringUTF(devices[index].second.c_str());
        attached.env->SetObjectArrayElement(ids, static_cast<jsize>(index), id);
        attached.env->SetObjectArrayElement(names, static_cast<jsize>(index), name);
        attached.env->DeleteLocalRef(id);
        attached.env->DeleteLocalRef(name);
    }
    attached.env->CallVoidMethod(g_owner, g_devices_callback, ids, names);
    attached.env->DeleteLocalRef(ids);
    attached.env->DeleteLocalRef(names);
    attached.env->DeleteLocalRef(string_type);
}

void notify_message(std::string const& device_id, std::vector<std::uint8_t> const& bytes) {
    AttachedEnvironment attached;
    if (attached.env == nullptr || g_owner == nullptr || g_message_callback == nullptr) return;
    auto id = attached.env->NewStringUTF(device_id.c_str());
    auto payload = attached.env->NewByteArray(static_cast<jsize>(bytes.size()));
    attached.env->SetByteArrayRegion(
        payload,
        0,
        static_cast<jsize>(bytes.size()),
        reinterpret_cast<jbyte const*>(bytes.data()));
    attached.env->CallVoidMethod(g_owner, g_message_callback, id, payload);
    attached.env->DeleteLocalRef(payload);
    attached.env->DeleteLocalRef(id);
}

void read_frames(std::string device_id, std::shared_ptr<Connection> connection) {
    try {
        init_apartment(apartment_type::multi_threaded);
        while (true) {
            if (connection->reader.LoadAsync(sizeof(std::uint32_t)).get() != sizeof(std::uint32_t)) break;
            auto size = connection->reader.ReadUInt32();
            if (size == 0 || size > kMaximumFrameSize) break;
            if (connection->reader.LoadAsync(size).get() != size) break;
            std::vector<std::uint8_t> payload(size);
            connection->reader.ReadBytes(payload);
            notify_message(device_id, payload);
        }
    } catch (...) {
    }
    std::scoped_lock lock(g_mutex);
    auto found = g_connections.find(device_id);
    if (found != g_connections.end() && found->second == connection) g_connections.erase(found);
    connection->socket.Close();
}

void install_connection(std::string const& device_id, StreamSocket const& socket) {
    auto connection = std::make_shared<Connection>();
    connection->socket = socket;
    connection->reader = DataReader(socket.InputStream());
    connection->reader.ByteOrder(ByteOrder::BigEndian);
    connection->reader.InputStreamOptions(InputStreamOptions::Partial);
    connection->writer = DataWriter(socket.OutputStream());
    connection->writer.ByteOrder(ByteOrder::BigEndian);
    {
        std::scoped_lock lock(g_mutex);
        auto old = g_connections.find(device_id);
        if (old != g_connections.end()) old->second->socket.Close();
        g_connections.insert_or_assign(device_id, connection);
    }
    std::thread(read_frames, device_id, connection).detach();
}

bool classic_supported() {
    try {
        init_apartment(apartment_type::multi_threaded);
    } catch (...) {
        // The calling JVM thread may already be initialized in a different apartment.
    }
    try {
        auto adapter = BluetoothAdapter::GetDefaultAsync().get();
        if (adapter == nullptr || !adapter.IsClassicSupported()) return false;
        auto radio = adapter.GetRadioAsync().get();
        return radio != nullptr && radio.State() == RadioState::On;
    } catch (...) {
        // BluetoothAdapter reports ERROR_DEVICE_NOT_AVAILABLE while the radio is off.
        return false;
    }
}

void stop_server_locked() {
    if (g_listener != nullptr && g_connection_token.value != 0) {
        g_listener.ConnectionReceived(g_connection_token);
    }
    if (g_provider != nullptr) g_provider.StopAdvertising();
    if (g_listener != nullptr) g_listener.Close();
    for (auto const& [_, connection] : g_connections) connection->socket.Close();
    g_connections.clear();
    g_listener = nullptr;
    g_provider = nullptr;
    g_connection_token = {};
}

void start_server_locked(guid const& service_uuid, std::string const& local_name) {
    if (g_provider != nullptr) return;
    try {
        init_apartment(apartment_type::multi_threaded);
    } catch (...) {}
    auto result = RfcommServiceProvider::CreateAsync(RfcommServiceId::FromUuid(service_uuid)).get();
    g_provider = result;

    auto name = local_name.substr(0, 200);
    DataWriter sdp_writer;
    sdp_writer.UnicodeEncoding(UnicodeEncoding::Utf8);
    sdp_writer.WriteByte(0x25);
    sdp_writer.WriteByte(static_cast<std::uint8_t>(name.size()));
    sdp_writer.WriteString(winrt::to_hstring(name));
    g_provider.SdpRawAttributes().Insert(kServiceNameAttribute, sdp_writer.DetachBuffer());

    g_listener = StreamSocketListener();
    g_connection_token = g_listener.ConnectionReceived([](auto const&, StreamSocketListenerConnectionReceivedEventArgs const& args) {
        auto socket = args.Socket();
        auto remote = winrt::to_string(socket.Information().RemoteAddress().CanonicalName());
        install_connection("incoming:" + remote, socket);
    });
    g_listener
        .BindServiceNameAsync(
            g_provider.ServiceId().AsString(),
            SocketProtectionLevel::BluetoothEncryptionAllowNullAuthentication)
        .get();
    g_provider.StartAdvertising(g_listener, true);
}
}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeInitialize(
    JNIEnv* env,
    jobject owner) {
    env->GetJavaVM(&g_vm);
    std::scoped_lock lock(g_mutex);
    if (g_owner != nullptr) env->DeleteGlobalRef(g_owner);
    g_owner = env->NewGlobalRef(owner);
    auto type = env->GetObjectClass(owner);
    g_state_callback = env->GetMethodID(type, "onBluetoothStateChanged", "(Z)V");
    g_devices_callback = env->GetMethodID(type, "onDevicesChanged", "([Ljava/lang/String;[Ljava/lang/String;)V");
    g_message_callback = env->GetMethodID(type, "onMessageReceived", "(Ljava/lang/String;[B)V");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeIsBluetoothEnabled(
    JNIEnv*,
    jobject) {
    try {
        return classic_supported() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeStartDiscovery(
    JNIEnv* env,
    jobject,
    jstring local_name,
    jstring service_uuid) {
    try {
        auto service_id = guid(to_utf8(service_uuid, env));
        {
            std::scoped_lock lock(g_mutex);
            start_server_locked(service_id, to_utf8(local_name, env));
        }
        auto selector = RfcommDeviceService::GetDeviceSelector(RfcommServiceId::FromUuid(service_id));
        auto found = DeviceInformation::FindAllAsync(selector).get();
        std::vector<std::pair<std::string, std::string>> devices;
        uint32_t const count = found.Size();
        devices.reserve(count);
        for (uint32_t i = 0; i < count; ++i) {
            auto device = found.GetAt(i);
            devices.emplace_back(winrt::to_string(device.Id()), winrt::to_string(device.Name()));
        }
        notify_devices(devices);
        notify_state(true);
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeStopDiscovery(
    JNIEnv*,
    jobject) {}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeStartServer(
    JNIEnv* env,
    jobject,
    jstring local_name,
    jstring service_uuid) {
    try {
        std::scoped_lock lock(g_mutex);
        start_server_locked(guid(to_utf8(service_uuid, env)), to_utf8(local_name, env));
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeStopServer(
    JNIEnv*,
    jobject) {
    std::scoped_lock lock(g_mutex);
    stop_server_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeConnect(
    JNIEnv* env,
    jobject,
    jstring device_id,
    jstring service_uuid) {
    try {
        try {
            init_apartment(apartment_type::multi_threaded);
        } catch (...) {}
        auto id = to_utf8(device_id, env);
        auto target_guid = guid(to_utf8(service_uuid, env));
        auto service = RfcommDeviceService::FromIdAsync(winrt::to_hstring(id)).get();
        if (service == nullptr || service.ServiceId().Uuid() != target_guid) {
            throw hresult_error(winrt::hresult(E_FAIL), L"RFCOMM shared-study service is unavailable");
        }
        StreamSocket socket;
        socket
            .ConnectAsync(
                service.ConnectionHostName(),
                service.ConnectionServiceName(),
                SocketProtectionLevel::BluetoothEncryptionAllowNullAuthentication)
            .get();
        install_connection(id, socket);
        service.Close();
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeDisconnect(
    JNIEnv* env,
    jobject,
    jstring device_id) {
    try {
        init_apartment(apartment_type::multi_threaded);
    } catch (...) {}
    auto id = to_utf8(device_id, env);
    std::scoped_lock lock(g_mutex);
    auto found = g_connections.find(id);
    if (found != g_connections.end()) {
        found->second->socket.Close();
        g_connections.erase(found);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_BluetoothClassicTransport_nativeSend(
    JNIEnv* env,
    jobject,
    jstring device_id,
    jbyteArray payload) {
    try {
        try {
            init_apartment(apartment_type::multi_threaded);
        } catch (...) {}
        auto id = to_utf8(device_id, env);
        std::shared_ptr<Connection> connection;
        {
            std::scoped_lock lock(g_mutex);
            auto found = g_connections.find(id);
            if (found == g_connections.end()) throw hresult_error(winrt::hresult(E_BOUNDS), L"RFCOMM connection is unavailable");
            connection = found->second;
        }
        auto size = static_cast<std::uint32_t>(env->GetArrayLength(payload));
        if (size == 0 || size > kMaximumFrameSize) throw hresult_error(winrt::hresult(E_INVALIDARG), L"Invalid RFCOMM frame size");
        std::vector<std::uint8_t> bytes(size);
        env->GetByteArrayRegion(payload, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        std::scoped_lock write_lock(connection->write_mutex);
        connection->writer.WriteUInt32(size);
        connection->writer.WriteBytes(bytes);
        connection->writer.StoreAsync().get();
        connection->writer.FlushAsync().get();
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
    }
}
