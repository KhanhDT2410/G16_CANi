import 'package:flutter/material.dart';
import '../providers/ekyc_provider.dart';
import 'cccd_qr_scanner_screen.dart';

class CccdConfirmDialog extends StatefulWidget {
  final EkycState state;
  final Function({
    required String cccdNumber,
    required String fullName,
    required String dob,
    required String gender,
    required String residenceAddress,
    String? issueDate,
  }) onConfirm;

  const CccdConfirmDialog({
    super.key,
    required this.state,
    required this.onConfirm,
  });

  static Future<void> show(
    BuildContext context, {
    required EkycState state,
    required Function({
      required String cccdNumber,
      required String fullName,
      required String dob,
      required String gender,
      required String residenceAddress,
      String? issueDate,
    }) onConfirm,
    CccdCardInfo? initialInfo,
  }) async {
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
        child: CccdConfirmDialog(
          state: state,
          onConfirm: onConfirm,
        ),
      ),
    );
  }

  @override
  State<CccdConfirmDialog> createState() => _CccdConfirmDialogState();
}

class _CccdConfirmDialogState extends State<CccdConfirmDialog> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _cccdController;
  late TextEditingController _nameController;
  late TextEditingController _dobController;
  late TextEditingController _addressController;
  late TextEditingController _issueDateController;
  String _gender = 'Nam';

  @override
  void initState() {
    super.initState();
    final s = widget.state;
    _cccdController = TextEditingController(text: s.confirmedCccd ?? '');
    _nameController = TextEditingController(text: s.confirmedFullName ?? '');
    _dobController = TextEditingController(text: s.confirmedDob ?? '');
    _addressController = TextEditingController(text: s.confirmedResidenceAddress ?? '');
    _issueDateController = TextEditingController(text: s.confirmedIssueDate ?? '');
    _gender = (s.confirmedGender == 'Nữ') ? 'Nữ' : 'Nam';
  }

  @override
  void dispose() {
    _cccdController.dispose();
    _nameController.dispose();
    _dobController.dispose();
    _addressController.dispose();
    _issueDateController.dispose();
    super.dispose();
  }

  void _submit() {
    if (_formKey.currentState?.validate() ?? false) {
      widget.onConfirm(
        cccdNumber: _cccdController.text.trim(),
        fullName: _nameController.text.trim().toUpperCase(),
        dob: _dobController.text.trim(),
        gender: _gender,
        residenceAddress: _addressController.text.trim(),
        issueDate: _issueDateController.text.trim(),
      );
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã cập nhật thông tin CCCD xác nhận thành công!'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  InputDecoration _inputDecoration({
    required String label,
    required String hint,
    required IconData prefixIcon,
  }) {
    return InputDecoration(
      labelText: label,
      labelStyle: const TextStyle(color: Color(0xFF64748B), fontSize: 13),
      hintText: hint,
      hintStyle: const TextStyle(color: Color(0xFF94A3B8), fontSize: 13.5),
      prefixIcon: Icon(prefixIcon, color: const Color(0xFF0077FF), size: 20),
      filled: true,
      fillColor: const Color(0xFFF8FAFC),
      counterText: '',
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFF0077FF), width: 1.5),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const inputTextStyle = TextStyle(
      fontSize: 14.5,
      fontWeight: FontWeight.w600,
      color: Color(0xFF0F172A),
    );

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Header
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: const BoxDecoration(
                      color: Color(0xFFEFF6FF),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.badge_outlined, color: Color(0xFF0077FF)),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Xác Nhận Thông Tin Thẻ CCCD',
                          style: TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 16.5,
                            color: Color(0xFF0B1B3D),
                            letterSpacing: -0.3,
                          ),
                        ),
                        SizedBox(height: 2),
                        Text(
                          'Kiểm tra và điều chỉnh thông tin chính xác theo thẻ thật',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Color(0xFF64748B)),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const Divider(height: 24, color: Color(0xFFF1F5F9)),

              // Số CCCD
              TextFormField(
                controller: _cccdController,
                keyboardType: TextInputType.number,
                maxLength: 12,
                style: inputTextStyle,
                decoration: _inputDecoration(
                  label: 'Số Căn cước công dân (12 chữ số) *',
                  hint: 'Ví dụ: 001202012345',
                  prefixIcon: Icons.fingerprint,
                ),
                validator: (val) {
                  if (val == null || val.trim().length != 12) {
                    return 'Số CCCD bắt buộc phải gồm đúng 12 chữ số';
                  }
                  if (!RegExp(r'^[0-9]+$').hasMatch(val.trim())) {
                    return 'Số CCCD chỉ chứa các chữ số 0-9';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),

              // Họ và tên
              TextFormField(
                controller: _nameController,
                textCapitalization: TextCapitalization.characters,
                style: inputTextStyle,
                decoration: _inputDecoration(
                  label: 'Họ và tên (In hoa có dấu hoặc không dấu) *',
                  hint: 'Ví dụ: NGUYỄN VĂN A',
                  prefixIcon: Icons.person,
                ),
                validator: (val) {
                  if (val == null || val.trim().isEmpty) {
                    return 'Vui lòng nhập họ và tên của bạn';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),

              // Ngày sinh & Giới tính
              Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: TextFormField(
                      controller: _dobController,
                      keyboardType: TextInputType.datetime,
                      style: inputTextStyle,
                      decoration: _inputDecoration(
                        label: 'Ngày sinh (dd/MM/yyyy) *',
                        hint: '26/07/2000',
                        prefixIcon: Icons.calendar_today,
                      ),
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) {
                          return 'Nhập ngày sinh';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: DropdownButtonFormField<String>(
                      initialValue: _gender,
                      style: inputTextStyle,
                      dropdownColor: Colors.white,
                      decoration: InputDecoration(
                        labelText: 'Giới tính',
                        labelStyle: const TextStyle(color: Color(0xFF64748B), fontSize: 13),
                        filled: true,
                        fillColor: const Color(0xFFF8FAFC),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFF0077FF), width: 1.5),
                        ),
                      ),
                      items: const [
                        DropdownMenuItem(value: 'Nam', child: Text('Nam')),
                        DropdownMenuItem(value: 'Nữ', child: Text('Nữ')),
                      ],
                      onChanged: (val) {
                        if (val != null) setState(() => _gender = val);
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Nơi thường trú
              TextFormField(
                controller: _addressController,
                maxLines: 2,
                style: inputTextStyle,
                decoration: _inputDecoration(
                  label: 'Nơi thường trú *',
                  hint: 'Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố',
                  prefixIcon: Icons.home_outlined,
                ),
                validator: (val) {
                  if (val == null || val.trim().isEmpty) {
                    return 'Vui lòng nhập nơi thường trú';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),

              // Ngày cấp
              TextFormField(
                controller: _issueDateController,
                keyboardType: TextInputType.datetime,
                style: inputTextStyle,
                decoration: _inputDecoration(
                  label: 'Ngày cấp (dd/MM/yyyy)',
                  hint: '10/05/2021',
                  prefixIcon: Icons.event_available,
                ),
              ),
              const SizedBox(height: 22),

              // Action Button (Gradient button)
              Container(
                height: 50,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  gradient: const LinearGradient(
                    colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF0077FF).withValues(alpha: 0.35),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: ElevatedButton.icon(
                  onPressed: _submit,
                  icon: const Icon(Icons.check, color: Colors.white, size: 20),
                  label: const Text(
                    'Lưu Thông Tin CCCD Xác Nhận',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 15.5,
                      color: Colors.white,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    shadowColor: Colors.transparent,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
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
