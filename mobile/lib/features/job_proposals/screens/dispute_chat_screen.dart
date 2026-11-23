import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/app/cani_tokens.dart';
import '../data/proposal_repository.dart';
import '../models/dispute_models.dart';
import '../widgets/dispute_chat_bubble.dart';
import '../widgets/dispute_chat_input_bar.dart';

class DisputeChatScreen extends ConsumerStatefulWidget {
  final String disputeId;
  final String disputeTitle;

  const DisputeChatScreen({
    super.key,
    required this.disputeId,
    required this.disputeTitle,
  });

  @override
  ConsumerState<DisputeChatScreen> createState() => _DisputeChatScreenState();
}

class _DisputeChatScreenState extends ConsumerState<DisputeChatScreen> {
  final ScrollController _scrollCtrl = ScrollController();
  Timer? _timer;
  List<DisputeChatMessageItem> _messages = [];
  String _disputeStatus = 'OPEN';
  bool _isLoading = true;
  bool _isSending = false;

  @override
  void initState() {
    super.initState();
    _fetchChat(initial: true);
    _timer = Timer.periodic(const Duration(milliseconds: 2500), (_) => _fetchChat(initial: false));
  }

  @override
  void dispose() {
    _timer?.cancel();
    _scrollCtrl.dispose();
    super.dispose();
  }

  Future<void> _fetchChat({bool initial = false}) async {
    try {
      final repo = ref.read(proposalRepositoryProvider);
      final data = await repo.fetchDisputeChat(widget.disputeId);
      if (!mounted) return;

      final oldLen = _messages.length;
      setState(() {
        _messages = data.messages;
        _disputeStatus = data.disputeStatus;
        if (initial) _isLoading = false;
      });

      if (initial) {
        repo.markDisputeChatRead(widget.disputeId);
        _scrollToBottom();
      } else if (data.messages.length > oldLen) {
        _scrollToBottom();
      }
    } catch (_) {
      if (initial && mounted) setState(() => _isLoading = false);
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent + 60,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _handleSend(String text, String? attachmentUrl) async {
    final tempMsg = DisputeChatMessageItem(
      id: 'temp-${DateTime.now().millisecondsSinceEpoch}',
      conversationId: 'temp',
      senderId: 'me',
      senderName: 'Tôi',
      senderRole: 'WORKER',
      messageText: text,
      attachmentUrl: attachmentUrl,
      isMe: true,
      createdAt: DateTime.now(),
    );

    setState(() {
      _messages = [..._messages, tempMsg];
      _isSending = true;
    });
    _scrollToBottom();

    try {
      final repo = ref.read(proposalRepositoryProvider);
      final sent = await repo.sendDisputeChatMessage(
        widget.disputeId,
        text,
        attachmentUrl: attachmentUrl,
      );
      if (mounted) {
        setState(() {
          _messages = _messages.map((m) => m.id == tempMsg.id ? sent : m).toList();
          _isSending = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSending = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể gửi tin nhắn: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isClosed = _disputeStatus == 'FINAL_RESOLVED';

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        elevation: 0.5,
        backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.disputeTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
            ),
            Row(
              children: [
                Container(
                  width: 7,
                  height: 7,
                  decoration: BoxDecoration(
                    color: isClosed ? const Color(0xFF16A34A) : const Color(0xFF0284C7),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 5),
                Text(
                  isClosed ? 'Đã phán quyết cuối cùng' : 'Trực tiếp với Trọng tài CSKH',
                  style: TextStyle(fontSize: 11, color: cani.textMuted, fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: isDark ? const Color(0xFF1E293B) : const Color(0xFFEFF6FF),
            child: Row(
              children: [
                Icon(Icons.shield_outlined, size: 16, color: cani.brand),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Kênh trao đổi độc lập với CSKH để làm rõ chứng cứ và bảo vệ quyền lợi của bạn.',
                    style: TextStyle(fontSize: 11.5, color: cani.brand, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _messages.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.forum_outlined, size: 48, color: cani.textMuted),
                            const SizedBox(height: 10),
                            Text(
                              'Chưa có trao đổi nào',
                              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: cani.textMain),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Hãy gửi ý kiến giải trình hoặc chứng cứ bổ sung để CSKH hỗ trợ đối soát.',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 12, color: cani.textMuted),
                            ),
                          ],
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollCtrl,
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        itemCount: _messages.length,
                        itemBuilder: (_, idx) => DisputeChatBubble(message: _messages[idx]),
                      ),
          ),
          DisputeChatInputBar(
            onSend: _handleSend,
            isSending: _isSending,
            isClosed: isClosed,
          ),
        ],
      ),
    );
  }
}
