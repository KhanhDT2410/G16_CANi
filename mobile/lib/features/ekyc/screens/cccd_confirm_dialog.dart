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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
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
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.badge_outlined, color: theme.colorScheme.primary),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Xác Nhận Thông Tin Thẻ CCCD',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                        SizedBox(height: 2),
                        Text(
                          'Kiểm tra và điều chỉnh thông tin chính xác theo thẻ thật',
                          style: TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const Divider(height: 24),

              // Số CCCD
              TextFormField(
                controller: _cccdController,
                keyboardType: TextInputType.number,
                maxLength: 12,
                decoration: const InputDecoration(
                  labelText: 'Số Căn cước công dân (12 chữ số) *',
                  hintText: 'Ví dụ: 001202012345',
                  prefixIcon: Icon(Icons.fingerprint),
                  border: OutlineInputBorder(),
                  counterText: '',
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
                decoration: const InputDecoration(
                  labelText: 'Họ và tên (In hoa có dấu hoặc không dấu) *',
                  hintText: 'Ví dụ: PHẠM DUY HƯNG',
                  prefixIcon: Icon(Icons.person),
                  border: OutlineInputBorder(),
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
                      decoration: const InputDecoration(
                        labelText: 'Ngày sinh (dd/MM/yyyy) *',
                        hintText: '26/07/2000',
                        prefixIcon: Icon(Icons.calendar_today),
                        border: OutlineInputBorder(),
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
                      value: _gender,
                      decoration: const InputDecoration(
                        labelText: 'Giới tính',
                        border: OutlineInputBorder(),
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
                decoration: const InputDecoration(
                  labelText: 'Nơi thường trú *',
                  hintText: 'Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố',
                  prefixIcon: Icon(Icons.home_outlined),
                  border: OutlineInputBorder(),
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
                decoration: const InputDecoration(
                  labelText: 'Ngày cấp (dd/MM/yyyy)',
                  hintText: '10/05/2021',
                  prefixIcon: Icon(Icons.event_available),
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),

              // Action Buttons
              FilledButton.icon(
                onPressed: _submit,
                icon: const Icon(Icons.check),
                label: const Text('Lưu Thông Tin CCCD Xác Nhận', style: TextStyle(fontWeight: FontWeight.bold)),
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
