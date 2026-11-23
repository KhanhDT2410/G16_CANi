import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/notifications/local_notification_service.dart';
import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/auth/screens/complete_profile_screen.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/screens/job_radar_screen.dart';
import 'package:cani/features/profile/profile.dart';
import 'package:cani/shared/widgets/bottom_nav_bar.dart';

/// Index of the currently selected bottom-nav tab. Shared so screens can deep-link / restore.
final homeTabIndexProvider = StateProvider<int>((ref) => 0);

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> with WidgetsBindingObserver {
  Timer? _notifSyncTimer;
  Set<String>? _seenNotifIds;
  bool _syncingNotifs = false;

  final List<Widget> _pages = const [
    JobRadarScreen(),
    MyAssignmentsScreen(),
    WorkerProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final user = ref.read(authStateProvider).user;
      if (user != null) {
        TermsOfServiceDialog.checkAndShow(context, user.id);
        _syncDeviceNotifications();
      }
    });
    _notifSyncTimer = Timer.periodic(const Duration(seconds: 20), (_) {
      _syncDeviceNotifications();
    });
  }

  @override
  void dispose() {
    _notifSyncTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _syncDeviceNotifications();
      if (ref.read(authStateProvider).isAuthenticated) {
        ref.invalidate(proposalsListProvider);
        ref.invalidate(assignmentsListProvider);
        if (ref.read(ekycProvider).result?.isApproved == true) {
          ref.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
        }
      }
    }
  }

  /// Đồng bộ thông báo mới từ backend và bật banner Local Notification trên thiết bị
  /// (bổ sung fallback khi thiết bị/emulator không có Google Play Services cho FCM).
  Future<void> _syncDeviceNotifications() async {
    if (_syncingNotifs || !mounted) return;
    final auth = ref.read(authStateProvider);
    if (!auth.isAuthenticated || auth.user == null) return;

    _syncingNotifs = true;
    try {
      final dio = ref.read(dioProvider);
      final res = await dio.get('/v1/notifications');
      final raw = res.data is Map ? res.data['data'] : null;
      if (raw is! List) return;

      final unread = raw
          .whereType<Map>()
          .where((item) => item['isRead'] != true && item['notificationId'] != null)
          .toList();
      final ids = unread.map((e) => e['notificationId'].toString()).toSet();

      if (_seenNotifIds == null) {
        _seenNotifIds = ids;
        return;
      }

      final newlyArrived = unread.where((e) => !_seenNotifIds!.contains(e['notificationId'].toString())).toList();
      _seenNotifIds!.addAll(ids);

      if (newlyArrived.isNotEmpty) {
        ref.invalidate(proposalsListProvider);
        ref.invalidate(assignmentsListProvider);
        ref.invalidate(incomeSummaryProvider);
        if (ref.read(ekycProvider).result?.isApproved == true) {
          ref.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
        }
        for (final n in newlyArrived.take(3)) {
          final idStr = n['notificationId'].toString();
          final title = n['title']?.toString() ?? 'Thông báo mới từ CANi';
          final body = n['body']?.toString() ?? '';
          await LocalNotificationService.showNotification(
            id: idStr.hashCode,
            title: title,
            body: body,
            payload: n['data']?.toString(),
          );
        }
      }
    } catch (_) {
      // ignore background polling failures silently
    } finally {
      _syncingNotifs = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Lắng nghe thay đổi trạng thái đăng nhập để hiển thị điều khoản một cách an toàn
    ref.listen<AuthState>(authStateProvider, (previous, next) {
      if (next.isAuthenticated && next.user != null && previous?.user?.id != next.user!.id) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            TermsOfServiceDialog.checkAndShow(context, next.user!.id);
          }
        });
      }
    });

    final authState = ref.watch(authStateProvider);
    final currentIndex = ref.watch(homeTabIndexProvider);

    if (authState.isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    if (!authState.isAuthenticated) {
      return const IntroScreen();
    }

    final currentUser = authState.user;
    if (currentUser != null &&
        ((currentUser.phone?.trim().isEmpty ?? true) || currentUser.fullName.trim().isEmpty)) {
      return const CompleteProfileScreen();
    }

    final assignments = ref.watch(assignmentsListProvider).asData?.value ?? const <ShiftAssignmentItem>[];
    final activeAssignmentsCount =
        assignments.where((a) => a.status != 'COMPLETED' && a.checkoutTime == null).length;

    return Scaffold(
      body: IndexedStack(
        index: currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: BottomNavBar(
        currentIndex: currentIndex,
        onTap: (index) {
          ref.read(homeTabIndexProvider.notifier).state = index;
          if (index == 0) {
            ref.invalidate(proposalsListProvider);
          } else if (index == 1) {
            // Tự động làm mới danh sách ca làm để đồng bộ ngay ca vừa nhận
            ref.invalidate(assignmentsListProvider);
            ref.invalidate(incomeSummaryProvider);
          }
        },
        items: [
          const BottomNavItem(
            icon: Icons.home_outlined,
            selectedIcon: Icons.home_rounded,
            label: 'Trang chủ',
          ),
          BottomNavItem(
            icon: Icons.assignment_outlined,
            selectedIcon: Icons.assignment_rounded,
            label: 'Ca làm',
            badgeCount: activeAssignmentsCount,
          ),
          const BottomNavItem(
            icon: Icons.person_outline_rounded,
            selectedIcon: Icons.person_rounded,
            label: 'Cá nhân',
          ),
        ],
      ),
    );
  }
}
