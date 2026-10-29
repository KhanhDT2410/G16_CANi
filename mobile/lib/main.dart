import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:cani/app/app.dart';
import 'package:cani/core/constants/api_constants.dart';
import 'package:cani/core/notifications/fcm_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Supabase.initialize(
    url: ApiConstants.supabaseUrl,
    publishableKey: ApiConstants.supabaseAnonKey,
  );

  // Khởi tạo Firebase Cloud Messaging (FCM) & Kênh thông báo nổi (Heads-up banner)
  await FcmService.initialize();

  runApp(
    const ProviderScope(
      child: CaniApp(),
    ),
  );
}
