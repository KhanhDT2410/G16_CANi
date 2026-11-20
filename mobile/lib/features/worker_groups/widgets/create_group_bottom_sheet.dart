import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/worker_groups/models/worker_group_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class CreateGroupBottomSheet extends ConsumerStatefulWidget {
  const CreateGroupBottomSheet({super.key});

  @override
  ConsumerState<CreateGroupBottomSheet> createState() =>
      _CreateGroupBottomSheetState();
}

class _CreateGroupBottomSheetState
    extends ConsumerState<CreateGroupBottomSheet> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _descController = TextEditingController();

  String? _selectedSkill;
  int _minSize = 5;
  int _maxSize = 15;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _submit(String skillFocus) async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    final model = CreateWorkerGroupModel(
      name: _nameController.text.trim(),
      description: _descController.text.trim().isEmpty
          ? null
          : _descController.text.trim(),
      skillFocus: skillFocus,
      minSize: _minSize,
      maxSize: _maxSize,
    );

    final success =
        await ref.read(workerGroupsProvider.notifier).createGroup(model);

    if (!mounted) return;
    setState(() => _isSubmitting = false);

    if (success) {
      Navigator.of(context).pop(true);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Lập đội "${model.name}" thành công!'),
          backgroundColor: const Color(0xFF0D9488),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Lập đội thất bại. Vui lòng kiểm tra lại thông tin!'),
          backgroundColor: Colors.redAccent,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final skillState = ref.watch(skillVerificationProvider);
    final skillNames = skillState.taxonomy.map((s) => s.name).toList();
    final skillOptions = skillNames.isNotEmpty
        ? skillNames
        : const [
            'Phục vụ bàn',
            'Pha chế (Bartender)',
            'Bếp / Phụ bếp',
            'Lễ tân / Check-in',
            'Điều phối âm thanh ánh sáng',
            'Hậu cần / Setup',
            'Thu ngân',
            'PG / PB',
            'Bảo vệ / An ninh',
            'MC / Hoạt náo',
          ];

    final currentSkill =
        (_selectedSkill != null && skillOptions.contains(_selectedSkill))
            ? _selectedSkill!
            : skillOptions.first;

    return Container(
      padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + bottomInset),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0FDFA),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFCCFBF1)),
                    ),
                    child: const Icon(
                      Icons.group_add_rounded,
                      color: Color(0xFF0D9488),
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                      'Lập Đội Mới',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Tên đội
              const Text(
                'Tên đội *',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 6),
              TextFormField(
                controller: _nameController,
                decoration: InputDecoration(
                  hintText: 'VD: Đội Tiệc Cưới Sài Gòn Pro',
                  hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                  ),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                ),
                validator: (val) {
                  if (val == null || val.trim().isEmpty) {
                    return 'Vui lòng nhập tên nhóm';
                  }
                  if (val.trim().length < 3) {
                    return 'Tên nhóm tối thiểu 3 ký tự';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Kỹ năng trọng tâm
              const Text(
                'Lĩnh vực / Kỹ năng trọng tâm *',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 6),
              DropdownButtonFormField<String>(
                initialValue: currentSkill,
                decoration: InputDecoration(
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                  ),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                ),
                items: skillOptions.map((opt) {
                  return DropdownMenuItem(
                    value: opt,
                    child: Text(
                      opt,
                      style: const TextStyle(fontSize: 13, color: Color(0xFF0F172A)),
                      overflow: TextOverflow.ellipsis,
                    ),
                  );
                }).toList(),
                onChanged: (val) {
                  if (val != null) setState(() => _selectedSkill = val);
                },
              ),
              const SizedBox(height: 14),

              // Quy mô thành viên
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Tối thiểu',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF334155),
                          ),
                        ),
                        const SizedBox(height: 6),
                        DropdownButtonFormField<int>(
                          initialValue: _minSize,
                          decoration: InputDecoration(
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                            ),
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 12, vertical: 10),
                          ),
                          items: [2, 3, 5, 8, 10].map((count) {
                            return DropdownMenuItem(
                              value: count,
                              child: Text('$count người'),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) {
                              const maxOptions = [5, 10, 15, 20, 30, 50];
                              setState(() {
                                _minSize = val;
                                if (_maxSize < _minSize) {
                                  _maxSize = maxOptions.firstWhere(
                                    (o) => o >= _minSize,
                                    orElse: () => 50,
                                  );
                                }
                              });
                            }
                          },
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Tối đa',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF334155),
                          ),
                        ),
                        const SizedBox(height: 6),
                        DropdownButtonFormField<int>(
                          initialValue: _maxSize,
                          decoration: InputDecoration(
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                            ),
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 12, vertical: 10),
                          ),
                          items: [5, 10, 15, 20, 30, 50]
                              .where((count) => count >= _minSize)
                              .map((count) {
                            return DropdownMenuItem(
                              value: count,
                              child: Text('$count người'),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) setState(() => _maxSize = val);
                          },
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Mô tả
              const Text(
                'Mô tả kinh nghiệm / tiêu chí của đội',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF334155),
                ),
              ),
              const SizedBox(height: 6),
              TextFormField(
                controller: _descController,
                maxLines: 3,
                decoration: InputDecoration(
                  hintText:
                      'VD: Đội chuyên nghiệp, đã phục vụ nhiều tiệc lớn tại Gem Center, White Palace...',
                  hintStyle: const TextStyle(fontSize: 12.5, color: Color(0xFF94A3B8)),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                  ),
                  contentPadding: const EdgeInsets.all(12),
                ),
              ),
              const SizedBox(height: 20),

              // Nút tạo nhóm
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _isSubmitting ? null : () => _submit(currentSkill),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0D9488),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: _isSubmitting
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          'Lập đội',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
