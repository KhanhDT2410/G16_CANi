import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/worker_groups/models/worker_group_models.dart';

class WorkerGroupsState {
  final List<WorkerGroupModel> myGroups;
  final List<WorkerGroupMemberModel> currentGroupMembers;
  final bool isLoading;
  final bool isMemberLoading;
  final String? errorMessage;

  const WorkerGroupsState({
    this.myGroups = const [],
    this.currentGroupMembers = const [],
    this.isLoading = false,
    this.isMemberLoading = false,
    this.errorMessage,
  });

  WorkerGroupsState copyWith({
    List<WorkerGroupModel>? myGroups,
    List<WorkerGroupMemberModel>? currentGroupMembers,
    bool? isLoading,
    bool? isMemberLoading,
    String? errorMessage,
  }) {
    return WorkerGroupsState(
      myGroups: myGroups ?? this.myGroups,
      currentGroupMembers: currentGroupMembers ?? this.currentGroupMembers,
      isLoading: isLoading ?? this.isLoading,
      isMemberLoading: isMemberLoading ?? this.isMemberLoading,
      errorMessage: errorMessage,
    );
  }
}

class WorkerGroupsNotifier extends StateNotifier<WorkerGroupsState> {
  WorkerGroupsNotifier(this._ref) : super(const WorkerGroupsState()) {
    loadMyGroups();
  }

  final Ref _ref;

  Future<void> loadMyGroups() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/worker-groups/my');

      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final listRaw = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as List<dynamic>?
            : body as List<dynamic>?;

        final groups = (listRaw ?? [])
            .map((e) => WorkerGroupModel.fromJson(e as Map<String, dynamic>))
            .toList();

        state = state.copyWith(myGroups: groups, isLoading: false);
        return;
      }
      state = state.copyWith(isLoading: false);
    } catch (e) {
      debugPrint('Lỗi tải danh sách đội: $e');
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể tải danh sách đội: $e',
      );
    }
  }

  Future<bool> createGroup(CreateWorkerGroupModel model) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post(
        '/v1/worker-groups',
        data: model.toJson(),
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadMyGroups();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi tạo đội mới: $e');
      return false;
    }
  }

  Future<void> loadGroupMembers(String groupId) async {
    state = state.copyWith(isMemberLoading: true, currentGroupMembers: []);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/worker-groups/$groupId/members');

      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final listRaw = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as List<dynamic>?
            : body as List<dynamic>?;

        final members = (listRaw ?? [])
            .map((e) =>
                WorkerGroupMemberModel.fromJson(e as Map<String, dynamic>))
            .toList();

        state = state.copyWith(
          currentGroupMembers: members,
          isMemberLoading: false,
        );
        return;
      }
      state = state.copyWith(isMemberLoading: false);
    } catch (e) {
      debugPrint('Lỗi tải thành viên đội: $e');
      state = state.copyWith(isMemberLoading: false);
    }
  }

  Future<bool> inviteMember({
    required String groupId,
    required String workerId,
    String? roleTitle,
  }) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post(
        '/v1/worker-groups/$groupId/members/invite',
        data: {
          'workerId': workerId,
          if (roleTitle != null && roleTitle.isNotEmpty) 'roleTitle': roleTitle,
        },
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadGroupMembers(groupId);
        await loadMyGroups();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi mời thành viên vào nhóm: $e');
      return false;
    }
  }

  Future<bool> removeMember({
    required String groupId,
    required String workerId,
  }) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.delete(
        '/v1/worker-groups/$groupId/members/$workerId',
      );

      if (response.statusCode == 200 || response.statusCode == 204) {
        await loadGroupMembers(groupId);
        await loadMyGroups();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi xóa hoặc rời nhóm: $e');
      return false;
    }
  }
}

final workerGroupsProvider =
    StateNotifierProvider<WorkerGroupsNotifier, WorkerGroupsState>((ref) {
  return WorkerGroupsNotifier(ref);
});
