import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
import 'package:cani/features/job_radar/screens/job_radar_screen.dart';
import 'package:cani/features/profile/profile.dart';
import 'package:cani/shared/widgets/bottom_nav_bar.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;

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
    final proposalsCount = ref.watch(proposalsCountProvider).asData?.value ?? 0;

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

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: BottomNavBar(
        currentIndex: _currentIndex,
        onTap: (index) {
          setState(() => _currentIndex = index);
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
            badgeCount: proposalsCount,
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
