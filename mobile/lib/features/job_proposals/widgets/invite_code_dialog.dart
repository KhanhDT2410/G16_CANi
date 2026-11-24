import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

/// Dialog nhập mã mời tham gia ca làm việc.
///
/// Quản lý vòng đời [TextEditingController] trong [State.dispose] để tránh
/// dispose controller trước khi animation đóng dialog kết thúc.
class InviteCodeDialog extends StatefulWidget {
  final String title;
  final double titleFontSize;

  const InviteCodeDialog({
    super.key,
    this.title = 'Tham gia ca bằng Mã Mời',
    this.titleFontSize = 16,
  });

  static Future<String?> show(
    BuildContext context, {
    String title = 'Tham gia ca bằng Mã Mời',
    double titleFontSize = 16,
  }) {
    return showDialog<String>(
      context: context,
      builder: (_) => InviteCodeDialog(
        title: title,
        titleFontSize: titleFontSize,
      ),
    );
  }

  @override
  State<InviteCodeDialog> createState() => _InviteCodeDialogState();
}

class _InviteCodeDialogState extends State<InviteCodeDialog> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final code = _controller.text.trim();
    if (code.isNotEmpty) {
      Navigator.pop(context, code);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;

    return AlertDialog(
      title: Text(
        widget.title,
        style: TextStyle(fontWeight: FontWeight.bold, fontSize: widget.titleFontSize),
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Nhập mã mời do Ban tổ chức sự kiện cung cấp (ví dụ: CANI-8XK2PW) để vào ca làm ngay:',
              style: TextStyle(fontSize: 13, color: cani.textBody),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _controller,
              textCapitalization: TextCapitalization.characters,
              onSubmitted: (_) => _submit(),
              decoration: const InputDecoration(
                hintText: 'CANI-XXXXXX',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.confirmation_number_outlined),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _submit,
          style: FilledButton.styleFrom(backgroundColor: cani.brand),
          child: const Text('Tham Gia'),
        ),
      ],
    );
  }
}
