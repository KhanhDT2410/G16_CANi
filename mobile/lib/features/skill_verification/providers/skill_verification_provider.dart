import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  Future<bool> declareSkills(List<Map<String, dynamic>> skills) async {
    state = state.copyWith(isSubmitting: true, clearError: true);
    try {
      final updatedSkills = await _repository.declareSkills(skills);
      final points = await _repository.getSkillPoints().catchError((_) => state.skillPoints);

      state = state.copyWith(
        isSubmitting: false,
        mySkills: updatedSkills,
        skillPoints: points,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
      return false;
    }
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

      // Re-fetch points, history, and my-skills to update overall dashboard
      final points = await _repository.getSkillPoints().catchError((_) => state.skillPoints);
      final history = await _repository.getHistory().catchError((_) => [result, ...state.history]);
      final mySkills = await _repository.getMySkills().catchError((_) => state.mySkills);

      state = state.copyWith(
        isSubmitting: false,
        lastResult: result,
        skillPoints: points,
        history: history,
        mySkills: mySkills,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isSubmitting: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
      return false;
    }
  }

  void clearLastResult() {
    state = state.copyWith(clearResult: true);
  }
}
