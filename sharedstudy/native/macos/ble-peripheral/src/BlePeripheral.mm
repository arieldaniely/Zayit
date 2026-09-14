#include <jni.h>

#import <CoreBluetooth/CoreBluetooth.h>
#import <Foundation/Foundation.h>

namespace {
constexpr NSUInteger kMaximumPendingNotifications = 1024;
constexpr jint kDefaultPacketSize = 20;

JavaVM* g_vm = nullptr;
jobject g_owner = nullptr;
jmethodID g_state_callback = nullptr;
jmethodID g_packet_callback = nullptr;
dispatch_queue_t g_ble_queue = nullptr;
char g_queue_key;

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

NSString* to_ns_string(JNIEnv* env, jstring value) {
    if (value == nullptr) return @"";
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return @"";
    NSString* result = [[NSString alloc] initWithCharacters:reinterpret_cast<const unichar*>(chars)
                                                     length:env->GetStringLength(value)];
    env->ReleaseStringChars(value, chars);
    return result;
}

void throw_java(JNIEnv* env, NSString* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.UTF8String ?: "BLE peripheral operation failed");
}

void notify_state(bool enabled) {
    AttachedEnvironment attached;
    if (attached.env == nullptr || g_owner == nullptr || g_state_callback == nullptr) return;
    attached.env->CallVoidMethod(g_owner, g_state_callback, enabled ? JNI_TRUE : JNI_FALSE);
    if (attached.env->ExceptionCheck()) attached.env->ExceptionClear();
}

void notify_packet(NSString* device_id, NSData* data) {
    AttachedEnvironment attached;
    if (attached.env == nullptr || g_owner == nullptr || g_packet_callback == nullptr) return;
    jstring id = attached.env->NewStringUTF(device_id.UTF8String);
    jbyteArray payload = attached.env->NewByteArray(static_cast<jsize>(data.length));
    if (id == nullptr || payload == nullptr) return;
    attached.env->SetByteArrayRegion(
        payload,
        0,
        static_cast<jsize>(data.length),
        reinterpret_cast<const jbyte*>(data.bytes));
    attached.env->CallVoidMethod(g_owner, g_packet_callback, id, payload);
    if (attached.env->ExceptionCheck()) attached.env->ExceptionClear();
    attached.env->DeleteLocalRef(payload);
    attached.env->DeleteLocalRef(id);
}

void on_ble_queue(dispatch_block_t block) {
    if (dispatch_get_specific(&g_queue_key) != nullptr) {
        block();
    } else {
        dispatch_sync(g_ble_queue, block);
    }
}
}  // namespace

@interface ZayitBlePeripheralDelegate : NSObject <CBPeripheralManagerDelegate>
@property(nonatomic, strong) CBPeripheralManager* manager;
@property(nonatomic, strong) CBMutableCharacteristic* writeCharacteristic;
@property(nonatomic, strong) CBMutableCharacteristic* notifyCharacteristic;
@property(nonatomic, strong) CBUUID* advertisedServiceUuid;
@property(nonatomic, copy) NSString* advertisedName;
@property(nonatomic, strong) NSMutableDictionary<NSString*, CBCentral*>* centrals;
@property(nonatomic, strong) NSMutableArray<NSDictionary*>* pendingNotifications;
@end

@implementation ZayitBlePeripheralDelegate

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _centrals = [NSMutableDictionary dictionary];
        _pendingNotifications = [NSMutableArray array];
    }
    return self;
}

- (void)peripheralManagerDidUpdateState:(CBPeripheralManager*)peripheral {
    notify_state(peripheral.state == CBManagerStatePoweredOn);
}

- (void)peripheralManager:(CBPeripheralManager*)peripheral
             didAddService:(CBService*)service
                     error:(NSError*)error {
    if (error != nil || self.advertisedServiceUuid == nil) return;
    [peripheral startAdvertising:@{
        CBAdvertisementDataLocalNameKey : self.advertisedName ?: @"Zayita",
        CBAdvertisementDataServiceUUIDsKey : @[ self.advertisedServiceUuid ],
    }];
}

- (void)peripheralManager:(CBPeripheralManager*)peripheral
    central:(CBCentral*)central
    didSubscribeToCharacteristic:(CBCharacteristic*)characteristic {
    self.centrals[central.identifier.UUIDString] = central;
    [self flushPendingNotifications];
}

