import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/shared/widgets/bottom_nav_bar.dart';

void main() {
  Future<void> pumpNavBar(WidgetTester tester, {int currentIndex = 0, int badgeCount = 0}) {
    return tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.light(),
        home: Scaffold(
          bottomNavigationBar: BottomNavBar(
            currentIndex: currentIndex,
            onTap: (_) {},
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
                badgeCount: badgeCount,
              ),
            ],
          ),
        ),
      ),
    );
  }

  testWidgets('selected pill is filled with light brand color', (tester) async {
    await pumpNavBar(tester, currentIndex: 0);
    await tester.pumpAndSettle();

    final brand = Theme.of(tester.element(find.byType(BottomNavBar))).cani.brand;
    expect(brand, const Color(0xFF5B86E5));

    final pills = tester
        .widgetList<AnimatedContainer>(find.byType(AnimatedContainer))
        .where((c) {
          final d = c.decoration;
          return d is BoxDecoration && d.color == brand;
        });
    expect(pills.isNotEmpty, isTrue, reason: 'selected pill must be filled with Theme brand');
  });

  testWidgets('badge renders on unselected item with count', (tester) async {
    await pumpNavBar(tester, currentIndex: 0, badgeCount: 3);
    await tester.pumpAndSettle();

    expect(find.text('3'), findsOneWidget);
    expect(find.byType(Badge), findsOneWidget);
  });
}
