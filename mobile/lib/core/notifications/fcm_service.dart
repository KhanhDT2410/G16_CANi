import 'dart:convert';
import 'dart:io' show Platform;
import 'package:dio/dio.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import 'local_notification_service.dart';

/// Hàm xử lý thông báo chạy ngầm ở cấp hệ thống (khi app bị kill hoặc chạy ngầm)
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  debugPrint('[FCM Background] Message received: ${message.messageId} - Title: ${message.notification?.title}');
}

class FcmService {
  FcmService._();

  static bool _isCoreInitialized = false;
  static bool _isPostFrameInitialized = false;
  static String? _cachedToken;
  static Map<String, dynamic>? _pendingNavigationData;
  static void Function(Map<String, dynamic> data)? _onNotificationNavigate;
  static void Function(Map<String, dynamic> data)? _onForegroundMessage;
  static void Function(String newToken)? _onTokenRefresh;
  static bool Function()? _canNavigate;
  static bool Function(Map<String, dynamic> data)? _shouldSuppressForegroundNotification;

  /// Khởi tạo nhẹ phần core của Firebase & LocalNotification trước `runApp()`.
  /// Tuyệt đối không chặn khung hình đầu tiên (first frame) bởi hộp thoại xin quyền OS
  /// hay lời gọi mạng `getToken()` / `getInitialMessage()`.
  static Future<void> initialize({
    void Function(Map<String, dynamic> data)? onNotificationNavigate,
    void Function(Map<String, dynamic> data)? onForegroundMessage,
    void Function(String newToken)? onTokenRefresh,
    bool Function()? canNavigate,
    bool Function(Map<String, dynamic> data)? shouldSuppressForegroundNotification,
  }) async {
    _onNotificationNavigate = onNotificationNavigate;
    _onForegroundMessage = onForegroundMessage;
    _onTokenRefresh = onTokenRefresh;
    _canNavigate = canNavigate;
    _shouldSuppressForegroundNotification = shouldSuppressForegroundNotification;

    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) {
      debugPrint('[FcmService] FCM is skipped on this non-mobile platform.');
      return;
    }

    if (_isCoreInitialized) return;

