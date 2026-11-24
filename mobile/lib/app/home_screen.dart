import 'dart:async';
import 'dart:collection';
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
  static const int _maxTrackedNotifIds = 200;

  Timer? _notifSyncTimer;
  final LinkedHashSet<String> _notifiedIds = LinkedHashSet<String>();
  DateTime? _lastSyncedCreatedAt;
  bool _hasInitialNotifSync = false;
  bool _syncingNotifs = false;
  final Set<int> _visitedTabs = {0};

  void _rememberNotifiedId(String id) {
    _notifiedIds.remove(id);
    _notifiedIds.add(id);
    while (_notifiedIds.length > _maxTrackedNotifIds) {
      _notifiedIds.remove(_notifiedIds.first);
    }
  }

  final List<Widget> _pages = const [
    JobRadarScreen(),
    MyAssignmentsScreen(),
    WorkerProfileScreen(),
  ];

  void _startNotifSyncTimer() {
    if (_notifSyncTimer?.isActive ?? false) return;
    _notifSyncTimer = Timer.periodic(const Duration(seconds: 20), (_) {
      _syncDeviceNotifications();
    });
  }

  void _stopNotifSyncTimer() {
    _notifSyncTimer?.cancel();
    _notifSyncTimer = null;
  }

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
    _startNotifSyncTimer();
  }

  @override
  void dispose() {
    _stopNotifSyncTimer();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        _startNotifSyncTimer();
        _syncDeviceNotifications();
        if (ref.read(authStateProvider).isAuthenticated) {
          ref.invalidate(proposalsListProvider);
          ref.invalidate(assignmentsListProvider);
          if (ref.read(ekycProvider).result?.isApproved == true) {
            ref.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
          }
        }
        break;
      case AppLifecycleState.paused:
      case AppLifecycleState.inactive:
      case AppLifecycleState.hidden:
      case AppLifecycleState.detached:
        _stopNotifSyncTimer();
        break;
    }
  }

  /// Đồng bộ thông báo mới từ backend và bật banner Local Notification trên thiết bị
  /// (bổ sung fallback khi thiết bị/emulator không có Google Play Services cho FCM).
  /// Sử dụng con trỏ thời gian [_lastSyncedCreatedAt] kết hợp tập hợp giới hạn [_notifiedIds]
  /// (tối đa 200 ID) để ngắt sớm các thông báo cũ khi hệ thống có >50 ca active cùng lúc.
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

      final cursor = _lastSyncedCreatedAt;
      DateTime? newestCreatedAt = cursor;

      if (!_hasInitialNotifSync) {
        for (final entry in raw) {
          if (entry is! Map) continue;
          if (entry['isRead'] == true || entry['notificationId'] == null) continue;
          final idStr = entry['notificationId'].toString();
          _rememberNotifiedId(idStr);
          final createdAt = DateTime.tryParse(entry['createdAt']?.toString() ?? '');
          if (createdAt != null && (newestCreatedAt == null || createdAt.isAfter(newestCreatedAt))) {
            newestCreatedAt = createdAt;
          }
        }
        _lastSyncedCreatedAt = newestCreatedAt;
        _hasInitialNotifSync = true;
        return;
      }

      final newlyArrived = <Map>[];
      for (final entry in raw) {
        if (entry is! Map) continue;
        if (entry['isRead'] == true || entry['notificationId'] == null) continue;

        final idStr = entry['notificationId'].toString();
        final createdAt = DateTime.tryParse(entry['createdAt']?.toString() ?? '');

        if (_notifiedIds.contains(idStr)) {
          if (cursor != null && createdAt != null && !createdAt.isAfter(cursor)) {
            break;
          }
          continue;
        }

        if (cursor != null && createdAt != null && createdAt.isBefore(cursor)) {
          break;
        }

        newlyArrived.add(entry);
        _rememberNotifiedId(idStr);
        if (createdAt != null && (newestCreatedAt == null || createdAt.isAfter(newestCreatedAt))) {
          newestCreatedAt = createdAt;
        }
      }

      _lastSyncedCreatedAt = newestCreatedAt;

      if (newlyArrived.isNotEmpty) {
        final isOnline = ref.read(jobRadarProvider).isOnline;
        final visibleArrived = newlyArrived.where((entry) {
          final typeStr = entry['type']?.toString().toUpperCase();
          if (!isOnline && typeStr == 'PROPOSAL') {
            return false;
          }
          return true;
        }).toList();

        if (visibleArrived.isEmpty) {
          return;
        }

        if (isOnline) {
          ref.invalidate(proposalsListProvider);
        }
        ref.invalidate(assignmentsListProvider);
        ref.invalidate(incomeSummaryProvider);
        if (isOnline && ref.read(ekycProvider).result?.isApproved == true) {
          ref.read(jobRadarProvider.notifier).scanNearbyShifts(silent: true);
        }
        for (final n in visibleArrived.take(3)) {
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
      if (previous?.user?.id != next.user?.id) {
        _notifiedIds.clear();
        _lastSyncedCreatedAt = null;
        _hasInitialNotifSync = false;
      }
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

    _visitedTabs.add(currentIndex);

    final activeAssignmentsCount = ref.watch(
      assignmentsListProvider.select(
        (asyncVal) =>
            (asyncVal.asData?.value ?? const <ShiftAssignmentItem>[])
                .where((a) => a.status != 'COMPLETED' && a.checkoutTime == null)
                .length,
      ),
    );

    return Scaffold(
      body: IndexedStack(
        index: currentIndex,
        children: List<Widget>.generate(
          _pages.length,
          (index) => _visitedTabs.contains(index) ? _pages[index] : const SizedBox.shrink(),
        ),
      ),
      bottomNavigationBar: BottomNavBar(
        currentIndex: currentIndex,
        onTap: (index) {
          _visitedTabs.add(index);
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
