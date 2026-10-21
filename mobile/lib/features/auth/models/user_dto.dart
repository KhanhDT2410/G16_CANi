/// DTO representing an authenticated user in CANi system.
class UserDto {
  const UserDto({
    required this.id,
    required this.email,
    required this.fullName,
    required this.role,
    required this.status,
    this.phone,
    this.avatarUrl,
    this.emailVerified = false,
  });

  final String id;
  final String email;
  final String fullName;
  final String role;
  final String status;
  final String? phone;
  final String? avatarUrl;
  final bool emailVerified;

  factory UserDto.fromJson(Map<String, Object?> json) {
    return UserDto(
      id: json['id'] as String? ?? '',
      email: json['email'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      role: json['role'] as String? ?? 'WORKER',
      status: json['status'] as String? ?? 'ACTIVE',
      phone: json['phone'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      emailVerified: json['emailVerified'] as bool? ?? false,
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'email': email,
      'fullName': fullName,
      'role': role,
      'status': status,
      'phone': phone,
      'avatarUrl': avatarUrl,
      'emailVerified': emailVerified,
    };
  }

  UserDto copyWith({
    String? id,
    String? email,
    String? fullName,
    String? role,
    String? status,
    String? phone,
    String? avatarUrl,
    bool? emailVerified,
  }) {
    return UserDto(
      id: id ?? this.id,
      email: email ?? this.email,
      fullName: fullName ?? this.fullName,
      role: role ?? this.role,
      status: status ?? this.status,
      phone: phone ?? this.phone,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      emailVerified: emailVerified ?? this.emailVerified,
    );
  }
}
