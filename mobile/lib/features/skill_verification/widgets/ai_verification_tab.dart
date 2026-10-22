import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/ai_verification_result_card.dart';

class AiVerificationTab extends ConsumerStatefulWidget {
  const AiVerificationTab({super.key});

  @override
  ConsumerState<AiVerificationTab> createState() => _AiVerificationTabState();
}

class _AiVerificationTabState extends ConsumerState<AiVerificationTab> {
  String? _selectedSkillId;
  Uint8List? _selectedFileBytes;
  String? _selectedFileName;
  final TextEditingController _notesController = TextEditingController();

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePickerPlatform.instance.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'pdf'],
      );

      if (result.isNotEmpty) {
        final file = result.first;
        final bytes = await file.readAsBytes();
        setState(() {
          _selectedFileBytes = bytes;
          _selectedFileName = file.name;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể chọn tệp: $e')),
        );
      }
    }
  }

  Future<void> _submitVerification() async {
    if (_selectedFileBytes == null || _selectedFileName == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng chọn chứng chỉ (JPG, PNG, PDF) trước khi gửi!'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    final success = await ref.read(skillVerificationProvider.notifier).submitVerification(
      fileBytes: _selectedFileBytes!,
      fileName: _selectedFileName!,
      skillId: _selectedSkillId,
      notes: _notesController.text,
    );

    if (mounted) {
      if (success) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('AI Gemini đã phân tích thành công chứng chỉ của bạn!'),
            backgroundColor: Colors.green,
          ),
        );
      } else {
        final err = ref.read(skillVerificationProvider).errorMessage ?? 'Lỗi không xác định';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Gửi thẩm định thất bại: $err'),
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

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Banner giới thiệu Tier 2 (BR-16)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [theme.colorScheme.primaryContainer, theme.colorScheme.surfaceVariant],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: theme.colorScheme.primary.withOpacity(0.2)),
            ),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: theme.colorScheme.primary,
                  child: const Icon(Icons.auto_awesome, color: Colors.white, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'AI Skill Verification (Gemini Vision)',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Tải lên chứng chỉ, bằng cấp hoặc chứng nhận nghề (Tier 2). AI tự động xác thực và cộng +25 SP/kỹ năng (BR-65)!',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Card form nộp chứng chỉ (A-15 Submit Verification)
          Card(
            elevation: 1,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Nộp Hồ Sơ / Chứng Chỉ Mới',
                    style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 16),

                  // Chọn kỹ năng mục tiêu
                  DropdownButtonFormField<String>(
                    value: _selectedSkillId,
                    decoration: const InputDecoration(
                      labelText: 'Kỹ năng mục tiêu (Taxonomy chuẩn)',
                      prefixIcon: Icon(Icons.work_outline),
                      border: OutlineInputBorder(),
                    ),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('Tự động nhận diện từ tài liệu (AI Auto-detect)')),
                      ...state.taxonomy.map(
                        (skill) => DropdownMenuItem(
                          value: skill.skillId,
                          child: Text('${skill.name} (${skill.category ?? "Sự kiện"})'),
                        ),
                      ),
                    ],
                    onChanged: (val) => setState(() => _selectedSkillId = val),
                  ),

                  const SizedBox(height: 16),

                  // Khung chọn tệp chứng chỉ thực tế (JPG, PNG, PDF)
                  InkWell(
                    onTap: _pickFile,
                    borderRadius: BorderRadius.circular(12),
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: _selectedFileName != null ? Colors.green : theme.colorScheme.outlineVariant,
                          width: _selectedFileName != null ? 2 : 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          Icon(
                            _selectedFileName != null ? Icons.check_circle : Icons.cloud_upload_outlined,
                            size: 44,
                            color: _selectedFileName != null ? Colors.green : theme.colorScheme.primary,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            _selectedFileName ?? 'Nhấn để chọn tệp chứng chỉ (PNG, JPG, PDF)',
                            style: TextStyle(
                              fontWeight: _selectedFileName != null ? FontWeight.bold : FontWeight.normal,
                              color: _selectedFileName != null ? Colors.green.shade800 : theme.colorScheme.onSurface,
                            ),
                            textAlign: TextAlign.center,
                          ),
                          if (_selectedFileBytes != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 4.0),
                              child: Text(
                                'Dung lượng: ${(_selectedFileBytes!.lengthInBytes / 1024).toStringAsFixed(1)} KB',
                                style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // Ghi chú
                  TextField(
                    controller: _notesController,
                    decoration: const InputDecoration(
                      labelText: 'Ghi chú thêm về khóa học / kinh nghiệm thực tế (nếu có)',
                      prefixIcon: Icon(Icons.note_alt_outlined),
                      border: OutlineInputBorder(),
                    ),
                    maxLines: 2,
                  ),

                  const SizedBox(height: 24),

                  // Nút Gửi thẩm định
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton.icon(
                      onPressed: state.isSubmitting ? null : _submitVerification,
                      icon: state.isSubmitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.bolt),
                      label: Text(
                        state.isSubmitting ? 'AI Gemini đang phân tích hồ sơ...' : 'GỬI THẨM ĐỊNH AI (GEMINI VISION)',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: theme.colorScheme.primary,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Hiển thị Card kết quả AI vừa thẩm định
          if (state.lastResult != null) ...[
            const SizedBox(height: 24),
            AiVerificationResultCard(result: state.lastResult!),
          ],
        ],
      ),
    );
  }
}
