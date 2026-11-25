import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/skill_verification/data/skill_verification_repository.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';

class SkillVerificationState {
  final bool isLoading;
  final bool isSubmitting;
  final String? errorMessage;
  final List<SkillModel> taxonomy;
  final List<WorkerSkillPointModel> skillPoints;
  final List<WorkerSkillItem> mySkills;
  final List<SkillVerificationResult> history;
  final SkillVerificationResult? lastResult;

  const SkillVerificationState({
    this.isLoading = false,
    this.isSubmitting = false,
    this.errorMessage,
    this.taxonomy = const [],
    this.skillPoints = const [],
    this.mySkills = const [],
    this.history = const [],
    this.lastResult,
  });

  SkillVerificationState copyWith({
    bool? isLoading,
    bool? isSubmitting,
    String? errorMessage,
    List<SkillModel>? taxonomy,
    List<WorkerSkillPointModel>? skillPoints,
    List<WorkerSkillItem>? mySkills,
    List<SkillVerificationResult>? history,
    SkillVerificationResult? lastResult,
    bool clearError = false,
    bool clearResult = false,
  }) {
    return SkillVerificationState(
      isLoading: isLoading ?? this.isLoading,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      taxonomy: taxonomy ?? this.taxonomy,
      skillPoints: skillPoints ?? this.skillPoints,
      mySkills: mySkills ?? this.mySkills,
      history: history ?? this.history,
      lastResult: clearResult ? null : (lastResult ?? this.lastResult),
    );
  }
}

final skillVerificationProvider = StateNotifierProvider<SkillVerificationNotifier, SkillVerificationState>((ref) {
  final repository = ref.watch(skillVerificationRepositoryProvider);
  return SkillVerificationNotifier(repository);
});

class SkillVerificationNotifier extends StateNotifier<SkillVerificationState> {
  final SkillVerificationRepository _repository;

  SkillVerificationNotifier(this._repository) : super(const SkillVerificationState()) {
    loadAll();
  }

  Future<void> loadAll() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final results = await Future.wait([
        _repository.getTaxonomy().catchError((_) => <SkillModel>[]),
        _repository.getSkillPoints().catchError((_) => <WorkerSkillPointModel>[]),
        _repository.getHistory().catchError((_) => <SkillVerificationResult>[]),
        _repository.getMySkills().catchError((_) => <WorkerSkillItem>[]),
      ]);

      state = state.copyWith(
        isLoading: false,
        taxonomy: results[0] as List<SkillModel>,
        skillPoints: results[1] as List<WorkerSkillPointModel>,
        history: results[2] as List<SkillVerificationResult>,
        mySkills: results[3] as List<WorkerSkillItem>,
      );
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể tải dữ liệu kỹ năng.'));
    }
  }

  Future<bool> declareSkills(List<Map<String, dynamic>> skills) async {
    state = state.copyWith(isSubmitting: true, clearError: true);
    try {
      final declaredSubset = await _repository.declareSkills(skills);
      final results = await Future.wait([
        _repository.getMySkills().catchError((_) => _mergeSkills(state.mySkills, declaredSubset)),
        _repository.getSkillPoints().catchError((_) => state.skillPoints),
      ]);

      state = state.copyWith(
        isSubmitting: false,
        mySkills: results[0] as List<WorkerSkillItem>,
        skillPoints: results[1] as List<WorkerSkillPointModel>,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể khai báo kỹ năng.'),
      );
      return false;
    }
  }

  List<WorkerSkillItem> _mergeSkills(List<WorkerSkillItem> existing, List<WorkerSkillItem> incoming) {
    final map = <String, WorkerSkillItem>{
      for (final item in existing) item.skillId: item,
    };
    for (final item in incoming) {
      map[item.skillId] = item;
    }
    return map.values.toList();
  }

  Future<bool> submitVerification({
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    state = state.copyWith(isSubmitting: true, clearError: true, clearResult: true);
    try {
      final result = await _repository.submitVerification(
        fileBytes: fileBytes,
        fileName: fileName,
        skillId: skillId,
        notes: notes,
      );
      await _applyVerificationOutcome(result);
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể gửi tài liệu thẩm định.'),
      );
      return false;
    }
  }

  Future<bool> resubmitVerification({
    required String verificationId,
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    state = state.copyWith(isSubmitting: true, clearError: true);
    try {
      final result = await _repository.resubmitVerification(
        verificationId: verificationId,
        fileBytes: fileBytes,
        fileName: fileName,
        skillId: skillId,
        notes: notes,
      );
      await _applyVerificationOutcome(result);
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể gửi lại tài liệu thẩm định.'),
      );
      return false;
    }
  }

  Future<bool> retryVerification(String verificationId) async {
    state = state.copyWith(isSubmitting: true, clearError: true);
    try {
      final result = await _repository.retryVerification(verificationId);
      await _applyVerificationOutcome(result);
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: AppErrorFormatter.format(e, fallback: 'Không thể thử lại xác minh chứng chỉ.'),
      );
      return false;
    }
  }

  Future<void> _applyVerificationOutcome(SkillVerificationResult result) async {
    final points = await _repository.getSkillPoints().catchError((_) => state.skillPoints);
    final history = await _repository.getHistory().catchError((_) {
      final updated = state.history.where((item) => item.id != result.id).toList();
      return [result, ...updated];
    });
    final mySkills = await _repository.getMySkills().catchError((_) => state.mySkills);

    state = state.copyWith(
      isSubmitting: false,
      lastResult: result,
      skillPoints: points,
      history: history,
      mySkills: mySkills,
    );
  }

  void clearLastResult() {
    state = state.copyWith(clearResult: true);
  }
}
