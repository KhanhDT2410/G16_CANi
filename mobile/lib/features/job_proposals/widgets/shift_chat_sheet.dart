import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/providers/auth_provider.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class ShiftChatSheet extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;

  const ShiftChatSheet({super.key, required this.assignment});

  @override
  ConsumerState<ShiftChatSheet> createState() => _ShiftChatSheetState();
}

class _ShiftChatSheetState extends ConsumerState<ShiftChatSheet> {
  final TextEditingController _inputController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  List<ShiftChatMessageItem> _messages = [];
  bool _isLoading = true;
  bool _isSending = false;
  Timer? _pollTimer;

  static const List<String> _quickTemplates = [
    'Tôi đã có mặt tại điểm tập kết sự kiện',
    'Nhờ BTC mở mã QR Check-in cho tôi vào ca',
    'Tôi đã hoàn tất checklist, nhờ BTC nghiệm thu & thanh toán',
    'Cần hỗ trợ xử lý tình huống tại khu vực trực',
  ];

  @override
  void initState() {
    super.initState();
    _fetchMessages(initial: true);
    _pollTimer = Timer.periodic(const Duration(seconds: 3), (_) => _fetchMessages());
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _inputController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  String get _shiftCode {
    final clean = widget.assignment.shiftId.replaceAll('-', '').toUpperCase();
    final short = clean.length >= 6 ? clean.substring(0, 6) : clean;
    return 'SH-$short';
  }

  Future<void> _fetchMessages({bool initial = false}) async {
    try {
      final list = await ref
          .read(proposalRepositoryProvider)
          .fetchShiftChatMessages(widget.assignment.shiftId);
      if (!mounted) return;
      final hadNew = list.length > _messages.length;
      setState(() {
        _messages = list;
        _isLoading = false;
      });
      if (initial || hadNew) {
        _scrollToBottom();
      }
    } catch (_) {
      if (mounted && initial) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _sendMessage([String? presetText]) async {
    final text = (presetText ?? _inputController.text).trim();
    if (text.isEmpty || _isSending) return;

    if (presetText == null) {
      _inputController.clear();
    }

    setState(() => _isSending = true);
    try {
      final sent = await ref.read(proposalRepositoryProvider).sendShiftChatMessage(
            shiftId: widget.assignment.shiftId,
            messageText: text,
          );
      if (!mounted) return;
      setState(() {
        _messages = [..._messages, sent];
        _isSending = false;
      });
      _scrollToBottom();
    } catch (e) {
      if (!mounted) return;
      setState(() => _isSending = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Không gửi được tin nhắn: ${e.toString().replaceAll("Exception: ", "")}'),
          backgroundColor: const Color(0xFFDC2626),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final currentUserId = ref.watch(authStateProvider).user?.id;
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Container(
      height: MediaQuery.of(context).size.height * 0.82,
      padding: EdgeInsets.only(bottom: bottomInset),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        children: [
          // 1. Header
          Container(
            padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1E293B) : Colors.white,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.05),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Column(
              children: [
                Container(
                  width: 38,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 10),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade400,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF0066FF), Color(0xFF0D9488)],
                        ),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.forum_rounded, color: Colors.white, size: 20),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Flexible(
                                child: Text(
                                  'Chat Ca Làm (BTC & CSKH)',
                                  style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFE0F2FE),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  _shiftCode,
                                  style: const TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.w800,
                                    color: Color(0xFF0369A1),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${widget.assignment.jobType} • ${widget.assignment.eventName}',
                            style: TextStyle(
                              fontSize: 11.5,
                              color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                              fontWeight: FontWeight.w600,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // 2. Quick reply chips
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            color: isDark ? const Color(0xFF1E293B).withValues(alpha: 0.6) : const Color(0xFFF1F5F9),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _quickTemplates.map((tpl) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ActionChip(
                      label: Text(
                        tpl,
                        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: Color(0xFF0369A1)),
                      ),
                      backgroundColor: isDark ? const Color(0xFF0F172A) : Colors.white,
                      side: const BorderSide(color: Color(0xFFBAE6FD)),
                      visualDensity: VisualDensity.compact,
                      onPressed: () => _sendMessage(tpl),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),

          // 3. Message stream
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _messages.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.mark_chat_unread_outlined, size: 48, color: Colors.blueGrey.shade300),
                              const SizedBox(height: 12),
                              Text(
                                'Bắt đầu trao đổi với Ban tổ chức & CSKH',
                                style: TextStyle(
                                  fontWeight: FontWeight.w800,
                                  fontSize: 14.5,
                                  color: isDark ? Colors.white : const Color(0xFF1E293B),
                                ),
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 6),
                              Text(
                                'Mọi tin nhắn trong ca ($_shiftCode) sẽ được đồng bộ trực tiếp tới Ban tổ chức sự kiện và bộ phận điều phối CANi.',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                                  height: 1.4,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ),
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollController,
                        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
                        itemCount: _messages.length,
                        itemBuilder: (context, index) {
                          final m = _messages[index];
                          final isMe = (currentUserId != null && m.senderId == currentUserId) ||
                              (m.senderRole == 'WORKER' && (m.senderId.isEmpty || currentUserId == null));
                          final isEo = m.senderRole == 'EVENT_ORGANIZER';

                          final roleBadgeText = isMe
                              ? 'Bạn'
                              : (isEo ? 'Ban Tổ Chức • ${m.senderName}' : 'CSKH CANi • ${m.senderName}');

                          final bubbleBg = isMe
                              ? const Color(0xFF0066FF)
                              : (isEo
                                  ? (isDark ? const Color(0xFF064E3B) : const Color(0xFFECFDF5))
                                  : (isDark ? const Color(0xFF1E293B) : Colors.white));

                          final textColor = isMe
                              ? Colors.white
                              : (isEo
                                  ? (isDark ? const Color(0xFFA7F3D0) : const Color(0xFF065F46))
                                  : (isDark ? Colors.white : const Color(0xFF0F172A)));

                          final timeStr =
                              '${m.createdAt.hour.toString().padLeft(2, '0')}:${m.createdAt.minute.toString().padLeft(2, '0')}';

                          return Align(
                            alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 10),
                              constraints: BoxConstraints(
                                maxWidth: MediaQuery.of(context).size.width * 0.78,
                              ),
                              child: Column(
                                crossAxisAlignment:
                                    isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                                children: [
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 3, left: 4, right: 4),
                                    child: Text(
                                      roleBadgeText,
                                      style: TextStyle(
                                        fontSize: 10.5,
                                        fontWeight: FontWeight.w700,
                                        color: isEo
                                            ? const Color(0xFF059669)
                                            : const Color(0xFF64748B),
                                      ),
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 10),
                                    decoration: BoxDecoration(
                                      color: bubbleBg,
                                      borderRadius: BorderRadius.only(
                                        topLeft: const Radius.circular(16),
                                        topRight: const Radius.circular(16),
                                        bottomLeft: Radius.circular(isMe ? 16 : 4),
                                        bottomRight: Radius.circular(isMe ? 4 : 16),
                                      ),
                                      border: isMe
                                          ? null
                                          : Border.all(
                                              color: isEo
                                                  ? const Color(0xFF6EE7B7)
                                                  : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
                                            ),
                                    ),
                                    child: Text(
                                      m.messageText,
                                      style: TextStyle(
                                        fontSize: 13.5,
                                        color: textColor,
                                        height: 1.38,
                                      ),
                                    ),
                                  ),
                                  Padding(
                                    padding: const EdgeInsets.only(top: 2, left: 4, right: 4),
                                    child: Text(
                                      timeStr,
                                      style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
          ),

          // 4. Input bar
          Container(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 14),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1E293B) : Colors.white,
              border: Border(
                top: BorderSide(color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
              ),
            ),
            child: SafeArea(
              top: false,
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _inputController,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _sendMessage(),
                      decoration: InputDecoration(
                        hintText: 'Nhập tin nhắn gửi BTC / CSKH...',
                        hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                        filled: true,
                        fillColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(999),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  SizedBox(
                    width: 44,
                    height: 44,
                    child: FilledButton(
                      onPressed: _isSending ? null : () => _sendMessage(),
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFF0066FF),
                        padding: EdgeInsets.zero,
                        shape: const CircleBorder(),
                      ),
                      child: _isSending
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                            )
                          : const Icon(Icons.send_rounded, size: 19, color: Colors.white),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
