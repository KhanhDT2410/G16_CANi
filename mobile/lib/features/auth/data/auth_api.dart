import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/auth/models/auth_request.dart';
import 'package:cani/features/auth/models/user_dto.dart';

final authApiProvider = Provider<AuthApi>((ref) {
  final dio = ref.watch(dioProvider);
  return AuthApi(dio);
});

/// API service communicating with CANi backend auth endpoints.
class AuthApi {
  const AuthApi(this._dio);

  final Dio _dio;

  Future<LoginResponseModel> login(LoginRequestModel request) async {
    final response = await _dio.post<Map<String, Object?>>(
      '/auth/login',
      data: request.toJson(),
    );
    final data = (response.data?['data'] as Map<String, Object?>?) ?? <String, Object?>{};
    return LoginResponseModel.fromJson(data);
  }

  Future<void> sendOtp(String email, [String otpType = 'REGISTER']) async {
    await _dio.post<void>(
      '/auth/send-otp',
      data: {'email': email, 'otpType': otpType},
    );
  }

  Future<LoginResponseModel> syncGoogleAuth(String supabaseAccessToken) async {
    final response = await _dio.post<Map<String, Object?>>(
      '/auth/google',
      data: {'supabaseAccessToken': supabaseAccessToken},
    );
    final data = (response.data?['data'] as Map<String, Object?>?) ?? <String, Object?>{};
    return LoginResponseModel.fromJson(data);
  }

  Future<UserDto> register(RegisterRequestModel request) async {
    final response = await _dio.post<Map<String, Object?>>(
      '/auth/register',
      data: request.toJson(),
    );
    final data = (response.data?['data'] as Map<String, Object?>?) ?? <String, Object?>{};
    return UserDto.fromJson(data);
  }

  Future<UserDto> getMe() async {
    final response = await _dio.get<Map<String, Object?>>('/auth/me');
    final data = (response.data?['data'] as Map<String, Object?>?) ?? <String, Object?>{};
    return UserDto.fromJson(data);
  }

  Future<void> verifyOtp(String email, String token) async {
    await _dio.post<void>(
      '/auth/verify-otp',
      data: {'email': email, 'token': token},
    );
  }

  Future<void> resetPassword(String email) async {
    await _dio.post<void>(
      '/auth/reset-password',
      data: {'email': email},
    );
  }
}
