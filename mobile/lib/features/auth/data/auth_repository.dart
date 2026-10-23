import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:cani/core/network/auth_interceptor.dart';

import 'package:cani/features/auth/data/auth_api.dart';
import 'package:cani/features/auth/models/auth_request.dart';
import 'package:cani/features/auth/models/user_dto.dart';

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final api = ref.watch(authApiProvider);
  final storage = ref.watch(secureStorageProvider);
  return AuthRepository(api, storage);
});

class AuthRepository {
  const AuthRepository(this._api, this._storage);

  final AuthApi _api;
  final FlutterSecureStorage _storage;

  static const _tokenKey = 'cani_token';
  static const _refreshTokenKey = 'cani_refresh_token';

  Future<LoginResponseModel> login(String email, String password) async {
    try {
      // 1. Authenticate via Backend API (which delegates to Supabase GoTrue)
      final response = await _api.login(
        LoginRequestModel(email: email, password: password),
      );

      // 2. Persist tokens in secure storage & in-memory cache
      AuthInterceptor.inMemoryToken = response.token;
      await _storage.write(key: _tokenKey, value: response.token);
      if (response.refreshToken != null) {
        await _storage.write(key: _refreshTokenKey, value: response.refreshToken);
      }

      return response;
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Đăng nhập không thành công. Vui lòng kiểm tra lại thông tin.');
    } catch (e) {
      throw Exception('Lỗi đăng nhập: ${e.toString()}');
    }
  }

  Future<void> sendOtp(String email) async {
    try {
      await _api.sendOtp(email, 'REGISTER');
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Không thể gửi mã OTP. Vui lòng kiểm tra lại email.');
    } catch (e) {
      throw Exception('Lỗi gửi OTP: ${e.toString()}');
    }
  }

  Future<UserDto> registerWorker({
    required String email,
    required String password,
    required String fullName,
    required String otpCode,
    String? phone,
  }) async {
    try {
      return await _api.register(
        RegisterRequestModel(
          email: email,
          password: password,
          fullName: fullName,
          otpCode: otpCode,
          phone: phone,
          accountType: 'WORKER',
        ),
      );
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Đăng ký tài khoản thất bại.');
    } catch (e) {
      throw Exception('Lỗi đăng ký: ${e.toString()}');
    }
  }

  Future<LoginResponseModel> signInWithGoogle() async {
    try {
      // 1. Mở Supabase OAuth Google Sign-in
      await Supabase.instance.client.auth.signInWithOAuth(
        OAuthProvider.google,
        redirectTo: 'cani://login-callback',
      );

      final session = Supabase.instance.client.auth.currentSession;
      if (session == null || session.accessToken.isEmpty) {
        throw Exception('Không nhận được phiên xác thực từ Google.');
      }

      // 2. Đồng bộ user sang CANi Backend API
      final response = await _api.syncGoogleAuth(session.accessToken);

      // 3. Lưu trữ token
      AuthInterceptor.inMemoryToken = response.token;
      await _storage.write(key: _tokenKey, value: response.token);
      if (response.refreshToken != null) {
        await _storage.write(key: _refreshTokenKey, value: response.refreshToken);
      }

      return response;
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Xác thực Google không thành công.');
    } catch (e) {
      throw Exception('Lỗi Google Sign-In: ${e.toString()}');
    }
  }

  Future<UserDto?> getSavedUser() async {
    try {
      final token = await _storage.read(key: _tokenKey);
      if (token == null || token.isEmpty) return null;
      return await _api.getMe();
    } catch (_) {
      await clearSession();
      return null;
    }
  }

  Future<void> logout() async {
    try {
      await Supabase.instance.client.auth.signOut();
    } catch (_) {}
    await clearSession();
  }

  Future<void> clearSession() async {
    AuthInterceptor.inMemoryToken = null;
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _refreshTokenKey);
  }
}
