import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/data/help_center_repository.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';

class CreateTicketScreen extends ConsumerStatefulWidget {
  const CreateTicketScreen({super.key});

  @override
  ConsumerState<CreateTicketScreen> createState() => _CreateTicketScreenState();
}

class _CreateTicketScreenState extends ConsumerState<CreateTicketScreen> {
  final _formKey = GlobalKey<FormState>();
  String _selectedCategory = 'GENERAL';
  final _subjectController = TextEditingController();
  final _descController = TextEditingController();
  bool _isSubmitting = false;

  static const List<Map<String, String>> _categories = [
    {'key': 'GENERAL', 'label': 'Thắc mắc chung'},
    {'key': 'ACCOUNT', 'label': 'Tài khoản & Xác thực eKYC'},
    {'key': 'BILLING', 'label': 'Thù lao & Thanh toán'},
    {'key': 'TECHNICAL', 'label': 'Lỗi ứng dụng hoặc GPS'},
    {'key': 'OTHER', 'label': 'Vấn đề khác'},
  ];

  @override
  void dispose() {
    _subjectController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _submitTicket() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);
    final repo = ref.read(helpCenterRepositoryProvider);

    try {
      final ticket = await repo.createTicket(
        category: _selectedCategory,
        subject: _subjectController.text.trim(),
        description: _descController.text.trim(),
      );

      ref.invalidate(myTicketsProvider);

      if (mounted) {
        setState(() => _isSubmitting = false);
        Navigator.pop(context, true);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đã tạo yêu cầu hỗ trợ mã số: ${ticket.referenceNo}'),
            backgroundColor: const Color(0xFF16A34A),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.toString().replaceAll('Exception: ', '')),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Gửi Yêu Cầu Hỗ Trợ',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: Color(0xFF0F172A)),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded, color: Color(0xFF0F172A)),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Danh mục
            const Text(
              'Danh mục yêu cầu:',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFCBD5E1)),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: _selectedCategory,
                  isExpanded: true,
                  items: _categories.map((c) {
                    return DropdownMenuItem(
                      value: c['key'],
                      child: Text(c['label']!, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    );
                  }).toList(),
                  onChanged: (val) {
                    if (val != null) setState(() => _selectedCategory = val);
                  },
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Tiêu đề
            const Text(
              'Tiêu đề tóm tắt:',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 6),
            TextFormField(
              controller: _subjectController,
              decoration: InputDecoration(
                hintText: 'Ví dụ: Sai lệch số tiền thù lao ca ngày 18/11...',
                hintStyle: const TextStyle(fontSize: 12.5, color: Color(0xFF94A3B8)),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              ),
              validator: (v) {
                if (v == null || v.trim().isEmpty) return 'Vui lòng nhập tiêu đề tóm tắt';
                return null;
              },
            ),
            const SizedBox(height: 16),

            // Mô tả chi tiết
            Row(
              children: [
                const Text(
                  'Mô tả chi tiết sự việc:',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
                ),
                const Spacer(),
                Text(
                  '${_descController.text.length}/3000 ký tự (Tối thiểu 30)',
                  style: TextStyle(
                    fontSize: 11,
                    color: _descController.text.length < 30 ? const Color(0xFFEF4444) : const Color(0xFF16A34A),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            TextFormField(
              controller: _descController,
              maxLines: 6,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: 'Vui lòng mô tả rõ sự cố, thời gian, tên sự kiện, mã ca làm việc liên quan để bộ phận CSKH hỗ trợ nhanh nhất...',
                hintStyle: const TextStyle(fontSize: 12.5, color: Color(0xFF94A3B8)),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                ),
                contentPadding: const EdgeInsets.all(12),
              ),
              validator: (v) {
                if (v == null || v.trim().length < 30) {
                  return 'Mô tả cần tối thiểu 30 ký tự để làm rõ sự việc';
                }
                return null;
              },
            ),
            const SizedBox(height: 24),

            // Nút gửi
            SizedBox(
              height: 46,
              child: FilledButton.icon(
                onPressed: _isSubmitting ? null : _submitTicket,
                icon: _isSubmitting
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Icon(Icons.send_rounded, size: 18),
                label: Text(
                  _isSubmitting ? 'Đang gửi yêu cầu...' : 'Gửi Yêu Cầu Hỗ Trợ',
                  style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5),
                ),
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF0066FF),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
