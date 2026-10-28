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

  static bool _isInitialized = false;
  static String? _cachedToken;

  /// Khởi tạo Firebase và cấu hình lắng nghe 3 trạng thái ứng dụng
  static Future<void> initialize({
    void Function(Map<String, dynamic> data)? onNotificationNavigate,
  }) async {
    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) {
      debugPrint('[FcmService] FCM is skipped on this non-mobile platform.');
      return;
    }

    if (_isInitialized) return;

    try {
      // 1. Khởi tạo Firebase Native
      await Firebase.initializeApp();

      // 2. Đăng ký hàm xử lý ngầm (Background Handler)
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

      // 3. Khởi tạo Local Notification cho trạng thái Foreground (khi đang mở app)
      await LocalNotificationService.initialize(
        onNotificationTapped: (response) {
          if (response.payload != null && onNotificationNavigate != null) {
            onNotificationNavigate({'payload': response.payload});
          }
        },
      );

      // 4. Xin quyền thông báo (Yêu cầu trên Android 13+ và iOS)
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        provisional: false,
      );

      debugPrint('[FcmService] Notification authorization status: ${settings.authorizationStatus}');

      // 5. Lấy FCM Device Token lần đầu
      _cachedToken = await messaging.getToken();
      debugPrint('[FcmService] Device FCM Token: $_cachedToken');

      // 6. Lắng nghe thông báo khi ứng dụng đang mở ở tiền cảnh (Foreground)
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('[FcmService] Foreground message received: ${message.notification?.title}');
        final noti = message.notification;
        if (noti != null) {
          LocalNotificationService.showNotification(
            id: message.hashCode,
            title: noti.title ?? 'CANi - Lời mời việc làm',
            body: noti.body ?? 'Bạn có thông báo mới từ hệ thống CANi',
            payload: message.data.toString(),
          );
        }
      });

      // 7. Lắng nghe khi người dùng bấm vào thông báo từ khay hệ thống (Background)
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('[FcmService] Notification opened from background: ${message.data}');
        if (onNotificationNavigate != null) {
          onNotificationNavigate(message.data);
        }
      });

      // 8. Xử lý trường hợp mở app từ trạng thái tắt hoàn toàn (Terminated)
      final initialMessage = await messaging.getInitialMessage();
      if (initialMessage != null && onNotificationNavigate != null) {
        debugPrint('[FcmService] App opened from terminated state via notification: ${initialMessage.data}');
        onNotificationNavigate(initialMessage.data);
      }

      _isInitialized = true;
    } catch (e) {
      debugPrint('[FcmService] Warning: Could not initialize Firebase FCM: $e');
    }
  }

  /// Đồng bộ FCM Device Token lên Backend Spring Boot
  static Future<void> syncTokenWithBackend(Dio dio, {String? explicitUserId}) async {
    if (kIsWeb || (!Platform.isAndroid && !Platform.isIOS)) return;

    try {
      final token = _cachedToken ?? await FirebaseMessaging.instance.getToken();
      if (token == null) {
        debugPrint('[FcmService] No FCM token available to sync.');
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
            if (explicitUserId != null) 'userId': explicitUserId,
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

  static String? get cachedToken => _cachedToken;
}
