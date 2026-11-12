import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:cani/app/app.dart';
import 'package:cani/core/constants/api_constants.dart';
import 'package:cani/core/notifications/fcm_service.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';

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

  // Khởi tạo Firebase Cloud Messaging (FCM) & Kênh thông báo nổi (Heads-up banner)
  await FcmService.initialize(
    onNotificationNavigate: (data) {
      debugPrint('[Notification Navigate] Navigating from notification data: $data');
      rootNavigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (_) => const MyAssignmentsScreen(),
        ),
      );
    },
  );

  runApp(
    const ProviderScope(
      child: CaniApp(),
    ),
  );
}
