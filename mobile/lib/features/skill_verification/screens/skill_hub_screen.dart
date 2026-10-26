import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/ai_verification_tab.dart';
import 'package:cani/features/skill_verification/widgets/skill_points_tab.dart';
import 'package:cani/features/skill_verification/widgets/verification_history_tab.dart';

class SkillHubScreen extends ConsumerWidget {
  const SkillHubScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text(
            'Trung Tâm Kỹ Năng & Bằng Cấp',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Làm mới dữ liệu kỹ năng',
              onPressed: () => ref.read(skillVerificationProvider.notifier).loadAll(),
            ),
          ],
          bottom: const TabBar(
            isScrollable: false,
            indicatorColor: Color(0xFF0077FF),
            labelColor: Color(0xFF0077FF),
            unselectedLabelColor: Color(0xFF64748B),
            labelStyle: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            tabs: [
              Tab(icon: Icon(Icons.workspace_premium_outlined, size: 20), text: 'Cấp bậc & SP'),
              Tab(icon: Icon(Icons.auto_awesome_outlined, size: 20), text: 'Thẩm định AI'),
              Tab(icon: Icon(Icons.history_edu_outlined, size: 20), text: 'Lịch sử'),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            SkillPointsTab(),
            AiVerificationTab(),
            VerificationHistoryTab(),
          ],
        ),
      ),
    );
  }
}
