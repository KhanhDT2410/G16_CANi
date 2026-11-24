import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart' hide AuthState;

import 'package:cani/app/app.dart';
import 'package:cani/app/home_screen.dart';
import 'package:cani/core/constants/api_constants.dart';
import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/notifications/fcm_service.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';

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

  bool isProposalNotificationData(Map<String, dynamic> data) {
    final rawPayload = data['payload'];
    final type = (data['type'] ?? (rawPayload is Map ? rawPayload['type'] : null))?.toString().toUpperCase();
    return type == 'PROPOSAL' ||
        type == 'NEW_PROPOSAL' ||
        type == 'EMERGENCY_PROPOSAL' ||
        type == 'GROUP_PROPOSAL' ||
        type == 'EMERGENCY_GROUP_PROPOSAL';
  }

  // Khởi tạo phần core nhẹ của FCM & LocalNotification (không chặn first frame bởi permission/getToken)
  await FcmService.initialize(
    canNavigate: () {
      final authState = container.read(authStateProvider);
      return !authState.isLoading &&
          authState.isAuthenticated &&
          rootNavigatorKey.currentState != null;
    },
    shouldSuppressForegroundNotification: (data) {
      if (!container.read(authStateProvider).isAuthenticated) return true;
      if (isProposalNotificationData(data) && !container.read(jobRadarProvider).isOnline) {
        return true;
      }
      return false;
    },
    onForegroundMessage: (data) {
      if (!container.read(authStateProvider).isAuthenticated) return;
      final isOnline = container.read(jobRadarProvider).isOnline;
      if (isProposalNotificationData(data) && !isOnline) return;
      if (isOnline) {
        container.invalidate(proposalsListProvider);
      }
      container.invalidate(assignmentsListProvider);
      container.invalidate(incomeSummaryProvider);
      if (isOnline && container.read(ekycProvider).result?.isApproved == true) {
        container.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
      }
    },
    onTokenRefresh: (_) {
      final authState = container.read(authStateProvider);
      if (authState.isAuthenticated && authState.user != null) {
        FcmService.syncTokenWithBackend(container.read(dioProvider), explicitUserId: authState.user!.id);
      }
    },
    onNotificationNavigate: (data) {
      debugPrint('[Notification Navigate] Navigating from notification data: $data');
      if (!container.read(authStateProvider).isAuthenticated) return;

      container.invalidate(proposalsListProvider);
      container.invalidate(assignmentsListProvider);
      container.invalidate(incomeSummaryProvider);
      if (container.read(ekycProvider).result?.isApproved == true) {
        container.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
      }

      final rawPayload = data['payload'];
      final type = data['type'] ?? (rawPayload is Map ? rawPayload['type'] : null);
      final isProposal = type == 'NEW_PROPOSAL' || type == 'EMERGENCY_PROPOSAL' || type == 'PROPOSAL';

      rootNavigatorKey.currentState?.popUntil((route) => route.isFirst);
      container.read(homeTabIndexProvider.notifier).state = isProposal ? 0 : 1;
    },
  );

  // Khi trạng thái đăng nhập khởi tạo xong và hợp lệ, xả mọi deep-link thông báo đang chờ
  container.listen<AuthState>(authStateProvider, (previous, next) {
    if (!next.isLoading && next.isAuthenticated) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        FcmService.flushPendingNavigation();
      });
    }
  });

  runApp(
    UncontrolledProviderScope(
      container: container,
      child: const CaniApp(),
    ),
  );

  // Sau khi khung hình đầu tiên đã vẽ, mới xin quyền thông báo, lấy FCM token và kiểm tra initialMessage
  WidgetsBinding.instance.addPostFrameCallback((_) {
    unawaited(FcmService.completePostFrameInitialization());
  });
}
