import 'package:dio/dio.dart';

/// Chuẩn hóa lỗi kỹ thuật (DioException, Exception, chuỗi tiếng Anh thô)
/// thành thông báo tiếng Việt ngắn gọn, thân thiện cho người dùng cuối.
class AppErrorFormatter {
  AppErrorFormatter._();

  static final RegExp _technicalPattern = RegExp(
    r'(DioException|SocketException|TimeoutException|ClientException|HandshakeException|'
    r'HttpException|FormatException|PlatformException|RangeError|NoSuchMethodError|'
    r'Stack Trace|type .* is not a subtype|XMLHttpRequest|Connection refused|'
    r'Failed host lookup|Software caused connection abort|Broken pipe)',
    caseSensitive: false,
  );

  static String format(
    dynamic error, {
    String fallback = 'Đã xảy ra lỗi. Vui lòng thử lại sau.',
  }) {
    if (error == null) return fallback;

    if (error is DioException) {
      return _fromDio(error, fallback: fallback);
    }

    var text = error.toString().trim();
    if (text.isEmpty) return fallback;

    // Gỡ tiền tố kỹ thuật phổ biến của Dart
    while (text.startsWith('Exception: ') ||
        text.startsWith('RepositoryException: ') ||
        text.startsWith('StateError: ')) {
      if (text.startsWith('Exception: ')) {
        text = text.substring('Exception: '.length).trim();
      } else if (text.startsWith('RepositoryException: ')) {
        text = text.substring('RepositoryException: '.length).trim();
      } else if (text.startsWith('StateError: ')) {
        text = text.substring('StateError: '.length).trim();
      }
    }

    if (text.isEmpty) return fallback;

    // Cắt bỏ phần log DioException(...) nếu vô tình bị nối vào cuối chuỗi
    final dioSuffixIdx = text.indexOf('(DioException');
    if (dioSuffixIdx > 0) {
      text = text.substring(0, dioSuffixIdx).trim();
    }

    if (_technicalPattern.hasMatch(text)) {
      return fallback;
    }

    return text;
  }

  static String _fromDio(
    DioException error, {
    required String fallback,
  }) {
    final resData = error.response?.data;
    if (resData is Map && resData['message'] != null) {
      final serverMsg = resData['message'].toString().trim();
      if (serverMsg.isNotEmpty && !_technicalPattern.hasMatch(serverMsg)) {
        return serverMsg;
      }
    }

    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return 'Kết nối máy chủ bị quá thời gian. Vui lòng thử lại.';
      case DioExceptionType.connectionError:
        return 'Không thể kết nối đến máy chủ CANi. Vui lòng kiểm tra mạng.';
      case DioExceptionType.cancel:
        return 'Yêu cầu đã được hủy.';
      case DioExceptionType.badCertificate:
        return 'Lỗi chứng chỉ kết nối bảo mật.';
      case DioExceptionType.badResponse:
      case DioExceptionType.unknown:
      default:
        break;
    }

    final statusCode = error.response?.statusCode;
    if (statusCode == 401) {
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    }
    if (statusCode == 403) {
      return 'Bạn không có quyền thực hiện thao tác này.';
    }
    if (statusCode == 404) {
      return 'Không tìm thấy dữ liệu yêu cầu.';
    }
    if (statusCode != null && statusCode >= 500) {
      return 'Máy chủ đang bận hoặc gặp sự cố. Vui lòng thử lại sau.';
    }

    return fallback;
  }
}
