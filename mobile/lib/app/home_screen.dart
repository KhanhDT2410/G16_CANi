import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/job_proposals/screens/my_assignments_screen.dart';
import 'package:cani/features/job_radar/screens/job_radar_screen.dart';
import 'package:cani/features/skill_verification/screens/skill_hub_screen.dart';
import 'package:cani/features/skill_verification/screens/worker_profile_screen.dart';

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
    SkillHubScreen(),
    WorkerProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);

    if (authState.isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    if (!authState.isAuthenticated) {
      return const LoginScreen();
    }

    final user = authState.user!;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      TermsOfServiceDialog.checkAndShow(context, user.id);
    });

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) => setState(() => _currentIndex = index),
        indicatorColor: const Color(0x260066FF),
        backgroundColor: Colors.white,
        elevation: 8,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map, color: Color(0xFF0066FF)),
            label: 'Bản đồ',
          ),
          NavigationDestination(
            icon: Icon(Icons.assignment_outlined),
            selectedIcon: Icon(Icons.assignment, color: Color(0xFF0066FF)),
            label: 'Lịch ca',
          ),
          NavigationDestination(
            icon: Icon(Icons.workspace_premium_outlined),
            selectedIcon: Icon(Icons.workspace_premium, color: Color(0xFF0066FF)),
            label: 'Kỹ năng',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person, color: Color(0xFF0066FF)),
            label: 'Tài khoản',
          ),
        ],
      ),
    );
  }
}
