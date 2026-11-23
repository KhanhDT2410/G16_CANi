import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import '../data/ekyc_repository.dart';
import '../models/ekyc_models.dart';

final ekycRepositoryProvider = Provider<EkycRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return EkycRepository(dio);
});

class EkycImageClientValidator {
  static const int maxFileSizeBytes = 5 * 1024 * 1024; // 5MB khớp giới hạn backend

  /// Kiểm tra tệp ảnh trước khi upload (rỗng, dung lượng <= 5MB, magic bytes JPEG/PNG/WEBP).
  /// Trả về thông báo lỗi nếu không hợp lệ, hoặc null nếu hợp lệ.
  static String? validate(Uint8List bytes, String fileName, String label) {
    if (bytes.isEmpty) {
      return '$label không có dữ liệu ảnh hợp lệ.';
    }
    if (bytes.length > maxFileSizeBytes) {
      return '$label vượt quá dung lượng tối đa 5MB. Vui lòng chụp lại ảnh nhẹ hơn.';
    }
    if (!_hasValidImageMagicBytes(bytes)) {
      return '$label không đúng định dạng ảnh JPEG, PNG hoặc WEBP hợp lệ.';
    }
    return null;
  }

  static bool _hasValidImageMagicBytes(Uint8List b) {
    if (b.length < 4) return false;
    // JPEG: FF D8 FF
    if (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) return true;
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (b.length >= 8 &&
        b[0] == 0x89 &&
        b[1] == 0x50 &&
        b[2] == 0x4E &&
        b[3] == 0x47 &&
        b[4] == 0x0D &&
        b[5] == 0x0A &&
        b[6] == 0x1A &&
        b[7] == 0x0A) {
      return true;
    }
    // WEBP: RIFF....WEBP
    if (b.length >= 12 &&
        b[0] == 0x52 &&
        b[1] == 0x49 &&
        b[2] == 0x46 &&
        b[3] == 0x46 &&
        b[8] == 0x57 &&
        b[9] == 0x45 &&
        b[10] == 0x42 &&
        b[11] == 0x50) {
      return true;
    }
    return false;
  }
}

class EkycState {
  final bool isLoading;
  final String? errorMessage;
  final bool isTechnicalError;
  final EkycVerificationItem? result;

  final Uint8List? frontBytes;
  final String? frontFileName;
  final Uint8List? backBytes;
  final String? backFileName;
  final Uint8List? selfieBytes;
  final String? selfieFileName;

  // Thông tin CCCD từ QR/nhập tay (chỉ dùng đối chiếu chéo phía backend)
  final String? confirmedCccd;
  final String? confirmedFullName;
  final String? confirmedDob;
  final String? confirmedGender;
  final String? confirmedResidenceAddress;
  final String? confirmedIssueDate;

  // Đồng ý xử lý dữ liệu sinh trắc học theo Nghị định 13/2023/NĐ-CP
  final EkycConsentPolicy consentPolicy;
  final bool consentAccepted;

  const EkycState({
    this.isLoading = false,
    this.errorMessage,
    this.isTechnicalError = false,
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
    this.consentPolicy = EkycConsentPolicy.fallback,
    this.consentAccepted = false,
  });

  bool get hasAllImages =>
      frontBytes != null && backBytes != null && selfieBytes != null;

  bool get isReadyToSubmit =>
      hasAllImages && (!consentPolicy.required || consentAccepted) && !isLoading;

  bool get hasConfirmedInfo =>
      confirmedCccd != null && confirmedCccd!.isNotEmpty;

  EkycState copyWith({
    bool? isLoading,
    String? errorMessage,
    bool? isTechnicalError,
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
    EkycConsentPolicy? consentPolicy,
    bool? consentAccepted,
    bool clearError = false,
    bool clearResult = false,
  }) {
    return EkycState(
      isLoading: isLoading ?? this.isLoading,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      isTechnicalError: clearError ? false : (isTechnicalError ?? this.isTechnicalError),
      result: clearResult ? null : (result ?? this.result),
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
      consentPolicy: consentPolicy ?? this.consentPolicy,
      consentAccepted: consentAccepted ?? this.consentAccepted,
    );
  }
}

class EkycNotifier extends StateNotifier<EkycState> {
  final EkycRepository _repository;
  final Ref _ref;

  EkycNotifier(this._repository, this._ref) : super(const EkycState()) {
    loadInitialData();
    _ref.listen<AuthState>(authStateProvider, (previous, next) {
      if (previous?.user?.id != next.user?.id) {
        // Chỉ xóa state cục bộ khi đổi tài khoản — TUYỆT ĐỐI không gọi API reset xóa dữ liệu server!
        clearLocalForm();
        if (next.isAuthenticated) {
          loadInitialData();
        }
      }
    });
  }

  Future<void> loadInitialData() async {
    await Future.wait([checkStatus(), loadConsentPolicy()]);
  }

