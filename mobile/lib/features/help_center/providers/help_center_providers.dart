import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/data/help_center_repository.dart';
import 'package:cani/features/help_center/models/help_center_models.dart';

/// Provider danh mục câu hỏi thường gặp đang chọn (null = Tất cả)
final selectedFaqCategoryProvider = StateProvider<String?>((ref) => null);

/// Provider từ khóa tìm kiếm FAQ
final faqSearchQueryProvider = StateProvider<String>((ref) => '');

/// Provider tải toàn bộ FAQ từ máy chủ
final faqsListProvider = FutureProvider<List<FaqItemModel>>((ref) async {
  final repo = ref.watch(helpCenterRepositoryProvider);
  return repo.fetchFaqs();
});

/// Provider danh sách FAQ sau khi áp dụng bộ lọc danh mục và từ khóa tìm kiếm
final filteredFaqsProvider = Provider<AsyncValue<List<FaqItemModel>>>((ref) {
  final faqsAsync = ref.watch(faqsListProvider);
  final selectedCategory = ref.watch(selectedFaqCategoryProvider);
  final query = ref.watch(faqSearchQueryProvider).trim().toLowerCase();

  return faqsAsync.whenData((list) {
    return list.where((item) {
      final matchesCategory = selectedCategory == null ||
          item.category.toUpperCase() == selectedCategory.toUpperCase();

      if (!matchesCategory) return false;

      if (query.isEmpty) return true;

      final question = item.question.toLowerCase();
      final summary = item.summary?.toLowerCase() ?? '';
      final answer = item.answer.toLowerCase();

      return question.contains(query) ||
          summary.contains(query) ||
          answer.contains(query);
    }).toList();
  });
});

/// Provider danh sách yêu cầu hỗ trợ của tôi
final myTicketsProvider = FutureProvider<List<SupportTicketModel>>((ref) async {
  final repo = ref.watch(helpCenterRepositoryProvider);
  return repo.fetchMyTickets();
});

/// Đếm số yêu cầu hỗ trợ đang chờ xử lý (OPEN hoặc IN_PROGRESS)
final pendingTicketsCountProvider = Provider<int>((ref) {
  final ticketsAsync = ref.watch(myTicketsProvider);
  return ticketsAsync.maybeWhen(
    data: (tickets) => tickets
        .where((t) => t.status == 'OPEN' || t.status == 'IN_PROGRESS')
        .length,
    orElse: () => 0,
  );
});

/// Provider chi tiết một yêu cầu hỗ trợ
final ticketDetailProvider =
    FutureProvider.family<SupportTicketModel, String>((ref, id) async {
  final repo = ref.watch(helpCenterRepositoryProvider);
  return repo.fetchTicketDetail(id);
});

/// Provider tải toàn bộ tin nhắn trò chuyện trực tuyến với CSKH
final supportChatMessagesProvider =
    FutureProvider<List<SupportChatMessageModel>>((ref) async {
  final repo = ref.watch(helpCenterRepositoryProvider);
  return repo.fetchMySupportChat();
});
