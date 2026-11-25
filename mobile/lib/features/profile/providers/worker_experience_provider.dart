import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/profile/models/worker_experience_model.dart';

class WorkerExperienceState {
  final WorkerExperienceModel? experience;
  final bool isLoading;
  final String? errorMessage;

  const WorkerExperienceState({
    this.experience,
    this.isLoading = false,
    this.errorMessage,
  });

  WorkerExperienceState copyWith({
    WorkerExperienceModel? experience,
    bool? isLoading,
    String? errorMessage,
  }) {
    return WorkerExperienceState(
      experience: experience ?? this.experience,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class WorkerExperienceNotifier extends StateNotifier<WorkerExperienceState> {
  WorkerExperienceNotifier(this._ref) : super(const WorkerExperienceState()) {
    loadExperience();
  }

  final Ref _ref;

  Future<void> loadExperience() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/workers/experience');
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final data = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as Map<String, dynamic>?
            : body as Map<String, dynamic>?;

        if (data != null) {
          final exp = WorkerExperienceModel.fromJson(data);
          state = state.copyWith(experience: exp, isLoading: false);
          return;
        }
      }
      state = state.copyWith(isLoading: false);
    } catch (e) {
      debugPrint('Lỗi tải thông tin kinh nghiệm worker: $e');
      state = state.copyWith(
        isLoading: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể tải thông tin kinh nghiệm.'),
      );
    }
  }

  Future<bool> updateExperience({required int yearsExperience, required String bio}) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.put(
        '/v1/workers/experience',
        data: {
          'yearsExperience': yearsExperience,
          'bio': bio,
        },
      );
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final data = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as Map<String, dynamic>?
            : body as Map<String, dynamic>?;

        if (data != null) {
          final exp = WorkerExperienceModel.fromJson(data);
          state = state.copyWith(experience: exp, isLoading: false);
          return true;
        }
      }
      state = state.copyWith(isLoading: false);
      return true;
    } catch (e) {
      debugPrint('Lỗi cập nhật kinh nghiệm worker: $e');
      state = state.copyWith(
        isLoading: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể cập nhật hồ sơ kinh nghiệm.'),
      );
      return false;
    }
  }
}

final workerExperienceProvider =
    StateNotifierProvider<WorkerExperienceNotifier, WorkerExperienceState>((ref) {
  return WorkerExperienceNotifier(ref);
});
