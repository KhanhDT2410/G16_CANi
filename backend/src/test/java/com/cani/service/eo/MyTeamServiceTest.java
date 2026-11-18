package com.cani.service.eo;

import com.cani.dto.team.AddMyTeamMemberRequestDto;
import com.cani.dto.team.MyTeamMemberResponseDto;
import com.cani.dto.team.UpdateMyTeamMemberRequestDto;
import com.cani.entity.EoWorker;
import com.cani.entity.EventOrganizer;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EoWorkerRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.MyTeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for MyTeamService (UC-65, UC-66, UC-67)")
class MyTeamServiceTest {

    @Mock
    private EoWorkerRepository eoWorkerRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MyTeamService myTeamService;

    private UUID eoUserId;
    private UUID eoId;
    private UUID workerId;
    private UUID workerUserId;

    private EventOrganizer mockEo;
    private Worker mockWorker;
    private User mockWorkerUser;
    private EoWorker mockEoWorker;

    @BeforeEach
    void setUp() {
        eoUserId = UUID.randomUUID();
        eoId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();

        User mockEoUser = User.builder()
                .userId(eoUserId)
                .email("eo@cani.vn")
                .fullName("Công ty Sự kiện Sao Mai")
                .role(UserRole.EVENT_ORGANIZER)
                .build();

        mockEo = EventOrganizer.builder()
                .eoId(eoId)
                .user(mockEoUser)
                .companyName("Sao Mai Event")
                .build();

        mockWorkerUser = User.builder()
                .userId(workerUserId)
                .email("worker@cani.vn")
                .phone("0987654321")
                .fullName("Nguyễn Văn An")
                .avatarUrl("https://cani.vn/avatar/an.png")
                .role(UserRole.WORKER)
                .build();

        mockWorker = Worker.builder()
                .workerId(workerId)
                .user(mockWorkerUser)
                .fullName("Nguyễn Văn An")
                .skillTier(SkillTier.SILVER)
                .reliabilityScore(95)
                .ratingAvg(new BigDecimal("4.80"))
                .isOnline(true)
                .build();

        mockEoWorker = EoWorker.builder()
                .eoWorkerId(UUID.randomUUID())
                .eventOrganizer(mockEo)
                .worker(mockWorker)
                .internalNote("Thợ chăm chỉ, đúng giờ")
                .isFavorited(true)
                .isBlacklisted(false)
                .totalShiftsTogether(5)
                .completionRate(new BigDecimal("100.00"))
                .avgRatingGiven(new BigDecimal("5.00"))
                .lastWorkedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("UC-65: Xem danh sách My Team (getMyTeam)")
    class GetMyTeamTests {

        @Test
        @DisplayName("Thành công: Trả về danh sách thành viên My Team đầy đủ thông tin")
        void getMyTeam_Success() {
            // Arrange
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findMyTeamMembers(eoId, "An", true)).thenReturn(List.of(mockEoWorker));

            // Act
            List<MyTeamMemberResponseDto> result = myTeamService.getMyTeam(eoUserId, "An", true);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            MyTeamMemberResponseDto member = result.get(0);
            assertEquals(workerId, member.getWorkerId());
            assertEquals("Nguyễn Văn An", member.getFullName());
            assertEquals("0987654321", member.getPhone());
            assertEquals(SkillTier.SILVER, member.getSkillTier());
            assertEquals(95, member.getReliabilityScore());
            assertTrue(member.getIsFavorited());
            assertEquals("Thợ chăm chỉ, đúng giờ", member.getInternalNote());

            // Verify
            verify(eventOrganizerRepository, times(1)).findByUser_UserId(eoUserId);
            verify(eoWorkerRepository, times(1)).findMyTeamMembers(eoId, "An", true);
        }

        @Test
        @DisplayName("Thành công: Trả về danh sách rỗng khi chưa có thợ nào")
        void getMyTeam_EmptyList() {
            // Arrange
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findMyTeamMembers(eoId, null, null)).thenReturn(Collections.emptyList());

            // Act
            List<MyTeamMemberResponseDto> result = myTeamService.getMyTeam(eoUserId, null, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Thất bại: EO không tồn tại ném ResourceNotFoundException")
        void getMyTeam_EoNotFound() {
            // Arrange
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> myTeamService.getMyTeam(eoUserId, null, null));
            verify(eoWorkerRepository, never()).findMyTeamMembers(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("UC-66: Thêm Worker vào My Team (addWorkerToMyTeam)")
    class AddWorkerToMyTeamTests {

        @Test
        @DisplayName("Thành công: Thêm bằng workerId hợp lệ")
        void addWorker_Success_WithWorkerId() {
            // Arrange
            AddMyTeamMemberRequestDto request = AddMyTeamMemberRequestDto.builder()
                    .workerId(workerId)
                    .internalNote("Thợ phụ trách set âm thanh")
                    .isFavorited(true)
                    .build();

            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
            when(eoWorkerRepository.existsByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId)).thenReturn(false);
            when(eoWorkerRepository.save(any(EoWorker.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            MyTeamMemberResponseDto result = myTeamService.addWorkerToMyTeam(eoUserId, request);

            // Assert
            assertNotNull(result);
            assertEquals(workerId, result.getWorkerId());
            assertEquals("Nguyễn Văn An", result.getFullName());
            assertEquals("Thợ phụ trách set âm thanh", result.getInternalNote());
            assertTrue(result.getIsFavorited());
            assertFalse(result.getIsBlacklisted());

            // Verify
            verify(eoWorkerRepository, times(1)).save(any(EoWorker.class));
        }

        @Test
        @DisplayName("Thành công: Thêm bằng số điện thoại khi không có workerId")
        void addWorker_Success_WithPhoneNumber() {
            // Arrange
            AddMyTeamMemberRequestDto request = AddMyTeamMemberRequestDto.builder()
                    .phone("0987654321")
                    .internalNote("Tìm kiếm qua SĐT")
                    .build();

            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(userRepository.findByPhone("0987654321")).thenReturn(Optional.of(mockWorkerUser));
            when(workerRepository.findByUser_UserId(workerUserId)).thenReturn(Optional.of(mockWorker));
            when(eoWorkerRepository.existsByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId)).thenReturn(false);
            when(eoWorkerRepository.save(any(EoWorker.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            MyTeamMemberResponseDto result = myTeamService.addWorkerToMyTeam(eoUserId, request);

            // Assert
            assertNotNull(result);
            assertEquals("Nguyễn Văn An", result.getFullName());
            verify(userRepository, times(1)).findByPhone("0987654321");
            verify(workerRepository, times(1)).findByUser_UserId(workerUserId);
        }

        @Test
        @DisplayName("Thất bại: Worker đã có trong My Team ném BadRequestException")
        void addWorker_AlreadyExists_ThrowsBadRequest() {
            // Arrange
            AddMyTeamMemberRequestDto request = AddMyTeamMemberRequestDto.builder()
                    .workerId(workerId)
                    .build();

            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(workerRepository.findById(workerId)).thenReturn(Optional.of(mockWorker));
            when(eoWorkerRepository.existsByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId)).thenReturn(true);

            // Act & Assert
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> myTeamService.addWorkerToMyTeam(eoUserId, request));
            assertTrue(ex.getMessage().contains("đã có trong danh sách My Team"));
            verify(eoWorkerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Thất bại: Không truyền cả workerId lẫn phone ném BadRequestException")
        void addWorker_MissingIdentifier_ThrowsBadRequest() {
            // Arrange
            AddMyTeamMemberRequestDto request = AddMyTeamMemberRequestDto.builder().build();
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));

            // Act & Assert
            assertThrows(BadRequestException.class, () -> myTeamService.addWorkerToMyTeam(eoUserId, request));
        }

        @Test
        @DisplayName("Thất bại: WorkerId không tồn tại ném ResourceNotFoundException")
        void addWorker_WorkerNotFound() {
            // Arrange
            AddMyTeamMemberRequestDto request = AddMyTeamMemberRequestDto.builder()
                    .workerId(workerId)
                    .build();
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> myTeamService.addWorkerToMyTeam(eoUserId, request));
        }
    }

    @Nested
    @DisplayName("Cập nhật thông tin thành viên (updateMyTeamMember)")
    class UpdateMyTeamMemberTests {

        @Test
        @DisplayName("Thành công: Cập nhật ghi chú và gắn cờ blacklist")
        void updateMember_Success() {
            // Arrange
            UpdateMyTeamMemberRequestDto request = UpdateMyTeamMemberRequestDto.builder()
                    .internalNote("Thợ đến muộn, cần theo dõi thêm")
                    .isFavorited(false)
                    .isBlacklisted(true)
                    .build();

            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId))
                    .thenReturn(Optional.of(mockEoWorker));
            when(eoWorkerRepository.save(any(EoWorker.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            MyTeamMemberResponseDto result = myTeamService.updateMyTeamMember(eoUserId, workerId, request);

            // Assert
            assertNotNull(result);
            assertEquals("Thợ đến muộn, cần theo dõi thêm", result.getInternalNote());
            assertFalse(result.getIsFavorited());
            assertTrue(result.getIsBlacklisted());
            verify(eoWorkerRepository, times(1)).save(mockEoWorker);
        }

        @Test
        @DisplayName("Thất bại: Thành viên không trong team ném ResourceNotFoundException")
        void updateMember_NotFound() {
            // Arrange
            UpdateMyTeamMemberRequestDto request = UpdateMyTeamMemberRequestDto.builder().build();
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> myTeamService.updateMyTeamMember(eoUserId, workerId, request));
        }
    }

    @Nested
    @DisplayName("UC-67: Xóa Worker khỏi My Team (removeWorkerFromMyTeam)")
    class RemoveWorkerFromMyTeamTests {

        @Test
        @DisplayName("Thành công: Xóa êm dịu Worker khỏi My Team")
        void removeWorker_Success() {
            // Arrange
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId))
                    .thenReturn(Optional.of(mockEoWorker));

            // Act
            myTeamService.removeWorkerFromMyTeam(eoUserId, workerId);

            // Assert & Verify
            verify(eoWorkerRepository, times(1)).delete(mockEoWorker);
        }

        @Test
        @DisplayName("Thất bại: Xóa thợ không tồn tại trong team ném ResourceNotFoundException")
        void removeWorker_NotFound() {
            // Arrange
            when(eventOrganizerRepository.findByUser_UserId(eoUserId)).thenReturn(Optional.of(mockEo));
            when(eoWorkerRepository.findByEventOrganizer_EoIdAndWorker_WorkerId(eoId, workerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> myTeamService.removeWorkerFromMyTeam(eoUserId, workerId));
            verify(eoWorkerRepository, never()).delete(any());
        }
    }
}
