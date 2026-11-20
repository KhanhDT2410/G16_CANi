import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/data/help_center_repository.dart';
import 'package:cani/features/help_center/models/help_center_models.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';
import 'package:cani/features/help_center/screens/create_ticket_screen.dart';

class MyTicketsScreen extends ConsumerWidget {
  const MyTicketsScreen({super.key});

  static void showDetailModal(BuildContext context, String ticketId) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _TicketDetailModal(ticketId: ticketId),
    );
  }

  void _showTicketDetail(BuildContext context, WidgetRef ref, SupportTicketModel ticket) {
    showDetailModal(context, ticket.id);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ticketsAsync = ref.watch(myTicketsProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Yêu Cầu Hỗ Trợ Của Tôi',
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
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final res = await Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const CreateTicketScreen()),
          );
          if (res == true) {
            ref.invalidate(myTicketsProvider);
          }
        },
        backgroundColor: const Color(0xFF0066FF),
        icon: const Icon(Icons.add_rounded, color: Colors.white),
        label: const Text(
          'Yêu cầu mới',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
        ),
      ),
      body: ticketsAsync.when(
        data: (tickets) {
          if (tickets.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.inbox_outlined, size: 56, color: Color(0xFF94A3B8)),
                  const SizedBox(height: 12),
                  const Text(
                    'Bạn chưa gửi yêu cầu hỗ trợ nào',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: Color(0xFF334155)),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    'Các vấn đề về thù lao hoặc sự cố ca làm sẽ xuất hiện tại đây.',
                    style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton.icon(
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const CreateTicketScreen()),
                    ),
                    icon: const Icon(Icons.add_rounded, size: 16),
                    label: const Text('Gửi yêu cầu đầu tiên'),
                  ),
                ],
              ),
            );
          }

          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(myTicketsProvider),
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 80),
              itemCount: tickets.length,
              separatorBuilder: (context, index) => const SizedBox(height: 10),
              itemBuilder: (ctx, index) {
                final t = tickets[index];
                return InkWell(
                  onTap: () => _showTicketDetail(context, ref, t),
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFFE2E8F0)),
                      boxShadow: const [
                        BoxShadow(color: Color(0x04000000), blurRadius: 4, offset: Offset(0, 1)),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Text(
                              t.referenceNo,
                              style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Color(0xFF64748B)),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: const Color(0xFFF1F5F9),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                t.categoryLabel,
                                style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: Color(0xFF475569)),
                              ),
                            ),
                            const Spacer(),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: t.statusBgColor,
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                t.statusLabel,
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: t.statusColor,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          t.subject,
                          style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: Color(0xFF0F172A)),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          t.description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 12, color: Color(0xFF475569), height: 1.3),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Lỗi: $e')),
      ),
    );
  }
}

class _TicketDetailModal extends ConsumerStatefulWidget {
  final String ticketId;

  const _TicketDetailModal({required this.ticketId});

  @override
  ConsumerState<_TicketDetailModal> createState() => _TicketDetailModalState();
}

class _TicketDetailModalState extends ConsumerState<_TicketDetailModal> {
  final _replyController = TextEditingController();
  bool _isReplying = false;

  @override
  void dispose() {
    _replyController.dispose();
    super.dispose();
  }

  Future<void> _sendReply() async {
    final text = _replyController.text.trim();
    if (text.isEmpty) return;

    setState(() => _isReplying = true);
    final repo = ref.read(helpCenterRepositoryProvider);

    try {
      await repo.replyTicket(ticketId: widget.ticketId, message: text);
      _replyController.clear();
      ref.invalidate(ticketDetailProvider(widget.ticketId));
      ref.invalidate(myTicketsProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
      }
    } finally {
      if (mounted) setState(() => _isReplying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detailAsync = ref.watch(ticketDetailProvider(widget.ticketId));

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.75,
        child: detailAsync.when(
          data: (ticket) {
            return Column(
              children: [
                // Header
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              ticket.referenceNo,
                              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFF64748B)),
                            ),
                            Text(
                              ticket.subject,
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: Color(0xFF0F172A)),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () => Navigator.pop(context),
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1),

                // Danh sách trao đổi
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      // Tin nhắn gốc của ticket
                      _buildMessageBubble(
                        author: 'Tôi (Người tạo yêu cầu)',
                        role: ticket.categoryLabel,
                        text: ticket.description,
                        isStaff: false,
                      ),
                      const SizedBox(height: 12),

                      // Tin nhắn phản hồi tiếp theo
                      ...ticket.messages.map((m) {
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _buildMessageBubble(
                            author: m.authorName,
                            role: m.isStaffReply ? 'CSKH CANi' : 'Tôi',
                            text: m.message,
                            isStaff: m.isStaffReply,
                          ),
                        );
                      }),
                    ],
                  ),
                ),

                // Thanh gửi phản hồi
                if (ticket.status != 'CLOSED')
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      border: Border(top: BorderSide(color: Color(0xFFE2E8F0))),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _replyController,
                            decoration: InputDecoration(
                              hintText: 'Nhập phản hồi trao đổi...',
                              hintStyle: const TextStyle(fontSize: 12.5),
                              isDense: true,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(10),
                                borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton.filled(
                          onPressed: _isReplying ? null : _sendReply,
                          icon: _isReplying
                              ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                              : const Icon(Icons.send_rounded, size: 16),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(child: Text('Lỗi: $e')),
        ),
      ),
    );
  }

  Widget _buildMessageBubble({
    required String author,
    required String role,
    required String text,
    required bool isStaff,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isStaff ? const Color(0xFFEFF6FF) : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: isStaff ? const Color(0xFFBFDBFE) : const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                author,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: isStaff ? const Color(0xFF0066FF) : const Color(0xFF334155),
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  color: isStaff ? const Color(0xFFDBEAFE) : const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  role,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: isStaff ? const Color(0xFF1E40AF) : const Color(0xFF64748B),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            text,
            style: const TextStyle(fontSize: 12.5, color: Color(0xFF0F172A), height: 1.4),
          ),
        ],
      ),
    );
  }
}
