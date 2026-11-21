import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/data/help_center_repository.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';
import 'package:cani/features/help_center/screens/create_ticket_screen.dart';
import 'package:cani/features/help_center/screens/customer_care_chat_screen.dart';
import 'package:cani/features/help_center/screens/my_tickets_screen.dart';
import 'package:cani/features/help_center/widgets/faq_accordion_tile.dart';
import 'package:cani/features/help_center/widgets/faq_category_chips.dart';
import 'package:cani/features/help_center/widgets/help_contact_card.dart';
import 'package:cani/features/help_center/widgets/help_notice_banner.dart';
import 'package:cani/features/help_center/widgets/help_search_bar.dart';
import 'package:cani/features/help_center/widgets/my_tickets_card.dart';

class HelpCenterScreen extends ConsumerWidget {
  const HelpCenterScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final faqsAsync = ref.watch(filteredFaqsProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Trung tâm Trợ giúp',
          style: TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: 17,
            color: Color(0xFF0F172A),
          ),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded, color: Color(0xFF0F172A)),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(faqsListProvider);
          ref.invalidate(myTicketsProvider);
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
          children: [
            // 1. THANH TÌM KIẾM CÂU HỎI
            const HelpSearchBar(),
            const SizedBox(height: 12),

            // 2. BĂNG THÔNG BÁO QUAN TRỌNG
            const HelpNoticeBanner(),
            const SizedBox(height: 14),

            // 3. THẺ YÊU CẦU HỖ TRỢ CỦA TÔI
            MyTicketsCard(
              onViewAll: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const MyTicketsScreen()),
                );
              },
              onCreateTicket: () async {
                final created = await Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const CreateTicketScreen()),
                );
                if (created == true) {
                  ref.invalidate(myTicketsProvider);
                }
              },
              onSelectTicket: (ticket) {
                MyTicketsScreen.showDetailModal(context, ticket.id);
              },
            ),
            const SizedBox(height: 18),

            // 4. TIÊU ĐỀ & BỘ LỌC DANH MỤC CÂU HỎI THƯỜNG GẶP
            const Row(
              children: [
                Icon(Icons.quiz_outlined, size: 18, color: Color(0xFF0D9488)),
                SizedBox(width: 8),
                Text(
                  'Câu hỏi thường gặp',
                  style: TextStyle(
                    fontSize: 14.5,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            const FaqCategoryChips(),
            const SizedBox(height: 12),

            // 5. DANH SÁCH CÂU HỎI ACCORDION
            faqsAsync.when(
              data: (faqs) {
                if (faqs.isEmpty) {
                  return Container(
                    padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFFE2E8F0)),
                    ),
                    child: const Column(
                      children: [
                        Icon(Icons.search_off_rounded, size: 40, color: Color(0xFF94A3B8)),
                        SizedBox(height: 8),
                        Text(
                          'Không tìm thấy câu hỏi phù hợp',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xFF334155)),
                        ),
                        SizedBox(height: 4),
                        Text(
                          'Hãy thử thay đổi từ khóa hoặc chọn danh mục khác.',
                          style: TextStyle(fontSize: 11.5, color: Color(0xFF64748B)),
                        ),
                      ],
                    ),
                  );
                }

                return Column(
                  children: faqs.map((faq) {
                    return FaqAccordionTile(
                      faq: faq,
                      onExpand: () {
                        // Tăng lượt xem trên máy chủ
                        ref.read(helpCenterRepositoryProvider).fetchFaqDetail(faq.id);
                      },
                    );
                  }).toList(),
                );
              },
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 30),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (err, _) => Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF2F2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text('Lỗi tải câu hỏi: $err', style: const TextStyle(color: Color(0xFFB91C1C))),
              ),
            ),
            const SizedBox(height: 18),

            // 6. CHÂN TRANG LIÊN HỆ HỖ TRỢ
            HelpContactCard(
              onOpenLiveChat: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const CustomerCareChatScreen()),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}
