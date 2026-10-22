import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class DeclareSkillsDialog extends ConsumerStatefulWidget {
  const DeclareSkillsDialog({super.key});

  static Future<void> show(BuildContext context) {
    return showDialog(
      context: context,
      builder: (context) => const DeclareSkillsDialog(),
    );
  }

  @override
  ConsumerState<DeclareSkillsDialog> createState() => _DeclareSkillsDialogState();
}

class _DeclareSkillsDialogState extends ConsumerState<DeclareSkillsDialog> {
  String? _selectedSkillId;
  String _selectedProficiency = 'BEGINNER';
  bool _isSubmitting = false;

  final List<Map<String, String>> _proficiencyOptions = const [
    {'value': 'BEGINNER', 'label': 'Cơ bản (Beginner)', 'desc': 'Nắm vững kiến thức căn bản, dưới sự kèm cặp'},
    {'value': 'INTERMEDIATE', 'label': 'Trung cấp (Intermediate)', 'desc': 'Làm việc độc lập trong ca sự kiện tiêu chuẩn'},
    {'value': 'ADVANCED', 'label': 'Nâng cao (Advanced)', 'desc': 'Kinh nghiệm dày dặn, xử lý sự cố linh hoạt'},
  ];

  Future<void> _submit() async {
    if (_selectedSkillId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng chọn một kỹ năng từ danh mục!'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    final success = await ref.read(skillVerificationProvider.notifier).declareSkills([
      {
        'skillId': _selectedSkillId,
        'proficiency': _selectedProficiency,
      }
    ]);

    if (mounted) {
      setState(() => _isSubmitting = false);
      if (success) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Khai báo kỹ năng thành công (Tier 1: Tự khai báo - BR-16)!'),
            backgroundColor: Colors.green,
          ),
        );
      } else {
        final err = ref.read(skillVerificationProvider).errorMessage ?? 'Lỗi không xác định';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Khai báo thất bại: $err'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(skillVerificationProvider);
    final theme = Theme.of(context);

    // Filter taxonomy: highlight already declared skills or allow updating
    final declaredSkillIds = state.mySkills.map((s) => s.skillId).toSet();

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      title: Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Icon(Icons.edit_note, color: theme.colorScheme.primary),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              'Tự Khai Báo Kỹ Năng',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.blue.shade50,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.blue.shade200),
              ),
              child: const Row(
                children: [
                  Icon(Icons.info_outline, size: 20, color: Colors.blue),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Bậc 1 (Self-Declared): Nhận đề xuất công việc ngay lập tức (BR-13). Không yêu cầu duyệt tài liệu.',
                      style: TextStyle(fontSize: 12, color: Colors.blueGrey),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Dropdown Skill
            DropdownButtonFormField<String>(
              value: _selectedSkillId,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Chọn kỹ năng',
                prefixIcon: Icon(Icons.star_outline),
                border: OutlineInputBorder(),
              ),
              items: state.taxonomy.map((s) {
                final isDeclared = declaredSkillIds.contains(s.skillId);
                return DropdownMenuItem(
                  value: s.skillId,
                  child: Text(
                    '${s.name}${isDeclared ? " (Đã khai báo)" : ""}',
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: isDeclared ? Colors.grey : null,
                      fontStyle: isDeclared ? FontStyle.italic : null,
                    ),
                  ),
                );
              }).toList(),
              onChanged: (val) => setState(() => _selectedSkillId = val),
            ),

            const SizedBox(height: 16),

            // Dropdown Proficiency
            DropdownButtonFormField<String>(
              value: _selectedProficiency,
              decoration: const InputDecoration(
                labelText: 'Trình độ tự đánh giá',
                prefixIcon: Icon(Icons.trending_up),
                border: OutlineInputBorder(),
              ),
              items: _proficiencyOptions.map((opt) {
                return DropdownMenuItem(
                  value: opt['value'],
                  child: Text(opt['label']!),
                );
              }).toList(),
              onChanged: (val) {
                if (val != null) {
                  setState(() => _selectedProficiency = val);
                }
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
          child: const Text('HỦY'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: theme.colorScheme.primary,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
          child: _isSubmitting
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Text('LƯU KỸ NĂNG', style: TextStyle(fontWeight: FontWeight.bold)),
        ),
      ],
    );
  }
}
