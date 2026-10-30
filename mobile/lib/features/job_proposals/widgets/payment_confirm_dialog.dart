import 'package:flutter/material.dart';

import '../models/proposal_models.dart';

class PaymentConfirmDialog extends StatefulWidget {
  final ShiftAssignmentItem assignment;

  const PaymentConfirmDialog({
    super.key,
    required this.assignment,
  });

  @override
  State<PaymentConfirmDialog> createState() => _PaymentConfirmDialogState();
}

class _PaymentConfirmDialogState extends State<PaymentConfirmDialog> {
  String _selectedStatus = 'CONFIRMED_FULL';
  final TextEditingController _commentCtrl = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _commentCtrl.dispose();
    super.dispose();
  }

  String _formatCurrency(double amount) {
    return '${amount.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.assignment;
    final declaredAmount = a.declaredAmount > 0 ? a.declaredAmount : a.totalPay;
    final methodStr = a.paymentMethod == 'BANK_TRANSFER' ? 'Chuyển khoản ngân hàng' : 'Tiền mặt';

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      backgroundColor: Colors.white,
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(22),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0077FF).withValues(alpha: 0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.payments_outlined, color: Color(0xFF0077FF), size: 24),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        'Xác nhận thù lao',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF1E293B)),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, size: 20, color: Color(0xFF64748B)),
                      onPressed: () => Navigator.of(context).pop(),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Card tóm tắt khai báo của EO
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        a.eventName,
                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Color(0xFF1E293B)),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Vị trí: ${a.jobType}',
                        style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                      ),
                      const Divider(height: 16, color: Color(0xFFE2E8F0)),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('BTC đã khai báo:', style: TextStyle(fontSize: 12, color: Color(0xFF475569))),
                          Text(
                            _formatCurrency(declaredAmount),
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: Color(0xFF059669)),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Hình thức:', style: TextStyle(fontSize: 12, color: Color(0xFF475569))),
                          Text(methodStr, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF334155))),
                        ],
                      ),
                      if (declaredAmount < a.totalPay) ...[
                        const SizedBox(height: 4),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text('Tổng thù lao ca:', style: TextStyle(fontSize: 12, color: Color(0xFF475569))),
                            Text(_formatCurrency(a.totalPay), style: const TextStyle(fontSize: 12, color: Color(0xFF64748B))),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                const Text(
                  'Tình trạng thực nhận của bạn:',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
                ),
                const SizedBox(height: 8),

                // 3 Options
                _buildOptionTile(
                  status: 'CONFIRMED_FULL',
                  title: 'Đã nhận đủ toàn bộ',
                  subtitle: 'Đã nhận đủ đúng theo số tiền thỏa thuận',
                  color: const Color(0xFF10B981),
                  icon: Icons.check_circle_outline,
                ),
                const SizedBox(height: 8),
                _buildOptionTile(
                  status: 'CONFIRMED_PARTIAL',
                  title: 'Nhận một phần',
                  subtitle: 'Số tiền thực nhận ít hơn mức khai báo',
                  color: const Color(0xFFF59E0B),
                  icon: Icons.warning_amber_rounded,
                ),
                const SizedBox(height: 8),
                _buildOptionTile(
                  status: 'NOT_RECEIVED',
                  title: 'Chưa nhận được tiền',
                  subtitle: 'Chưa nhận được hoặc sai lệch hoàn toàn (CSKH hỗ trợ)',
                  color: const Color(0xFFEF4444),
                  icon: Icons.error_outline,
                ),
                const SizedBox(height: 14),

                // Ô ghi chú
                TextFormField(
                  controller: _commentCtrl,
                  maxLines: 2,
                  style: const TextStyle(fontSize: 13),
                  decoration: InputDecoration(
                    labelText: _selectedStatus == 'CONFIRMED_FULL' ? 'Ghi chú (không bắt buộc)' : 'Lý do / Số tiền thực tế (*)',
                    hintText: _selectedStatus == 'CONFIRMED_FULL'
                        ? 'Nhập lời cảm ơn hoặc phản hồi...'
                        : 'Ví dụ: Mới nhận 300k tiền mặt, còn thiếu 200k...',
                    hintStyle: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: Color(0xFFCBD5E1))),
                    enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: Color(0xFFE2E8F0))),
                    focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: Color(0xFF0077FF), width: 1.5)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  ),
                  validator: (val) {
                    if (_selectedStatus != 'CONFIRMED_FULL' && (val == null || val.trim().length < 4)) {
                      return 'Vui lòng nhập lý do cụ thể để CSKH hỗ trợ đối soát';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 18),

                // Actions
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          side: const BorderSide(color: Color(0xFFCBD5E1)),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        child: const Text('Hủy', style: TextStyle(color: Color(0xFF64748B), fontWeight: FontWeight.w600)),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      flex: 2,
                      child: ElevatedButton(
                        onPressed: () {
                          if (_formKey.currentState?.validate() ?? false) {
                            Navigator.of(context).pop({
                              'status': _selectedStatus,
                              'comment': _commentCtrl.text.trim(),
                            });
                          }
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: _selectedStatus == 'NOT_RECEIVED'
                              ? const Color(0xFFDC2626)
                              : (_selectedStatus == 'CONFIRMED_PARTIAL' ? const Color(0xFFD97706) : const Color(0xFF0077FF)),
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          elevation: 0,
                        ),
                        child: Text(
                          _selectedStatus == 'NOT_RECEIVED' ? 'Báo cáo sự cố' : 'Gửi xác nhận',
                          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildOptionTile({
    required String status,
    required String title,
    required String subtitle,
    required Color color,
    required IconData icon,
  }) {
    final isSelected = _selectedStatus == status;

    return InkWell(
      onTap: () => setState(() => _selectedStatus = status),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
        decoration: BoxDecoration(
          color: isSelected ? color.withValues(alpha: 0.08) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? color : const Color(0xFFE2E8F0),
            width: isSelected ? 1.5 : 1.0,
          ),
        ),
        child: Row(
          children: [
            Icon(
              isSelected ? Icons.radio_button_checked : Icons.radio_button_off,
              color: isSelected ? color : const Color(0xFF94A3B8),
              size: 20,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: isSelected ? color : const Color(0xFF1E293B),
                    ),
                  ),
                  Text(
                    subtitle,
                    style: const TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                  ),
                ],
              ),
            ),
            Icon(icon, color: isSelected ? color : const Color(0xFFCBD5E1), size: 18),
          ],
        ),
      ),
    );
  }
}

