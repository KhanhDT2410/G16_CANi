import 'dart:async';
import 'dart:io' show Socket;
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
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
      onRequest: (options, handler) {
        // Chuẩn hóa đường dẫn: loại bỏ tiền tố '/api' nếu repository truyền '/api/v1/...'
        if (options.path.startsWith('/api/')) {
          options.path = options.path.substring(4);
        }
        return handler.next(options);
      },
      onError: (error, handler) async {
        // Tự động fallback nếu kết nối thất bại (ưu tiên 127.0.0.1 / localhost / IP LAN / Emulator 10.0.2.2)
        if ((error.type == DioExceptionType.connectionError ||
                error.type == DioExceptionType.connectionTimeout) &&
            error.requestOptions.extra['retried_lan'] == null) {
          error.requestOptions.extra['retried_lan'] = true;

          final fallbackHosts = [
            '127.0.0.1:8080',
            'localhost:8080',
            '192.168.1.19:8080',
            '10.0.2.2:8080',
            '192.168.1.10:8080',
            '192.168.100.31:8080',
            '192.168.1.20:8080',
          ];
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
            receiveTimeout: ApiConstants.receiveTimeout,
          );

          final candidates = fallbackHosts
              .where((host) => !error.requestOptions.baseUrl.contains(host))
              .toList();

          String? reachableHost;
          if (!kIsWeb && candidates.isNotEmpty) {
            final completer = Completer<String?>();
            var remaining = candidates.length;
            for (final host in candidates) {
              final parts = host.split(':');
              final ip = parts[0];
              final port = int.tryParse(parts.length > 1 ? parts[1] : '8080') ?? 8080;
              Socket.connect(ip, port, timeout: const Duration(milliseconds: 900)).then((socket) {
                socket.destroy();
                if (!completer.isCompleted) {
                  completer.complete(host);
                }
              }).catchError((_) {
                remaining -= 1;
                if (remaining == 0 && !completer.isCompleted) {
                  completer.complete(null);
                }
              });
            }
            reachableHost = await completer.future;
          }

          if (reachableHost != null) {
            final fallbackBaseUrl = error.requestOptions.baseUrl.replaceAll(
              RegExp(r'(127\.0\.0\.1|localhost|192\.168\.\d+\.\d+|10\.0\.2\.2):8080'),
              reachableHost,
            );
            try {
              final retryData = rawData is FormData ? rawData.clone() : rawData;
              final fallbackDio = Dio(BaseOptions(
                baseUrl: fallbackBaseUrl,
                connectTimeout: const Duration(seconds: 3),
                receiveTimeout: ApiConstants.receiveTimeout,
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
              // Fall through to handler.next(error)
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
