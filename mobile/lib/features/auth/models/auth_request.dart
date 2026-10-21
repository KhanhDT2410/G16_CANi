import 'package:cani/features/auth/models/user_dto.dart';

/// Request body model for password login.
class LoginRequestModel {
  const LoginRequestModel({
    required this.email,
    required this.password,
  });

  final String email;
  final String password;

  Map<String, Object?> toJson() {
    return {
      'email': email,
      'password': password,
    };
  }
}

/// Request body model for Worker registration on mobile.
class RegisterRequestModel {
  const RegisterRequestModel({
    required this.email,
    required this.password,
    required this.fullName,
    this.phone,
    this.accountType = 'WORKER',
  });

  final String email;
  final String password;
  final String fullName;
  final String? phone;
  final String accountType;

  Map<String, Object?> toJson() {
    return {
      'email': email,
      'password': password,
      'fullName': fullName,
      'phone': phone,
      'accountType': accountType,
    };
  }
}

/// Response model for successful login from Backend API.
class LoginResponseModel {
  const LoginResponseModel({
    required this.token,
    required this.user,
    this.refreshToken,
    this.expiresIn,
  });

  final String token;
  final UserDto user;
  final String? refreshToken;
  final int? expiresIn;

  factory LoginResponseModel.fromJson(Map<String, Object?> json) {
    return LoginResponseModel(
      token: json['token'] as String? ?? '',
      refreshToken: json['refreshToken'] as String?,
      expiresIn: (json['expiresIn'] as num?)?.toInt(),
      user: UserDto.fromJson(
        (json['user'] as Map<String, Object?>?) ?? <String, Object?>{},
      ),
    );
  }
}
