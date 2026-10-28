import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/notifications/fcm_service.dart';
import 'package:cani/features/auth/data/auth_repository.dart';
import 'package:cani/features/auth/models/user_dto.dart';

class AuthState {
  const AuthState({
    this.isLoading = false,
    this.user,
    this.errorMessage,
  });

  final bool isLoading;
  final UserDto? user;
  final String? errorMessage;

  bool get isAuthenticated => user != null;

  AuthState copyWith({
    bool? isLoading,
    UserDto? user,
    String? errorMessage,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      user: user ?? this.user,
      errorMessage: errorMessage,
    );
  }
}

final authStateProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  final dio = ref.watch(dioProvider);
  return AuthNotifier(repository, dio);
});

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._repository, this._dio) : super(const AuthState()) {
    checkAuthStatus();
  }

  final AuthRepository _repository;
  final Dio _dio;

  Future<void> checkAuthStatus() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final user = await _repository.getSavedUser();
      state = state.copyWith(isLoading: false, user: user);
      if (user != null) {
        FcmService.syncTokenWithBackend(_dio, explicitUserId: user.id);
      }
    } catch (_) {
      state = state.copyWith(isLoading: false, user: null);
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final response = await _repository.login(email, password);
      state = state.copyWith(isLoading: false, user: response.user);
      FcmService.syncTokenWithBackend(_dio, explicitUserId: response.user.id);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> sendOtp(String email) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      await _repository.sendOtp(email);
      state = state.copyWith(isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> loginWithGoogle() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final response = await _repository.signInWithGoogle();
      state = state.copyWith(isLoading: false, user: response.user);
      FcmService.syncTokenWithBackend(_dio, explicitUserId: response.user.id);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> register({
    required String email,
    required String password,
    required String fullName,
    required String otpCode,
    String? phone,
  }) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      await _repository.registerWorker(
        email: email,
        password: password,
        fullName: fullName,
        otpCode: otpCode,
        phone: phone,
      );
      state = state.copyWith(isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<void> logout() async {
    state = state.copyWith(isLoading: true);
    final currentUserId = state.user?.id;
    try {
      await FcmService.unregisterToken(_dio, explicitUserId: currentUserId);
    } catch (_) {}
    await _repository.logout();
    state = const AuthState();
  }

  void clearError() {
    state = state.copyWith(errorMessage: null);
  }
}