    try {
      // 1. Khởi tạo Firebase Native
      await Firebase.initializeApp();

      // 2. Đăng ký hàm xử lý ngầm (Background Handler)
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

      // 3. Khởi tạo Local Notification cho trạng thái Foreground (khi đang mở app)
      await LocalNotificationService.initialize(
        onNotificationTapped: (response) {
          if (response.payload != null) {
            try {
              final decoded = jsonDecode(response.payload!);
              if (decoded is Map<String, dynamic>) {
                handleOrBufferNavigation(decoded);
                return;
              }
            } catch (_) {}
            handleOrBufferNavigation({'payload': response.payload});
          }
        },
      );

      final messaging = FirebaseMessaging.instance;

      // 4. Lắng nghe thông báo khi ứng dụng đang mở ở tiền cảnh (Foreground)
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('[FcmService] Foreground message received: ${message.notification?.title}');
        if (_shouldSuppressForegroundNotification?.call(message.data) ?? false) {
          debugPrint('[FcmService] Suppressed foreground job notification while Nhận việc is OFF.');
          return;
        }
        final noti = message.notification;
        if (noti != null) {
          LocalNotificationService.showNotification(
            id: message.hashCode,
            title: noti.title ?? 'CANi - Thông báo mới',
            body: noti.body ?? 'Bạn có thông báo mới từ hệ thống CANi',
            payload: jsonEncode(message.data),
          );
        }
        _onForegroundMessage?.call(message.data);
      });

      // Đồng bộ lại token nếu FCM tự xoay vòng token mới
      messaging.onTokenRefresh.listen((newToken) {
        _cachedToken = newToken;
        _onTokenRefresh?.call(newToken);
      });

      // 5. Lắng nghe khi người dùng bấm vào thông báo từ khay hệ thống (Background)
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('[FcmService] Notification opened from background: ${message.data}');
        handleOrBufferNavigation(message.data);
      });

      _isCoreInitialized = true;
    } catch (e) {
      debugPrint('[FcmService] Warning: Could not initialize Firebase FCM core: $e');
    }
  }

  /// Thực thi sau khi khung hình đầu tiên (first frame) đã hiển thị:
  /// xin quyền thông báo, lấy FCM token, đồng bộ backend và xử lý deep-link lúc mở app.
  static Future<void> completePostFrameInitialization() async {
    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) return;
    if (_isPostFrameInitialized) {
      flushPendingNavigation();
      return;
    }
    _isPostFrameInitialized = true;

    try {
      if (!_isCoreInitialized) {
        await initialize(
          onNotificationNavigate: _onNotificationNavigate,
          onForegroundMessage: _onForegroundMessage,
          onTokenRefresh: _onTokenRefresh,
          canNavigate: _canNavigate,
          shouldSuppressForegroundNotification: _shouldSuppressForegroundNotification,
        );
      }
      if (!_isCoreInitialized) return;

      final messaging = FirebaseMessaging.instance;

      // 1. Xin quyền thông báo sau first frame để không chặn splash/home render
      final settings = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        provisional: false,
      );
      debugPrint('[FcmService] Notification authorization status: ${settings.authorizationStatus}');

      // 2. Lấy FCM Device Token lần đầu và kích hoạt đồng bộ nếu phiên đăng nhập đã sẵn sàng
      _cachedToken = await messaging.getToken();
      debugPrint('[FcmService] Device FCM Token: $_cachedToken');
      if (_cachedToken != null) {
        _onTokenRefresh?.call(_cachedToken!);
      }

      // 3. Xử lý trường hợp mở app từ trạng thái tắt hoàn toàn (Terminated)
      final initialMessage = await messaging.getInitialMessage();
      if (initialMessage != null) {
        debugPrint('[FcmService] App opened from terminated state via notification: ${initialMessage.data}');
        handleOrBufferNavigation(initialMessage.data);
      } else {
        flushPendingNavigation();
      }
    } catch (e) {
      debugPrint('[FcmService] Warning: Post-frame FCM setup failed: $e');
    }
  }

  /// Lưu payload điều hướng vào bộ đệm nếu Navigator hoặc AuthState chưa sẵn sàng,
  /// và chỉ thực thi khi cả hai đã khởi tạo xong.
  static void handleOrBufferNavigation(Map<String, dynamic> data) {
    _pendingNavigationData = Map<String, dynamic>.from(data);
    flushPendingNavigation();
  }

  /// Xả payload điều hướng đang chờ nếu `canNavigate` đã thỏa mãn.
  static void flushPendingNavigation() {
    final pending = _pendingNavigationData;
    final navigate = _onNotificationNavigate;
    if (pending == null || navigate == null) return;
    if (_canNavigate != null && !_canNavigate!()) {
      return;
    }
    _pendingNavigationData = null;
    navigate(pending);
  }

  /// Đồng bộ FCM Device Token lên Backend Spring Boot
  static Future<void> syncTokenWithBackend(Dio dio, {String? explicitUserId}) async {
    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) return;
    if (!_isCoreInitialized && Firebase.apps.isEmpty) return;

    try {
      final token = _cachedToken ?? (_isPostFrameInitialized ? await FirebaseMessaging.instance.getToken() : null);
      if (token == null) {
        debugPrint('[FcmService] FCM token not yet fetched; will sync after post-frame init.');
        return;
      }

      _cachedToken = token;

      final deviceType = Platform.isAndroid ? 'ANDROID' : 'IOS';
      final payload = {
        'fcmToken': token,
        'deviceType': deviceType,
        'deviceName': Platform.isAndroid ? 'Android Device' : 'iOS Device',
        'appVersion': '1.0.0+1',
      };

      final response = await dio.post(
        '/v1/devices/tokens',
        data: payload,
        queryParameters: explicitUserId != null ? {'userId': explicitUserId} : null,
      );

      debugPrint('[FcmService] Successfully synced FCM token with backend. Status: ${response.statusCode}');
    } catch (e) {
      debugPrint('[FcmService] Failed to sync FCM token with backend: $e');
    }
  }

  /// Thu hồi Token trên Backend khi Đăng xuất (Logout)
  static Future<void> unregisterToken(Dio dio, {String? explicitUserId}) async {
    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) return;

    try {
      if (_cachedToken != null) {
        await dio.delete(
          '/v1/devices/tokens',
          queryParameters: {
            'fcmToken': _cachedToken,
            'userId': ?explicitUserId,
          },
        );
        debugPrint('[FcmService] Successfully unregistered FCM token on backend.');
      }
      await FirebaseMessaging.instance.deleteToken();
      _cachedToken = null;
    } catch (e) {
      debugPrint('[FcmService] Failed to unregister FCM token: $e');
    }
  }
}
