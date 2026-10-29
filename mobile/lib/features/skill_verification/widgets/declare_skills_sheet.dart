import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'skill_hub_theme.dart';

class DeclareSkillsSheet extends ConsumerStatefulWidget {
  const DeclareSkillsSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const DeclareSkillsSheet(),
    );
  }

  @override
  ConsumerState<DeclareSkillsSheet> createState() => _DeclareSkillsSheetState();
}

class _DeclareSkillsSheetState extends ConsumerState<DeclareSkillsSheet> {
  int _currentStep = 1; // 1: Pick Skill, 2: Pick Level & Save
  String? _selectedSkillId;
  String _selectedProficiency = 'INTERMEDIATE';
  bool _isSaving = false;

  final List<Map<String, String>> _levels = const [
    {'code': 'BEGINNER', 'label': 'Cơ bản', 'desc': 'Có kiến thức nền tảng, cần hướng dẫn ban đầu'},
    {'code': 'INTERMEDIATE', 'label': 'Trung cấp', 'desc': 'Đã có kinh nghiệm, làm việc độc lập trong các ca sự kiện tiêu chuẩn'},
    {'code': 'ADVANCED', 'label': 'Nâng cao', 'desc': 'Kinh nghiệm dày dặn, xử lý linh hoạt mọi tình huống nghiệp vụ'},
    {'code': 'EXPERT', 'label': 'Chuyên gia', 'desc': 'Chuyên môn xuất sắc, có khả năng điều phối và hướng dẫn nhóm'},
  ];

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(skillVerificationProvider);
    final declaredSkillIds = state.mySkills.map((s) => s.skillId).toSet();

    return Container(
      height: MediaQuery.of(context).size.height * 0.68,
      decoration: const BoxDecoration(
        color: SkillHubColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: SkillHubColors.borderSoft,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 14),

          // Header with Step Pill
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _currentStep == 1 ? '1. Chọn kỹ năng' : '2. Mức độ thành thạo',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                  color: SkillHubColors.textPrimary,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFCAF0F8),
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: SkillHubColors.borderSoft),
                ),
                child: Text(
                  'Bước $_currentStep / 2',
                  style: const TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w700,
                    color: SkillHubColors.textSecondary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Step Body
          Expanded(
            child: _currentStep == 1
                ? _buildStep1TagCloud(state.taxonomy, declaredSkillIds)
                : _buildStep2LevelSelector(state.taxonomy),
          ),

          // Action Button
          _buildActionButton(),
        ],
      ),
    );
  }

  Widget _buildStep1TagCloud(List<SkillModel> taxonomy, Set<String> declaredIds) {
    if (taxonomy.isEmpty) {
      return const Center(
        child: CircularProgressIndicator(color: SkillHubColors.ctaPrimary),
      );
    }

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Chọn các kỹ năng bạn đã có kinh nghiệm để nhận đề xuất ca làm việc phù hợp:',
            style: TextStyle(fontSize: 12, color: SkillHubColors.textSecondary, height: 1.4),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: taxonomy.map((skill) {
              final isSelected = _selectedSkillId == skill.skillId;
              final isAlreadyDeclared = declaredIds.contains(skill.skillId);

              return ChoiceChip(
                label: Text(
                  '${skill.name}${isAlreadyDeclared ? " (Đã có)" : ""}',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                    color: isSelected ? Colors.white : SkillHubColors.textPrimary,
                  ),
                ),
                selected: isSelected,
                selectedColor: SkillHubColors.ctaPrimary,
                backgroundColor: SkillHubColors.surfaceTint,
                side: BorderSide(
                  color: isSelected ? SkillHubColors.ctaPrimary : SkillHubColors.borderSoft,
                  width: 1.2,
                ),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
                onSelected: (selected) {
                  setState(() => _selectedSkillId = selected ? skill.skillId : null);
                },
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildStep2LevelSelector(List<SkillModel> taxonomy) {
    final selectedSkill = taxonomy.where((s) => s.skillId == _selectedSkillId).firstOrNull;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (selectedSkill != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: const Color(0xFFE8F9FD),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: SkillHubColors.borderSoft),
              ),
              child: Row(
                children: [
                  const Icon(Icons.check_circle_outline, size: 16, color: SkillHubColors.ctaHover),
                  const SizedBox(width: 8),
                  Text(
                    'Đang chọn: ${selectedSkill.name}',
                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12, color: SkillHubColors.textPrimary),
                  ),
                ],
              ),
            ),
          const Text(
            'Chọn mức độ thành thạo hiện tại của bạn để tối ưu ghép nối ca làm:',
            style: TextStyle(fontSize: 12, color: SkillHubColors.textSecondary, height: 1.4),
          ),
          const SizedBox(height: 12),
          ..._levels.map((level) {
            final isChosen = _selectedProficiency == level['code'];
            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              decoration: BoxDecoration(
                color: isChosen ? const Color(0xFFE8F9FD) : SkillHubColors.surfaceTint,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                  color: isChosen ? SkillHubColors.ctaPrimary : SkillHubColors.borderSoft,
                  width: isChosen ? 1.5 : 1,
                ),
              ),
              child: ListTile(
                dense: true,
                title: Text(
                  level['label']!,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    color: isChosen ? SkillHubColors.textPrimary : Colors.black87,
                  ),
                ),
                subtitle: Text(level['desc']!, style: const TextStyle(fontSize: 11)),
                trailing: isChosen
                    ? const Icon(Icons.check_circle, color: SkillHubColors.ctaPrimary, size: 20)
                    : const Icon(Icons.radio_button_unchecked, color: Colors.grey, size: 20),
                onTap: () => setState(() => _selectedProficiency = level['code']!),
              ),
            );
          }),
        ],
      ),
    );
  }

  Widget _buildActionButton() {
    return Row(
      children: [
        if (_currentStep == 2) ...[
          OutlinedButton(
            onPressed: () => setState(() => _currentStep = 1),
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: SkillHubColors.borderSoft),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            ),
            child: const Text('Quay lại', style: TextStyle(color: SkillHubColors.textSecondary)),
          ),
          const SizedBox(width: 10),
        ],
        Expanded(
          child: SizedBox(
            height: 46,
            child: ElevatedButton(
              onPressed: _isSaving
                  ? null
                  : () {
                      if (_currentStep == 1) {
                        if (_selectedSkillId == null) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Vui lòng chọn một kỹ năng để tiếp tục!')),
                          );
                          return;
                        }
                        setState(() => _currentStep = 2);
                      } else {
                        _saveSkill();
                      }
                    },
              style: ElevatedButton.styleFrom(
                backgroundColor: SkillHubColors.ctaPrimary,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: _isSaving
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text(
                      _currentStep == 1 ? 'Tiếp tục chọn trình độ →' : 'Lưu & Kích hoạt kỹ năng',
                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Colors.white),
                    ),
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _saveSkill() async {
    setState(() => _isSaving = true);
    final success = await ref.read(skillVerificationProvider.notifier).declareSkills([
      {
        'skillId': _selectedSkillId,
        'proficiency': _selectedProficiency,
      }
    ]);
    if (mounted) {
      setState(() => _isSaving = false);
      if (success) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Cập nhật kỹ năng thành công! Hồ sơ của bạn đã sẵn sàng nhận việc.'),
            backgroundColor: Colors.green,
          ),
        );
      }
    }
  }
}
