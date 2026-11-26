import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/profile/models/blocked_user_model.dart';

class BlockListState {
  final List<BlockedUserModel> blockedUsers;
  final bool isLoading;
  final String? errorMessage;

  const BlockListState({
    this.blockedUsers = const [],
    this.isLoading = false,
    this.errorMessage,
  });

  BlockListState copyWith({
    List<BlockedUserModel>? blockedUsers,
    bool? isLoading,
    String? errorMessage,
  }) {
    return BlockListState(
      blockedUsers: blockedUsers ?? this.blockedUsers,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class BlockListNotifier extends StateNotifier<BlockListState> {
  BlockListNotifier(this._ref) : super(const BlockListState()) {
    loadBlockedList();
  }

  final Ref _ref;

  Future<void> loadBlockedList() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/blocks');
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final listRaw = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as List<dynamic>?
            : body as List<dynamic>?;

        final items = (listRaw ?? [])
            .map((e) => BlockedUserModel.fromJson(e as Map<String, dynamic>))
            .toList();

        state = state.copyWith(blockedUsers: items, isLoading: false);
        return;
      }
      state = state.copyWith(isLoading: false);
    } catch (e) {
      debugPrint('Lỗi tải danh sách chặn: $e');
      state = state.copyWith(
        isLoading: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể tải danh sách chặn.'),
      );
    }
  }

  Future<bool> unblockUser(String blockedUserId) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.delete('/v1/blocks/$blockedUserId');
      if (response.statusCode == 200 || response.statusCode == 204) {
        state = state.copyWith(
          blockedUsers: state.blockedUsers
              .where((u) => u.blockedUserId != blockedUserId && u.id != blockedUserId)
              .toList(),
        );
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi bỏ chặn người dùng: $e');
      return false;
    }
  }
}

final blockListProvider =
    StateNotifierProvider<BlockListNotifier, BlockListState>((ref) {
  return BlockListNotifier(ref);
});
