#include <jni.h>

#include <windows.h>
#include <winerror.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.Bluetooth.h>
#include <winrt/Windows.Devices.Bluetooth.Advertisement.h>
#include <winrt/Windows.Devices.Bluetooth.GenericAttributeProfile.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace Windows::Devices::Bluetooth;
using namespace Windows::Devices::Bluetooth::Advertisement;
using namespace Windows::Devices::Bluetooth::GenericAttributeProfile;
using namespace Windows::Storage::Streams;

namespace {
JavaVM* g_vm = nullptr;
jobject g_owner = nullptr;
jmethodID g_state_callback = nullptr;
jmethodID g_packet_callback = nullptr;

std::mutex g_mutex;
GattServiceProvider g_provider{nullptr};
GattLocalCharacteristic g_write_characteristic{nullptr};
GattLocalCharacteristic g_notify_characteristic{nullptr};
BluetoothLEAdvertisementPublisher g_name_publisher{nullptr};
event_token g_write_token{};
event_token g_clients_token{};
std::unordered_map<std::string, GattSubscribedClient> g_clients;

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
    const char* chars = env->GetStringUTFChars(value, nullptr);
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
    if (attached.env == nullptr || g_owner == nullptr || g_state_callback == nullptr) return;
    attached.env->CallVoidMethod(g_owner, g_state_callback, enabled ? JNI_TRUE : JNI_FALSE);
}

void notify_packet(std::string const& device_id, std::vector<std::uint8_t> const& bytes) {
    AttachedEnvironment attached;
    if (attached.env == nullptr || g_owner == nullptr || g_packet_callback == nullptr) return;
    auto id = attached.env->NewStringUTF(device_id.c_str());
    auto payload = attached.env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (id == nullptr || payload == nullptr) return;
    attached.env->SetByteArrayRegion(
        payload,
        0,
        static_cast<jsize>(bytes.size()),
        reinterpret_cast<jbyte const*>(bytes.data()));
    attached.env->CallVoidMethod(g_owner, g_packet_callback, id, payload);
    attached.env->DeleteLocalRef(payload);
    attached.env->DeleteLocalRef(id);
}

void rebuild_subscribed_clients() {
    std::scoped_lock lock(g_mutex);
    g_clients.clear();
    if (g_notify_characteristic == nullptr) return;
    auto clients = g_notify_characteristic.SubscribedClients();
    uint32_t const count = clients.Size();
    for (uint32_t i = 0; i < count; ++i) {
        auto client = clients.GetAt(i);
        g_clients.insert_or_assign(winrt::to_string(client.Session().DeviceId().Id()), client);
    }
}

void stop_locked() {
    if (g_write_characteristic != nullptr && g_write_token.value != 0) {
        g_write_characteristic.WriteRequested(g_write_token);
    }
    if (g_notify_characteristic != nullptr && g_clients_token.value != 0) {
        g_notify_characteristic.SubscribedClientsChanged(g_clients_token);
    }
    if (g_name_publisher != nullptr) g_name_publisher.Stop();
    if (g_provider != nullptr) g_provider.StopAdvertising();
    g_clients.clear();
    g_name_publisher = nullptr;
    g_notify_characteristic = nullptr;
    g_write_characteristic = nullptr;
    g_provider = nullptr;
    g_write_token = {};
    g_clients_token = {};
}

