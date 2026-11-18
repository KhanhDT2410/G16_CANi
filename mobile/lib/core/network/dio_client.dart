import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/constants/api_constants.dart';
import 'package:cani/core/network/auth_interceptor.dart';

/// Global Dio instance provider.
///
/// All features should inject this via Riverpod instead of
/// creating their own Dio instances or using static singletons.
final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: '${ApiConstants.baseUrl}${ApiConstants.apiPath}',
      connectTimeout: ApiConstants.connectTimeout,
      receiveTimeout: ApiConstants.receiveTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  );

  dio.interceptors.addAll([
    AuthInterceptor(),
    InterceptorsWrapper(
      onError: (error, handler) async {
        // Tự động fallback nếu kết nối thất bại (thử qua IP LAN máy tính 192.168.1.20 hoặc Emulator 10.0.2.2)
        if ((error.type == DioExceptionType.connectionError ||
                error.type == DioExceptionType.connectionTimeout) &&
            error.requestOptions.extra['retried_lan'] == null) {
          error.requestOptions.extra['retried_lan'] = true;
          
          final fallbackHosts = ['192.168.1.20:8080', '10.0.2.2:8080', 'localhost:8080', '127.0.0.1:8080'];
          final rawData = error.requestOptions.data;
          final retryHeaders = Map<String, dynamic>.from(error.requestOptions.headers);
          if (rawData is FormData) {
            retryHeaders.remove('content-type');
            retryHeaders.remove('Content-Type');
          }
          final retryOptions = Options(
            method: error.requestOptions.method,
            headers: retryHeaders,
            extra: error.requestOptions.extra,
            responseType: error.requestOptions.responseType,
            contentType: rawData is FormData ? null : error.requestOptions.contentType,
            validateStatus: error.requestOptions.validateStatus,
            receiveTimeout: const Duration(seconds: 4),
            sendTimeout: const Duration(seconds: 4),
          );

          for (final host in fallbackHosts) {
            if (error.requestOptions.baseUrl.contains(host)) continue;
            final fallbackBaseUrl = error.requestOptions.baseUrl.replaceAll(
              RegExp(r'(127\.0\.0\.1|localhost|192\.168\.\d+\.\d+|10\.0\.2\.2):8080'),
              host,
            );
            try {
              final retryData = rawData is FormData ? rawData.clone() : rawData;
              final fallbackDio = Dio(BaseOptions(
                baseUrl: fallbackBaseUrl,
                connectTimeout: const Duration(seconds: 3),
              ));
              final response = await fallbackDio.request(
                error.requestOptions.path,
                data: retryData,
                queryParameters: error.requestOptions.queryParameters,
                options: retryOptions,
              );
              dio.options.baseUrl = fallbackBaseUrl;
              return handler.resolve(response);
            } catch (_) {
              // Tiếp tục thử host kế tiếp
            }
          }
        }
        handler.next(error);
      },
    ),
  ]);

  ref.onDispose(() => dio.close());

  return dio;
});
