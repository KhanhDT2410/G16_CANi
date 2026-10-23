import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import '../data/ekyc_repository.dart';
import '../models/ekyc_models.dart';

final ekycRepositoryProvider = Provider<EkycRepository>((ref) {
  return EkycRepository();
});

class EkycState {
  final bool isLoading;
  final String? errorMessage;
  final EkycVerificationItem? result;

  final Uint8List? frontBytes;
  final String? frontFileName;
  final Uint8List? backBytes;
  final String? backFileName;
  final Uint8List? selfieBytes;
  final String? selfieFileName;

  // Thông tin CCCD đã quét mã QR hoặc người dùng xác nhận
  final String? confirmedCccd;
  final String? confirmedFullName;
  final String? confirmedDob;
  final String? confirmedGender;
  final String? confirmedResidenceAddress;
  final String? confirmedIssueDate;

  const EkycState({
    this.isLoading = false,
    this.errorMessage,
    this.result,
    this.frontBytes,
    this.frontFileName,
    this.backBytes,
    this.backFileName,
    this.selfieBytes,
    this.selfieFileName,
    this.confirmedCccd,
    this.confirmedFullName,
    this.confirmedDob,
    this.confirmedGender,
    this.confirmedResidenceAddress,
    this.confirmedIssueDate,
  });

  bool get isReadyToSubmit =>
      frontBytes != null && backBytes != null && selfieBytes != null && !isLoading;

  bool get hasConfirmedInfo =>
      confirmedCccd != null && confirmedCccd!.isNotEmpty;

  EkycState copyWith({
    bool? isLoading,
    String? errorMessage,
    EkycVerificationItem? result,
    Uint8List? frontBytes,
    String? frontFileName,
    Uint8List? backBytes,
    String? backFileName,
    Uint8List? selfieBytes,
    String? selfieFileName,
    String? confirmedCccd,
    String? confirmedFullName,
    String? confirmedDob,
    String? confirmedGender,
    String? confirmedResidenceAddress,
    String? confirmedIssueDate,
    bool clearError = false,
  }) {
    return EkycState(
      isLoading: isLoading ?? this.isLoading,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      result: result ?? this.result,
      frontBytes: frontBytes ?? this.frontBytes,
      frontFileName: frontFileName ?? this.frontFileName,
      backBytes: backBytes ?? this.backBytes,
      backFileName: backFileName ?? this.backFileName,
      selfieBytes: selfieBytes ?? this.selfieBytes,
      selfieFileName: selfieFileName ?? this.selfieFileName,
      confirmedCccd: confirmedCccd ?? this.confirmedCccd,
      confirmedFullName: confirmedFullName ?? this.confirmedFullName,
      confirmedDob: confirmedDob ?? this.confirmedDob,
      confirmedGender: confirmedGender ?? this.confirmedGender,
      confirmedResidenceAddress: confirmedResidenceAddress ?? this.confirmedResidenceAddress,
      confirmedIssueDate: confirmedIssueDate ?? this.confirmedIssueDate,
    );
  }
}

class EkycNotifier extends StateNotifier<EkycState> {
  final EkycRepository _repository;
  final Ref _ref;

  EkycNotifier(this._repository, this._ref) : super(const EkycState()) {
    checkStatus();
  }

  Future<void> checkStatus() async {
    final status = await _repository.getMyEkycStatus();
    if (status != null) {
      state = state.copyWith(result: status);
    }
  }

  void setFrontImage(Uint8List bytes, String fileName) {
    state = state.copyWith(frontBytes: bytes, frontFileName: fileName, clearError: true);
  }

  void setBackImage(Uint8List bytes, String fileName) {
    state = state.copyWith(backBytes: bytes, backFileName: fileName, clearError: true);
  }

  void setSelfieImage(Uint8List bytes, String fileName) {
    state = state.copyWith(selfieBytes: bytes, selfieFileName: fileName, clearError: true);
  }

  void setConfirmedCccdInfo({
    required String cccdNumber,
    required String fullName,
    required String dob,
    required String gender,
    required String residenceAddress,
    String? issueDate,
  }) {
    state = state.copyWith(
      confirmedCccd: cccdNumber,
      confirmedFullName: fullName,
      confirmedDob: dob,
      confirmedGender: gender,
      confirmedResidenceAddress: residenceAddress,
      confirmedIssueDate: issueDate,
      clearError: true,
    );
  }

  Future<bool> submitVerification() async {
    if (!state.isReadyToSubmit) return false;

    state = state.copyWith(isLoading: true, clearError: true);

    try {
      final user = _ref.read(authStateProvider).user;
      final userId = user?.id;

      final res = await _repository.submitEkyc(
        frontBytes: state.frontBytes!,
        frontFileName: state.frontFileName ?? 'front.jpg',
        backBytes: state.backBytes!,
        backFileName: state.backFileName ?? 'back.jpg',
        selfieBytes: state.selfieBytes!,
        selfieFileName: state.selfieFileName ?? 'selfie.jpg',
        userId: userId,
        cccdNumber: state.confirmedCccd,
        fullName: state.confirmedFullName,
        dob: state.confirmedDob,
        gender: state.confirmedGender,
        residenceAddress: state.confirmedResidenceAddress,
        issueDate: state.confirmedIssueDate,
      );

      state = state.copyWith(isLoading: false, result: res);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  void resetForm() {
    state = const EkycState();
  }
}

final ekycProvider = StateNotifierProvider<EkycNotifier, EkycState>((ref) {
  final repo = ref.watch(ekycRepositoryProvider);
  return EkycNotifier(repo, ref);
});
