export type UserRole = 'ADMIN' | 'CUSTOMER_CARE' | 'EVENT_ORGANIZER' | 'WORKER';

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'PENDING_VERIFICATION';

export type AccountType = 'EVENT_ORGANIZER' | 'WORKER';

export interface UserDto {
  id: string;
  email: string;
  phone?: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  avatarUrl?: string;
  emailVerified: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken?: string;
  expiresIn?: number;
  user: UserDto;
}

export interface RegisterRequest {
  email: string;
  fullName: string;
  phone?: string;
  password: string;
  accountType: AccountType;
  companyName?: string;
}

export interface GoogleLoginRequest {
  supabaseAccessToken: string;
}

export interface VerifyOtpRequest {
  email: string;
  token: string;
}

export interface ResetPasswordRequest {
  email: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}
