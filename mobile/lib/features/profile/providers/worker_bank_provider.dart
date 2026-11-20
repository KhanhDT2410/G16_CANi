import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/profile/models/worker_bank_model.dart';

class WorkerBankState {
  final WorkerBankModel? bankAccount;
  final bool isLoading;
  final String? errorMessage;

  const WorkerBankState({
    this.bankAccount,
    this.isLoading = false,
    this.errorMessage,
  });

  WorkerBankState copyWith({
    WorkerBankModel? bankAccount,
    bool? isLoading,
    String? errorMessage,
  }) {
    return WorkerBankState(
      bankAccount: bankAccount ?? this.bankAccount,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class WorkerBankNotifier extends StateNotifier<WorkerBankState> {
  WorkerBankNotifier(this._ref) : super(const WorkerBankState()) {
    loadBankAccount();
  }

  final Ref _ref;
  static const _storage = FlutterSecureStorage();

  String _bankStorageKey(String field, String? userId) {
    final safeUser = (userId != null && userId.isNotEmpty) ? userId : 'guest';
    return 'cani_worker_bank_${safeUser}_$field';
  }

  Future<void> loadBankAccount() async {
    final userId = _ref.read(authStateProvider).user?.id;
    if (userId == null || userId.isEmpty) {
      state = const WorkerBankState(isLoading: false);
      return;
    }

    state = state.copyWith(isLoading: true, errorMessage: null);

    // 1. Nạp nhanh từ bộ nhớ an toàn theo từng userId
    try {
      final savedBank = await _storage.read(key: _bankStorageKey('code', userId));
      final savedAccount = await _storage.read(key: _bankStorageKey('account', userId));
      final savedName = await _storage.read(key: _bankStorageKey('owner', userId));
      if (savedAccount != null && savedAccount.isNotEmpty) {
        state = state.copyWith(
          bankAccount: WorkerBankModel(
            bankCode: savedBank,
            bankAccountNumber: savedAccount,
            bankAccountName: savedName,
          ),
          isLoading: false,
        );
      }
    } catch (_) {}

    // 2. Đồng bộ trực tiếp từ Cloud Database qua API GET /v1/workers/bank-account
    try {
      final dio = _ref.read(dioProvider);
      final response = await dio.get('/v1/workers/bank-account');
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data;
        final data = body is Map<String, dynamic> && body.containsKey('data')
            ? body['data'] as Map<String, dynamic>?
            : body as Map<String, dynamic>?;

        if (data != null) {
          final model = WorkerBankModel.fromJson(data);
          state = state.copyWith(bankAccount: model, isLoading: false);

          if (model.bankCode != null) {
            await _storage.write(key: _bankStorageKey('code', userId), value: model.bankCode!);
          }
          if (model.bankAccountNumber != null) {
            await _storage.write(key: _bankStorageKey('account', userId), value: model.bankAccountNumber!);
          }
          if (model.bankAccountName != null) {
            await _storage.write(key: _bankStorageKey('owner', userId), value: model.bankAccountName!);
          }
          return;
        }
      }
      state = state.copyWith(isLoading: false);
    } catch (e) {
      debugPrint('Lỗi tải thông tin tài khoản ngân hàng: $e');
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }
}

final workerBankProvider =
    StateNotifierProvider<WorkerBankNotifier, WorkerBankState>((ref) {
  return WorkerBankNotifier(ref);
});
