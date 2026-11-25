import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/help_center/data/help_center_repository.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';
import 'package:cani/features/help_center/widgets/support_chat_bubble.dart';
import 'package:cani/features/help_center/widgets/support_chat_input_bar.dart';
import 'package:cani/features/help_center/widgets/support_topic_chips.dart';

class CustomerCareChatScreen extends ConsumerStatefulWidget {
  final String? initialShiftId;
  final String? initialShiftName;

  const CustomerCareChatScreen({
    super.key,
    this.initialShiftId,
    this.initialShiftName,
  });

  @override
  ConsumerState<CustomerCareChatScreen> createState() =>
      _CustomerCareChatScreenState();
}

class _CustomerCareChatScreenState
    extends ConsumerState<CustomerCareChatScreen> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  Timer? _pollingTimer;
  bool _isSending = false;

  @override
  void initState() {
    super.initState();
    _pollingTimer = Timer.periodic(const Duration(seconds: 4), (_) {
      if (!mounted) return;
      final state = ref.read(supportChatMessagesProvider);
      if (state.isLoading || state.hasError) return;
      ref.invalidate(supportChatMessagesProvider);
    });
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
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
    final text = (presetText ?? _textController.text).trim();
    if (text.isEmpty || _isSending) return;

    setState(() => _isSending = true);
    if (presetText == null) _textController.clear();

    try {
      await ref
          .read(helpCenterRepositoryProvider)
          .sendSupportChatMessage(
            messageText: text,
            shiftId: widget.initialShiftId,
          );
      ref.invalidate(supportChatMessagesProvider);
      _scrollToBottom();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppErrorFormatter.format(e, fallback: 'Không thể gửi tin nhắn.'))),
        );
      }
    } finally {
      if (mounted) setState(() => _isSending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final messagesAsync = ref.watch(supportChatMessagesProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        titleSpacing: 0,
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded, color: Color(0xFF0F172A)),
          onPressed: () => Navigator.pop(context),
        ),
        title: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: const BoxDecoration(
                color: Color(0xFFDCFCE7),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.headset_mic_rounded,
                color: Color(0xFF16A34A),
                size: 20,
              ),
            ),
            const SizedBox(width: 10),
            const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Hỗ Trợ Trực Tuyến CANi',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
                Text(
                  'Đang trực tuyến - Sẵn sàng giải đáp',
                  style: TextStyle(
                    fontSize: 11,
                    color: Color(0xFF16A34A),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          SupportTopicChips(onSelectTopic: (topic) => _sendMessage(topic)),
          Expanded(
            child: messagesAsync.when(
              data: (messages) {
                if (messages.isEmpty) {
                  return const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.forum_outlined,
                            size: 48,
                            color: Color(0xFF94A3B8),
                          ),
                          SizedBox(height: 12),
                          Text(
                            'Xin chào bạn!',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF334155),
                            ),
                          ),
                          SizedBox(height: 6),
                          Text(
                            'Đội ngũ CSKH CANi luôn sẵn sàng hỗ trợ bạn.',
                            style: TextStyle(
                              fontSize: 12,
                              color: Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                _scrollToBottom();
                return ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  itemCount: messages.length,
                  itemBuilder: (context, index) =>
                      SupportChatBubble(message: messages[index]),
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (err, _) {
                final friendlyMessage = err
                    .toString()
                    .replaceFirst(RegExp(r'^Exception:\s*'), '')
                    .trim();
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.error_outline_rounded,
                          size: 44,
                          color: Color(0xFFDC2626),
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'Không thể tải lịch sử trò chuyện',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          friendlyMessage.isNotEmpty
                              ? friendlyMessage
                              : 'Đã xảy ra lỗi khi kết nối với CSKH.',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Color(0xFF64748B),
                          ),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton.icon(
                          onPressed: () =>
                              ref.invalidate(supportChatMessagesProvider),
                          icon: const Icon(Icons.refresh_rounded, size: 18),
                          label: const Text('Thử lại'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF16A34A),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 20,
                              vertical: 10,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          SupportChatInputBar(
            controller: _textController,
            isSending: _isSending,
            onSend: () => _sendMessage(),
          ),
        ],
      ),
    );
  }
}