- (void)peripheralManager:(CBPeripheralManager*)peripheral
    central:(CBCentral*)central
    didUnsubscribeFromCharacteristic:(CBCharacteristic*)characteristic {
    [self.centrals removeObjectForKey:central.identifier.UUIDString];
    NSIndexSet* discarded = [self.pendingNotifications indexesOfObjectsPassingTest:
        ^BOOL(NSDictionary* entry, NSUInteger index, BOOL* stop) {
            return [entry[@"deviceId"] isEqualToString:central.identifier.UUIDString];
        }];
    [self.pendingNotifications removeObjectsAtIndexes:discarded];
}

- (void)peripheralManager:(CBPeripheralManager*)peripheral
    didReceiveWriteRequests:(NSArray<CBATTRequest*>*)requests {
    for (CBATTRequest* request in requests) {
        if (![request.characteristic.UUID isEqual:self.writeCharacteristic.UUID] || request.value == nil) {
            [peripheral respondToRequest:request withResult:CBATTErrorRequestNotSupported];
            continue;
        }
        NSString* deviceId = request.central.identifier.UUIDString;
        self.centrals[deviceId] = request.central;
        notify_packet(deviceId, request.value);
        [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
    }
}

- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager*)peripheral {
    [self flushPendingNotifications];
}

- (BOOL)sendData:(NSData*)data toDevice:(NSString*)deviceId {
    CBCentral* central = self.centrals[deviceId];
    if (central == nil || self.notifyCharacteristic == nil) return NO;
    if (data.length > central.maximumUpdateValueLength) return NO;
    if ([self.manager updateValue:data
                forCharacteristic:self.notifyCharacteristic
             onSubscribedCentrals:@[ central ]]) {
        return YES;
    }
    if (self.pendingNotifications.count >= kMaximumPendingNotifications) {
        [self.pendingNotifications removeObjectAtIndex:0];
    }
    [self.pendingNotifications addObject:@{ @"deviceId" : deviceId, @"data" : data }];
    return YES;
}

- (void)flushPendingNotifications {
    while (self.pendingNotifications.count > 0) {
        NSDictionary* entry = self.pendingNotifications.firstObject;
        NSString* deviceId = entry[@"deviceId"];
        NSData* data = entry[@"data"];
        CBCentral* central = self.centrals[deviceId];
        if (central == nil) {
            [self.pendingNotifications removeObjectAtIndex:0];
            continue;
        }
        if (![self.manager updateValue:data
                     forCharacteristic:self.notifyCharacteristic
                  onSubscribedCentrals:@[ central ]]) {
            return;
        }
        [self.pendingNotifications removeObjectAtIndex:0];
    }
}

- (void)stop {
    [self.manager stopAdvertising];
    [self.manager removeAllServices];
    [self.centrals removeAllObjects];
    [self.pendingNotifications removeAllObjects];
    self.writeCharacteristic = nil;
    self.notifyCharacteristic = nil;
    self.advertisedServiceUuid = nil;
    self.advertisedName = nil;
}

@end

