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
        // Tự động fallback sang IP LAN máy chủ nếu kết nối localhost bị từ chối trên thiết bị thật
        if ((error.type == DioExceptionType.connectionError ||
                error.type == DioExceptionType.connectionTimeout) &&
            error.requestOptions.extra['retried_lan'] == null &&
            error.requestOptions.baseUrl.contains('localhost')) {
          error.requestOptions.extra['retried_lan'] = true;
          final lanBaseUrl = error.requestOptions.baseUrl.replaceFirst('localhost', '192.168.100.31');
          final rawData = error.requestOptions.data;
          final retryData = rawData is FormData ? rawData.clone() : rawData;
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
            receiveTimeout: error.requestOptions.receiveTimeout,
            sendTimeout: error.requestOptions.sendTimeout,
          );
          try {
            final fallbackDio = Dio(BaseOptions(baseUrl: lanBaseUrl));
            final response = await fallbackDio.request(
              error.requestOptions.path,
              data: retryData,
              queryParameters: error.requestOptions.queryParameters,
              options: retryOptions,
            );
            dio.options.baseUrl = lanBaseUrl;
            return handler.resolve(response);
          } catch (_) {
            // Nếu fallback LAN cũng không thành công thì chuyển tiếp lỗi ban đầu
          }
        }
        handler.next(error);
      },
    ),
  ]);

  ref.onDispose(() => dio.close());

  return dio;
});
