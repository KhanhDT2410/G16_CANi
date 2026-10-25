import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/job_proposals/screens/job_proposals_screen.dart';
import 'package:cani/features/job_radar/screens/job_radar_screen.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/ai_verification_tab.dart';
import 'package:cani/features/skill_verification/widgets/skill_points_tab.dart';
import 'package:cani/features/skill_verification/widgets/verification_history_tab.dart';
import 'package:cani/features/skill_verification/widgets/worker_profile_tab.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;

  final List<Widget> _pages = const [
    JobRadarScreen(),
    SkillPointsTab(),
    AiVerificationTab(),
    VerificationHistoryTab(),
    WorkerProfileTab(),
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
    final theme = Theme.of(context);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      TermsOfServiceDialog.checkAndShow(context, user.id);
    });

    final titles = [
      'Bản Đồ Việc Làm',
      'Bảng Điểm Kỹ Năng',
      'AI Thẩm Định Chứng Chỉ',
      'Lịch Sử Xác Thực',
      'Hồ Sơ Worker & CV',
    ];

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            CircleAvatar(
              radius: 16,
              backgroundColor: theme.colorScheme.primaryContainer,
              child: Text(
                user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'W',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                  color: theme.colorScheme.onPrimaryContainer,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    titles[_currentIndex],
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
                  ),
                  Text(
                    user.fullName,
                    style: TextStyle(fontSize: 12, color: theme.colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.work_history_outlined),
            tooltip: 'Lời mời việc & Ca làm',
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const JobProposalsScreen()),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.assignment_outlined),
            tooltip: 'Hồ sơ kỹ năng & Thẩm định',
            onPressed: () => setState(() => _currentIndex = 1),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Làm mới dữ liệu',
            onPressed: () => ref.read(skillVerificationProvider.notifier).loadAll(),
          ),
        ],
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) => setState(() => _currentIndex = index),
        indicatorColor: const Color(0xFF0077FF).withOpacity(0.15),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map, color: Color(0xFF0077FF)),
            label: 'Bản đồ',
          ),
          NavigationDestination(
            icon: Icon(Icons.work_outline),
            selectedIcon: Icon(Icons.work, color: Color(0xFF0077FF)),
            label: 'Việc làm',
          ),
          NavigationDestination(
            icon: Icon(Icons.notifications_none),
            selectedIcon: Icon(Icons.notifications, color: Color(0xFF0077FF)),
            label: 'Thông báo',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: Icon(Icons.account_balance_wallet, color: Color(0xFF0077FF)),
            label: 'Thu nhập',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person, color: Color(0xFF0077FF)),
            label: 'Cá nhân',
          ),
        ],
      ),
    );
  }
}
