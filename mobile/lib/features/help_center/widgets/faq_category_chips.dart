import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/providers/help_center_providers.dart';

class FaqCategoryChips extends ConsumerWidget {
  const FaqCategoryChips({super.key});

  static const List<Map<String, String?>> _categories = [
    {'label': 'Tất cả', 'key': null},
    {'label': 'Tài khoản & Định danh', 'key': 'ACCOUNT'},
    {'label': 'Ca làm & Điểm danh', 'key': 'SHIFT'},
    {'label': 'Thù lao & Thanh toán', 'key': 'PAYMENT'},
    {'label': 'Tranh chấp & Khiếu nại', 'key': 'DISPUTE'},
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedKey = ref.watch(selectedFaqCategoryProvider);

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: _categories.map((cat) {
          final isSelected = selectedKey == cat['key'];
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: InkWell(
              onTap: () {
                ref.read(selectedFaqCategoryProvider.notifier).state = cat['key'];
              },
              borderRadius: BorderRadius.circular(8),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                decoration: BoxDecoration(
                  color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFE2E8F0),
                  ),
                ),
                child: Text(
                  cat['label']!,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                    color: isSelected ? Colors.white : const Color(0xFF334155),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}
