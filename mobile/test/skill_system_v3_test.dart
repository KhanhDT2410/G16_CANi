import 'package:flutter_test/flutter_test.dart';

import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

void main() {
  group('Skill System V3 - SkillHubColors mapping & hierarchy', () {
    test('Maps all 7 backend SkillTier ranks into 3 Vietnamese development levels cleanly', () {
      expect(SkillHubColors.getDevelopmentLevelLabel('STARTER'), 'Mới bắt đầu');
      expect(SkillHubColors.getDevelopmentLevelLabel('BRONZE'), 'Mới bắt đầu');

      expect(SkillHubColors.getDevelopmentLevelLabel('SILVER'), 'Có kinh nghiệm');
      expect(SkillHubColors.getDevelopmentLevelLabel('GOLD'), 'Có kinh nghiệm');
      expect(SkillHubColors.getDevelopmentLevelLabel('PLATINUM'), 'Có kinh nghiệm');

      expect(SkillHubColors.getDevelopmentLevelLabel('DIAMOND'), 'Thành thạo');
      expect(SkillHubColors.getDevelopmentLevelLabel('MASTER'), 'Thành thạo');
    });

    test('Formats all 7 backend SkillTier rank titles in Vietnamese without raw enum codes', () {
      expect(SkillHubColors.getTierTitle('STARTER'), 'Khởi đầu');
      expect(SkillHubColors.getTierTitle('BRONZE'), 'Đồng');
      expect(SkillHubColors.getTierTitle('SILVER'), 'Bạc');
      expect(SkillHubColors.getTierTitle('GOLD'), 'Vàng');
      expect(SkillHubColors.getTierTitle('PLATINUM'), 'Bạch kim');
      expect(SkillHubColors.getTierTitle('DIAMOND'), 'Kim cương');
      expect(SkillHubColors.getTierTitle('MASTER'), 'Tinh anh');
      expect(SkillHubColors.getDevelopmentAndTierLabel('GOLD'), 'Có kinh nghiệm (Vàng)');
    });

    test('Maps VerificationTier labels independently of SkillTier', () {
      expect(SkillHubColors.getVerificationTierLabel('SELF_DECLARED'), 'Tự khai báo');
      expect(SkillHubColors.getVerificationTierLabel('AI_DOCUMENT'), 'Đã xác thực tài liệu');
      expect(
        SkillHubColors.getVerificationTierLabel('PLATFORM_PROVEN'),
        'Đã chứng minh qua ca làm',
      );
    });

    test('resolveHighestTier returns highest SkillTier regardless of item order', () {
      const spStarter = WorkerSkillPointModel(
        id: 'sp-1',
        jobType: 'Phát tờ rơi',
        spVerify: 0,
        spExperience: 20,
        spRating: 0,
        totalSp: 20,
        tier: 'STARTER',
        totalShiftsCompleted: 1,
        avgRating: 0.0,
      );
      const spDiamond = WorkerSkillPointModel(
        id: 'sp-2',
        jobType: 'Phục vụ bàn',
        spVerify: 50,
        spExperience: 400,
        spRating: 270,
        totalSp: 720,
        tier: 'DIAMOND',
        totalShiftsCompleted: 20,
        avgRating: 4.9,
      );
      const spSilver = WorkerSkillPointModel(
        id: 'sp-3',
        jobType: 'Lễ tân',
        spVerify: 25,
        spExperience: 100,
        spRating: 95,
        totalSp: 220,
        tier: 'SILVER',
        totalShiftsCompleted: 5,
        avgRating: 4.7,
      );

      expect(SkillHubColors.resolveHighestTier([spStarter, spDiamond, spSilver]), 'DIAMOND');
      expect(SkillHubColors.resolveHighestTier(const []), 'STARTER');
    });
  });

  group('Skill System V3 - Models parsing & anti-fake rating guards', () {
    test('WorkerSkillPointModel.fromJson parses skillId, ratingCount and defaults avgRating to 0.0 (never fake 5.0)', () {
      final unrated = WorkerSkillPointModel.fromJson({
        'id': 'wsp-1',
        'skillId': '11111111-1111-1111-1111-111111111111',
        'jobType': 'Phục vụ bàn',
        'spVerify': 25,
        'spExperience': 20,
        'totalSp': 45,
        'tier': 'STARTER',
        'totalShiftsCompleted': 1,
      });

      expect(unrated.skillId, '11111111-1111-1111-1111-111111111111');
      expect(unrated.ratingCount, 0);
      expect(unrated.avgRating, 0.0);
      expect(unrated.hasRealRating, isFalse);

      final rated = WorkerSkillPointModel.fromJson({
        'id': 'wsp-2',
        'skillId': '22222222-2222-2222-2222-222222222222',
        'jobType': 'Pha chế',
        'spVerify': 50,
        'spExperience': 60,
        'spRating': 90,
        'totalSp': 200,
        'tier': 'SILVER',
        'totalShiftsCompleted': 3,
        'avgRating': 4.85,
        'ratingCount': 2,
      });

      expect(rated.ratingCount, 2);
      expect(rated.avgRating, 4.85);
      expect(rated.hasRealRating, isTrue);
    });

    test('WorkerSkillItem.tierBadgeLabel reflects expired AI certificate cleanly', () {
      final expiredAi = WorkerSkillItem.fromJson({
        'id': 'ws-1',
        'skillId': '33333333-3333-3333-3333-333333333333',
        'skillName': 'Bảo vệ sự kiện',
        'verificationTier': 'AI_DOCUMENT',
        'verificationStatus': 'APPROVED',
        'isExpired': true,
      });
      expect(expiredAi.tierBadgeLabel, 'Chứng chỉ hết hạn');

      final activeProven = WorkerSkillItem.fromJson({
        'id': 'ws-2',
        'skillId': '44444444-4444-4444-4444-444444444444',
        'skillName': 'Phục vụ bàn',
        'verificationTier': 'PLATFORM_PROVEN',
        'verificationStatus': 'APPROVED',
        'isExpired': false,
      });
      expect(activeProven.tierBadgeLabel, 'Đã chứng minh qua ca làm');
    });

    test('JobProposalItem.fromJson parses performed skill, matched skills and recommendationReasonVi', () {
      final proposal = JobProposalItem.fromJson({
        'proposalId': 'prop-1',
        'shiftId': 'shift-1',
        'eventId': 'event-1',
        'eventName': 'Gala Dinner 2026',
        'venueAddress': 'Quận 1, TP.HCM',
        'jobType': 'Phục vụ tiệc',
        'description': 'Phục vụ bàn VIP',
        'startTime': '2026-12-01T08:00:00Z',
        'endTime': '2026-12-01T12:00:00Z',
        'performedSkillId': '55555555-5555-5555-5555-555555555555',
        'performedSkillName': 'Phục vụ bàn',
        'requiredSkillNames': ['Phục vụ bàn'],
        'preferredSkillNames': ['Tiếng Anh giao tiếp'],
        'matchedSkillNames': ['Phục vụ bàn'],
        'recommendationReasonVi': 'Kỹ năng đã xác thực phù hợp với ca (Phục vụ bàn)',
        'hourlyWage': 60000,
        'allowance': 20000,
        'totalEstimatedPay': 260000,
        'matchScore': 92.5,
        'status': 'PENDING',
      });

      expect(proposal.performedSkillId, '55555555-5555-5555-5555-555555555555');
      expect(proposal.performedSkillName, 'Phục vụ bàn');
      expect(proposal.requiredSkillNames, ['Phục vụ bàn']);
      expect(proposal.preferredSkillNames, ['Tiếng Anh giao tiếp']);
      expect(proposal.matchedSkillNames, ['Phục vụ bàn']);
      expect(proposal.recommendationReasonVi, contains('Kỹ năng đã xác thực'));
    });

    test('SkillVerificationResult classifies failureCode flags cleanly for Step 3 button routing', () {
      final unsupported = SkillVerificationResult.fromJson({
        'id': 'ver-1',
        'workerId': 'w-1',
        'jobType': 'Pha chế',
        'status': 'REJECTED',
        'verificationSource': 'UNSUPPORTED_SOURCE',
        'failureCode': 'UNSUPPORTED_SOURCE',
        'routingReason': 'CANi chưa hỗ trợ tra cứu chứng chỉ của đơn vị này',
      });
      expect(unsupported.isUnsupportedIssuer, isTrue);
      expect(unsupported.isTempFailure, isFalse);
      expect(unsupported.isSelfDeclaredCv, isFalse);
      expect(unsupported.isEkycRequired, isFalse);

      final cvResult = SkillVerificationResult.fromJson({
        'id': 'ver-2',
        'workerId': 'w-1',
        'jobType': 'Pha chế',
        'status': 'REJECTED',
        'verificationSource': 'UNSUPPORTED_SOURCE',
        'failureCode': 'CV_SELF_DECLARED',
        'routingReason': 'Tài liệu là CV tự khai',
      });
      expect(cvResult.isSelfDeclaredCv, isTrue);
      expect(cvResult.isUnsupportedIssuer, isFalse);

      final tempFail = SkillVerificationResult.fromJson({
        'id': 'ver-3',
        'workerId': 'w-1',
        'jobType': 'Pha chế',
        'status': 'VERIFICATION_FAILED',
        'failureCode': 'TEMPORARY_FAILURE',
      });
      expect(tempFail.isTempFailure, isTrue);
      expect(tempFail.isUnsupportedIssuer, isFalse);

      final ekycReq = SkillVerificationResult.fromJson({
        'id': 'ver-4',
        'workerId': 'w-1',
        'jobType': 'Pha chế',
        'status': 'REJECTED',
        'failureCode': 'EKYC_REQUIRED',
      });
      expect(ekycReq.isEkycRequired, isTrue);
      expect(ekycReq.isUnsupportedIssuer, isFalse);
    });
  });
}
