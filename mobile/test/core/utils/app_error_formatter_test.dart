import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppErrorFormatter & RepositoryException', () {
    test('RepositoryException.toString() returns only clean message without DioException dump', () {
      final dioErr = DioException(
        requestOptions: RequestOptions(path: '/v1/workers/assignments/1/checkin'),
        response: Response(
          requestOptions: RequestOptions(path: '/v1/workers/assignments/1/checkin'),
          statusCode: 400,
          data: {'message': 'Mã QR không hợp lệ. Vui lòng quét mã QR chính thức từ Nhà tổ chức!'},
        ),
        type: DioExceptionType.badResponse,
      );

      final ex = RepositoryException(
        'Mã QR không hợp lệ. Vui lòng quét mã QR chính thức từ Nhà tổ chức!',
        dioErr,
      );

      expect(
        ex.toString(),
        'Mã QR không hợp lệ. Vui lòng quét mã QR chính thức từ Nhà tổ chức!',
      );
      expect(ex.toString(), isNot(contains('DioException')));
    });

    test('AppErrorFormatter strips Exception prefix and technical DioException suffixes', () {
      expect(
        AppErrorFormatter.format(
          Exception('Mã QR không hợp lệ (DioException [bad response]: status 400)'),
        ),
        'Mã QR không hợp lệ',
      );
    });

    test('AppErrorFormatter translates DioException network/timeout/status errors to Vietnamese', () {
      final timeoutErr = DioException(
        requestOptions: RequestOptions(path: '/test'),
        type: DioExceptionType.connectionTimeout,
      );
      expect(
        AppErrorFormatter.format(timeoutErr),
        'Kết nối máy chủ bị quá thời gian. Vui lòng thử lại.',
      );

      final serverErr = DioException(
        requestOptions: RequestOptions(path: '/test'),
        response: Response(
          requestOptions: RequestOptions(path: '/test'),
          statusCode: 500,
        ),
        type: DioExceptionType.badResponse,
      );
      expect(
        AppErrorFormatter.format(serverErr),
        'Máy chủ đang bận hoặc gặp sự cố. Vui lòng thử lại sau.',
      );
    });

    test('AppErrorFormatter hides raw English technical exception strings with fallback', () {
      expect(
        AppErrorFormatter.format(
          Exception('SocketException: Failed host lookup: api.example.com'),
          fallback: 'Không thể tải dữ liệu.',
        ),
        'Không thể tải dữ liệu.',
      );
    });
  });
}
