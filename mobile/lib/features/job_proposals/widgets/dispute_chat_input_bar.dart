import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/app/cani_tokens.dart';
import 'package:cani/shared/widgets/cani_snackbar.dart';
import '../../ekyc/screens/card_scanner_screen.dart';
import '../data/proposal_repository.dart';

class DisputeChatInputBar extends ConsumerStatefulWidget {
  final Future<void> Function(String text, String? attachmentUrl) onSend;
  final bool isSending;
  final bool isClosed;

  const DisputeChatInputBar({
    super.key,
    required this.onSend,
    this.isSending = false,
    this.isClosed = false,
  });

  @override
  ConsumerState<DisputeChatInputBar> createState() => _DisputeChatInputBarState();
}

class _DisputeChatInputBarState extends ConsumerState<DisputeChatInputBar> {
  final TextEditingController _ctrl = TextEditingController();
  Uint8List? _previewBytes;
  String? _uploadedAttachmentUrl;
  bool _isUploadingPhoto = false;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _pickPhoto() async {
    final result = await Navigator.push<CardScanResult>(
      context,
      MaterialPageRoute(
        builder: (_) => const CardScannerScreen(scanType: ScanType.workProof),
      ),
    );
    if (result == null || !mounted) return;

    setState(() => _isUploadingPhoto = true);
    try {
      final repo = ref.read(proposalRepositoryProvider);
      final url = await repo.uploadProofBytes(result.bytes, result.fileName);
      if (mounted) {
        setState(() {
          _previewBytes = result.bytes;
          _uploadedAttachmentUrl = url;
          _isUploadingPhoto = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isUploadingPhoto = false);
        CaniSnackBar.show(
          context,
          message: 'Chưa gửi được ảnh. Vui lòng thử lại.',
          tone: CaniNoticeTone.error,
        );
      }
    }
  }

  Future<void> _handleSend() async {
    final text = _ctrl.text.trim();
    if (text.isEmpty && _uploadedAttachmentUrl == null) return;
    final att = _uploadedAttachmentUrl;
    _ctrl.clear();
    setState(() {
      _previewBytes = null;
      _uploadedAttachmentUrl = null;
    });
    await widget.onSend(text.isNotEmpty ? text : 'Gửi ảnh minh chứng', att);
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (widget.isClosed) {
      return Container(
        padding: const EdgeInsets.all(14),
        color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.lock_outline_rounded, size: 16, color: cani.textMuted),
            const SizedBox(width: 8),
            Flexible(
              child: Text(
                'Vụ việc đã đóng, kênh trao đổi đã được khép lại.',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: cani.textMuted),
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: EdgeInsets.only(
        left: 12,
        right: 12,
        top: 8,
        bottom: MediaQuery.of(context).viewInsets.bottom > 0 ? 8 : 16,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        border: Border(top: BorderSide(color: cani.border)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_previewBytes != null) ...[
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.memory(_previewBytes!, width: 44, height: 44, fit: BoxFit.cover),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Đã đính kèm 1 ảnh minh chứng',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: cani.brand),
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close_rounded, size: 18, color: cani.danger),
                    onPressed: () => setState(() {
                      _previewBytes = null;
                      _uploadedAttachmentUrl = null;
                    }),
                  ),
                ],
              ),
            ),
          ],
          Row(
            children: [
              IconButton(
                tooltip: 'Chụp ảnh minh chứng',
                icon: _isUploadingPhoto
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : Icon(Icons.camera_alt_outlined, size: 22, color: cani.brand),
                onPressed: _isUploadingPhoto || widget.isSending ? null : _pickPhoto,
              ),
              Expanded(
                child: TextField(
                  controller: _ctrl,
                  maxLines: 4,
                  minLines: 1,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => _handleSend(),
                  decoration: InputDecoration(
                    hintText: 'Nhập nội dung giải trình với CSKH...',
                    hintStyle: TextStyle(fontSize: 13, color: cani.textMuted),
                    filled: true,
                    fillColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(20),
                      borderSide: BorderSide(color: cani.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(20),
                      borderSide: BorderSide(color: cani.border),
                    ),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              IconButton.filled(
                icon: widget.isSending
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.send_rounded, size: 18),
                style: IconButton.styleFrom(
                  backgroundColor: cani.brand,
                  foregroundColor: Colors.white,
                ),
                onPressed: widget.isSending ? null : _handleSend,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
