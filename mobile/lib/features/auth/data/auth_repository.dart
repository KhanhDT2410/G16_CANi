import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:cani/core/constants/api_constants.dart';
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

  Future<void> sendResetPasswordOtp(String email) async {
    try {
      await _api.sendOtp(email, 'RESET_PASSWORD');
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Không thể gửi mã OTP đặt lại mật khẩu. Vui lòng kiểm tra lại email.');
    } catch (e) {
      throw Exception('Lỗi gửi OTP: ${e.toString()}');
    }
  }

  Future<void> confirmResetPassword({
    required String email,
    required String otp,
    required String newPassword,
  }) async {
    try {
      await _api.confirmResetPassword(
        email: email,
        otp: otp,
        newPassword: newPassword,
      );
    } on DioException catch (e) {
      final message = (e.response?.data is Map)
          ? (e.response?.data as Map)['message']?.toString()
          : null;
      throw Exception(message ?? 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra lại mã OTP.');
    } catch (e) {
      throw Exception('Lỗi đặt lại mật khẩu: ${e.toString()}');
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
      // 1. Dọn dẹp phiên xác thực Supabase cũ nếu còn sót lại
      if (Supabase.instance.client.auth.currentSession != null) {
        try {
          await Supabase.instance.client.auth.signOut();
        } catch (_) {}
      }

      // 2. Khởi tạo Native Google Sign-In qua Google Play Services
      final googleSignIn = GoogleSignIn(
        serverClientId: ApiConstants.googleWebClientId,
        scopes: ['email', 'profile'],
      );

      // Đăng xuất Google phiên trước để người dùng có thể chọn tài khoản khác
      try {
        await googleSignIn.signOut();
      } catch (_) {}

      final googleUser = await googleSignIn.signIn();
      if (googleUser == null) {
        throw Exception('Bạn đã hủy đăng nhập Google.');
      }

      final googleAuth = await googleUser.authentication;
      final idToken = googleAuth.idToken;
      final accessToken = googleAuth.accessToken;

      if (idToken == null || idToken.isEmpty) {
        throw Exception('Không nhận được ID Token từ Google. Vui lòng kiểm tra lại Google Play Services.');
      }

      // 3. Đăng nhập Supabase trực tiếp bằng ID Token (Native, không mở Web Browser)
      final authResponse = await Supabase.instance.client.auth.signInWithIdToken(
        provider: OAuthProvider.google,
        idToken: idToken,
        accessToken: accessToken,
      );

      final session = authResponse.session;
      if (session == null || session.accessToken.isEmpty) {
        throw Exception('Không nhận được phiên xác thực từ Google ID Token.');
      }

      // 4. Đồng bộ user sang CANi Backend API với targetRole: WORKER
      final response = await _api.syncGoogleAuth(session.accessToken, 'WORKER');

      // 5. Kiểm tra vai trò: App di động dành riêng cho Worker
      if (response.user.role.toUpperCase() != 'WORKER') {
        await clearSession();
        throw Exception('Tài khoản của bạn là ${response.user.role}. Ứng dụng này chỉ dành cho Nhân sự (Worker). Vui lòng đăng nhập trên Web Portal.');
      }

      // 6. Lưu trữ token
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
      throw Exception(message ?? 'Đồng bộ tài khoản Google với hệ thống CANi thất bại.');
    } catch (e) {
      if (e is Exception) rethrow;
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
