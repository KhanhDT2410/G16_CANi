import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/worker_groups/models/group_proposal_models.dart';
import 'package:cani/features/worker_groups/models/worker_group_models.dart';

class WorkerGroupsState {
  final List<WorkerGroupModel> myGroups;
  final List<WorkerGroupModel> pendingInvites;
  final List<WorkerGroupMemberModel> currentGroupMembers;
  final List<GroupProposalModel> groupProposals;
  final bool isLoading;
  final bool isMemberLoading;
  final bool isProposalLoading;
  final String? errorMessage;

  const WorkerGroupsState({
    this.myGroups = const [],
    this.pendingInvites = const [],
    this.currentGroupMembers = const [],
    this.groupProposals = const [],
    this.isLoading = false,
    this.isMemberLoading = false,
    this.isProposalLoading = false,
    this.errorMessage,
  });

  WorkerGroupsState copyWith({
    List<WorkerGroupModel>? myGroups,
    List<WorkerGroupModel>? pendingInvites,
    List<WorkerGroupMemberModel>? currentGroupMembers,
    List<GroupProposalModel>? groupProposals,
    bool? isLoading,
    bool? isMemberLoading,
    bool? isProposalLoading,
    String? errorMessage,
  }) {
    return WorkerGroupsState(
      myGroups: myGroups ?? this.myGroups,
      pendingInvites: pendingInvites ?? this.pendingInvites,
      currentGroupMembers: currentGroupMembers ?? this.currentGroupMembers,
      groupProposals: groupProposals ?? this.groupProposals,
      isLoading: isLoading ?? this.isLoading,
      isMemberLoading: isMemberLoading ?? this.isMemberLoading,
      isProposalLoading: isProposalLoading ?? this.isProposalLoading,
      errorMessage: errorMessage,
    );
  }
}

class WorkerGroupsNotifier extends StateNotifier<WorkerGroupsState> {
  WorkerGroupsNotifier(this._ref) : super(const WorkerGroupsState()) {
    loadMyGroups();
    loadMyPendingInvites();
  }

  final Ref _ref;

  Future<void> loadMyPendingInvites() async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/worker-groups/my/invites');

      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final listRaw = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as List<dynamic>?
            : body as List<dynamic>?;

        final invites = (listRaw ?? [])
            .map((e) => WorkerGroupModel.fromJson(e as Map<String, dynamic>))
            .toList();

        state = state.copyWith(pendingInvites: invites);
      }
    } catch (e) {
      debugPrint('Lỗi tải danh sách lời mời vào đội: $e');
    }
  }

  Future<bool> acceptInvite(String groupId) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post('/v1/worker-groups/$groupId/join');

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadMyGroups();
        await loadMyPendingInvites();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi chấp nhận lời mời vào nhóm: $e');
      return false;
    }
  }

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

  /// Task 3: Danh sách đề xuất ca gửi đến đội (chỉ Leader/Admin, API trả 403 với member thường).
  Future<void> loadGroupProposals(String groupId) async {
    state = state.copyWith(isProposalLoading: true);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio
          .get('/v1/worker-groups/$groupId/group-proposals');

      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final listRaw =
            body is Map<String, dynamic> && body.containsKey('data')
                ? body['data'] as List<dynamic>?
                : body as List<dynamic>?;

        final proposals = (listRaw ?? [])
            .whereType<Map<String, dynamic>>()
            .map((e) => GroupProposalModel.fromJson(e))
            .toList();

        state =
            state.copyWith(groupProposals: proposals, isProposalLoading: false);
        return;
      }
      state = state.copyWith(
        groupProposals: const [],
        isProposalLoading: false,
      );
    } catch (e) {
      // Không phải leader (403) hoặc lỗi mạng: để trống, không phá UI.
      debugPrint('Lỗi tải đề xuất ca cho đội: $e');
      state = state.copyWith(
        groupProposals: const [],
        isProposalLoading: false,
      );
    }
  }

  /// Re-check điều kiện member (kết quả tính mới) trước khi leader accept.
  Future<void> loadEligibleMembers(String groupId, String proposalId) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get(
        '/v1/worker-groups/$groupId/group-proposals/eligible',
        queryParameters: {'proposalId': proposalId},
      );

      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final dto = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as Map<String, dynamic>?
            : body as Map<String, dynamic>?;

        if (dto != null) {
          final fresh = GroupProposalModel.fromJson(dto);
          state = state.copyWith(
            groupProposals: state.groupProposals
                .map((p) => p.proposalId == proposalId ? fresh : p)
                .toList(),
          );
        }
      }
    } catch (e) {
      debugPrint('Lỗi tải thành viên đủ điều kiện: $e');
    }
  }

  /// Accept trả 409 khi slot vừa bị fill hết / 400 khi member không đủ điều kiện:
  /// caller hiển thị SnackBar lỗi chung rồi reload danh sách.
  Future<bool> acceptGroupProposal(
    String groupId,
    String proposalId,
    List<String> memberWorkerIds,
  ) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post(
        '/v1/worker-groups/$groupId/group-proposals/$proposalId/accept',
        data: {'memberWorkerIds': memberWorkerIds},
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadGroupProposals(groupId);
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi xác nhận đề xuất ca cho đội: $e');
      return false;
    }
  }

  Future<bool> rejectGroupProposal(
    String groupId,
    String proposalId,
    String? reason,
  ) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post(
        '/v1/worker-groups/$groupId/group-proposals/$proposalId/reject',
        data: {
          if (reason != null && reason.isNotEmpty) 'reason': reason,
        },
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadGroupProposals(groupId);
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi từ chối đề xuất ca cho đội: $e');
      return false;
    }
  }

  /// Task 5: Chuyển giao đội trưởng sang member ACTIVE khác (PENDING proposals bị hết hạn).
  Future<bool> transferLeader(
    String groupId,
    String newLeaderWorkerId,
  ) async {
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.post(
        '/v1/worker-groups/$groupId/transfer-leader',
        data: {'newLeaderWorkerId': newLeaderWorkerId},
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        await loadMyGroups();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Lỗi chuyển giao đội trưởng: $e');
      return false;
    }
  }
}

final workerGroupsProvider =
    StateNotifierProvider<WorkerGroupsNotifier, WorkerGroupsState>((ref) {
  return WorkerGroupsNotifier(ref);
});
