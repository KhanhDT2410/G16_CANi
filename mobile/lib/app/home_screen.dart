import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/auth/screens/complete_profile_screen.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
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

class _HomeScreenState extends ConsumerState<HomeScreen> {

  final List<Widget> _pages = const [
    JobRadarScreen(),
    MyAssignmentsScreen(),
    WorkerProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final user = ref.read(authStateProvider).user;
      if (user != null) {
        TermsOfServiceDialog.checkAndShow(context, user.id);
      }
    });
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