namespace {
ZayitBlePeripheralDelegate* g_delegate = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeInitialize(
    JNIEnv* env,
    jobject owner) {
    env->GetJavaVM(&g_vm);
    if (g_owner != nullptr) env->DeleteGlobalRef(g_owner);
    g_owner = env->NewGlobalRef(owner);
    jclass type = env->GetObjectClass(owner);
    g_state_callback = env->GetMethodID(type, "onBluetoothStateChanged", "(Z)V");
    g_packet_callback = env->GetMethodID(type, "onPacketReceived", "(Ljava/lang/String;[B)V");
    env->DeleteLocalRef(type);

    if (g_ble_queue == nullptr) {
        g_ble_queue = dispatch_queue_create("io.github.arieldaniely.zayita.sharedstudy.ble", DISPATCH_QUEUE_SERIAL);
        dispatch_queue_set_specific(g_ble_queue, &g_queue_key, &g_queue_key, nullptr);
    }
    on_ble_queue(^{
        if (g_delegate == nullptr) {
            g_delegate = [[ZayitBlePeripheralDelegate alloc] init];
            g_delegate.manager = [[CBPeripheralManager alloc] initWithDelegate:g_delegate
                                                                          queue:g_ble_queue
                                                                        options:@{
                CBPeripheralManagerOptionShowPowerAlertKey : @YES,
            }];
        }
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeIsBluetoothEnabled(
    JNIEnv*,
    jobject) {
    __block BOOL enabled = NO;
    on_ble_queue(^{ enabled = g_delegate.manager.state == CBManagerStatePoweredOn; });
    return enabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeStart(
    JNIEnv* env,
    jobject,
    jstring service_uuid,
    jstring local_name,
    jstring write_uuid,
    jstring notify_uuid) {
    NSString* serviceUuid = to_ns_string(env, service_uuid);
    NSString* localName = to_ns_string(env, local_name);
    NSString* writeUuid = to_ns_string(env, write_uuid);
    NSString* notifyUuid = to_ns_string(env, notify_uuid);
    __block NSString* failure = nil;

    on_ble_queue(^{
        if (g_delegate.manager.state != CBManagerStatePoweredOn) {
            failure = @"Bluetooth is not powered on or permission was denied";
            return;
        }
        [g_delegate stop];
        CBUUID* serviceId = [CBUUID UUIDWithString:serviceUuid];
        g_delegate.advertisedServiceUuid = serviceId;
        g_delegate.advertisedName = localName.length > 0 ? localName : @"Zayita";
        g_delegate.writeCharacteristic = [[CBMutableCharacteristic alloc]
            initWithType:[CBUUID UUIDWithString:writeUuid]
              properties:CBCharacteristicPropertyWrite | CBCharacteristicPropertyWriteWithoutResponse
                   value:nil
             permissions:CBAttributePermissionsWriteable];
        g_delegate.notifyCharacteristic = [[CBMutableCharacteristic alloc]
            initWithType:[CBUUID UUIDWithString:notifyUuid]
              properties:CBCharacteristicPropertyNotify
                   value:nil
             permissions:CBAttributePermissionsReadable];
        CBMutableService* service = [[CBMutableService alloc] initWithType:serviceId primary:YES];
        service.characteristics = @[ g_delegate.writeCharacteristic, g_delegate.notifyCharacteristic ];
        [g_delegate.manager addService:service];
    });
    if (failure != nil) throw_java(env, failure);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeStop(
    JNIEnv*,
    jobject) {
    on_ble_queue(^{ [g_delegate stop]; });
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeSend(
    JNIEnv* env,
    jobject,
    jstring device_id,
    jbyteArray payload) {
    NSString* deviceId = to_ns_string(env, device_id);
    jsize length = env->GetArrayLength(payload);
    NSMutableData* data = [NSMutableData dataWithLength:static_cast<NSUInteger>(length)];
    env->GetByteArrayRegion(payload, 0, length, reinterpret_cast<jbyte*>(data.mutableBytes));
    __block BOOL sent = NO;
    on_ble_queue(^{ sent = [g_delegate sendData:data toDevice:deviceId]; });
    if (!sent) throw_java(env, @"The BLE client is not subscribed or the packet exceeds its MTU");
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeDisconnect(
    JNIEnv* env,
    jobject,
    jstring device_id) {
    NSString* deviceId = to_ns_string(env, device_id);
    on_ble_queue(^{
        [g_delegate.centrals removeObjectForKey:deviceId];
        NSIndexSet* discarded = [g_delegate.pendingNotifications indexesOfObjectsPassingTest:
            ^BOOL(NSDictionary* entry, NSUInteger index, BOOL* stop) {
                return [entry[@"deviceId"] isEqualToString:deviceId];
            }];
        [g_delegate.pendingNotifications removeObjectsAtIndexes:discarded];
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_kdroidfilter_seforimapp_features_sharedstudy_JniBlePeripheralEndpoint_nativeMaximumPacketSize(
    JNIEnv* env,
    jobject,
    jstring device_id) {
    NSString* deviceId = to_ns_string(env, device_id);
    __block jint size = kDefaultPacketSize;
    on_ble_queue(^{
        CBCentral* central = g_delegate.centrals[deviceId];
        if (central != nil) {
            size = static_cast<jint>(MAX(static_cast<NSUInteger>(kDefaultPacketSize), central.maximumUpdateValueLength));
        }
    });
    return size;
}