  Future<void> loadConsentPolicy() async {
    try {
      final policy = await _repository.getConsentPolicy();
      state = state.copyWith(consentPolicy: policy);
    } catch (_) {
      state = state.copyWith(consentPolicy: EkycConsentPolicy.fallback);
    }
  }

  void setConsentAccepted(bool accepted) {
    state = state.copyWith(consentAccepted: accepted, clearError: true);
  }

  Future<void> checkStatus() async {
    final status = await _repository.getMyEkycStatus();
    if (status != null) {
      state = state.copyWith(result: status, clearError: true);
    } else {
      state = state.copyWith(clearResult: true);
    }
  }

  String? setFrontImage(Uint8List bytes, String fileName) {
    final err = EkycImageClientValidator.validate(bytes, fileName, 'Ảnh mặt trước CCCD');
    if (err != null) {
      state = state.copyWith(errorMessage: err, isTechnicalError: false);
      return err;
    }
    state = state.copyWith(frontBytes: bytes, frontFileName: fileName, clearError: true);
    return null;
  }

  String? setBackImage(Uint8List bytes, String fileName) {
    final err = EkycImageClientValidator.validate(bytes, fileName, 'Ảnh mặt sau CCCD');
    if (err != null) {
      state = state.copyWith(errorMessage: err, isTechnicalError: false);
      return err;
    }
    state = state.copyWith(backBytes: bytes, backFileName: fileName, clearError: true);
    return null;
  }

  String? setSelfieImage(Uint8List bytes, String fileName) {
    final err = EkycImageClientValidator.validate(bytes, fileName, 'Ảnh chân dung Selfie');
    if (err != null) {
      state = state.copyWith(errorMessage: err, isTechnicalError: false);
      return err;
    }
    state = state.copyWith(selfieBytes: bytes, selfieFileName: fileName, clearError: true);
    return null;
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

    final frontErr = EkycImageClientValidator.validate(
      state.frontBytes!,
      state.frontFileName ?? 'front.jpg',
      'Ảnh mặt trước CCCD',
    );
    final backErr = EkycImageClientValidator.validate(
      state.backBytes!,
      state.backFileName ?? 'back.jpg',
      'Ảnh mặt sau CCCD',
    );
    final selfieErr = EkycImageClientValidator.validate(
      state.selfieBytes!,
      state.selfieFileName ?? 'selfie.jpg',
      'Ảnh chân dung Selfie',
    );
    final validationError = frontErr ?? backErr ?? selfieErr;
    if (validationError != null) {
      state = state.copyWith(errorMessage: validationError, isTechnicalError: false);
      return false;
    }

    state = state.copyWith(isLoading: true, clearError: true);

    try {
      final res = await _repository.submitEkyc(
        frontBytes: state.frontBytes!,
        frontFileName: state.frontFileName ?? 'front.jpg',
        backBytes: state.backBytes!,
        backFileName: state.backFileName ?? 'back.jpg',
        selfieBytes: state.selfieBytes!,
        selfieFileName: state.selfieFileName ?? 'selfie.jpg',
        consentAccepted: state.consentAccepted,
        consentNoticeVersion: state.consentPolicy.noticeVersion,
        cccdNumber: state.confirmedCccd,
        fullName: state.confirmedFullName,
        dob: state.confirmedDob,
        gender: state.confirmedGender,
        residenceAddress: state.confirmedResidenceAddress,
        issueDate: state.confirmedIssueDate,
      );

      state = state.copyWith(
        isLoading: false,
        result: res,
        clearError: true,
      );
      return true;
    } on EkycSubmitException catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.message,
        isTechnicalError: e.isTechnicalError,
      );
      return false;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Lỗi kết nối tạm thời tới hệ thống xác thực. Vui lòng thử lại.',
        isTechnicalError: true,
      );
      return false;
    }
  }

  /// Thử lại gửi xác minh bằng bộ ảnh đang có sẵn trong bộ nhớ (không yêu cầu chụp lại).
  Future<bool> retryVerification() async {
    if (!state.hasAllImages) {
      clearLocalResultOnly();
      return false;
    }
    return submitVerification();
  }

  /// Quay lại form chụp ảnh mà không xóa dữ liệu đã lưu trên server.
  void clearLocalResultOnly() {
    state = state.copyWith(clearResult: true, clearError: true);
  }

  /// Đặt lại toàn bộ form cục bộ (KHÔNG gọi API xóa hồ sơ server).
  void clearLocalForm() {
    state = const EkycState();
  }

  /// Giữ tương thích cho luồng bắt đầu chụp lại cục bộ mà không hạ cấp tài khoản đã APPROVED.
  void resetForm() {
    clearLocalForm();
  }
}

final ekycProvider = StateNotifierProvider<EkycNotifier, EkycState>((ref) {
  final repo = ref.watch(ekycRepositoryProvider);
  return EkycNotifier(repo, ref);
});
