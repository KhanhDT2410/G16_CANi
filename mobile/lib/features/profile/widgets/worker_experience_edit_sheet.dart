import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/profile/models/worker_experience_model.dart';
import 'package:cani/features/profile/providers/worker_experience_provider.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class WorkerExperienceEditSheet extends ConsumerStatefulWidget {
  const WorkerExperienceEditSheet({
    super.key,
    this.initialExperience,
    this.focusBio = false,
  });

  final WorkerExperienceModel? initialExperience;
  final bool focusBio;

  @override
  ConsumerState<WorkerExperienceEditSheet> createState() => _WorkerExperienceEditSheetState();
}

class _WorkerExperienceEditSheetState extends ConsumerState<WorkerExperienceEditSheet> {
  late final TextEditingController _yearsController;
  late final TextEditingController _bioController;
  late final FocusNode _bioFocusNode;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _yearsController = TextEditingController(
      text: (widget.initialExperience?.yearsExperience ?? 0).toString(),
    );
    _bioController = TextEditingController(
      text: widget.initialExperience?.bio ?? '',
    );
    _bioFocusNode = FocusNode();

    if (widget.focusBio) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _bioFocusNode.requestFocus();
        }
      });
    }
  }

  @override
  void dispose() {
    _yearsController.dispose();
    _bioController.dispose();
    _bioFocusNode.dispose();
    super.dispose();
  }

  Future<void> _handleSave() async {
    final years = int.tryParse(_yearsController.text.trim()) ?? 0;
    final bio = _bioController.text.trim();

    setState(() => _isSaving = true);

    final success = await ref.read(workerExperienceProvider.notifier).updateExperience(
          yearsExperience: years,
          bio: bio,
        );

    if (!mounted) return;
    setState(() => _isSaving = false);

    if (success) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cập nhật thông tin hồ sơ thành công!'),
          backgroundColor: Color(0xFF059669),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Có lỗi xảy ra khi cập nhật hồ sơ.'),
          backgroundColor: Color(0xFFDC2626),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
        left: 20,
        right: 20,
        top: 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.edit_note_rounded, color: Color(0xFF0066FF), size: 24),
                const SizedBox(width: 8),
                Text(
                  'Cập nhật Hồ sơ & Giới thiệu',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: t.text,
                      ),
                ),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              'Số năm kinh nghiệm làm sự kiện',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: t.text),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: _yearsController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                hintText: 'Ví dụ: 2',
                suffixText: 'năm',
                filled: true,
                fillColor: t.surface,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide(color: t.border),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Giới thiệu bản thân',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: t.text),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: _bioController,
              focusNode: _bioFocusNode,
              maxLines: 4,
              maxLength: 500,
              decoration: InputDecoration(
                hintText: 'Mô tả ngắn gọn kỹ năng, thái độ làm việc, các loại sự kiện bạn từng tham gia...',
                filled: true,
                fillColor: t.surface,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide(color: t.border),
                ),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: _isSaving ? null : _handleSave,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0066FF),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
                child: _isSaving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text(
                        'Lưu thông tin hồ sơ',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                      ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
