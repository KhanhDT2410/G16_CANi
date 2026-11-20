import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:cani/app/app.dart';
import 'package:cani/app/home_screen.dart';
import 'package:cani/core/constants/api_constants.dart';
import 'package:cani/core/notifications/fcm_service.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  if (ApiConstants.supabaseUrl.isNotEmpty && ApiConstants.supabaseAnonKey.isNotEmpty) {
    try {
      await Supabase.initialize(
        url: ApiConstants.supabaseUrl,
        publishableKey: ApiConstants.supabaseAnonKey,
      );
    } catch (e) {
      debugPrint('[Supabase Init Warning] Failed to initialize Supabase: $e');
    }
  } else {
    debugPrint('[Supabase Init Info] Supabase credentials not provided. Skipping initialization.');
  }

  final container = ProviderContainer();

  // Khởi tạo Firebase Cloud Messaging (FCM) & Kênh thông báo nổi (Heads-up banner)
  await FcmService.initialize(
    onNotificationNavigate: (data) {
      debugPrint('[Notification Navigate] Navigating from notification data: $data');
      if (!container.read(authStateProvider).isAuthenticated) return;

      final rawPayload = data['payload'];
      final type = data['type'] ?? (rawPayload is Map ? rawPayload['type'] : null);
      final isProposal = type == 'NEW_PROPOSAL' || type == 'EMERGENCY_PROPOSAL' || type == 'PROPOSAL';

      rootNavigatorKey.currentState?.popUntil((route) => route.isFirst);
      container.read(homeTabIndexProvider.notifier).state = isProposal ? 0 : 1;
    },
  );

  runApp(
    UncontrolledProviderScope(
      container: container,
      child: const CaniApp(),
    ),
  );
}