bool peripheral_supported() {
    auto adapter = BluetoothAdapter::GetDefaultAsync().get();
    return adapter != nullptr && adapter.IsPeripheralRoleSupported();
}
}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeInitialize(
    JNIEnv* env,
    jobject owner) {
    try {
        init_apartment(apartment_type::multi_threaded);
    } catch (hresult_error const& error) {
        if (error.code() != winrt::hresult(RPC_E_CHANGED_MODE)) {
            throw_java(env, winrt::to_string(error.message()));
            return;
        }
    }
    env->GetJavaVM(&g_vm);
    std::scoped_lock lock(g_mutex);
    if (g_owner != nullptr) env->DeleteGlobalRef(g_owner);
    g_owner = env->NewGlobalRef(owner);
    auto type = env->GetObjectClass(owner);
    g_state_callback = env->GetMethodID(type, "onBluetoothStateChanged", "(Z)V");
    g_packet_callback = env->GetMethodID(type, "onPacketReceived", "(Ljava/lang/String;[B)V");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeIsBluetoothEnabled(
    JNIEnv* env,
    jobject) {
    try {
        return peripheral_supported() ? JNI_TRUE : JNI_FALSE;
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeStart(
    JNIEnv* env,
    jobject,
    jstring service_uuid,
    jstring local_name,
    jstring write_uuid,
    jstring notify_uuid) {
    try {
        auto service_id = guid(to_utf8(service_uuid, env));
        auto write_id = guid(to_utf8(write_uuid, env));
        auto notify_id = guid(to_utf8(notify_uuid, env));
        auto name = winrt::to_hstring(to_utf8(local_name, env));

        std::scoped_lock lock(g_mutex);
        stop_locked();
        if (!peripheral_supported()) throw hresult_error(winrt::hresult(E_NOTIMPL), L"BLE peripheral role is unsupported");

        auto provider_result = GattServiceProvider::CreateAsync(service_id).get();
        if (provider_result.Error() != BluetoothError::Success) {
            throw hresult_error(winrt::hresult(E_FAIL), L"Unable to create the shared-study GATT service");
        }
        g_provider = provider_result.ServiceProvider();

        GattLocalCharacteristicParameters write_parameters;
        write_parameters.CharacteristicProperties(
            GattCharacteristicProperties::Write | GattCharacteristicProperties::WriteWithoutResponse);
        write_parameters.WriteProtectionLevel(GattProtectionLevel::Plain);
        write_parameters.UserDescription(L"Zayit shared-study write");
        auto write_result = g_provider.Service().CreateCharacteristicAsync(write_id, write_parameters).get();
        if (write_result.Error() != BluetoothError::Success) {
            throw hresult_error(winrt::hresult(E_FAIL), L"Unable to create the shared-study write characteristic");
        }
        g_write_characteristic = write_result.Characteristic();

        GattLocalCharacteristicParameters notify_parameters;
        notify_parameters.CharacteristicProperties(GattCharacteristicProperties::Notify);
        notify_parameters.ReadProtectionLevel(GattProtectionLevel::Plain);
        notify_parameters.UserDescription(L"Zayit shared-study notify");
        auto notify_result = g_provider.Service().CreateCharacteristicAsync(notify_id, notify_parameters).get();
        if (notify_result.Error() != BluetoothError::Success) {
            throw hresult_error(winrt::hresult(E_FAIL), L"Unable to create the shared-study notify characteristic");
        }
        g_notify_characteristic = notify_result.Characteristic();

        g_write_token = g_write_characteristic.WriteRequested([](auto const&, GattWriteRequestedEventArgs const& args) {
            auto deferral = args.GetDeferral();
            try {
                auto request = args.GetRequestAsync().get();
                if (request != nullptr) {
                    auto reader = DataReader::FromBuffer(request.Value());
                    std::vector<std::uint8_t> bytes(reader.UnconsumedBufferLength());
                    if (!bytes.empty()) reader.ReadBytes(bytes);
                    notify_packet(winrt::to_string(args.Session().DeviceId().Id()), bytes);
                    if (request.Option() == GattWriteOption::WriteWithResponse) request.Respond();
                }
            } catch (...) {
            }
            deferral.Complete();
        });
        g_clients_token = g_notify_characteristic.SubscribedClientsChanged([](auto const&, auto const&) {
            rebuild_subscribed_clients();
        });

        GattServiceProviderAdvertisingParameters advertising;
        advertising.IsConnectable(true);
        advertising.IsDiscoverable(true);
        g_provider.StartAdvertising(advertising);

        BluetoothLEAdvertisement advertisement;
        advertisement.LocalName(name);
        advertisement.ServiceUuids().Append(service_id);
        g_name_publisher = BluetoothLEAdvertisementPublisher(advertisement);
        g_name_publisher.Start();
        notify_state(true);
    } catch (hresult_error const& error) {
        std::scoped_lock lock(g_mutex);
        stop_locked();
        throw_java(env, winrt::to_string(error.message()));
    } catch (std::exception const& error) {
        std::scoped_lock lock(g_mutex);
        stop_locked();
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeStop(
    JNIEnv*,
    jobject) {
    std::scoped_lock lock(g_mutex);
    stop_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeSend(
    JNIEnv* env,
    jobject,
    jstring device_id,
    jbyteArray payload) {
    try {
        auto id = to_utf8(device_id, env);
        std::vector<std::uint8_t> bytes(static_cast<std::size_t>(env->GetArrayLength(payload)));
        env->GetByteArrayRegion(
            payload,
            0,
            static_cast<jsize>(bytes.size()),
            reinterpret_cast<jbyte*>(bytes.data()));

        GattSubscribedClient client{nullptr};
        GattLocalCharacteristic characteristic{nullptr};
        {
            std::scoped_lock lock(g_mutex);
            auto found = g_clients.find(id);
            if (found == g_clients.end()) throw hresult_error(winrt::hresult(E_BOUNDS), L"BLE client is not subscribed");
            client = found->second;
            characteristic = g_notify_characteristic;
        }
        DataWriter writer;
        writer.WriteBytes(bytes);
        characteristic.NotifyValueAsync(writer.DetachBuffer(), client).get();
    } catch (hresult_error const& error) {
        throw_java(env, winrt::to_string(error.message()));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeDisconnect(
    JNIEnv* env,
    jobject,
    jstring device_id) {
    auto id = to_utf8(device_id, env);
    std::scoped_lock lock(g_mutex);
    auto found = g_clients.find(id);
    if (found != g_clients.end()) {
        found->second.Session().Close();
        g_clients.erase(found);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeMaximumPacketSize(
    JNIEnv* env,
    jobject,
    jstring device_id) {
    auto id = to_utf8(device_id, env);
    std::scoped_lock lock(g_mutex);
    auto found = g_clients.find(id);
    if (found == g_clients.end()) return 20;
    return static_cast<jint>(std::max<std::uint16_t>(20, found->second.Session().MaxPduSize() - 3));
}
