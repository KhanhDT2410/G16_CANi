import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:cani/features/ekyc/models/ekyc_models.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';

void main() {
  group('EkycVerificationItem status mapping', () {
    test('maps all 6 standardized backend statuses accurately', () {
      EkycVerificationItem build(String status) => EkycVerificationItem.fromJson({
            'verificationId': 'v-1',
            'workerId': 'w-1',
            'status': status,
            'routingDecision': status,
            'routingReason': 'reason',
          });

      final approved = build('APPROVED');
      expect(approved.isApproved, isTrue);
      expect(approved.isPending, isFalse);
      expect(approved.isTechnicalFailure, isFalse);
      expect(approved.isRejected, isFalse);

      final pendingReview = build('PENDING_REVIEW');
      expect(pendingReview.isPendingReview, isTrue);
      expect(pendingReview.isPending, isTrue);
      expect(pendingReview.isApproved, isFalse);
      expect(pendingReview.isRejected, isFalse);

      final processing = build('PROCESSING');
      expect(processing.isProcessing, isTrue);
      expect(processing.isPending, isTrue);
      expect(processing.isApproved, isFalse);

      final pending = build('PENDING');
      expect(pending.isProcessing, isTrue);
      expect(pending.isApproved, isFalse);

      final techFailure = build('TECHNICAL_FAILURE');
      expect(techFailure.isTechnicalFailure, isTrue);
      expect(techFailure.retryable, isTrue);
      expect(techFailure.canRetry, isTrue);
      expect(techFailure.isRejected, isFalse);
      expect(techFailure.isApproved, isFalse);

      final rejected = build('REJECTED');
      expect(rejected.isRejected, isTrue);
      expect(rejected.isTechnicalFailure, isFalse);
      expect(rejected.isApproved, isFalse);
    });

    test('keeps faceMatchScore and ocrConfidence nullable and parses expiry/age/consent fields', () {
      final item = EkycVerificationItem.fromJson({
        'verificationId': 'v-2',
        'status': 'TECHNICAL_FAILURE',
        'failureCode': 'VNPT_TIMEOUT',
        'routingReason': 'Timeout',
      });
      expect(item.faceMatchScore, isNull);
      expect(item.ocrConfidence, isNull);
      expect(item.failureCode, 'VNPT_TIMEOUT');
      expect(item.retryable, isTrue);

      final expiredPending = EkycVerificationItem.fromJson({
        'verificationId': 'v-3',
        'status': 'PENDING_REVIEW',
        'imagesAvailable': false,
        'imagesExpired': true,
        'imagesUnavailableReason': 'Ảnh hết hạn lưu trữ',
        'meetsMinimumAge': true,
        'consentNoticeVersion': '2026.11-nd13-v1',
        'routingReason': 'Pending review',
      });
      expect(expiredPending.isExpiredPending, isTrue);
      expect(expiredPending.canRetry, isTrue);
      expect(expiredPending.meetsMinimumAge, isTrue);
      expect(expiredPending.consentNoticeVersion, '2026.11-nd13-v1');

      final policy = EkycConsentPolicy.fromJson({
        'required': true,
        'noticeVersion': '2026.11-nd13-v1',
        'title': 'Tiêu đề',
        'summary': 'Nội dung',
      });
      expect(policy.required, isTrue);
      expect(policy.noticeVersion, '2026.11-nd13-v1');
    });
  });

  group('EkycImageClientValidator', () {
    test('accepts valid JPEG, PNG, and WEBP magic bytes', () {
      final jpeg = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
      final png = Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00]);
      final webp = Uint8List.fromList([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]);

      expect(EkycImageClientValidator.validate(jpeg, 'front.jpg', 'Mặt trước'), isNull);
      expect(EkycImageClientValidator.validate(png, 'back.png', 'Mặt sau'), isNull);
      expect(EkycImageClientValidator.validate(webp, 'selfie.webp', 'Selfie'), isNull);
    });

    test('rejects empty, non-image, and oversized files', () {
      expect(EkycImageClientValidator.validate(Uint8List(0), 'empty.jpg', 'Mặt trước'), isNotNull);

      final fakeJpg = Uint8List.fromList('<html>fake</html>'.codeUnits);
      expect(EkycImageClientValidator.validate(fakeJpg, 'fake.jpg', 'Mặt trước'), isNotNull);

      final oversized = Uint8List(EkycImageClientValidator.maxFileSizeBytes + 1);
      oversized[0] = 0xFF;
      oversized[1] = 0xD8;
      oversized[2] = 0xFF;
      expect(EkycImageClientValidator.validate(oversized, 'huge.jpg', 'Mặt trước'), isNotNull);
    });
  });
}
