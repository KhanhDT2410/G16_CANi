import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/screens/profile_screen.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';

void main() {
  testWidgets('WorkerProfileScreen renders with real data', (tester) async {
    const user = UserDto(
      id: '1',
      email: 'a@cani.vn',
      fullName: 'Nguyễn Văn A',
      role: 'WORKER',
      status: 'ACTIVE',
    );
    const sp = WorkerSkillPointModel(
      id: '1',
      jobType: 'BARTENDER',
      spVerify: 50,
      spExperience: 100,
      spRating: 190,
      totalSp: 340,
      tier: 'GOLD',
      totalShiftsCompleted: 10,
      avgRating: 4.8,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileDataProvider.overrideWith(
            (ref) => ProfileData(user: user, skillPoints: [sp], ekycResult: null),
          ),
        ],
        child: const MaterialApp(home: WorkerProfileScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Nguyễn Văn A'), findsOneWidget);
    expect(find.text('4.8'), findsOneWidget);
    expect(find.text('Đánh giá TB'), findsOneWidget);
    expect(find.text('Hạng Vàng'), findsOneWidget);
    expect(find.text('Sắp ra mắt'), findsWidgets);
    // Logout footer is the last lazy-ListView item — scroll it into view.
    await tester.scrollUntilVisible(find.text('Đăng xuất'), 200);
    expect(find.text('Đăng xuất'), findsOneWidget);
  });
}
